@file:Suppress("MemberVisibilityCanBePrivate")

package edu.illinois.cs.cs125.jenisol.core.generators

import edu.illinois.cs.cs125.jenisol.core.EdgeType
import edu.illinois.cs.cs125.jenisol.core.FixedParameters
import edu.illinois.cs.cs125.jenisol.core.ParameterGroup
import edu.illinois.cs.cs125.jenisol.core.RandomGroup
import edu.illinois.cs.cs125.jenisol.core.RandomParameters
import edu.illinois.cs.cs125.jenisol.core.RandomType
import edu.illinois.cs.cs125.jenisol.core.SimpleType
import edu.illinois.cs.cs125.jenisol.core.Solution
import edu.illinois.cs.cs125.jenisol.core.asArray
import edu.illinois.cs.cs125.jenisol.core.deepCopy
import edu.illinois.cs.cs125.jenisol.core.isEdgeType
import edu.illinois.cs.cs125.jenisol.core.isFixedParameters
import edu.illinois.cs.cs125.jenisol.core.isRandomParameters
import edu.illinois.cs.cs125.jenisol.core.isRandomType
import edu.illinois.cs.cs125.jenisol.core.isSimpleType
import java.lang.ClassCastException
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.Type
import kotlin.random.Random

@Suppress("ArrayInDataClass")
data class Parameters(
    val solution: Array<Any?>,
    val submission: Array<Any?>,
    val solutionCopy: Array<Any?>,
    val submissionCopy: Array<Any?>,
    val type: Type,
    val complexity: Complexity = ZeroComplexity
) {
    enum class Type { EMPTY, SIMPLE, EDGE, MIXED, RANDOM, FIXED_FIELD, RANDOM_METHOD }

    override fun equals(other: Any?) = when {
        this === other -> true
        other is Parameters -> solutionCopy.contentDeepEquals(other.solutionCopy)
        else -> false
    }

    override fun hashCode() = solutionCopy.contentHashCode()
}

interface ParametersGenerator {
    val simple: List<Parameters>
    val edge: List<Parameters>
    val mixed: List<Parameters>
    fun random(complexity: Complexity): Parameters
}

typealias ParametersGeneratorGenerator = (random: Random) -> ParametersGenerator

class GeneratorFactory(private val executables: Set<Executable>, val solution: Solution) {
    val solutionClass = solution.solution

    private val methodParameterGenerators = executables.map { it to MethodParametersGeneratorGenerator(it) }.toMap()

    private val typesNeeded = methodParameterGenerators
        .filter { (_, generator) -> generator.needsParameterGenerator }
        .keys
    private val typeGenerators: Map<Type, TypeGeneratorGenerator>

