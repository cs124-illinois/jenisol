@file:Suppress("MagicNumber", "TooManyFunctions")

package edu.illinois.cs.cs125.jenisol.core.generators

import com.rits.cloning.Cloner
import edu.illinois.cs.cs125.jenisol.core.EdgeType
import edu.illinois.cs.cs125.jenisol.core.RandomType
import edu.illinois.cs.cs125.jenisol.core.SimpleType
import edu.illinois.cs.cs125.jenisol.core.TestRunner
import edu.illinois.cs.cs125.jenisol.core.cleanTypeName
import edu.illinois.cs.cs125.jenisol.core.deepCopy
import edu.illinois.cs.cs125.jenisol.core.unwrap
import java.lang.IllegalStateException
import java.lang.reflect.Array
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.nio.ByteBuffer
import kotlin.math.pow
import kotlin.random.Random
import kotlin.reflect.jvm.javaMethod

class Complexity(var level: Int = MIN) {
    init {
        require(level in 0..MAX) { "Invalid complexity value: $level" }
    }

    fun next(): Complexity {
        if (level < MAX) {
            level++
        }
        return this
    }

    fun prev(): Complexity {
        if (level > MIN) {
            level--
        }
        return this
    }

    fun power(base: Int = 2) = base.toDouble().pow(level.toDouble()).toLong()

    companion object {
        const val MIN = 1
        const val MAX = 8
        val ALL = (MIN..MAX).map { Complexity(it) }
    }
}

val ZeroComplexity = Complexity(0)

open class Value<T>(
    val solution: T,
    val submission: T,
    val solutionCopy: T,
    val submissionCopy: T,
    val unmodifiedCopy: T,
    val complexity: Complexity,
)

fun <T> cloneOrCopy(value: T, cloner: Cloner, fastCopy: Boolean, copier: () -> T): T = if (fastCopy) {
    cloner.deepClone(value)
} else {
    copier()
}

interface TypeGenerator<T> {
    val simple: Set<Value<T>>
    val edge: Set<Value<T?>>
    fun random(complexity: Complexity, runner: TestRunner?): Value<T>
}

