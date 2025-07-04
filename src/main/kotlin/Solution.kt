@file:Suppress("TooManyFunctions")

package edu.illinois.cs.cs125.jenisol.core

import com.rits.cloning.Cloner
import edu.illinois.cs.cs125.jenisol.core.generators.GeneratorFactory
import edu.illinois.cs.cs125.jenisol.core.generators.Parameters
import edu.illinois.cs.cs125.jenisol.core.generators.boxType
import edu.illinois.cs.cs125.jenisol.core.generators.fileSystemDummyExecutable
import edu.illinois.cs.cs125.jenisol.core.generators.getArrayDimension
import edu.illinois.cs.cs125.jenisol.core.generators.getArrayType
import edu.illinois.cs.cs125.jenisol.core.generators.systemInDummyExecutable
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import kotlin.random.Random
import kotlin.reflect.full.companionObject

class Solution(val solution: Class<*>) {
    init {
        solution.declaredFields.filter { it.isStatic() && !it.isJenisol() && !it.isPrivate() && !it.isFinal() }
            .also { fields ->
                checkDesign(fields.isEmpty()) {
                    "No support for testing classes with modifiable static fields yet: ${fields.map { it.name }}"
                }
            }
    }

    val allFields = solution.declaredFields.filter {
        !it.isJenisol() && !it.isPrivate()
    }

    val allExecutables =
        (solution.declaredMethods.toSet() + solution.declaredConstructors.toSet())
            .filterNotNull()
            .filter {
                ((!it.isPrivate() && !it.isJenisol() || it.isCheckDesign())) &&
                    !it.isSynthetic &&
                    !(it is Method && it.isBridge)
            }.toSet().also {
                checkDesign(it.isNotEmpty() || allFields.isNotEmpty()) { "Found no methods or fields to test" }
            }

    private val allExecutablesWithPrivate = (solution.declaredMethods.toSet() + solution.declaredConstructors.toSet())
        .filterNotNull()
        .filter {
            ((!it.isJenisol() || it.isCheckDesign())) &&
                !it.isSynthetic &&
                !(it is Method && it.isBridge)
        }.toSet()

    init {
        allExecutables.forEach { it.isAccessible = true }
    }

    val bothExecutables = solution.declaredMethods.asSequence().toSet().filterNotNull().filter {
        it.isBoth()
    }.onEach { checkDesign { Both.validate(it, solution) } }.toSet()

    private fun Executable.receiverParameter() = parameterTypes.any { it == solution }

    private fun Executable.objectParameter() = parameterTypes.any { it == Any::class.java || it == Object::class.java }

    val receiverGenerators = allExecutables.filter { executable ->
        !executable.receiverParameter()
    }.filter { executable ->
        when (executable) {
            is Constructor<*> -> true
            is Method -> executable.isStatic() &&
                (
                    executable.returnType == solution ||
                        (
                            executable.returnType.isArray &&
                                executable.returnType.getArrayType() == solution &&
                                executable.returnType.getArrayDimension() == 1
                            )
                    )
        }
    }.toSet()
    val methodsToTest = (allExecutables - receiverGenerators + bothExecutables).also {
        if (it.isEmpty() && !solution.isDesignOnly()) {
            when {
                allExecutablesWithPrivate.isNotEmpty() -> {
                    failDesign {
                        "Methods to test must be public or package-private, but all found are private"
                    }
                }

                else -> {
                    failDesign { "Found methods that generate receivers but no ways to test them" }
                }
            }
        }
        checkDesign(it.isNotEmpty() || solution.isDesignOnly()) {
            "Found methods that generate receivers but no ways to test them"
        }
    }

    fun defaultTestingWeight(executable: Executable): Double {
        require(executable in methodsToTest)
        return if (executable.parameterCount == 0) {
            1.0
        } else {
            2.0
        }
    }

    private val needsReceiver = methodsToTest.filter { executable ->
        executable.receiverParameter() || (executable is Method && !executable.isStatic())
    }.toSet()
    private val receiverTransformers = methodsToTest.filterIsInstance<Method>().filter { method ->
        method.returnType.name != "void" && method.returnType != solution
    }.filter { method ->
        !method.isStatic() || method.receiverParameter()
    }.toSet()

    val skipReceiver = needsReceiver.isEmpty() &&
        receiverTransformers.isEmpty() &&
        (
            receiverGenerators.isEmpty() ||
                (receiverGenerators.size == 1 && receiverGenerators.first().parameters.isEmpty())
            )

