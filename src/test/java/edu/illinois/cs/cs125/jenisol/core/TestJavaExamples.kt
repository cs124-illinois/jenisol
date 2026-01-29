package edu.illinois.cs.cs125.jenisol.core

import io.github.classgraph.ClassGraph
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import kotlin.time.ExperimentalTime

@ExperimentalTime
class TestJavaExamples :
    StringSpec(
        {
            val ignoredPackages = setOf<String>()

            val specialCases = mapOf<String, suspend () -> Unit>(
                "examples.java.receiver.constructoralwaysthrows" to {
                    examples.java.receiver.constructoralwaysthrows.Correct::class.java.test(
                        ignoreSolutionSequenceMismatch = true,
                    )
                },
                "examples.java.receiver.arrayfactory" to {
                    examples.java.receiver.arrayfactory.Correct::class.java.test(
                        ignoreSolutionSequenceMismatch = true,
                    )
                },
                "examples.java.noreceiver.filternotnullwithrandomgeneratesnull" to {
                    try {
                        examples.java.noreceiver.filternotnullwithrandomgeneratesnull.Correct::class.java.test()
                        error("Should have failed")
                    } catch (_: Exception) {
                    }
                },
            )

            val discoveredPackages = mutableSetOf<String>()

            ClassGraph()
                .acceptPackages("examples.java")
                .rejectPackages("examples.java.submissiondesign")
                .scan()
                .allClasses
                .filter { it.simpleName == "Correct" }
                .map { it.loadClass() }
                .sortedBy { it.packageName }
                .forEach { klass ->
                    val pkg = klass.packageName
                    discoveredPackages += pkg
                    val testName = if (pkg in ignoredPackages) "!${klass.testName()}" else klass.testName()
                    val specialCase = specialCases[pkg]
                    if (specialCase != null) {
                        testName { specialCase() }
                    } else {
                        testName { klass.test() }
                    }
                }

            // Verify all special cases were found (catches stale entries)
            val unmatched = specialCases.keys - discoveredPackages
            check(unmatched.isEmpty()) { "Special cases not found on classpath: $unmatched" }

            // fixedparametersusesrandom has no Correct class — it's a standalone test
            examples.java.noreceiver.fixedparametersusesrandom.First::class.java.also {
                "${it.testName()}" {
                    val firstSolution = Solution(it)
                    shouldThrow<IllegalStateException> {
                        firstSolution.checkFields(it)
                    }

                    val second = examples.java.noreceiver.fixedparametersusesrandom.Second::class.java
                    val exception = shouldThrow<IllegalStateException> {
                        firstSolution.checkFields(second)
                    }
                    exception.message shouldContain "Field FIXED"

                    val third = examples.java.noreceiver.fixedparametersusesrandom.Third::class.java
                    firstSolution.checkFields(third)
                }
            }
        },
    )