@Suppress("UNCHECKED_CAST", "LongParameterList")
class OverrideTypeGenerator(
    private val klass: Class<*>,
    simpleValues: Set<Any>? = null,
    simpleMethod: Method? = null,
    edgeValues: Set<Any?>? = null,
    edgeMethod: Method? = null,
    private val rand: Method? = null,
    random: Random = Random,
    private val cloner: Cloner,
    defaultGenerator: TypeGeneratorGenerator? = null,
) : TypeGenerator<Any> {
    init {
        check(!(simpleValues != null && simpleMethod != null)) {
            "Can't provide both simple values and a simple generation method"
        }
        check(!(edgeValues != null && edgeMethod != null)) {
            "Can't provide both simple values and a simple generation method"
        }
    }

    private val name: String = klass.name

    @Suppress("ComplexCondition")
    private val default =
        if ((simpleValues == null && simpleMethod == null) ||
            (edgeValues == null && edgeMethod == null) ||
            rand == null
        ) {
            check(defaultGenerator != null) { "Override type generator for $name needs default generator" }
            defaultGenerator(random, cloner)
        } else {
            null
        }

    private val simpleFastCopy = simpleMethod?.getAnnotation(SimpleType::class.java)?.fastCopy ?: false

    private val simpleOverride: Set<Value<Any>>? = when {
        simpleValues != null -> simpleValues.values(ZeroComplexity, cloner)
        simpleMethod != null -> {
            val solution = simpleMethod.invoke(null) as kotlin.Array<*>
            check(solution.none { it == null }) {
                "@SimpleType methods must not return arrays containing null"
            }
            @Suppress("TooGenericExceptionCaught")
            val submission = try {
                cloneOrCopy(solution, cloner, simpleFastCopy) { simpleMethod.invoke(null) as kotlin.Array<*> }
            } catch (e: Throwable) {
                if (!simpleFastCopy) {
                    throw e
                }
                error("Cloning parameters failed. Try disabling fast copy by setting fastCopy = false on @SimpleType")
            }
            val solutionCopy =
                cloneOrCopy(solution, cloner, simpleFastCopy) { simpleMethod.invoke(null) as kotlin.Array<*> }
            val submissionCopy =
                cloneOrCopy(solution, cloner, simpleFastCopy) { simpleMethod.invoke(null) as kotlin.Array<*> }
            val unmodifiedCopy =
                cloneOrCopy(solution, cloner, simpleFastCopy) { simpleMethod.invoke(null) as kotlin.Array<*> }
            check(
                setOf(
                    solution.size,
                    submission.size,
                    solutionCopy.size,
                    submissionCopy.size,
                    unmodifiedCopy.size,
                ).size == 1,
            ) {
                "@SimpleType method returned unequal arrays"
            }
            solution.indices.map { i ->
                check(
                    setOf(
                        solution[i],
                        submission[i],
                        solutionCopy[i],
                        submissionCopy[i],
                        unmodifiedCopy[i],
                    ).size == 1,
                ) {
                    "@SimpleType method did not return equal arrays (you may need to implement hashCode)"
                }
                Value(
                    solution[i]!!,
                    submission[i]!!,
                    solutionCopy[i]!!,
                    submissionCopy[i]!!,
                    unmodifiedCopy[i]!!,
                    ZeroComplexity,
                )
            }.toSet()
        }

        else -> null
    }

    private val edgeFastCopy = edgeMethod?.getAnnotation(EdgeType::class.java)?.fastCopy ?: false

    private val edgeOverride: Set<Value<Any?>>? = when {
        edgeValues != null -> edgeValues.values(ZeroComplexity, cloner)
        edgeMethod != null -> {
            val solution = edgeMethod.invoke(null) as kotlin.Array<*>

            @Suppress("TooGenericExceptionCaught")
            val submission = try {
                cloneOrCopy(solution, cloner, edgeFastCopy) { edgeMethod.invoke(null) as kotlin.Array<*> }
            } catch (e: Throwable) {
                if (!simpleFastCopy) {
                    throw e
                }
                error("Cloning parameters failed. Try disabling fast copy by setting fastCopy = false on @EdgeType")
            }
            val solutionCopy =
                cloneOrCopy(solution, cloner, edgeFastCopy) { edgeMethod.invoke(null) as kotlin.Array<*> }
            val submissionCopy =
                cloneOrCopy(solution, cloner, edgeFastCopy) { edgeMethod.invoke(null) as kotlin.Array<*> }
            val unmodifiedCopy =
                cloneOrCopy(solution, cloner, edgeFastCopy) { edgeMethod.invoke(null) as kotlin.Array<*> }
            check(
                setOf(
                    solution.size,
                    submission.size,
                    solutionCopy.size,
                    submissionCopy.size,
                    unmodifiedCopy.size,
                ).size == 1,
            ) {
                "@EdgeType method returned unequal arrays"
            }
            solution.indices.map { i ->
                check(
                    setOf(
                        solution[i],
                        submission[i],
                        solutionCopy[i],
                        submissionCopy[i],
                        unmodifiedCopy[i],
                    ).size == 1,
                ) {
                    "@EdgeType method did not return equal arrays (you may need to implement hashCode)"
                }
                Value(solution[i], submission[i], solutionCopy[i], submissionCopy[i], unmodifiedCopy[i], ZeroComplexity)
            }.toSet()
        }

        else -> null
    }
    private val randomGroup = RandomGroup(random.nextLong())

    override val simple: Set<Value<Any>> =
        simpleOverride ?: default?.simple as Set<Value<Any>>

    override val edge: Set<Value<Any?>> =
        edgeOverride ?: default?.edge as Set<Value<Any?>>

    @Suppress("TooGenericExceptionCaught")
    private fun getRandom(random: java.util.Random, complexity: Complexity) = try {
        unwrap {
            when (rand!!.parameters.size) {
                1 -> rand.invoke(null, random)
                2 -> rand.invoke(null, complexity.level, random)
                else -> error("Bad argument count for @RandomType")
            }
        }.let {
            check(it != null) { "@RandomType method returned null" }
            it
        }
    } catch (e: Exception) {
        error("@RandomType method threw an exception: $e")
    }

    private val randomFastCopy = rand?.getAnnotation(RandomType::class.java)?.fastCopy ?: false
    override fun random(complexity: Complexity, runner: TestRunner?): Value<Any> {
        if (rand == null) {
            check(default != null) { "Couldn't find rand generator for $name" }
            return default.random(complexity, runner) as Value<Any>
        }

        randomGroup.start()
        val solution = getRandom(randomGroup.random, complexity)

        @Suppress("TooGenericExceptionCaught")
        val submission = try {
            cloneOrCopy(solution, cloner, randomFastCopy) { getRandom(randomGroup.random, complexity) }
        } catch (e: Throwable) {
            if (!simpleFastCopy) {
                throw e
            }
            error("Cloning parameters failed. Try disabling fast copy by setting fastCopy = false on @RandomType")
        }
        val solutionCopy = cloneOrCopy(solution, cloner, randomFastCopy) { getRandom(randomGroup.random, complexity) }
        val submissionCopy = cloneOrCopy(solution, cloner, randomFastCopy) { getRandom(randomGroup.random, complexity) }
        val unmodifiedCopy = cloneOrCopy(solution, cloner, randomFastCopy) { getRandom(randomGroup.random, complexity) }
        randomGroup.stop()

        check(setOf(solution, submission, solutionCopy, submissionCopy, unmodifiedCopy).size == 1) {
            "@${RandomType.name} method for ${klass.name} did not return equal values"
        }
        return Value(solution, submission, solutionCopy, submissionCopy, unmodifiedCopy, complexity)
    }
}