    val fauxStatic = !skipReceiver &&
        solution.superclass == Any::class.java &&
        solution.declaredFields.all { it.isJenisol() || it.isStatic() } &&
        solution.declaredMethods.all {
            it.isJenisol() ||
                (it.returnType != solution && !it.receiverParameter() && !it.objectParameter())
        } &&
        solution.declaredConstructors.let { it.size == 1 && it.first().parameterCount == 0 }

    val usesSystemIn = methodsToTest.any { it.provideSystemIn() }
    val usesFileSystem = methodsToTest.any { it.provideFileSystem() } || solution.provideFileSystem()

    init {
        check(!(solution.provideFileSystem() && methodsToTest.any { it.provideFileSystem() })) {
            "Can't used @ProvideFileSystem annotation on both class and method"
        }
    }

    init {
        if (needsReceiver.isNotEmpty()) {
            checkDesign(receiverGenerators.isNotEmpty()) { "No way to generate needed receivers" }
        }
        if (!skipReceiver && !fauxStatic && receiverGenerators.isNotEmpty()) {
            checkDesign(!(receiverTransformers.isEmpty() && bothExecutables.isEmpty())) {
                "No way to verify generated receivers"
            }
        }
    }

    val instanceValidator = solution.declaredMethods.filter {
        it.isInstanceValidator()
    }.also {
        checkDesign(it.size <= 1) { "Solution has multiple methods annotated with @InstanceValidator" }
    }.firstOrNull()?.also {
        checkDesign { InstanceValidator.validate(it) }
    }

    val customCompares = solution.declaredMethods.filter {
        it.isCompare()
    }.filterNotNull().let { compareMethods ->
        compareMethods.forEach { Compare.validate(it) }
        checkDesign {
            require(compareMethods.distinctBy { it.returnType }.size == compareMethods.size) {
                "Found two or more @Compare methods examining the same type"
            }
        }
        compareMethods.associateBy { it.parameterTypes.first().boxType() }
    }

    val initializer: Executable? = solution.superclass.declaredMethods.filter {
        it.isInitializer()
    }.also {
        checkDesign(it.size <= 1) { "Solution parent class ${solution.superclass.name} has multiple initializers" }
    }.firstOrNull()?.also {
        checkDesign { Initializer.validate(it) }
    }
    private val initializers = initializer?.let { setOf(it) } ?: setOf()
    private val generatorExecutables = allExecutables + initializers + if (usesSystemIn) {
        setOf(systemInDummyExecutable)
    } else {
        setOf()
    } + if (usesFileSystem) {
        setOf(fileSystemDummyExecutable)
    } else {
        setOf()
    }

    val generatorFactory: GeneratorFactory = GeneratorFactory(generatorExecutables, this)

    private val defaultReceiverCount = if (skipReceiver) {
        0
    } else if (fauxStatic) {
        1
    } else {
        receiverGenerators.sumOf { generator ->
            generatorFactory.get(Random, Cloner.shared())[generator]!!.fixed.filter {
                it.type == Parameters.Type.SIMPLE || it.type == Parameters.Type.FIXED_FIELD
            }.size
        } * 2
    }
    private val defaultMethodCount = (
        (allExecutables - receiverGenerators).sumOf { generator ->
            if (generator.receiverParameter()) {
                defaultReceiverCount
            } else {
                generatorFactory.get(Random, Cloner.shared())[generator]!!.fixed.size.coerceAtLeast(1) +
                    if (receiverGenerators.isNotEmpty() && generator.objectParameter()) {
                        defaultReceiverCount
                    } else {
                        0
                    }
            }
        } * 2
        ) + bothExecutables.size

    val limits: Map<Executable, Int> = solution.declaredMethods
        .filter { it.hasLimit() }
        .associateWith {
            check(it in (allExecutables + bothExecutables)) {
                "Can only use @Limit on tested methods and constructors and methods annotated with @Both"
            }
            it.getAnnotation(Limit::class.java)!!.value
        }

    private val methodLimit = if (limits.keys == methodsToTest) {
        limits.values.sum()
    } else {
        Integer.MAX_VALUE
    }

