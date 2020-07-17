@file:Suppress("MemberVisibilityCanBePrivate")

package edu.illinois.cs.cs125.jenisol.core

import edu.illinois.cs.cs125.jenisol.core.generators.Generators
import edu.illinois.cs.cs125.jenisol.core.generators.Value
import edu.illinois.cs.cs125.jenisol.core.generators.ZeroComplexity
import edu.illinois.cs.cs125.jenisol.core.generators.boxArray
import edu.illinois.cs.cs125.jenisol.core.generators.isAnyArray
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.time.Instant
import java.util.Arrays

@Suppress("ArrayInDataClass")
data class Result<T, P : ParameterGroup>(
    @JvmField val parameters: P,
    @JvmField val returned: T?,
    @JvmField val threw: Throwable?,
    @JvmField val stdout: String,
    @JvmField val stderr: String,
    @JvmField val modifiedParameters: Boolean
) {
    @Suppress("UNCHECKED_CAST")
    constructor(parameters: Array<Any?>, capturedResult: CapturedResult, modifiedParameters: Boolean) : this(
        parameters.toParameterGroup() as P,
        capturedResult.returned as T?,
        capturedResult.threw,
        capturedResult.stdout,
        capturedResult.stderr,
        modifiedParameters
    )
}

@Suppress("ArrayInDataClass")
data class TestResult<T, P : ParameterGroup>(
    @JvmField val runnerID: Int,
    @JvmField val stepCount: Int,
    @JvmField val runnerCount: Int,
    @JvmField val executable: Executable,
    @JvmField val type: Type,
    @JvmField val parameters: P,
    @JvmField val solution: Result<T, P>,
    @JvmField val submission: Result<T, P>,
    @JvmField val interval: Interval,
    @JvmField val complexity: Int,
    @JvmField val solutionClass: Class<*>,
    @JvmField val submissionClass: Class<*>,
    @JvmField var message: String? = null,
    @JvmField val differs: MutableSet<Differs> = mutableSetOf()
) {
    enum class Type { CONSTRUCTOR, INITIALIZER, METHOD }
    enum class Differs { STDOUT, STDERR, RETURN, THREW, PARAMETERS, VERIFIER_THREW }

    val succeeded: Boolean
        get() = differs.isEmpty()
    val failed: Boolean
        get() = !succeeded

    @Suppress("unused")
    var verifierThrew: Throwable? = null

    @Suppress("ComplexMethod", "LongMethod")
    fun explain(): String {
        val arrayOfParameters = parameters.toArray()
        val methodString = if (type == Type.CONSTRUCTOR) {
            submissionClass.simpleName
        } else {
            executable.name
        } + "(" +
            executable.parameters
                .mapIndexed { index, parameter ->
                    "${parameter.type.simpleName} ${parameter.name} = ${print(arrayOfParameters[index])}"
                }
                .joinToString(", ") +
            ")"

        val resultString = when {
            verifierThrew != null -> verifierThrew!!.message
            differs.contains(Differs.STDOUT) -> {
                """
Solution printed:
---
${solution.stdout}---
Submission printed:
---
${submission.stdout}---""".trim()
            }
            differs.contains(Differs.STDERR) -> {
                """
Solution printed to STDERR:
---
${solution.stderr}---
Submission printed to STDERR:
---
${submission.stderr}---""".trim()
            }
            differs.contains(Differs.RETURN) -> {
                """
Solution returned: ${print(solution.returned)}
Submission returned: ${print(submission.returned)}
                """.trimIndent()
            }
            differs.contains(Differs.THREW) -> {
                if (solution.threw == null) {
                    """Solution did not throw an exception"""
                } else {
                    """Solution threw: ${solution.threw}"""
                } + "\n" + if (submission.threw == null) {
                    """Submission did not throw an exception"""
                } else {
                    """Submission threw: ${submission.threw}"""
                }
            }
            differs.contains(Differs.PARAMETERS) -> {
                if (!solution.modifiedParameters && submission.modifiedParameters) {
                    """
Solution did not modify its parameters
Submission did modify its parameters to ${print(submission.parameters.toArray())}
                    """.trim()
                } else if (solution.modifiedParameters && !submission.modifiedParameters) {
                    """
Solution modified its parameters to ${print(solution.parameters.toArray())}
Submission did not modify its parameters
                    """.trim()
                } else {
                    """
Solution modified its parameters to ${print(solution.parameters.toArray())}
Submission modified its parameters to ${print(submission.parameters.toArray())}
                    """.trim()
                }
            }
            else -> error("Unexplained result")
        }
        return "Testing $methodString failed:\n${message?.let { it + "\n" } ?: ""}$resultString"
    }
}

