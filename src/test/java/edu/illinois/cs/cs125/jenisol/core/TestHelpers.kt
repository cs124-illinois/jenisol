package edu.illinois.cs.cs125.jenisol.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class KotlinTest {
    @Suppress("UNUSED_PARAMETER")
    fun method(first: Int, second: String): Int = first
}

class TestHelpers :
    StringSpec({
        "should print methods properly" {
            val parameters = Two(1, "two")
            val method = Test::class.java.declaredMethods.first()
            method.formatBoundMethodCall(
                parameters,
                Test::class.java,
            ) shouldBe """method(int first = 1, String second = "two")"""
            method.formatBoundMethodCall(
                parameters,
                KotlinTest::class.java,
            ) shouldBe """method(first: Int = 1, second: String = "two")"""
        }
        "should reformat types properly" {
            "byte".toKotlinType() shouldBe "Byte"
            "byte[]".toKotlinType() shouldBe "ByteArray"
            "byte[][]".toKotlinType() shouldBe "Array<ByteArray>"
            "byte[][][]".toKotlinType() shouldBe "Array<Array<ByteArray>>"
        }
        "should safely print anonymous exceptions with custom toString" {
            // Create an anonymous exception with a toString that would throw SecurityException
            val exception = object : Exception("PlaceNotFound") {
                @Suppress("ExceptionRaisedInUnexpectedLocation")
                override fun toString(): String = throw SecurityException("Cannot call toString outside sandbox")
            }

            // safePrint should fall back to className: message format
            // For anonymous classes, includes the full class name with message
            val result = exception.safePrint()
            result shouldBe $$"edu.illinois.cs.cs125.jenisol.core.TestHelpers$1$3$exception$1: PlaceNotFound"
        }
    })