    private val defaultTotalCount = (
        (defaultReceiverCount * Settings.DEFAULT_RECEIVER_RETRIES) +
            (defaultMethodCount.coerceAtMost(methodLimit) * defaultReceiverCount.coerceAtLeast(1))
        )

    @Suppress("MemberVisibilityCanBePrivate")
    val maxCount = if (limits.keys == methodsToTest && (skipReceiver || fauxStatic)) {
        defaultTotalCount.coerceAtLeast(methodLimit)
    } else {
        Integer.MAX_VALUE
    }

    val receiverAsParameter = methodsToTest.any { executable ->
        executable.receiverParameter() || executable.objectParameter()
    }

    fun setCounts(settings: Settings): Settings {
        check(settings.shrink != null) {
            "shrink setting must be specified"
        }
        val testCount = if (settings.testCount != -1) {
            check(settings.minTestCount == -1 && settings.maxTestCount == -1) {
                "Can't set testCount and minTestCount or maxTestCount"
            }
            settings.testCount
        } else {
            defaultTotalCount.let {
                if (settings.minTestCount != -1) {
                    it.coerceAtLeast(settings.minTestCount)
                } else {
                    it
                }
            }.let {
                if (settings.maxTestCount != -1) {
                    it.coerceAtMost(settings.maxTestCount)
                } else {
                    it
                }
            }
        }.coerceAtMost(maxCount)
        val methodCount = if (settings.methodCount != -1) {
            settings.methodCount
        } else {
            defaultMethodCount
        }
        check(settings.receiverCount == -1 || receiverAsParameter) {
            "receiverCount can only be set when the receiver is used as a parameter"
        }
        val receiverCount = if (settings.receiverCount != -1) {
            settings.receiverCount
        } else if (receiverAsParameter) {
            defaultReceiverCount
        } else {
            -1
        }
        return settings.copy(testCount = testCount, methodCount = methodCount, receiverCount = receiverCount)
    }

    val verifiers = solution.declaredMethods.filter { it.isVerify() }.associateBy { verifier ->
        val matchingMethod = methodsToTest.filter { methodToTest ->
            val returnType = when (methodToTest) {
                is Constructor<*> -> solution
                is Method -> methodToTest.genericReturnType
            }
            @Suppress("TooGenericExceptionCaught", "SwallowedException")
            try {
                Verify.validate(verifier, returnType, methodToTest.genericParameterTypes)
                true
            } catch (_: Exception) {
                false
            }
        }
        checkDesign(matchingMethod.isNotEmpty()) { "@Verify method $verifier matched no solution methods" }
        checkDesign(matchingMethod.size == 1) { "@Verify method $verifier matched multiple solution methods" }
        matchingMethod[0]
    }

    val filters: Map<Executable, Method> = solution.declaredMethods.filter { it.isFilterParameters() }
        .mapNotNull { filter ->
            FilterParameters.validate(filter).let { filterTypes ->
                (methodsToTest + receiverGenerators).filter {
                    it.genericParameterTypes.contentEquals(filterTypes)
                }.also {
                    check(it.size <= 1) { "Filter matched multiple methods: ${it.size}" }
                }.firstOrNull()
            }?.let {
                it to filter
            }
        }.toMap()

    val receiverCompare = object : Comparator {
        override val descendants = true
        override val isInterface = false
        override fun compare(
            solution: Any,
            submission: Any,
            solutionClass: Class<*>?,
            submissionClass: Class<*>?,
        ): Boolean = true
    }

    init {
        @Suppress("MagicNumber")
        solution.declaredMethods
            .filter { it.hasKotlinMirrorOK() }
            .forEach {
                check(it.name.length > 3 && (it.name.startsWith("set") || it.name.startsWith("get"))) {
                    "Can only use @KotlinMirrorOK on Java setters and getters"
                }
            }
    }

    fun submission(submission: Class<*>) = Submission(this, submission)

    fun checkFields(otherSolution: Class<*>) {
        check(solution != otherSolution) {
            "Should not check fields on identical classes"
        }
        solution.declaredFields.filter { it.isJenisol() }.forEach { field ->
            field.isAccessible = true

            val otherField = otherSolution.declaredFields.find {
                it.isJenisol() && it.name == field.name
            }?.also {
                it.isAccessible = true
            }
            check(otherField != null) {
                "Couldn't find field ${field.name} on alternate solution"
            }

            val (firstValue, secondValue) =
                @Suppress("TooGenericExceptionCaught")
                try {
                    Pair(field.get(null), otherField.get(null))
                } catch (e: Exception) {
                    error("Retrieving field ${field.name} failed: $e")
                }
            check(firstValue.deepEquals(secondValue, Comparators(), null, null)) {
                error(
                    "Field ${field.name} was not equal between solution instances. " +
                        "Make sure any randomness is consistent.",
                )
            }
        }
    }
}

