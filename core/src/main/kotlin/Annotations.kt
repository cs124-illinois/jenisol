@file:Suppress("TooManyFunctions", "MemberVisibilityCanBePrivate")

package edu.illinois.cs.cs125.jenisol.core

import com.rits.cloning.Cloner
import edu.illinois.cs.cs125.jenisol.core.generators.boxArray
import edu.illinois.cs.cs125.jenisol.core.generators.compareBoxed
import edu.illinois.cs.cs125.jenisol.core.generators.isAnyArray
import edu.illinois.cs.cs125.jenisol.core.generators.isLambdaMethod
import java.lang.RuntimeException
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.Random

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class SimpleType {
    companion object {
        fun validate(field: Field): Class<*> = validateTypeField(field, SimpleType::class.java.simpleName)
    }
}

fun Field.isSimpleType() = isAnnotationPresent(SimpleType::class.java)

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class EdgeType {
    companion object {
        fun validate(field: Field): Class<*> = validateTypeField(field, EdgeType::class.java.simpleName)
    }
}

fun Field.isEdgeType() = isAnnotationPresent(EdgeType::class.java)

private val typeFieldAnnotations = setOf(SimpleType::class.java, EdgeType::class.java)
private fun validateTypeField(field: Field, name: String): Class<*> {
    check(field.isStatic()) { "@$name fields must be static" }
    check(field.isFinal()) { "@$name fields must be final" }
    check(field.type.isArray) { "@$name fields must annotate arrays" }
    field.isAccessible = true
    return field.type.componentType
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RandomType {
    companion object {
        val name: String = RandomType::class.java.simpleName
        fun validate(method: Method): Class<*> {
            check(method.isStatic()) { "@$name methods must be static" }
            check(
                method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == Int::class.java &&
                    method.parameterTypes[1] == Random::class.java
            ) {
                "@$name methods must accept parameters (int complexity, java.util.Random random)"
            }
            method.isAccessible = true
            return method.returnType
        }
    }
}

fun Method.isRandomType() = isAnnotationPresent(RandomType::class.java)

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class FixedParameters {
    companion object {
        val name: String = FixedParameters::class.java.simpleName
        fun validate(field: Field): Array<Type> {
            check(field.isStatic()) { "@$name fields must be static" }
            check(field.genericType is ParameterizedType) {
                "@$name parameter fields must annotate parameterized collections"
            }
            (field.genericType as ParameterizedType).also { collectionType ->
                check(collectionType.actualTypeArguments.size == 1) {
                    "@$name parameter fields must annotate parameterized collections"
                }
                collectionType.actualTypeArguments.first().also { itemType ->
                    check(itemType is ParameterizedType && itemType.rawType in parameterGroupTypes) {
                        "@$name parameter fields must annotate collections of types " +
                            parameterGroupTypes.joinToString(", ") { it.simpleName }
                    }
                    field.isAccessible = true
                    return itemType.actualTypeArguments
                }
            }
        }
    }
}

fun Field.isFixedParameters() = isAnnotationPresent(FixedParameters::class.java)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RandomParameters {
    companion object {
        val name: String = RandomParameters::class.java.simpleName
        fun validate(method: Method): Array<Type> {
            check(method.isStatic()) { "@$name methods must be static" }
            check(
                method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == Int::class.java &&
                    method.parameterTypes[1] == Random::class.java
            ) {
                "@$name methods must accept parameters (int complexity, java.util.Random random)"
            }
            check(method.returnType in parameterGroupTypes) {
                "@$name parameter methods must return one of types " +
                    parameterGroupTypes.joinToString(", ") { it.simpleName }
            }
            method.isAccessible = true
            return (method.genericReturnType as ParameterizedType).actualTypeArguments
        }
    }
}

fun Method.isRandomParameters() = isAnnotationPresent(RandomParameters::class.java)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class FilterParameters {
    companion object {
        val name: String = FilterParameters::class.java.simpleName
        fun validate(method: Method): Array<Type> {
            check(method.isStatic()) { "@$name methods must be static" }
            check(method.returnType.name == "void") {
                "@$name method return values will not be used and should be void"
            }
            method.isAccessible = true
            return method.genericParameterTypes
        }
    }
}

fun Method.isFilterParameters() = isAnnotationPresent(FilterParameters::class.java)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Initializer {
    companion object {
        val name: String = Initializer::class.java.simpleName
        fun validate(method: Method) {
            check(!method.isStatic()) { "@$name methods must not be static" }
            check(method.returnType.name == "void") {
                "@$name method return values will not be used and should be void"
            }
            method.isAccessible = true
        }
    }
}

fun Method.isInitializer() = isAnnotationPresent(Initializer::class.java)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class InstanceValidator {
    companion object {
        val name: String = InstanceValidator::class.java.simpleName
        fun validate(method: Method) {
            check(method.isStatic()) { "@$name methods must be static" }
            check(
                method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == Object::class.java
            ) {
                "@${RandomParameters.name} methods must accept parameters (Object instance)"
            }
            check(method.returnType.name == "void") {
                "@$name method return values will not be used and should be void"
            }
            method.isAccessible = true
        }
    }
}

fun Method.isInstanceValidator() = isAnnotationPresent(InstanceValidator::class.java)

class InstanceValidationException(msg: String) : RuntimeException(msg)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Verify {
    companion object {
        val name: String = Verify::class.java.simpleName
        fun validate(method: Method, returnType: Type, parameterTypes: Array<Type>) {
            check(method.isStatic()) { "@$name methods must be static" }
            check(method.returnType.name == "void") {
                "@$name method return values will not be used and should be void"
            }
            check(
                method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == TestResult::class.java &&
                    method.genericParameterTypes[0] is ParameterizedType &&
                    (method.genericParameterTypes[0] as ParameterizedType).actualTypeArguments.size == 2 &&
                    (
                        (method.genericParameterTypes[0] as ParameterizedType)
                            .actualTypeArguments[0]
                        ).compareBoxed(returnType) &&
                    (method.genericParameterTypes[0] as ParameterizedType)
                        .actualTypeArguments[1].parameterGroupMatches(parameterTypes)
            ) {
                "@$name methods must accept parameters " +
                    "(${TestResult::class.java.simpleName}<${returnType.typeName}> results)"
            }
            method.isAccessible = true
        }
    }
}

fun Method.isVerify() = isAnnotationPresent(Verify::class.java)

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Both {
    companion object {
        val name: String = Both::class.java.simpleName
        fun validate(method: Method, solution: Class<*>) {
            check(method.isStatic()) { "@$name methods must be static" }
            check(method.returnType.name != "void") {
                "@$name method return values must not be void"
            }
            check(method.returnType != solution) {
                "@$name method return values must not return a receiver type"
            }
            check(
                method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] !== solution &&
                    method.parameterTypes[0].isAssignableFrom(solution)
            ) {
                "@$name methods must accept a single parameter that the solution class inherits from"
            }
            method.isAccessible = true
        }
    }
}