    init {
        val neededTypes = executables
            .filter { it in typesNeeded }
            .map { it.parameterTypes }
            .toTypedArray().flatten().distinct().toSet()
        val simple: MutableMap<Class<*>, Set<Any>> = mutableMapOf()
        val edge: MutableMap<Class<*>, Set<Any?>> = mutableMapOf()
        solutionClass.declaredFields
            .filter { it.isSimpleType() || it.isEdgeType() }
            .forEach { field ->
                val simpleName = SimpleType::class.java.simpleName
                val edgeName = EdgeType::class.java.simpleName
                check(!(field.isSimpleType() && field.isEdgeType())) {
                    "Cannot use both @$simpleName and @$edgeName annotations on same field"
                }
                if (field.isSimpleType()) {
                    SimpleType.validate(field).also { klass ->
                        check(klass !in simple) { "Duplicate @$simpleName annotation for type ${klass.name}" }
                        check(klass in neededTypes) {
                            "@$simpleName annotation for type ${klass.name} that is not used by the solution"
                        }
                        @Suppress("UNCHECKED_CAST")
                        simple[klass] = field.get(null).asArray().toSet().also { simpleCases ->
                            check(simpleCases.none { it == null }) { "@$simpleName values should not include null" }
                        } as Set<Any>
                    }
                }
                if (field.isEdgeType()) {
                    EdgeType.validate(field).also { klass ->
                        check(klass !in edge) { "Duplicate @$edgeName annotation for type ${klass.name}" }
                        check(klass in neededTypes) {
                            "@$edgeName annotation for type ${klass.name} that is not used by the solution"
                        }
                        edge[klass] = field.get(null).asArray().toSet()
                    }
                }
            }
        val rand: MutableMap<Class<*>, Method> = mutableMapOf()
        solutionClass.declaredMethods
            .filter { it.isRandomType() }
            .forEach { method ->
                val randName = Random::class.java.simpleName
                RandomType.validate(method).also { klass ->
                    check(klass !in rand) { "Duplicate @$randName method for type ${klass.name}" }
                    check(klass in neededTypes) {
                        "@$randName annotation for type ${klass.name} that is not used by the solution"
                    }
                    rand[klass] = method
                }
            }
        val generatorMappings = (simple.keys + edge.keys + rand.keys).toSet().map { klass ->
            klass to { random: Random ->
                OverrideTypeGenerator(
                    klass,
                    simple[klass],
                    edge[klass],
                    rand[klass],
                    random,
                    Defaults[klass]
                ) as TypeGenerator<Any>
            }
        }.toMutableList()

        if (solution.noReceiver) {
            check(solutionClass !in neededTypes) {
                "Incorrectly calculated whether we needed a receiver: " +
                    "${solution.noReceiver} v. ${solutionClass !in neededTypes}"
            }
        }

        if (solutionClass in neededTypes) {
            check(solutionClass !in simple.keys && solutionClass !in edge.keys && solutionClass !in rand.keys) {
                "Type generation annotations not supported for receiver types"
            }
            // Add this so the next check doesn't fail.
            // The receiver generator cannot be set up until the submission class is available
            @Suppress("RedundantLambdaArrow")
            generatorMappings.add(solutionClass to { _: Random -> UnconfiguredReceiverGenerator })
        }
        typeGenerators = generatorMappings.toMap()
    }

    // Check to make sure we can generate all needed parameters
    init {
        executables.filter { it in typesNeeded }.forEach { executable ->
            TypeParameterGenerator(executable.parameters, typeGenerators)
        }
    }

    fun get(
        random: Random = Random, settings: Solution.Settings, receiverGenerator: ReceiverGenerator? = null,
        forExecutables: Set<Executable> = executables, from: Generators? = null
    ): Generators {
        val typeGeneratorsWithReceiver = object : Map<Type, TypeGeneratorGenerator> by typeGenerators {
            override fun get(key: Type): TypeGeneratorGenerator? {
                if (key == solutionClass) {
                    check(receiverGenerator != null) { "Needed a receiver generator that was not provided" }
                    return { receiverGenerator }
                } else {
                    return typeGenerators[key]
                }
            }
        }
        return forExecutables
            .map { executable ->
                if (from != null && executable in from) {
                    from[executable]
                }
                if (executable.parameters.isEmpty()) {
                    executable to EmptyParameterMethodGenerator()
                } else {
                    val parameterGenerator = { random: Random ->
                        TypeParameterGenerator(executable.parameters, typeGeneratorsWithReceiver, random)
                    }
                    executable to (
                        methodParameterGenerators[executable]?.generate(
                            parameterGenerator,
                            settings,
                            random
                        ) ?: error("Didn't find a method parameter generator that should exist")
                        )
                }
            }
            .toMap()
            .let {
                Generators(it)
            }
    }
}

class Generators(private val map: Map<Executable, ExecutableGenerator>) : Map<Executable, ExecutableGenerator> by map

interface ExecutableGenerator {
    val fixed: List<Parameters>
    fun random(complexity: Complexity): Parameters
    fun generate(): Parameters
    fun next()
    fun prev()
}

class MethodParametersGeneratorGenerator(target: Executable) {
    val fixedParameters: Collection<ParameterGroup>?
    val randomParameters: Method?