sealed class TypeGenerators<T>(internal val random: Random, private val cloner: Cloner) : TypeGenerator<T>
typealias TypeGeneratorGenerator = (random: Random, cloner: Cloner) -> TypeGenerator<*>

object Defaults {
    val map = mutableMapOf<Class<*>, TypeGeneratorGenerator>()

    init {
        map[Byte::class.java] = ByteGenerator.Companion::create
        map[java.lang.Byte::class.java] = BoxedGenerator.create(Byte::class.java)
        map[Short::class.java] = ShortGenerator.Companion::create
        map[java.lang.Short::class.java] = BoxedGenerator.create(Short::class.java)
        map[Int::class.java] = IntGenerator.Companion::create
        map[java.lang.Integer::class.java] = BoxedGenerator.create(Int::class.java)
        map[Long::class.java] = LongGenerator.Companion::create
        map[java.lang.Long::class.java] = BoxedGenerator.create(Long::class.java)
        map[Float::class.java] = FloatGenerator.Companion::create
        map[java.lang.Float::class.java] = BoxedGenerator.create(Float::class.java)
        map[Double::class.java] = DoubleGenerator.Companion::create
        map[java.lang.Double::class.java] = BoxedGenerator.create(Double::class.java)
        map[Boolean::class.java] = BooleanGenerator.Companion::create
        map[java.lang.Boolean::class.java] = BoxedGenerator.create(Boolean::class.java)
        map[Char::class.java] = CharGenerator.Companion::create
        map[java.lang.Character::class.java] = BoxedGenerator.create(Char::class.java)
        map[String::class.java] = StringGenerator.Companion::create
        map[Any::class.java] = ObjectGenerator.Companion::create
    }

    operator fun get(klass: Class<*>): TypeGeneratorGenerator {
        map[klass]?.also { return it }
        if (klass.isArray && map.containsKey(klass.getArrayType())) {
            return { random, cloner ->
                ArrayGenerator(
                    random,
                    cloner,
                    klass.componentType,
                    create(klass.componentType, random, cloner),
                )
            }
        }
        error("Cannot find generator for class ${klass.name}")
    }

    @Suppress("ComplexCondition", "ReturnCount", "ComplexMethod")
    operator fun get(type: Type): TypeGeneratorGenerator {
        if (type is Class<*>) {
            try {
                return get(type)
            } catch (_: IllegalStateException) {
            }
        }
        if (type is ParameterizedType) {
            if (type.rawType == java.util.List::class.java &&
                type.actualTypeArguments.size == 1
            ) {
                val typeArgument = if (type.actualTypeArguments[0].typeName == "?") {
                    Any::class.java
                } else {
                    type.actualTypeArguments[0]
                }
                if (map.containsKey(typeArgument)) {
                    return { random, cloner -> ListGenerator(random, cloner, create(typeArgument, random, cloner)) }
                }
            } else if (
                type.rawType == java.util.Set::class.java &&
                type.actualTypeArguments.size == 1 &&
                map.containsKey(type.actualTypeArguments[0])
            ) {
                return { random, cloner ->
                    SetGenerator(
                        random,
                        cloner,
                        create(type.actualTypeArguments[0], random, cloner),
                    )
                }
            } else if (type.rawType == java.util.Map::class.java && type.actualTypeArguments.size == 2 &&
                map.containsKey(type.actualTypeArguments[0]) && map.containsKey(type.actualTypeArguments[1])
            ) {
                return { random, cloner ->
                    MapGenerator(
                        random,
                        cloner,
                        create(type.actualTypeArguments[0], random, cloner),
                        create(type.actualTypeArguments[1], random, cloner),
                    )
                }
            }
        }
        error("Cannot find generator for type ${type.cleanTypeName()}")
    }