fun solution(klass: Class<*>) = Solution(klass)

fun Executable.isStatic() = Modifier.isStatic(modifiers)
fun Executable.isPrivate() = Modifier.isPrivate(modifiers)
fun Executable.isPublic() = Modifier.isPublic(modifiers)
fun Executable.isProtected() = Modifier.isProtected(modifiers)
fun Executable.isPackagePrivate() = !isPublic() && !isPrivate() && !isProtected()

fun Class<*>.isPrivate() = Modifier.isPrivate(modifiers)
fun Class<*>.isPublic() = Modifier.isPublic(modifiers)
fun Class<*>.isProtected() = Modifier.isProtected(modifiers)

fun Class<*>.isAbstract() = Modifier.isAbstract(modifiers)

fun Class<*>.isFinal() = Modifier.isFinal(modifiers)

fun Class<*>.isStatic() = Modifier.isStatic(modifiers)

fun Class<*>.isPackagePrivate() = !isPublic() && !isPrivate() && !isProtected()

fun Executable.isKotlinCompanionAccessor(): Boolean {
    check(declaringClass.isKotlin()) { "Should only check Kotlin classes: ${declaringClass.name}" }
    return name.startsWith("access${"$"}get") || name.startsWith("access${"$"}set")
}

fun Executable.isDataClassGenerated() = name == "equals" ||
    name == "hashCode" ||
    name == "toString" ||
    name == "copy" ||
    name == "copy${"$"}default" ||
    name.startsWith("component")

@Suppress("SwallowedException")
fun Class<*>.hasKotlinCompanion() = try {
    isKotlin() && kotlin.companionObject != null
} catch (_: UnsupportedOperationException) {
    false
}

@Suppress("SwallowedException")
fun Executable.isKotlinCompanion() = try {
    declaringClass.isKotlin() && declaringClass.kotlin.isCompanion
} catch (_: UnsupportedOperationException) {
    false
}

@Suppress("ComplexMethod", "NestedBlockDepth")
fun String.toKotlinType() = when {
    this == "byte" -> "Byte"
    this == "short" -> "Short"
    this == "int" -> "Int"
    this == "long" -> "Long"
    this == "float" -> "Float"
    this == "double" -> "Double"
    this == "char" -> "Char"
    this == "boolean" -> "Boolean"
    this == "Integer" -> "Int"
    this == "Object" -> "Any"
    this.endsWith("[]") -> {
        var currentType = this
        var arrayCount = -1
        while (currentType.endsWith("[]")) {
            currentType = currentType.removeSuffix("[]")
            arrayCount++
        }
        var name = when (currentType) {
            "byte" -> "ByteArray"
            "short" -> "ShortArray"
            "int" -> "IntArray"
            "long" -> "LongArray"
            "float" -> "FloatArray"
            "double" -> "DoubleArray"
            "char" -> "CharArray"
            "boolean" -> "BooleanArray"
            else -> "Array<$currentType>"
        }
        repeat(arrayCount) {
            name = "Array<$name>"
        }
        name
    }

    else -> this
}
    .replace("java.util.List", "List")
    .replace("java.util.Map", "Map")
    .replace("java.util.Set", "Set")

@Suppress("CyclomaticComplexMethod")
fun Executable.fullName(isKotlin: Boolean = false): String {
    val visibilityModifier = getVisibilityModifier(isKotlin)?.plus(" ")
    val isConstructor = this is Constructor<*>

    val returnType = when (this) {
        is Constructor<*> -> ""
        is Method -> genericReturnType.cleanTypeName(isKotlin)
    }.let { type ->
        if (isKotlin) {
            type.toKotlinType()
        } else {
            type
        }
    }.let {
        if (it.isNotBlank()) {
            if (isKotlin) {
                it
            } else {
                "$it "
            }
        } else {
            it
        }
    }
    return if (!isKotlin) {
        "${visibilityModifier ?: ""}${
            if (isStatic()) {
                "static "
            } else {
                ""
            }
        }$returnType$name(${parameters.joinToString(", ") { it.parameterizedType.cleanTypeName(false) }})"
    } else {
        "${visibilityModifier ?: ""}${
            if (!isConstructor) {
                "fun "
            } else {
                ""
            }
        }$name(${
            parameters.joinToString(", ") {
                it.parameterizedType.cleanTypeName(true).toKotlinType()
            }
        })${
            if (!isConstructor) {
                ": $returnType"
            } else {
                ""
            }
        }"
    }
}