fun Method.isBoth() = isAnnotationPresent(Both::class.java)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Configure(val strictOutput: Boolean = false)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DesignOnly

fun Class<*>.isDesignOnly() = isAnnotationPresent(DesignOnly::class.java)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CheckSource {
    companion object {
        val name: String = CheckSource::class.java.simpleName
        fun validate(method: Method) {
            check(method.isStatic()) { "@$name methods must be static" }
            check(method.returnType.name == "void") {
                "@$name method return values will not be used and should be void"
            }
            check(method.parameterTypes.size == 1 && method.parameterTypes[0] == String::class.java) {
                "@$name methods must accept parameters (String source)"
            }
            method.isAccessible = true
        }
    }
}

fun Method.isCheckSource() = isAnnotationPresent(CheckSource::class.java)

fun Field.isStatic() = Modifier.isStatic(modifiers)
fun Field.isFinal() = Modifier.isFinal(modifiers)
fun Field.isPublic() = Modifier.isPublic(modifiers)
fun Field.isPrivate() = Modifier.isPrivate(modifiers)
fun Field.isProtected() = Modifier.isProtected(modifiers)
fun Field.isPackagePrivate() = !isPublic() && !isPrivate() && !isProtected()

fun Any.asArray(): Array<*> {
    return when (this) {
        is ByteArray -> this.toTypedArray()
        is ShortArray -> this.toTypedArray()
        is IntArray -> this.toTypedArray()
        is LongArray -> this.toTypedArray()
        is FloatArray -> this.toTypedArray()
        is DoubleArray -> this.toTypedArray()
        is CharArray -> this.toTypedArray()
        is BooleanArray -> this.toTypedArray()
        else -> this as Array<*>
    }
}