    fun create(klass: Class<*>, random: Random = Random, cloner: Cloner): TypeGenerator<*> = get(klass)(random, cloner)
    fun create(type: Type, random: Random = Random, cloner: Cloner): TypeGenerator<*> = get(type)(random, cloner)
}

class ListGenerator(random: Random, private val cloner: Cloner, private val componentGenerator: TypeGenerator<*>) :
    TypeGenerators<Any>(random, cloner) {

    override val simple: Set<Value<Any>>
        get() {
            val simpleCases = componentGenerator.simple.mapNotNull { it.solutionCopy }
            return setOf(
                listOf(),
                simpleCases,
            ).values(ZeroComplexity, cloner)
        }

    override val edge: Set<Value<Any?>> = setOf<Any?>(null).values(ZeroComplexity, cloner)

    override fun random(complexity: Complexity, runner: TestRunner?): Value<Any> {
        val listSize = random.nextInt((complexity.level * 2).coerceAtLeast(2))
        return mutableListOf<Any>().apply {
            repeat(listSize) {
                add(componentGenerator.random(complexity, runner).solutionCopy!!)
            }
        }.value(complexity, cloner)
    }
}

class SetGenerator(random: Random, private val cloner: Cloner, private val componentGenerator: TypeGenerator<*>) :
    TypeGenerators<Any>(random, cloner) {

    override val simple: Set<Value<Any>>
        get() {
            val simpleCases = componentGenerator.simple.mapNotNull { it.solutionCopy }.toSet()
            return setOf(
                setOf(),
                simpleCases,
            ).values(ZeroComplexity, cloner)
        }

    override val edge: Set<Value<Any?>> = setOf<Any?>(null).values(ZeroComplexity, cloner)

    override fun random(complexity: Complexity, runner: TestRunner?): Value<Any> {
        val setSize = random.nextInt(complexity.level * 2).coerceAtLeast(2)
        return mutableSetOf<Any>().apply {
            repeat(setSize) {
                add(componentGenerator.random(complexity, runner).solutionCopy!!)
            }
        }.value(complexity, cloner)
    }
}

class MapGenerator(
    random: Random,
    private val cloner: Cloner,
    private val keyGenerator: TypeGenerator<*>,
    private val valueGenerator: TypeGenerator<*>,
) :
    TypeGenerators<Any>(random, cloner) {

    override val simple: Set<Value<Any>>
        get() {
            val keys = keyGenerator.simple.mapNotNull { it.solutionCopy }
            val values = valueGenerator.simple.mapNotNull { it.solutionCopy }
            require(keys.isNotEmpty()) { "Can't build a map from empty keys" }
            require(values.isNotEmpty()) { "Can't build a map from empty values" }

            val simpleMap = mutableMapOf<Any, Any>().apply {
                for (i in keys.indices) {
                    this[keys[i]] = values[i % values.size]
                }
            }.toMap()
            val maps = mutableSetOf(
                mapOf(),
                mapOf(keys.first() to values.first()),
                simpleMap,
            )
            if (keys.size > 1) {
                maps.add(mapOf(keys[0] to values.first(), keys[1] to values.first()))
            }
            return maps.toSet().values(ZeroComplexity, cloner)
        }

    override val edge: Set<Value<Any?>> = setOf<Any?>(null).values(ZeroComplexity, cloner)

    override fun random(complexity: Complexity, runner: TestRunner?): Value<Any> {
        val keySize = random.nextInt((complexity.level * 2).coerceAtLeast(2))
        return mutableMapOf<Any, Any>().apply {
            repeat(keySize) {
                this[keyGenerator.random(complexity, runner).solutionCopy!!] =
                    valueGenerator.random(complexity, runner).solutionCopy!!
            }
        }.value(complexity, cloner)
    }
}