fun Type.cleanTypeName(isKotlin: Boolean = false): String = if (this is ParameterizedType) {
    "${rawType.typeName}<${
        actualTypeArguments.map { it.typeName.replace("java.lang.", "") }.joinToString(", ") {
            if (isKotlin) {
                it.toKotlinType()
            } else {
                it
            }
        }
    }>"
} else {
    typeName.replace("java.lang.", "")
}

fun Field.fullName(): String {
    val visibilityModifier = getVisibilityModifier()?.plus(" ")
    return "${visibilityModifier ?: ""}${type.canonicalName?.removePrefix("java.lang.") ?: "Unknown"} $name"
}

fun Class<*>.visibilityMatches(klass: Class<*>) = when {
    isPublic() -> klass.isPublic()
    isPrivate() -> klass.isPrivate()
    isProtected() -> klass.isProtected()
    else -> klass.isPackagePrivate()
}

fun Executable.visibilityMatches(executable: Executable, submission: Class<*>) = when {
    isPublic() && submission.isKotlin() && executable.isPackagePrivate() -> true
    isPublic() -> executable.isPublic()
    isPrivate() -> executable.isPrivate()
    isProtected() -> executable.isProtected()
    else -> executable.isPackagePrivate()
}

fun Field.visibilityMatches(solutionField: Field) = when {
    isPublic() -> solutionField.isPublic()
    isPrivate() -> solutionField.isPrivate()
    isProtected() -> solutionField.isProtected()
    else -> solutionField.isPackagePrivate()
}

fun Class<*>.getVisibilityModifier() = when {
    isPublic() -> "public"
    isPrivate() -> "private"
    isProtected() -> "protected"
    else -> null
}

fun Executable.getVisibilityModifier(isKotlin: Boolean = false) = when {
    !isKotlin && isPublic() -> "public"
    isPrivate() -> "private"
    isProtected() -> "protected"
    else -> null
}

fun Field.getVisibilityModifier() = when {
    isPublic() -> "public"
    isPrivate() -> "private"
    isProtected() -> "protected"
    else -> null
}

fun Class<*>.findMethod(method: Method, solution: Class<*>) = this.declaredMethods.find {
    it != null &&
        it.visibilityMatches(method, this) &&
        it.name == method.name &&
        compareParameters(method.genericParameterTypes, solution, it.genericParameterTypes, this) &&
        compareReturn(method.genericReturnType, solution, it.genericReturnType, this) &&
        it.isStatic() == method.isStatic()
} ?: if (hasKotlinCompanion()) {
    this.kotlin.companionObject?.java?.declaredMethods?.find {
        it != null &&
            it.visibilityMatches(method, this) &&
            it.name == method.name &&
            compareParameters(method.genericParameterTypes, solution, it.genericParameterTypes, this) &&
            compareReturn(method.genericReturnType, solution, it.genericReturnType, this)
    }
} else {
    null
}

fun compareReturn(solutionReturn: Type, solution: Class<*>, submissionReturn: Type, submission: Class<*>) = when {
    solutionReturn == submissionReturn -> true
    solutionReturn == solution && submissionReturn == submission -> true
    solutionReturn is Class<*> &&
        submissionReturn is Class<*> &&
        solutionReturn.isArray &&
        solutionReturn.getArrayType() == solution &&
        submissionReturn.isArray &&
        submissionReturn.getArrayType() == submission &&
        solutionReturn.getArrayDimension() == submissionReturn.getArrayDimension() -> true

    solutionReturn is TypeVariable<*> && submissionReturn is TypeVariable<*> ->
        solutionReturn.bounds.contentEquals(submissionReturn.bounds)

    else -> false
}