    init {
        val parameterTypes = target.parameterTypes.map { type -> type as Type }.toTypedArray()
        fixedParameters = target.declaringClass.declaredFields
            .filter { field -> field.isFixedParameters() }
            .filter { field ->
                FixedParameters.validate(field).compareBoxed(parameterTypes)
            }.also {
                check(it.size <= 1) {
                    "Multiple @${FixedParameters.name} annotations match method ${target.name}"
                }
            }.firstOrNull()?.let { field ->
                val values = field.get(null)
                check(values is Collection<*>) { "@${FixedParameters.name} field does not contain a collection" }
                check(values.isNotEmpty()) { "@${FixedParameters.name} field contains as empty collection" }
                try {
                    @Suppress("UNCHECKED_CAST")
                    values as Collection<ParameterGroup>
                } catch (e: ClassCastException) {
                    error("@${FixedParameters.name} field does not contain a collection of parameter groups")
                }
                values.forEach {
                    val solutionParameters = it.deepCopy()
                    val submissionParameters = it.deepCopy()
                    check(solutionParameters !== submissionParameters) {
                        "@${FixedParameters.name} field produces referentially equal copies"
                    }
                    check(solutionParameters == submissionParameters) {
                        "@${FixedParameters.name} field does not produce equal copies"
                    }
                }
                values
            }
        randomParameters = target.declaringClass.declaredMethods
            .filter { method -> method.isRandomParameters() }
            .filter { method -> RandomParameters.validate(method).compareBoxed(parameterTypes) }
            .also {
                check(it.size <= 1) {
                    "Multiple @${RandomParameters.name} annotations match method ${target.name}"
                }
            }.firstOrNull()
    }

    val needsParameterGenerator = fixedParameters == null || randomParameters == null
    fun generate(
        parametersGenerator: ParametersGeneratorGenerator?,
        settings: Solution.Settings,
        random: Random = Random
    ) = ConfiguredParametersGenerator(parametersGenerator, settings, random, fixedParameters, randomParameters)
}

class ConfiguredParametersGenerator(
    parametersGenerator: ParametersGeneratorGenerator?,
    private val settings: Solution.Settings,
    private val random: Random = Random,
    overrideFixed: Collection<ParameterGroup>? = null,
    private val overrideRandom: Method? = null
) : ExecutableGenerator {
    private val generator = if (overrideFixed != null && overrideRandom != null) {
        null
    } else {
        check(parametersGenerator != null) { "Parameter generator required but not provided" }
        parametersGenerator(random)
    }

    private fun Collection<ParameterGroup>.toFixedParameters(): List<Parameters> = map {
        Parameters(
            it.deepCopy().toArray(),
            it.deepCopy().toArray(),
            it.deepCopy().toArray(),
            it.deepCopy().toArray(),
            Parameters.Type.FIXED_FIELD
        )
    }

    private fun List<Parameters>.trim(count: Int) = if (this.size <= count) {
        this
    } else {
        this.shuffled(random).take(count)
    }

    override val fixed: List<Parameters> by lazy {
        if (overrideFixed != null) {
            overrideFixed.toFixedParameters()
        } else {
            check(generator != null) { "Automatic parameter generator was unexpectedly null" }
            generator.let {
                it.simple.trim(settings.simpleCount) +
                    it.edge.trim(settings.edgeCount) +
                    it.mixed.trim(settings.mixedCount)
            }.trim(settings.fixedCount)
        }
    }

    private val randomPair = RandomGroup(random.nextLong())
    private var index = 0
    private var bound: Complexity? = null
    private val complexity = Complexity()
    private var randomStarted = false

    override fun random(complexity: Complexity): Parameters = if (overrideRandom != null) {
        check(randomPair.synced) { "Random pair was out of sync before parameter generation" }
        val solutionParameters =
            overrideRandom.invoke(null, complexity.level, randomPair.solution) as ParameterGroup
        val submissionParameters =
            overrideRandom.invoke(null, complexity.level, randomPair.submission) as ParameterGroup
        val solutionCopyParameters =
            overrideRandom.invoke(null, complexity.level, randomPair.solutionCopy) as ParameterGroup
        val submissionCopyParameters =
            overrideRandom.invoke(null, complexity.level, randomPair.submissionCopy) as ParameterGroup
        check(randomPair.synced) { "Random pair was out of sync after parameter generation" }
        check(setOf(solutionParameters, submissionParameters, submissionCopyParameters).size == 1) {
            "@${RandomParameters.name} did not generate equal parameters"
        }
        Parameters(
            solutionParameters.toArray(),
            submissionParameters.toArray(),
            solutionCopyParameters.toArray(),
            submissionCopyParameters.toArray(),
            Parameters.Type.RANDOM_METHOD,
            complexity
        )
    } else {
        check(generator != null) { "Automatic parameter generator was unexpectedly null" }
        generator.random(complexity)
    }

    override fun generate(): Parameters {
        return if (index in fixed.indices) {
            fixed[index]
        } else {
            random(bound ?: complexity).also { randomStarted = true }
        }.also {
            index++
        }
    }

    override fun next() {
        if (randomStarted) {
            complexity.next()
        }
    }

    override fun prev() {
        if (randomStarted) {
            if (bound == null) {
                bound = complexity
            } else {
                bound!!.prev()
            }
        }
    }
}