class ArrayGenerator(
    random: Random,
    private val cloner: Cloner,
    private val klass: Class<*>,
    private val componentGenerator: TypeGenerator<*>,
) :
    TypeGenerators<Any>(random, cloner) {

    override val simple: Set<Value<Any>>
        get() {
            val simpleCases = componentGenerator.simple.map { it.solutionCopy }
            return setOf(
                Array.newInstance(klass, 0),
                Array.newInstance(klass, simpleCases.size).also { array ->
                    simpleCases.forEachIndexed { index, value ->
                        Array.set(array, index, value)
                    }
                },
            ).values(ZeroComplexity, cloner)
        }

    override val edge: Set<Value<Any?>> = setOf(null).values(ZeroComplexity, cloner)

    override fun random(complexity: Complexity, runner: TestRunner?): Value<Any> {
        return random(complexity, complexity, true, runner)
    }

    fun random(complexity: Complexity, componentComplexity: Complexity, top: Boolean, runner: TestRunner?): Value<Any> {
        val (currentComplexity, nextComplexity) = if (klass.isArray) {
            complexity.level.let { level ->
                val currentLevel = if (level == 0) {
                    0
                } else {
                    random.nextInt(level)
                }
                Pair(Complexity(currentLevel), Complexity(level - currentLevel))
            }
        } else {
            Pair(complexity, null)
        }
        val arraySize = random.nextInt((currentComplexity.level * 2).coerceAtLeast(2)).let {
            if (top && it == 0) {
                1
            } else {
                it
            }
        }
        return (
            Array.newInstance(klass, arraySize).also { array ->
                for (index in 0 until arraySize) {
                    val value = if (componentGenerator is ArrayGenerator) {
                        check(nextComplexity != null) { "Invalid complexity split" }
                        componentGenerator.random(nextComplexity, componentComplexity, false, runner)
                    } else {
                        componentGenerator.random(componentComplexity, runner)
                    }.solutionCopy
                    Array.set(array, index, value)
                }
            }
            ).value(complexity, cloner)
    }
}

@Suppress("UNCHECKED_CAST")
class BoxedGenerator(random: Random, cloner: Cloner, klass: Class<*>) : TypeGenerators<Any>(random, cloner) {
    private val primitiveGenerator = Defaults.create(klass, random, cloner)
    override val simple = primitiveGenerator.simple as Set<Value<Any>>
    override val edge = (
        primitiveGenerator.edge +
            setOf(Value(null, null, null, null, null, ZeroComplexity))
        ) as Set<Value<Any?>>

    override fun random(complexity: Complexity, runner: TestRunner?) =
        primitiveGenerator.random(complexity, runner) as Value<Any>

    companion object {
        fun create(klass: Class<*>) = { random: Random, cloner: Cloner -> BoxedGenerator(random, cloner, klass) }
    }
}

private fun randomNumber(max: Number, range: LongRange, random: Random) =
    (random.nextLong(max.toLong()) - (max.toLong() / 2)).also {
        check(it in range) { "Random number generated out of range" }
    }

class ByteGenerator(random: Random, private val cloner: Cloner) : TypeGenerators<Byte>(random, cloner) {

    override val simple = byteArrayOf(-1, 0, 1).toSet().values(ZeroComplexity, cloner)

    override val edge = setOf<Byte>().values(ZeroComplexity, cloner)

    override fun random(complexity: Complexity, runner: TestRunner?) = random(complexity, random).value(
        complexity,
        cloner,
    )

    companion object {
        fun random(complexity: Complexity, random: Random = Random) =
            randomNumber(complexity.power(), Byte.MIN_VALUE.toLong()..Byte.MAX_VALUE.toLong(), random)
                .toByte()

        fun create(random: Random = Random, cloner: Cloner) = ByteGenerator(random, cloner)
    }
}

class ShortGenerator(random: Random, private val cloner: Cloner) : TypeGenerators<Short>(random, cloner) {

    override val simple = shortArrayOf(-1, 0, 1).toSet().values(ZeroComplexity, cloner)

    override val edge = setOf<Short>().values(ZeroComplexity, cloner)

    override fun random(complexity: Complexity, runner: TestRunner?) = random(complexity, random).value(
        complexity,
        cloner,
    )

    companion object {
        fun random(complexity: Complexity, random: Random = Random) =
            randomNumber(complexity.power(4), Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong(), random)
                .toShort()

        fun create(random: Random = Random, cloner: Cloner) = ShortGenerator(random, cloner)
    }
}

class IntGenerator(random: Random, private val cloner: Cloner) : TypeGenerators<Int>(random, cloner) {