@Suppress("ComplexMethod")
fun compareParameters(
    solutionParameters: Array<Type>,
    solution: Class<*>,
    submissionParameters: Array<Type>,
    submission: Class<*>,
): Boolean {
    if (solutionParameters.size != submissionParameters.size) {
        return false
    }
    return solutionParameters
        .zip(submissionParameters.fixReceivers(submission, solution))
        .all { (solutionType, submissionType) ->
            when {
                solutionType == submissionType -> true
                solutionType !is ParameterizedType &&
                    submissionType is ParameterizedType &&
                    submissionType.rawType == solutionType -> {
                    submissionType.actualTypeArguments.all { it is Any }
                }

                solutionType is TypeVariable<*> && submissionType is TypeVariable<*> ->
                    solutionType.bounds.contentEquals(submissionType.bounds)

                submission.isKotlin() &&
                    solutionType is ParameterizedType &&
                    submissionType is ParameterizedType &&
                    solutionType.rawType == submissionType.rawType -> {
                    var matches = true
                    @Suppress("LoopWithTooManyJumpStatements")
                    for ((index, solutionTypeArgument) in solutionType.actualTypeArguments.withIndex()) {
                        val submissionTypeArgument = submissionType.actualTypeArguments[index]
                        if (solutionTypeArgument.typeName == "java.lang.Object" &&
                            submissionTypeArgument.typeName === "?"
                        ) {
                            continue
                        }
                        if (submissionTypeArgument.typeName.removePrefix("? extends").trim()
                            == solutionTypeArgument.typeName
                        ) {
                            continue
                        }
                        if (solutionTypeArgument != submissionTypeArgument) {
                            matches = false
                            break
                        }
                    }
                    matches
                }

                else -> false
            }
        }
}

typealias SubmissionClass = Class<*>

fun SubmissionClass.findConstructor(solutionConstructor: Constructor<*>, solution: Class<*>) = this.declaredConstructors
    .filterNotNull()
    .filter { it.isPublic() }
    .find {
        compareParameters(solutionConstructor.genericParameterTypes, solution, it.genericParameterTypes, this)
    }

fun Array<Type>.fixReceivers(from: Type, to: Type) = map {
    when (it) {
        from -> to
        else -> it
    }
}.toTypedArray()

fun Class<*>.findField(solutionField: Field) = this.declaredFields.find { submissionField ->
    submissionField != null &&
        submissionField.visibilityMatches(solutionField) &&
        submissionField.name == solutionField.name &&
        submissionField.type == solutionField.type &&
        submissionField.isStatic() == solutionField.isStatic()
}

class SolutionDesignError(message: String?) : Exception(message)

private fun failDesign(message: () -> Any) {
    designError(message().toString())
}

private fun checkDesign(check: Boolean, message: () -> Any) {
    if (!check) {
        designError(message().toString())
    }
}

private fun <T> checkDesign(method: () -> T): T {
    @Suppress("TooGenericExceptionCaught")
    return try {
        method()
    } catch (e: Exception) {
        designError(e.message)
    }
}

private fun designError(message: String?): Nothing = throw SolutionDesignError(message)

data class Settings(
    val seed: Int = -1,
    val testCount: Int = -1,
    val shrink: Boolean? = null,
    val runAll: Boolean? = null,
    val methodCount: Int = -1,
    val receiverCount: Int = -1,
    val minTestCount: Int = -1,
    val maxTestCount: Int = -1,
    val testing: Boolean? = null,
    val recordTrace: Boolean? = null,
) {
    companion object {
        const val DEFAULT_RECEIVER_RETRIES = 4
        val DEFAULTS = Settings(
            runAll = false,
            testing = false,
            recordTrace = false,
        )
    }

    @Suppress("LongMethod", "ComplexMethod")
    infix fun merge(other: Settings): Settings = Settings(
        if (other.seed != -1) {
            other.seed
        } else {
            seed
        },
        if (other.testCount != -1) {
            other.testCount
        } else {
            testCount
        },
        other.shrink ?: shrink,
        other.runAll ?: runAll,
        if (other.methodCount != -1) {
            other.methodCount
        } else {
            methodCount
        },
        if (other.receiverCount != -1) {
            other.receiverCount
        } else {
            receiverCount
        },
        if (other.minTestCount != -1) {
            other.minTestCount
        } else {
            minTestCount
        },
        if (other.maxTestCount != -1) {
            other.maxTestCount
        } else {
            maxTestCount
        },
        other.testing ?: testing,
        other.recordTrace ?: recordTrace,
    )
}