@Suppress("EmptyFunctionBlock")
class EmptyParameterMethodGenerator : ExecutableGenerator {
    override val fixed = listOf(Parameters(arrayOf(), arrayOf(), arrayOf(), arrayOf(), Parameters.Type.EMPTY))
    override fun random(complexity: Complexity) = fixed.first()

    override fun prev() {}
    override fun next() {}
    override fun generate(): Parameters = fixed.first()
}

class TypeParameterGenerator(
    parameters: Array<out Parameter>,
    generators: Map<Type, TypeGeneratorGenerator> = mapOf(),
    private val random: Random = Random
) : ParametersGenerator {
    private val parameterGenerators = parameters.map {
        val type = it.parameterizedType
        if (type in generators) {
            generators[type]
        } else {
            require(type is Class<*>) { "No default generators are registered for non-class types" }
            Defaults[type]
        }?.invoke(random) ?: error(
            "Couldn't find generator for parameter ${it.name} with type ${it.parameterizedType.typeName}"
        )
    }

    private fun List<Set<Value<*>>>.combine(type: Parameters.Type) = product().map { list ->
        list.map {
            check(it is Value<*>) { "Didn't find the right type in our parameter list" }
            Quad(it.solution, it.submission, it.submissionCopy, it.submissionCopy)
        }.unzip().let { (solution, submission, solutionCopy, submissionCopy) ->
            Parameters(
                solution.toTypedArray(),
                submission.toTypedArray(),
                solutionCopy.toTypedArray(),
                submissionCopy.toTypedArray(),
                type
            )
        }
    }

    override val simple by lazy {
        parameterGenerators.map { it.simple }.combine(Parameters.Type.SIMPLE)
    }
    override val edge by lazy {
        parameterGenerators.map { it.edge }.combine(Parameters.Type.EDGE)
    }
    override val mixed by lazy {
        parameterGenerators.map { it.simple + it.edge }.combine(Parameters.Type.MIXED).filter {
            it !in simple && it !in edge
        }
    }

    override fun random(complexity: Complexity): Parameters {
        return parameterGenerators.map { it.random(complexity) }.map {
            Quad(it.solution, it.submission, it.solutionCopy, it.submissionCopy)
        }.unzip().let { (solution, submission, solutionCopy, submissionCopy) ->
            Parameters(
                solution.toTypedArray(),
                submission.toTypedArray(),
                solutionCopy.toTypedArray(),
                submissionCopy.toTypedArray(),
                Parameters.Type.RANDOM,
                complexity
            )
        }
    }
}

fun List<*>.product() = fold(listOf(listOf<Any?>())) { acc, set ->
    require(set is Collection<*>) { "Error computing product" }
    acc.flatMap { list -> set.map { element -> list + element } }
}.toSet()

data class Quad<T>(val first: T, val second: T, val third: T, val fourth: T)

@Suppress("MagicNumber")
fun <E> List<Quad<E>>.unzip(): List<List<E>> {
    @Suppress("RemoveExplicitTypeArguments")
    return fold(listOf(ArrayList<E>(), ArrayList<E>(), ArrayList<E>(), ArrayList<E>())) { r, i ->
        r[0].add(i.first)
        r[1].add(i.second)
        r[2].add(i.third)
        r[3].add(i.fourth)
        r
    }
}