    override val simple = (-1..1).toSet().values(ZeroComplexity, cloner)
    override val edge = setOf<Int>().values(ZeroComplexity, cloner)
    override fun random(complexity: Complexity, runner: TestRunner?) = random(complexity, random).value(
        complexity,
        cloner,
    )

    companion object {
        fun random(complexity: Complexity, random: Random = Random) =
            randomNumber(complexity.power(8), Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong(), random)
                .toInt()

        fun create(random: Random = Random, cloner: Cloner) = IntGenerator(random, cloner)
    }
}

class LongGenerator(random: Random, private val cloner: Cloner) : TypeGenerators<Long>(random, cloner) {

    override val simple = (-1L..1L).toSet().values(ZeroComplexity, cloner)
    override val edge = setOf<Long>().values(ZeroComplexity, cloner)
    override fun random(complexity: Complexity, runner: TestRunner?) = random(complexity, random).value(
        complexity,
        cloner,
    )

    companion object {
        fun random(complexity: Complexity, random: Random = Random) =
            randomNumber(complexity.power(16), Long.MIN_VALUE..Long.MAX_VALUE, random)

        fun create(random: Random = Random, cloner: Cloner) = LongGenerator(random, cloner)
    }
}

class FloatGenerator(random: Random, private val cloner: Cloner) : TypeGenerators<Float>(random, cloner) {

    override val simple = setOf(-0.1f, 0.0f, 0.1f).values(ZeroComplexity, cloner)
    override val edge = setOf<Float>().values(ZeroComplexity, cloner)
    override fun random(complexity: Complexity, runner: TestRunner?) = random(complexity, random).value(
        complexity,
        cloner,
    )

    companion object {
        fun random(complexity: Complexity, random: Random = Random) =
            IntGenerator.random(complexity, random) * random.nextFloat()

        fun create(random: Random = Random, cloner: Cloner) = FloatGenerator(random, cloner)
    }
}

class DoubleGenerator(random: Random, private val cloner: Cloner) : TypeGenerators<Double>(random, cloner) {

    override val simple = setOf(-0.1, 0.0, 0.1).values(ZeroComplexity, cloner)
    override val edge = setOf<Double>().values(ZeroComplexity, cloner)
    override fun random(complexity: Complexity, runner: TestRunner?) = random(complexity, random).value(
        complexity,
        cloner,
    )

    companion object {
        fun random(complexity: Complexity, random: Random = Random) =
            FloatGenerator.random(complexity, random) * random.nextDouble()

        fun create(random: Random = Random, cloner: Cloner) = DoubleGenerator(random, cloner)
    }
}

class BooleanGenerator(random: Random, private val cloner: Cloner) : TypeGenerators<Boolean>(random, cloner) {

    override val simple = setOf(true, false).values(ZeroComplexity, cloner)
    override val edge = setOf<Boolean>().values(ZeroComplexity, cloner)
    override fun random(complexity: Complexity, runner: TestRunner?) = random.nextBoolean().value(complexity, cloner)

    companion object {
        fun create(random: Random = Random, cloner: Cloner) = BooleanGenerator(random, cloner)
    }
}

class CharGenerator(random: Random, private val cloner: Cloner) : TypeGenerators<Char>(random, cloner) {

    override val simple = setOf('A', '0').values(ZeroComplexity, cloner)
    override val edge = setOf<Char>().values(ZeroComplexity, cloner)
    override fun random(complexity: Complexity, runner: TestRunner?) =
        ALPHANUMERIC_CHARS.random(random).value(complexity, cloner)

    companion object {
        val ALPHANUMERIC_CHARS: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9') + ' '
        fun create(random: Random = Random, cloner: Cloner) = CharGenerator(random, cloner)
    }
}

class StringGenerator(random: Random, private val cloner: Cloner) : TypeGenerators<String>(random, cloner) {

    override val simple =
        setOf("t", "gwa", "8 circle", "").map { it.toCharArray() }.map { String(it) }.toSet().values(
            ZeroComplexity,
            cloner,
        )
    override val edge = listOf<String?>(null).values(ZeroComplexity, cloner)
    override fun random(complexity: Complexity, runner: TestRunner?) = random(complexity, random).value(
        complexity,
        cloner,
    )

    companion object {
        @Suppress("MemberVisibilityCanBePrivate")
        fun random(complexity: Complexity, random: Random = Random): String {
            return (0 until random.nextInt((complexity.level * 2 + 1)))
                .map { random.nextInt(CharGenerator.ALPHANUMERIC_CHARS.size) }
                .map(CharGenerator.ALPHANUMERIC_CHARS::get)
                .joinToString("")
        }

        fun create(random: Random = Random, cloner: Cloner) = StringGenerator(random, cloner)
    }
}