fun print(value: Any?): String = when {
    value === null -> "null"
    value is ByteArray -> Arrays.toString(value)
    value is ShortArray -> Arrays.toString(value)
    value is IntArray -> Arrays.toString(value)
    value is LongArray -> Arrays.toString(value)
    value is FloatArray -> Arrays.toString(value)
    value is DoubleArray -> Arrays.toString(value)
    value is CharArray -> Arrays.toString(value)
    value is BooleanArray -> Arrays.toString(value)
    value is Array<*> -> value.joinToString { print(it) }
    else -> value.toString()
}

@Suppress("UNUSED")
class TestResults(
    val results: List<TestResult<Any, ParameterGroup>>
) : List<TestResult<Any, ParameterGroup>> by results {
    val succeeded = all { it.succeeded }
    val failed = !succeeded
    fun explain() = if (succeeded) {
        "Passed"
    } else {
        filter { it.failed }.sortedBy { it.complexity }.let { result ->
            val leastComplex = result.first().complexity
            result.filter { it.complexity == leastComplex }
        }.minBy { it.stepCount }!!.explain()
    }
}

class TestRunner(
    val runnerID: Int,
    val submission: Submission,
    var generators: Generators,
    val constructors: Sequence<Constructor<*>>
) {

    val methodIterator = submission.solution.solutionMethods.cycle()
    val testResults: MutableList<TestResult<*, *>> = mutableListOf()

    val ready: Boolean
        get() = testResults.none { it.failed } && receivers != null

    var receivers: Value<Any?>? = null

    init {
        if (submission.solution.noReceiver) {
            receivers = Value(null, null, null, null, ZeroComplexity)
        }
    }

    private var count = 0

    @Suppress("ComplexMethod", "LongMethod", "ComplexCondition")
    fun run(solutionExecutable: Executable, stepCount: Int, type: TestResult.Type? = null): TestResult<*, *> {
        val start = Instant.now()
        val submissionExecutable = submission.submissionExecutables[solutionExecutable]
            ?: error("couldn't find a submission method that should exist")
        check(solutionExecutable::class.java == submissionExecutable::class.java) {
            "solution and submission executable are not the same type"
        }

        val generator = generators[solutionExecutable]
            ?: error("couldn't find a parameter generator that should exist")
        val parameters = generator.generate()

        val stepType = type ?: when (solutionExecutable) {
            is Method -> TestResult.Type.METHOD
            is Constructor<*> -> TestResult.Type.CONSTRUCTOR
            else -> error("encountered unknown executable type: $solutionExecutable")
        }

        if (stepType == TestResult.Type.METHOD) {
            check(receivers != null) { "No receivers available" }
        }
        val solutionResult = submission.solution.captureOutput {
            unwrap {
                when (solutionExecutable) {
                    is Method -> solutionExecutable.invoke(receivers!!.solution, *parameters.solution)
                    is Constructor<*> -> solutionExecutable.newInstance(*parameters.solution)
                    else -> error("encountered unknown executable type: $solutionExecutable")
                }
            }
        }.let {
            Result<Any, ParameterGroup>(
                parameters.solution, it, !submission.compare(parameters.solution, parameters.solutionCopy)
            )
        }

        val submissionResult = submission.solution.captureOutput {
            unwrap {
                when (submissionExecutable) {
                    is Method -> submissionExecutable.invoke(receivers!!.submission, *parameters.submission)
                    is Constructor<*> -> submissionExecutable.newInstance(*parameters.submission)
                    else -> error("encountered unknown executable type: $submissionExecutable")
                }
            }
        }.let {
            Result<Any, ParameterGroup>(
                parameters.submission, it, !submission.compare(parameters.submission, parameters.submissionCopy)
            )
        }

        // If this is a constructor and it didn't fail, generate additional references object
        // so that we can donate this to the receiver generator later
        val (solutionCopy, submissionCopy) = if (
            solutionExecutable is Constructor<*> && submissionExecutable is Constructor<*> &&
            solutionResult.returned != null && submissionResult.returned != null
        ) {
            Pair(submission.solution.captureOutput {
                unwrap { solutionExecutable.newInstance(*parameters.solutionCopy) }
            }.let {
                Result<Any, ParameterGroup>(parameters.solutionCopy, it, false)
            }, submission.solution.captureOutput {
                unwrap { submissionExecutable.newInstance(*parameters.submissionCopy) }
            }.let {
                Result<Any, ParameterGroup>(parameters.submissionCopy, it, false)
            })
        } else {
            Pair(null, null)
        }

        return TestResult(
            runnerID,
            stepCount, count++,
            solutionExecutable, stepType, parameters.solutionCopy.toParameterGroup(),
            solutionResult, submissionResult,
            Interval(start),
            parameters.complexity.level,
            submission.solution.solution,
            submission.submission
        ).also { step ->
            submission.verify(step)
            testResults.add(step)
            if (step.succeeded) {
                generator.next()
            } else {
                generator.prev()
            }
            if (step.succeeded && stepType == TestResult.Type.CONSTRUCTOR) {
                // If both constructors throw identically, then the step didn't fail but
                // this test runner still can't proceed
                receivers = if (step.solution.returned != null) {
                    Value(
                        step.solution.returned,
                        step.submission.returned,
                        solutionCopy!!.returned,
                        submissionCopy!!.returned,
                        parameters.complexity
                    )
                } else {
                    null
                }
            }
        }
    }

    var created = false
    fun next(stepCount: Int): Boolean {
        if (!submission.solution.noReceiver && !created) {
            run(constructors.first(), stepCount)
            if (ready && submission.solution.initializer != null) {
                run(submission.solution.initializer, stepCount, TestResult.Type.INITIALIZER)
            }
            created = true
        } else {
            run(methodIterator.first(), stepCount)
        }
        return ready
    }
}

data class Interval(val start: Instant, val end: Instant) {
    constructor(start: Instant) : this(start, Instant.now())
}

@Suppress("ComplexMethod", "MapGetWithNotNullAssertionOperator")
fun Any.deepEquals(
    submission: Any?,
    comparators: Comparators
): Boolean = when {
    this === submission -> true
    submission == null -> false
    this::class.java in comparators -> comparators[this::class.java].compare(this, submission)
    this is ParameterGroup && submission is ParameterGroup ->
        this.toArray().deepEquals(submission.toArray(), comparators)
    this.isAnyArray() != submission.isAnyArray() -> false
    this.isAnyArray() && submission.isAnyArray() -> {
        val solutionBoxed = this.boxArray()
        val submissionBoxed = submission.boxArray()
        (solutionBoxed.size == submissionBoxed.size) && solutionBoxed.zip(submissionBoxed)
            .all { (solution, submission) ->
                when {
                    solution === submission -> true
                    solution == null || submission === null -> false
                    else -> solution.deepEquals(submission, comparators)
                }
            }
    }
    else -> this == submission
}