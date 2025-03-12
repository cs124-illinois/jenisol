package edu.illinois.cs.cs125.jenisol.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe

class TestVersion :
    StringSpec({
        "should have a valid version" {
            VERSION shouldNotBe "unspecified"
        }
    })