class SystemIn(input: List<String>) {
    val input = input.map { String(it.toCharArray()) }

    constructor(input: String) : this(listOf(input))

    override fun equals(other: Any?) = when (other) {
        !is SystemIn -> false
        else -> input == other.input
    }

    override fun hashCode() = input.hashCode()
}

@Suppress("UNUSED_PARAMETER")
internal fun systemInDummy(systemIn: SystemIn): Nothing = error("Should not be called")
internal val systemInDummyExecutable = ::systemInDummy.javaMethod!!

class JenisolFileSystem(val files: Map<String, ByteArray?> = mapOf()) {
    constructor(filename: String, contents: String) : this(mapOf(filename to contents.toByteArray()))

    val asByteBuffers: Map<String, ByteBuffer?>
        get() = files.mapValues {
            if (it.value != null) {
                ByteBuffer.wrap(it.value)
            } else {
                null
            }
        }

    override fun equals(other: Any?) = when (other) {
        !is JenisolFileSystem -> false
        else -> asByteBuffers == other.asByteBuffers
    }

    override fun hashCode(): Int {
        return asByteBuffers.hashCode()
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun fileSystemDummy(fileSystem: JenisolFileSystem): Nothing = error("Should not be called")
internal val fileSystemDummyExecutable = ::fileSystemDummy.javaMethod!!

data class JenisolAny(private val value: Int)

@Suppress("UNCHECKED_CAST")
class ObjectGenerator(
    random: Random,
    private val cloner: Cloner,
    private val receiverGenerator: ReceiverGenerator? = null,
) : TypeGenerators<Any>(random, cloner) {
    private val defaultObjects = Defaults.map.filterKeys { !it.isPrimitive && it != Any::class.java }
        .mapValues { (_, generator) -> generator(random, cloner) }

    init {
        check(defaultObjects.isNotEmpty()) { "No default objects to generate" }
    }

    override val simple: Set<Value<Any>>
        get() = (
            listOf(JenisolAny(random.nextInt())).values(ZeroComplexity, cloner) +
                (receiverGenerator?.simple ?: setOf()) +
                defaultObjects.values.map { it.simple }.flatten().distinct().take(SIMPLE_LIMIT)
            ).toSet() as Set<Value<Any>>

    override val edge: Set<Value<Any?>>
        get() = (
            listOf(null as Any?).values(ZeroComplexity, cloner) +
                (receiverGenerator?.edge ?: setOf()) +
                defaultObjects.values.map { it.edge }.flatten().distinct().take(EDGE_LIMIT)
            ).toSet() as Set<Value<Any?>>

    override fun random(complexity: Complexity, runner: TestRunner?): Value<Any> =
        if (receiverGenerator != null && random.nextBoolean()) {
            if (random.nextBoolean() && receiverGenerator.simple.isNotEmpty()) {
                receiverGenerator.simple.shuffled(random).first()
            } else {
                receiverGenerator.random(complexity, runner)
            }
        } else {
            defaultObjects.values.shuffled(random).first().random(complexity, runner) as Value<Any>
        }

    companion object {
        fun create(random: Random = Random, cloner: Cloner) = ObjectGenerator(random, cloner)
        const val SIMPLE_LIMIT = 8
        const val EDGE_LIMIT = 8
    }
}

fun <T> Collection<T>.values(complexity: Complexity, cloner: Cloner) = toSet().also {
    check(size == it.size) { "Collection of values was not distinct" }
}.map {
    Value(
        cloner.deepClone(it),
        cloner.deepClone(it),
        cloner.deepClone(it),
        cloner.deepClone(it),
        cloner.deepClone(it),
        complexity,
    )
}.toSet()

inline fun <reified T> T.value(complexity: Complexity, cloner: Cloner) =
    Value(deepCopy(cloner), deepCopy(cloner), deepCopy(cloner), deepCopy(cloner), deepCopy(cloner), complexity)

fun <T> Class<T>.getArrayType(start: Boolean = true): Class<*> {
    check(!start || isArray) { "Must be called on an array type" }
    return if (!isArray) {
        this
    } else {
        componentType.getArrayType(false)
    }
}

fun <T> Class<T>.getArrayDimension(start: Boolean = true): Int {
    check(!start || isArray) { "Must be called on an array type" }
    return if (!isArray) {
        0
    } else {
        1 + componentType.getArrayDimension(false)
    }
}

fun kotlin.Array<Type>.compareBoxed(other: kotlin.Array<Type>) = when {
    size != other.size -> false
    else ->
        zip(other).all { (mine, other) ->
            when {
                mine is Class<*> && other is Class<*> -> mine.compareBoxed(other)
                else -> mine.compare(other)
            }
        }
}

fun Type.compare(other: Type): Boolean {
    return when (other) {
        this -> true
        else -> false
    }
}

fun Type.compareBoxed(other: Type) = when {
    this == other -> true
    this is Class<*> && other is Class<*> -> this.compareBoxed(other)
    else -> false
}

fun <T> Class<T>.compareBoxed(other: Class<*>) = when {
    this == other -> true
    wrap() == other.wrap() -> true
    compareBoxedArrays(other) -> true
    else -> false
}

fun <T> Class<T>.compareBoxedArrays(other: Class<*>) = when {
    !isArray -> false
    !other.isArray -> false
    getArrayDimension() != other.getArrayDimension() -> false
    getArrayType().wrap() != other.getArrayType().wrap() -> false
    else -> true
}

fun <T> Class<T>.wrap(): Class<*> = when {
    this == Byte::class.java -> java.lang.Byte::class.java
    this == Short::class.java -> java.lang.Short::class.java
    this == Int::class.java -> java.lang.Integer::class.java
    this == Long::class.java -> java.lang.Long::class.java
    this == Float::class.java -> java.lang.Float::class.java
    this == Double::class.java -> java.lang.Double::class.java
    this == Char::class.java -> java.lang.Character::class.java
    this == Boolean::class.java -> java.lang.Boolean::class.java
    this == Void::class.java -> java.lang.Void::class.java
    this.name == "void" -> java.lang.Void::class.java
    else -> this
}

@Suppress("ComplexMethod")
fun Class<*>.boxType(): Class<*> = when {
    this == Byte::class.java -> java.lang.Byte::class.java
    this == Short::class.java -> java.lang.Short::class.java
    this == Int::class.java -> java.lang.Integer::class.java
    this == Long::class.java -> java.lang.Long::class.java
    this == Float::class.java -> java.lang.Float::class.java
    this == Double::class.java -> java.lang.Double::class.java
    this == Char::class.java -> java.lang.Character::class.java
    this == Boolean::class.java -> java.lang.Boolean::class.java
    else -> this
}

fun Any.boxArray(): kotlin.Array<*> = when (this) {
    is ByteArray -> this.toTypedArray()
    is ShortArray -> this.toTypedArray()
    is IntArray -> this.toTypedArray()
    is LongArray -> this.toTypedArray()
    is FloatArray -> this.toTypedArray()
    is DoubleArray -> this.toTypedArray()
    is CharArray -> this.toTypedArray()
    is BooleanArray -> this.toTypedArray()
    is kotlin.Array<*> -> this
    else -> error("Value is not an array: ${this::class.java}")
}

fun Any.isAnyArray() = when (this) {
    is ByteArray -> true
    is ShortArray -> true
    is IntArray -> true
    is LongArray -> true
    is FloatArray -> true
    is DoubleArray -> true
    is CharArray -> true
    is BooleanArray -> true
    is kotlin.Array<*> -> true
    else -> false
}

fun Any.isLambdaMethod() = this.javaClass.name.contains("$${"$"}Lambda$")

class RandomGroup(seed: Long = Random.nextLong()) {
    private val seedGenerator = Random(seed)
    private var _random: java.util.Random? = null

    private var running: Boolean = false
    private var currentSeed: Long = seedGenerator.nextLong()
    private var ended: Long? = null

    fun start() {
        check(!running) { "Already started" }
        currentSeed = seedGenerator.nextLong()
        _random = null
        ended = null
    }

    val random: java.util.Random
        get() {
            val thisEnd = _random?.nextLong()
            if (ended != null) {
                check(thisEnd == ended) { "Random generator out of sync: $ended $thisEnd" }
            }
            ended = thisEnd
            _random = java.util.Random().also { it.setSeed(currentSeed) }
            return _random!!
        }

    fun stop() {
        check(_random != null) { "Never used" }
        val thisEnd = _random?.nextLong()
        if (ended != null) {
            check(thisEnd == ended) { "Random generator out of sync" }
        }
    }
}