fun Executable.isJenisol() = setOf(
    RandomType::class.java,
    RandomParameters::class.java,
    Initializer::class.java,
    Verify::class.java,
    Both::class.java,
    FilterParameters::class.java,
    InstanceValidator::class.java,
    CheckSource::class.java,
).any {
    isAnnotationPresent(it)
}

fun Field.isJenisol() = (typeFieldAnnotations + FixedParameters::class.java).any {
    isAnnotationPresent(it)
}

private val parameterGroupTypes = setOf(
    None::class.java,
    One::class.java,
    Two::class.java,
    Three::class.java,
    Four::class.java
)

interface ParameterGroup {
    fun toArray(): Array<Any?>
    fun toList(): List<Any?>
}

fun ParameterGroup.deepCopy(): ParameterGroup {
    return Cloner().let { cloner ->
        toArray().toList().map {
            if (it?.isLambdaMethod() == true) {
                it
            } else {
                cloner.deepClone(it)
            }
        }.toTypedArray().toParameterGroup()
    }
}

@Suppress("MagicNumber")
fun Array<Any?>.toParameterGroup() = when (size) {
    0 -> None
    1 -> One(get(0))
    2 -> Two(get(0), get(1))
    3 -> Three(get(0), get(1), get(2))
    4 -> Four(get(0), get(1), get(2), get(3))
    else -> error("No parameter group for array of size $size")
}

object None : ParameterGroup {
    override fun toArray() = arrayOf<Any?>()
    override fun toList() = listOf<Any?>()
    override fun toString(): String {
        return "None()"
    }
}

class One<I>(setFirst: I) : ParameterGroup {
    @JvmField
    val first: I = if (setFirst is String) {
        @Suppress("UNCHECKED_CAST")
        String(setFirst.toCharArray()) as I
    } else {
        setFirst
    }

    override fun toArray() = arrayOf<Any?>(first)
    override fun toList() = listOf<Any?>(first)

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is One<*> -> false
        else -> listOf(first).deepCompare(listOf(other.first))
    }

    override fun hashCode() = first?.deepHashCode() ?: 0
    override fun toString(): String {
        return toArray().safeContentDeepToString()
    }
}

class Two<I, J>(setFirst: I, setSecond: J) : ParameterGroup {
    @JvmField
    val first: I = if (setFirst is String) {
        @Suppress("UNCHECKED_CAST")
        String(setFirst.toCharArray()) as I
    } else {
        setFirst
    }

    @JvmField
    val second: J = if (setSecond is String) {
        @Suppress("UNCHECKED_CAST")
        String(setSecond.toCharArray()) as J
    } else {
        setSecond
    }

    override fun toArray() = arrayOf(first, second)
    override fun toList() = listOf(first, second)

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is Two<*, *> -> false
        else -> listOf(first, second).deepCompare(listOf(other.first, other.second))
    }

    override fun hashCode(): Int {
        var result = first?.deepHashCode() ?: 0
        result = 31 * result + (second?.deepHashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return toArray().safeContentDeepToString()
    }
}

class Three<I, J, K>(setFirst: I, setSecond: J, setThird: K) : ParameterGroup {
    @JvmField
    val first: I = if (setFirst is String) {
        @Suppress("UNCHECKED_CAST")
        String(setFirst.toCharArray()) as I
    } else {
        setFirst
    }

    @JvmField
    val second: J = if (setSecond is String) {
        @Suppress("UNCHECKED_CAST")
        String(setSecond.toCharArray()) as J
    } else {
        setSecond
    }

    @JvmField
    val third: K = if (setThird is String) {
        @Suppress("UNCHECKED_CAST")
        String(setThird.toCharArray()) as K
    } else {
        setThird
    }

    override fun toArray() = arrayOf(first, second, third)
    override fun toList() = listOf(first, second, third)

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is Three<*, *, *> -> false
        else -> listOf(first, second, third).deepCompare(listOf(other.first, other.second, other.third))
    }

    override fun hashCode(): Int {
        var result = first?.deepHashCode() ?: 0
        result = 31 * result + (second?.deepHashCode() ?: 0)
        result = 31 * result + (third?.deepHashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return toArray().safeContentDeepToString()
    }
}

