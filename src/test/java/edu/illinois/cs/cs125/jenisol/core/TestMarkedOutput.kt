package edu.illinois.cs.cs125.jenisol.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TestMarkedOutput :
    StringSpec({
        "should leave output without trailing spaces unchanged" {
            "why\n".toMarkedOutput() shouldBe "why\n<-- (length 4)"
            "why".toMarkedOutput() shouldBe "why<-- (length 3)"
            "".toMarkedOutput() shouldBe "<-- (length 0)"
            "  why\n".toMarkedOutput() shouldBe "  why\n<-- (length 6)"
        }
        "should put the marker on a final line with trailing spaces" {
            "why   \n".toMarkedOutput() shouldBe
                "why   <-- (length 7 including the final newline, line 1 has trailing spaces)"
            "why   ".toMarkedOutput() shouldBe "why   <-- (length 6, line 1 has trailing spaces)"
        }
        "should note earlier lines with trailing spaces" {
            "a \nb\n".toMarkedOutput() shouldBe "a \nb\n<-- (length 5, line 1 has trailing spaces)"
            "a \nb \n".toMarkedOutput() shouldBe
                "a \nb <-- (length 6 including the final newline, lines 1, 2 have trailing spaces)"
            "a \n".repeat(7).toMarkedOutput() shouldBe
                "a \n".repeat(7).removeSuffix("\n") +
                "<-- (length 21 including the final newline, lines 1, 2, 3, 4, 5, ... have trailing spaces)"
        }
        "should ignore carriage returns" {
            "why\r\n".toMarkedOutput() shouldBe "why\r\n<-- (length 5)"
        }
        "should note unprintable characters only when asked" {
            "why\u0007\n".toMarkedOutput() shouldBe "why\u0007\n<-- (length 5)"
            "why\u0007\n".toMarkedOutput(noteUnprintable = true) shouldBe
                "why\u0007\n<-- (length 5, contains unprintable characters)"
        }
    })
