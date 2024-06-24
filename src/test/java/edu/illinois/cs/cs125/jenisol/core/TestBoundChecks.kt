package edu.illinois.cs.cs125.jenisol.core

import examples.boundchecktesting.TestBoundsA
import examples.boundchecktesting.TestBoundsB
import examples.boundchecktesting.TestBoundsC
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable

fun Type.rewriteType(parameter: String, replace: Type) = when (this) {
    is ParameterizedType -> object : ParameterizedType {
        override fun getActualTypeArguments(): Array<Type> = this@rewriteType.actualTypeArguments.map {
            when {
                it.typeName == parameter -> replace
                else -> it
            }
        }.toTypedArray()

        override fun getRawType(): Type? = this@rewriteType.rawType

        override fun getOwnerType(): Type? = this@rewriteType.ownerType
    }

    else -> this
}

fun TypeVariable<*>.matches(klass: Class<*>) = bounds.map { bound ->
    bound.rewriteType(this.name, klass)
}.all { rewritten ->
    (rewritten is Class<*> && rewritten.isAssignableFrom(klass)) ||
        klass.genericInterfaces.any { it == rewritten }
}

class Testing
class TestBoundChecks :
    StringSpec({
        "should check bounds" {
            TestBoundsA::class.java.typeParameters.first().also { classTypeParameter ->
                classTypeParameter.matches(Any::class.java) shouldBe true
                classTypeParameter.matches(String::class.java) shouldBe true
                classTypeParameter.matches(Testing::class.java) shouldBe true
            }
            TestBoundsB::class.java.typeParameters.first().also { classTypeParameter ->
                classTypeParameter.matches(Any::class.java) shouldBe false
                classTypeParameter.matches(String::class.java) shouldBe true
                classTypeParameter.matches(Testing::class.java) shouldBe false
            }
            TestBoundsC::class.java.typeParameters.first().also { classTypeParameter ->
                classTypeParameter.matches(Any::class.java) shouldBe false
                classTypeParameter.matches(String::class.java) shouldBe false
                classTypeParameter.matches(Integer::class.java) shouldBe true
            }
        }
    })