class Four<I, J, K, L>(
    setFirst: I,
    setSecond: J,
    setThird: K,
    setFourth: L
) : ParameterGroup {
    @JvmField
    val first: I = if (setFirst is String) {
        @Suppress("UNCHECKED_CAST")
        String(setFirst.toCharArray()) as I
    } else {
        setFirst
    }

    @JvmField
    val second: J = if (setSecond is String) {
        @Suppress("UNCHECKED_CAST")
        String(setSecond.toCharArray()) as J
    } else {
        setSecond
    }

    @JvmField
    val third: K = if (setThird is String) {
        @Suppress("UNCHECKED_CAST")
        String(setThird.toCharArray()) as K
    } else {
        setThird
    }

    @JvmField
    val fourth: L = if (setFourth is String) {
        @Suppress("UNCHECKED_CAST")
        String(setFourth.toCharArray()) as L
    } else {
        setFourth
    }

    override fun toArray() = arrayOf(first, second, third, fourth)
    override fun toList() = listOf(first, second, third, fourth)

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is Four<*, *, *, *> -> false
        else ->
            listOf(first, second, third, fourth)
                .deepCompare(listOf(other.first, other.second, other.third, other.fourth))
    }

    override fun hashCode(): Int {
        var result = first?.deepHashCode() ?: 0
        result = 31 * result + (second?.deepHashCode() ?: 0)
        result = 31 * result + (third?.deepHashCode() ?: 0)
        result = 31 * result + (fourth?.deepHashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return toArray().safeContentDeepToString()
    }
}

fun Any.deepHashCode() = when {
    isAnyArray() -> boxArray().contentDeepHashCode()
    isLambdaMethod() -> this.javaClass.name.hashCode()
    else -> hashCode()
}

@Suppress("ComplexMethod")
fun List<*>.deepCompare(other: List<*>) = if (size != other.size) {
    false
} else {
    zip(other).all { (mine, other) ->
        when {
            mine == other -> true
            mine == null && other != null -> false
            mine != null && other == null -> false
            mine == null -> false
            other == null -> false
            mine.isLambdaMethod() && other.isLambdaMethod() -> mine.javaClass.name == other.javaClass.name
            mine.isAnyArray() != other.isAnyArray() -> false
            !mine.isAnyArray() -> mine == other
            mine.boxArray().contentDeepEquals(other.boxArray()) -> true
            else -> false
        }
    }
}

@Suppress("ReturnCount")
fun Type.parameterGroupMatches(parameters: Array<Type>) = if (this == None::class.java && parameters.isEmpty()) {
    true
} else {
    (this as ParameterizedType).actualTypeArguments.compareBoxed(parameters)
}

// Borrowed from Kotlin core

@Suppress("MagicNumber")
internal fun <T> Array<out T>?.safeContentDeepToString(): String {
    if (this == null) return "null"
    val length = size.coerceAtMost((Int.MAX_VALUE - 2) / 5) * 5 + 2 // in order not to overflow Int.MAX_VALUE
    return buildString(length) {
        safeContentDeepToStringInternal(this, mutableListOf())
    }
}

@Suppress("ComplexMethod")
private fun <T> Array<out T>.safeContentDeepToStringInternal(result: StringBuilder, processed: MutableList<Array<*>>) {
    if (this in processed) {
        result.append("[...]")
        return
    }
    processed.add(this)
    result.append('[')

    for (i in indices) {
        if (i != 0) {
            result.append(", ")
        }
        when (val element = this[i]) {
            null -> result.append("null")
            is Array<*> -> element.safeContentDeepToStringInternal(result, processed)
            is ByteArray -> result.append(element.contentToString())
            is ShortArray -> result.append(element.contentToString())
            is IntArray -> result.append(element.contentToString())
            is LongArray -> result.append(element.contentToString())
            is FloatArray -> result.append(element.contentToString())
            is DoubleArray -> result.append(element.contentToString())
            is CharArray -> result.append(element.contentToString())
            is BooleanArray -> result.append(element.contentToString())
            else -> result.append(element.safePrint())
        }
    }

    result.append(']')
    processed.removeAt(processed.lastIndex)
}

fun Any.safePrint() = try {
    toString()
} catch (_: Exception) {
    this::class.simpleName!!
}
