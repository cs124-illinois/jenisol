package edu.illinois.cs.cs125.jenisol.core

import io.github.classgraph.ClassGraph
import io.kotest.core.spec.style.StringSpec

class TestKotlinExamples :
    StringSpec(
        {
            val ignoredPackages = setOf<String>()

            ClassGraph()
                .acceptPackages("examples.kotlin")
                .scan()
                .allClasses
                .filter { it.simpleName == "Correct" || it.simpleName == "CorrectKt" }
                .map { it.loadClass() }
                .groupBy { it.packageName }
                .toSortedMap()
                .map { (_, classes) ->
                    classes.find { it.simpleName == "Correct" } ?: classes.first()
                }
                .forEach { klass ->
                    val testName = if (klass.packageName in ignoredPackages) {
                        "!${klass.testName()}"
                    } else {
                        klass.testName()
                    }
                    testName { klass.test() }
                }
        },
    )
