@file:Suppress("MemberVisibilityCanBePrivate")

package edu.illinois.cs.cs125.jenisol.core

import edu.illinois.cs.cs125.jenisol.core.generators.Complexity
import edu.illinois.cs.cs125.jenisol.core.generators.Generators
import edu.illinois.cs.cs125.jenisol.core.generators.JenisolFileSystem
import edu.illinois.cs.cs125.jenisol.core.generators.ParameterValues
import edu.illinois.cs.cs125.jenisol.core.generators.Parameters
import edu.illinois.cs.cs125.jenisol.core.generators.SystemIn
import edu.illinois.cs.cs125.jenisol.core.generators.Value
import edu.illinois.cs.cs125.jenisol.core.generators.ZeroComplexity
import edu.illinois.cs.cs125.jenisol.core.generators.boxType
import edu.illinois.cs.cs125.jenisol.core.generators.fileSystemDummyExecutable
import edu.illinois.cs.cs125.jenisol.core.generators.getArrayDimension
import edu.illinois.cs.cs125.jenisol.core.generators.getArrayType
import edu.illinois.cs.cs125.jenisol.core.generators.systemInDummyExecutable
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method
import kotlin.reflect.full.companionObjectInstance

data class Result<T, P : ParameterGroup>(
    @JvmField val parameters: P,
    @JvmField val returned: T?,
    @JvmField val threw: Throwable?,
    @JvmField val stdout: String,
    @JvmField val stderr: String,
    @JvmField val stdin: String,
    @JvmField val interleavedOutput: String,
    @JvmField val truncatedLines: Int,
    @JvmField val tag: Any?,
    @JvmField val modifiedParameters: Boolean,
    @JvmField val lengthNanos: Long,
) {
    @Suppress("UNCHECKED_CAST")
    constructor(
        parameters: Array<Any?>,
        capturedResult: CapturedResult,
        modifiedParameters: Boolean,
        lengthNanos: Long,
    ) : this(
        parameters.toParameterGroup() as P,
        capturedResult.returned as T?,
        capturedResult.threw,
        capturedResult.stdout,
        capturedResult.stderr,
        capturedResult.stdin,
        capturedResult.interleavedInputOutput,
        capturedResult.truncatedLines,
        capturedResult.tag,
        modifiedParameters,
        lengthNanos,
    )

    override fun toString(): String = "Result(parameters=$parameters, " +
        "returned=${returned?.safePrint()}, " +
        "threw=${threw?.safePrint()}, " +
        "stdout='$stdout', " +
        "stderr='$stderr', " +
        "stdin='$stdin', " +
        "tag='$tag', " +
        "modifiedParameters=$modifiedParameters)"
}

internal fun <P : ParameterGroup> Executable.formatBoundMethodCall(parameterValues: P, klass: Class<*>): String {
    val arrayOfParameters = parameterValues.toArray()
    return if (this is Constructor<*>) {
        if (klass.isKotlin()) {
            klass.simpleName
        } else {
            "new ${klass.simpleName}"
        }
    } else {
        name
    } + "(" +
        parameters
            .mapIndexed { index, parameter ->
                if (klass.isKotlin()) {
                    "${parameter.name}: ${parameter.type.kotlin.simpleName} = ${print(arrayOfParameters[index])}"
                } else {
                    "${parameter.type.simpleName} ${parameter.name} = ${print(arrayOfParameters[index])}"
                }
            }
            .joinToString(", ") +
        ")"
}

private fun String.hasUnprintableCharacter() = this.any { char -> !char.isWhitespace() && char < 32.toChar() }

@Suppress("unused")
data class TestResult<T, P : ParameterGroup>(
    @JvmField val runnerID: Int,
    @JvmField val stepCount: Int,
    @JvmField val runnerCount: Int,
    @JvmField val solutionExecutable: Executable,
    @JvmField val submissionExecutable: Executable,
    @JvmField val type: Type,
    @JvmField val allParameters: Parameters,
    @JvmField val solution: Result<T, P>,
    @JvmField val submission: Result<T, P>,
    @JvmField val timeNanos: Long,
    @JvmField val complexity: Int,
    @JvmField val solutionClass: Class<*>,
    @JvmField val submissionClass: Class<*>,
    @JvmField val solutionReceiver: Any?,
    @JvmField val submissionReceiver: Any?,
    @JvmField var message: String? = null,
    @JvmField val differs: MutableSet<Differs> = mutableSetOf(),
    @JvmField val submissionIsKotlin: Boolean = submissionClass.isKotlin(),
    @JvmField val existingReceiverMismatch: Boolean = false,
    @JvmField val solutionMethodString: String,
    @JvmField val submissionMethodString: String,
    @JvmField val currentRandom: Int,
    @JvmField val randomCount: Int,
    @JvmField val solutionTimeNanos: Long,
    @JvmField val submissionTimeNanos: Long,
) {
    @Suppress("UNCHECKED_CAST")
    @JvmField
    val parameters: P = allParameters.unmodifiedCopy.toParameterGroup() as P

    enum class Type { CONSTRUCTOR, INITIALIZER, METHOD, STATIC_METHOD, FACTORY_METHOD, COPY_CONSTRUCTOR }
    enum class Differs {
        STDOUT,
        STDERR,
        INTERLEAVED_OUTPUT,
        RETURN,
        THREW,
        PARAMETERS,
        VERIFIER_THREW,
        INSTANCE_VALIDATION_THREW,
    }

    val succeeded: Boolean
        get() = differs.isEmpty()
    val failed: Boolean
        get() = !succeeded

    var verifierThrew: Throwable? = null

    @Suppress("ComplexMethod", "LongMethod", "NestedBlockDepth")
    fun explain(stacktrace: Boolean = false, omitMethodName: Boolean = false): String {
        check(failed) { "Can't explain successful result" }

        val resultString = when {
            verifierThrew != null -> "Verifier threw an exception: ${verifierThrew!!.message}"
            differs.contains(Differs.THREW) -> {
                if (solution.threw == null) {
                    """Solution did not throw an exception"""
                } else {
                    """Solution threw: ${solution.threw}"""
                } + "\n" + if (submission.threw == null) {
                    """Submission did not throw an exception"""
                } else {
                    """Submission threw: ${submission.threw}""" + if (stacktrace) {
                        "\n" + submission.threw.stackTraceToString().lines().let { lines ->
                            val trimIndex = lines.indexOfFirst { it.trim().startsWith("at java.base") }.let {
                                if (it == -1) {
                                    lines.size
                                } else {
                                    it
                                }
                            }
                            lines.take(trimIndex)
                        }.joinToString("\n")
                    } else {
                        ""
                    }
                }
            }

            differs.contains(Differs.STDOUT) -> {
                """
Solution printed:
-->
${solution.stdout}<-- (length ${solution.stdout.length})
Submission printed:
-->
${submission.stdout}<-- ${
                    if (submission.stdout.hasUnprintableCharacter()) {
                        "(length ${submission.stdout.length}, contains unprintable characters)"
                    } else {
                        "(length ${submission.stdout.length})"
                    }
                }""".trim()
            }

            differs.contains(Differs.STDERR) -> {
                """
Solution printed to STDERR:
-->
${solution.stderr}<-- (length ${solution.stderr.length})
Submission printed to STDERR:
-->
${submission.stderr}<-- ${
                    if (submission.stderr.hasUnprintableCharacter()) {
                        "(length ${submission.stderr.length}, contains unprintable characters)"
                    } else {
                        "(length ${submission.stderr.length})"
                    }
                }""".trim()
            }

            differs.contains(Differs.INTERLEAVED_OUTPUT) -> {
                """
Combined solution input and output:
-->
${solution.interleavedOutput}<-- (length ${solution.interleavedOutput.length})
Combined submission input and output:
-->
${submission.interleavedOutput}<-- ${
                    if (submission.interleavedOutput.hasUnprintableCharacter()) {
                        "(length ${submission.interleavedOutput.length}, contains unprintable characters)"
                    } else {
                        "(length ${submission.interleavedOutput.length})"
                    }
                }""".trim()
            }

            differs.contains(Differs.RETURN) -> {
                """
Solution returned: ${print(solution.returned)}
Submission returned: ${print(submission.returned)}
                """.trim()
            }

            differs.contains(Differs.PARAMETERS) -> {
                if (!solution.modifiedParameters && submission.modifiedParameters) {
                    """
Solution did not modify its parameters
Submission did modify its parameters to ${
                        print(
                            submission.parameters.toArray(),
                        )
                    }
                    """.trim()
                } else if (solution.modifiedParameters && !submission.modifiedParameters) {
                    """
Solution modified its parameters to ${print(solution.parameters.toArray())}
Submission did not modify its parameters
                    """.trim()
                } else {
                    """
Solution modified its parameters to ${print(solution.parameters.toArray())}
Submission modified its parameters to ${
                        print(
                            submission.parameters.toArray(),
                        )
                    }
                    """.trim()
                }
            }

            else -> error("Unexplained result")
        }
        return "Testing ${
            if (omitMethodName) {
                ""
            } else {
                "$submissionMethodString "
            }
        }failed:\n" +
            "$resultString${message?.let { "\nAdditional Explanation: $it" } ?: ""}"
    }

    override fun toString(): String = "TestResult(runnerID=$runnerID," +
        "stepCount=$stepCount, " +
        "runnerCount=$runnerCount, " +
        "solutionExecutable=$solutionExecutable, " +
        "submissionExecutable=$submissionExecutable, " +
        "type=$type, " +
        "allParameters=$allParameters, " +
        "solution=$solution, " +
        "submission=$submission, " +
        "timeNanos=$timeNanos, " +
        "complexity=$complexity, " +
        "solutionClass=$solutionClass, " +
        "submissionClass=$submissionClass, " +
        "solutionReceiver=$solutionReceiver, " +
        "submissionReceiver=$submissionReceiver, " +
        "message=$message, " +
        "differs=$differs, " +
        "submissionIsKotlin=$submissionIsKotlin, " +
        "existingReceiverMismatch=$existingReceiverMismatch, " +
        "solutionMethodString='$solutionMethodString', " +
        "submissionMethodString='$submissionMethodString', " +
        "currentRandom=$currentRandom, " +
        "randomCount=$randomCount, " +
        "solutionTimeNanos=$solutionTimeNanos, " +
        "submissionTimeNanos=$submissionTimeNanos, " +
        "parameters=$parameters, " +
        "verifierThrew=$verifierThrew)"
}

fun print(value: Any?): String = when {
    value === null -> "null"
    value is ByteArray -> value.contentToString()
    value is ShortArray -> value.contentToString()
    value is IntArray -> value.contentToString()
    value is LongArray -> value.contentToString()
    value is FloatArray -> value.contentToString()
    value is DoubleArray -> value.contentToString()
    value is CharArray -> value.contentToString()
    value is BooleanArray -> value.contentToString()
    value is Array<*> -> value.safeContentDeepToString()
    value is String -> "\"$value\""
    else -> value.safePrint()
}

@Suppress("UNUSED", "LongParameterList", "JavaDefaultMethodsNotOverriddenByDelegation")
class TestResults(
    val results: List<TestResult<Any, ParameterGroup>>,
    val settings: Settings,
    val completed: Boolean,
    val threw: Throwable? = null,
    val timeout: Boolean,
    val finishedReceivers: Boolean,
    val untestedReceivers: Int,
    designOnly: Boolean? = null,
    val skippedSteps: List<Int>,
    val stepCount: Int,
    val loopCount: Int,
    val randomTrace: List<Int>? = null,
) : List<TestResult<Any, ParameterGroup>> by results {
    val succeeded = designOnly ?: finishedReceivers && all { it.succeeded } && completed
    val failed = !succeeded
    fun explain(stacktrace: Boolean = false) = if (succeeded) {
        "Passed by completing ${results.size} tests"
    } else if (!finishedReceivers) {
        "Didn't complete generating receivers"
    } else if (!completed && !failed) {
        "Did not complete testing: $timeout"
    } else {
        filter { it.failed }.sortedBy { it.complexity }.let { result ->
            val leastComplex = result.first().complexity
            result.filter { it.complexity == leastComplex }
        }.minByOrNull { it.stepCount }!!.explain(stacktrace)
    }

    fun printTrace() = println(toString())

    @Suppress("MagicNumber")
    override fun toString(): String {
        val rawString = map { result ->
            result.apply {
                val solutionName: String = if (type == TestResult.Type.CONSTRUCTOR) {
                    ""
                } else {
                    "${solutionReceiver?.javaClass?.simpleName ?: solutionClass.simpleName}."
                }

                return@map stepCount.toString().padStart(4, ' ') +
                    "${runnerID.toString().padStart(4, ' ')}: SOL: " +
                    "${solutionName}$solutionMethodString -> ${solution.returned}" +
                    "\n${" ".repeat(8)}: SUB: ${submissionReceiver ?: submissionClass.simpleName}." +
                    "$submissionMethodString -> ${submission.returned}"
            }
        }.joinToString("\n")

        return rawString
    }
}

@Suppress("LongParameterList")
class TestRunner(
    val runnerID: Int,
    val submission: Submission,
    var generators: Generators,
    val receiverGenerators: Sequence<Executable>,
    val captureOutputControlInput: CaptureOutputControlInput,
    val methodPicker: Submission.ExecutablePicker,
    val settings: Settings,
    val runners: List<TestRunner>,
    var receivers: Value<Any?>?,
    val random: Submission.RecordingRandom,
    val testingEventListener: TestingEventListener,
) {
    val testResults = mutableListOf<TestResult<*, *>>()
    val skippedTests = mutableListOf<Int>()

    var staticOnly = submission.solution.skipReceiver

    val failed: Boolean
        get() = testResults.any { it.failed }
    val ready: Boolean
        get() = methodPicker.more() &&
            if (staticOnly) {
                true
            } else {
                (settings.runAll!! && receivers?.solution != null) ||
                    (testResults.none { it.failed } && receivers != null)
            }
    var ranLastTest = false
    var skippedLastTest = false

    var lastComplexity: Complexity? = null

    var returnedReceivers: List<Value<Any?>>? = null

    var created: Boolean
    var initialized: Boolean = false
    var tested: Boolean = false

    val systemInParameterGenerator = generators[systemInDummyExecutable]
    val fileSystemParameterGenerator = generators[fileSystemDummyExecutable]

    val classFileSystemParameters = if (submission.solution.usesFileSystem) {
        fileSystemParameterGenerator!!.generate(this)
    } else {
        null
    }

    init {
        if (receivers == null && staticOnly) {
            receivers = if (!submission.submission.hasKotlinCompanion()) {
                Value(null, null, null, null, null, ZeroComplexity)
            } else {
                Value(
                    null,
                    submission.submission.kotlin.companionObjectInstance,
                    null,
                    submission.submission.kotlin.companionObjectInstance,
                    null,
                    ZeroComplexity,
                )
            }
        }
        created = receivers != null
    }

    var count = 0

    fun Executable.checkParameters(parameters: Array<Any?>) {
        val mismatchedTypes = parameterTypes.zip(parameters).filter { (klass, parameter) ->
            if (parameter == null) {
                klass.isPrimitive
            } else {
                !klass.boxType().isAssignableFrom(parameter::class.java)
            }
        }

        check(mismatchedTypes.isEmpty()) {
            mismatchedTypes.first().let { (klass, parameter) -> "Can't assign $klass from $parameter" }
        }
    }

    fun Executable.pairRun(
        receiver: Any?,
        parameters: Array<Any?>,
        parametersCopy: Array<Any?>? = null,
        systemInParameters: SystemIn? = null,
        fileSystemParameters: JenisolFileSystem? = null,
    ): Result<Any, ParameterGroup> {
        checkParameters(parameters)
        if (parametersCopy != null) {
            checkParameters(parametersCopy)
        }

        val systemIn = systemInParameters?.input ?: listOf()
        val fileSystem = fileSystemParameters?.files ?: mapOf()

        val started = System.nanoTime()
        return captureOutputControlInput(systemIn, fileSystem) {
            @Suppress("SpreadOperator")
            unwrap {
                when (this@pairRun) {
                    is Method -> this@pairRun.invoke(receiver, *parameters)
                    is Constructor<*> -> this@pairRun.newInstance(*parameters)
                }
            }
        }.let {
            Result(
                parameters,
                it,
                parametersCopy?.let { !submission.compare(parameters, parametersCopy) } ?: false,
                System.nanoTime() - started,
            )
        }
    }

    private data class SolutionSubmissionResultPair(
        val solution: Result<Any, ParameterGroup>,
        val submission: Result<Any, ParameterGroup>,
    )

    private data class SolutionSubmissionReturnPair(val solution: Any, val submission: Any?)

    @Suppress("ReturnCount")
    private fun SolutionSubmissionResultPair.returnedReceivers(): Boolean {
        if (solution.returned == null) {
            return false
        }
        val solutionClass = this@TestRunner.submission.solution.solution
        val solutionReturnedClass = solution.returned::class.java
        if (!(
                solutionReturnedClass == solutionClass ||
                    (solutionReturnedClass.isArray && solutionReturnedClass.getArrayType() == solutionClass)
                )
        ) {
            return false
        }
        if (settings.runAll!!) {
            return true
        }
        if (submission.returned == null) {
            return false
        }
        val submissionClass = this@TestRunner.submission.submission
        val submissionReturnedClass = submission.returned::class.java
        if (submissionClass == submissionReturnedClass && !solutionReturnedClass.isArray) {
            return true
        }
        if (!(submissionReturnedClass.isArray && submissionReturnedClass.getArrayType() == submissionClass)) {
            return false
        }
        check(submissionReturnedClass.isArray)
        check(solutionReturnedClass.isArray)
        require(solution.returned::class.java.getArrayDimension() == 1) {
            "No support for multi-dimensional receiver array donations"
        }
        if (submission.returned::class.java.getArrayDimension() != 1) {
            return false
        }
        val solutionSize = (solution.returned as Array<*>).size
        val submissionSize = (submission.returned as Array<*>).size
        return solutionSize == submissionSize
    }

    @Suppress("ReturnCount")
    private fun extractReceivers(
        results: ParameterValues<Result<Any, ParameterGroup>>,
        parameters: Parameters,
        settings: Settings,
    ): MutableList<Value<Any?>> {
        if (!SolutionSubmissionResultPair(results.solution, results.submission).returnedReceivers()) {
            return mutableListOf()
        }

        check(results.solution.returned!!::class.java == results.solutionCopy.returned!!::class.java) {
            "${parameters.solutionCopy.map { it }} ${parameters.solution.map { it }} " +
                "${results.solution.returned::class.java} ${results.solutionCopy.returned::class.java}"
        }

        return if (!results.solution.returned::class.java.isArray) {
            listOf(
                Value(
                    results.solution.returned,
                    results.submission.returned,
                    results.solutionCopy.returned,
                    results.submissionCopy.returned,
                    results.unmodifiedCopy.returned,
                    parameters.complexity,
                ),
            )
        } else {
            val solutions = results.solution.returned as Array<*>
            val submissions = results.submission.returned as Array<*>
            val solutionCopies = results.solutionCopy.returned as Array<*>
            val submissionCopies = results.submissionCopy.returned as Array<*>
            val unmodifiedCopies = results.unmodifiedCopy.returned as Array<*>

            if (solutions.size != submissions.size && !settings.runAll!!) {
                return mutableListOf()
            }
            solutions.indices.map { i ->
                Value(
                    solutions[i],
                    submissions.getOrNull(i),
                    solutionCopies[i],
                    submissionCopies.getOrNull(i),
                    unmodifiedCopies.getOrNull(i),
                    parameters.complexity,
                )
            }.toList()
        }.toMutableList()
    }

    @Suppress("ReturnCount")
    private fun linkReceivers(
        results: SolutionSubmissionResultPair,
        settings: Settings,
    ): MutableList<SolutionSubmissionReturnPair> {
        if (!results.returnedReceivers()) {
            return mutableListOf()
        }

        return if (!results.solution.returned!!::class.java.isArray) {
            listOf(SolutionSubmissionReturnPair(results.solution.returned, results.submission.returned))
        } else {
            val solutions = results.solution.returned as Array<*>
            val submissions = results.submission.returned as Array<*>

            if (solutions.size != submissions.size && !settings.runAll!!) {
                return mutableListOf()
            }
            solutions.indices.map { i ->
                SolutionSubmissionReturnPair(solutions[i]!!, submissions.getOrNull(i))
            }.toList()
        }.toMutableList()
    }

    fun willSkip() = settings.runAll!! && !staticOnly && created && receivers!!.submission == null

    @Suppress("ComplexMethod", "LongMethod", "ComplexCondition", "ReturnCount", "NestedBlockDepth")
    fun run(solutionExecutable: Executable, stepCount: Int, type: TestResult.Type? = null) {
        ranLastTest = false
        skippedLastTest = false

        if (willSkip()) {
            skippedLastTest = true
            skippedTests += stepCount
            return
        }

        val creating = !created && type != TestResult.Type.INITIALIZER
        // Only proceed past failures if forced
        check(!failed || (settings.runAll!! || staticOnly))

        val isBoth = solutionExecutable.isAnnotationPresent(Both::class.java)

        val start = System.nanoTime()
        val submissionExecutable = if (isBoth) {
            solutionExecutable
        } else {
            submission.submissionExecutables[solutionExecutable]
                ?: error("couldn't find a submission method that should exist")
        }
        check(solutionExecutable::class.java == submissionExecutable::class.java) {
            "solution and submission executable are not the same type"
        }

        val (parameters, generator) = if (isBoth) {
            Pair(Parameters.fromReceivers(receivers!!), null)
        } else {
            generators[solutionExecutable]?.let {
                Pair(it.generate(this), it)
            } ?: error("couldn't find a parameter generator that should exist: $solutionExecutable")
        }

        val stepType = type ?: if (!created) {
            when (solutionExecutable) {
                is Constructor<*> -> TestResult.Type.CONSTRUCTOR
                is Method -> TestResult.Type.FACTORY_METHOD
            }
        } else {
            check(receivers != null) { "No receivers available" }
            when (solutionExecutable) {
                is Constructor<*> -> {
                    check(!staticOnly) { "Static-only testing should not generate receivers" }
                    TestResult.Type.COPY_CONSTRUCTOR
                }

                is Method -> {
                    when (staticOnly || submission.solution.fauxStatic) {
                        true -> TestResult.Type.STATIC_METHOD
                        false -> TestResult.Type.METHOD
                    }
                }
            }
        }

        val stepReceivers = when {
            solutionExecutable.isStatic() && submissionExecutable.isKotlinCompanion() -> {
                Value(
                    receivers?.solution,
                    submission.submission.kotlin.companionObjectInstance,
                    receivers?.solutionCopy,
                    submission.submission.kotlin.companionObjectInstance,
                    submission.submission.kotlin.companionObjectInstance,
                    receivers?.complexity ?: ZeroComplexity,
                )
            }

            receivers != null -> receivers
            else -> Value(null, null, null, null, null, ZeroComplexity)
        } ?: error("Didn't set receivers")

        @Suppress("SpreadOperator")
        try {
            unwrap {
                submission.solution.filters[solutionExecutable]?.invoke(null, *parameters.solution)
            }
        } catch (_: SkipTest) {
            return
        } catch (_: BoundComplexity) {
            generator?.prev()
            return
        } catch (e: TestingControlException) {
            error("TestingControl exception mismatch: ${e::class.java})")
        }

        val solutionMethodString = solutionExecutable.formatBoundMethodCall(
            parameters.solution.toParameterGroup(),
            submission.solution.solution,
        )

        val systemInParameters = if (solutionExecutable.provideSystemIn()) {
            systemInParameterGenerator!!.generate(this)
        } else {
            null
        }
        val fileSystemParameters = if (solutionExecutable.provideFileSystem()) {
            fileSystemParameterGenerator!!.generate(this)
        } else if (submission.solution.usesFileSystem) {
            classFileSystemParameters
        } else {
            null
        }

        // Have to run these together to keep things in sync
        val solutionResult = solutionExecutable.pairRun(
            stepReceivers.solution,
            parameters.solution,
            parameters.solutionCopy,
            systemInParameters?.solution?.get(0) as SystemIn?,
            fileSystemParameters?.solution?.get(0) as JenisolFileSystem?,
        )

        val solutionCopy = solutionExecutable.pairRun(
            stepReceivers.solutionCopy,
            parameters.solutionCopy,
            systemInParameters = systemInParameters?.solutionCopy?.get(0) as SystemIn?,
            fileSystemParameters = fileSystemParameters?.solutionCopy?.get(0) as JenisolFileSystem?,
        )

        if (solutionResult.threw != null &&
            TestingControlException::class.java.isAssignableFrom(solutionResult.threw::class.java)
        ) {
            if (solutionResult.threw is SkipTest) {
                // Skip this test like it never happened
                return
            } else if (solutionResult.threw is BoundComplexity) {
                // Bound complexity at this point but don't fail
                generator?.prev()
                return
            }
            error("TestingControl exception mismatch: ${solutionResult.threw::class.java})")
        }

        val submissionMethodString = submissionExecutable.formatBoundMethodCall(
            parameters.submission.toParameterGroup(),
            submission.submission,
        )

        val submissionResult = submissionExecutable.pairRun(
            stepReceivers.submission,
            parameters.submission,
            parameters.submissionCopy,
            systemInParameters?.submission?.get(0) as SystemIn?,
            fileSystemParameters?.submission?.get(0) as JenisolFileSystem?,
        )

        val submissionCopy = submissionExecutable.pairRun(
            stepReceivers.submissionCopy,
            parameters.submissionCopy,
            systemInParameters = systemInParameters?.submissionCopy?.get(0) as SystemIn?,
            fileSystemParameters = fileSystemParameters?.submissionCopy?.get(0) as JenisolFileSystem?,
        )

        val linkedReceivers = linkReceivers(
            SolutionSubmissionResultPair(solutionResult, submissionResult),
            settings,
        )
        val existingReceiverMismatch = linkedReceivers.map {
            Pair(it, submission.findReceiver(runners, it.solution))
        }.filter { (_, runner) ->
            runner != null
        }.any { (result, runner) ->
            runner!!.receivers!!.submission !== result.submission
        }

        ranLastTest = true
        val step = TestResult(
            runnerID,
            stepCount, count++,
            solutionExecutable, submissionExecutable, stepType, parameters,
            solutionResult, submissionResult,
            System.nanoTime() - start,
            parameters.complexity.level,
            submission.solution.solution,
            submission.submission,
            stepReceivers.solution,
            stepReceivers.submission,
            existingReceiverMismatch = existingReceiverMismatch,
            solutionMethodString = solutionMethodString,
            submissionMethodString = submissionMethodString,
            currentRandom = random.lastRandom,
            randomCount = random.currentIndex,
            solutionTimeNanos = solutionResult.lengthNanos,
            submissionTimeNanos = submissionResult.lengthNanos,
        )

        val unmodifiedCopy = submissionExecutable.pairRun(
            stepReceivers.unmodifiedCopy,
            parameters.unmodifiedCopy,
            systemInParameters = systemInParameters?.unmodifiedCopy?.get(0) as SystemIn?,
            fileSystemParameters = fileSystemParameters?.unmodifiedCopy?.get(0) as JenisolFileSystem?,
        )

        val createdReceivers = extractReceivers(
            ParameterValues(solutionResult, submissionResult, solutionCopy, submissionCopy, unmodifiedCopy),
            parameters,
            settings,
        )

        if (creating && submissionResult.returned != null && submission.solution.instanceValidator != null) {
            @Suppress("TooGenericExceptionCaught")
            try {
                unwrap { submission.solution.instanceValidator.invoke(null, submissionResult.returned) }
            } catch (@Suppress("DEPRECATION") e: ThreadDeath) {
                throw e
            } catch (e: Throwable) {
                step.differs.add(TestResult.Differs.INSTANCE_VALIDATION_THREW)
                step.verifierThrew = e
            }
        }

        if (step.succeeded) {
            submission.verify(solutionExecutable, step)
        }
        testResults.add(step)

        if (step.succeeded || settings.runAll!!) {
            generator?.next()
        } else {
            generator?.prev()
        }

        lastComplexity = parameters.complexity

        if ((step.succeeded || settings.runAll!!) && creating && createdReceivers.isNotEmpty()) {
            receivers = createdReceivers.removeAt(0)
        }
        if ((step.succeeded || settings.runAll!!)) {
            returnedReceivers = createdReceivers
        }
    }

    fun next(stepCount: Int): TestRunner {
        testingEventListener(StartTest(stepCount))
        if (!created) {
            run(receiverGenerators.first(), stepCount)
            created = true
        } else if (!initialized && submission.solution.initializer != null) {
            run(submission.solution.initializer, stepCount, TestResult.Type.INITIALIZER)
            initialized = true
        } else {
            initialized = true
            run(methodPicker.next(), stepCount)
        }
        tested = true
        testingEventListener(EndTest(stepCount))
        return this
    }
}

sealed class TestingControlException : RuntimeException()
class SkipTest : TestingControlException()
class BoundComplexity : TestingControlException()

private val hashCodeRegex = Regex("@[0-9a-fA-F]+$")
private val lambdaRegex = Regex("""\$\${"$"}Lambda[^\s)]+""")

@Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
fun TestResults.solutionTestingSequence(): List<String> {
    val orderedReceivers = mutableMapOf<String, MutableList<String>>()
    forEach { result ->
        val namesToCheck = listOf(result.solutionReceiver.toString(), result.solution.returned.toString())
        for (checkingName in namesToCheck) {
            if (checkingName == "null") {
                continue
            }
            val regexMatch = hashCodeRegex.find(checkingName)
            if (regexMatch !== null) {
                val solutionClass = result.solutionClass.simpleName
                if (!orderedReceivers.contains(solutionClass)) {
                    orderedReceivers[solutionClass] = mutableListOf()
                }
                if (!orderedReceivers[solutionClass]!!.contains(checkingName)) {
                    orderedReceivers[solutionClass]!! += checkingName
                }
            }
        }
    }

    val outputRemaps = mutableMapOf<String, String>()
    for ((klass, receiverList) in orderedReceivers) {
        for ((i, receiver) in receiverList.withIndex()) {
            outputRemaps[receiver] = "$klass#$i"
        }
    }

    return mapIndexed { i, result ->
        val receiverName = result.solutionReceiver.toString()
        val resultName = result.solution.returned.toString()

        val callString = if (receiverName != "null") {
            "$receiverName.${result.solutionMethodString}"
        } else {
            "${result.solutionClass.simpleName}.${result.solutionMethodString}"
        }
        val resultString = if (result.solution.threw != null) {
            "threw ${result.solution.threw.javaClass.simpleName}"
        } else {
            if (resultName != "null") {
                "-> $resultName"
            } else if (result.solution.stdout.isNotEmpty()) {
                "printed \"${result.solution.stdout.trimEnd()}\""
            } else {
                ""
            }
        }

        @Suppress("MagicNumber")
        var fullString = "${i.toString().padStart(3, ' ')}: $callString $resultString"
        for ((to, from) in outputRemaps) {
            fullString = fullString.replace(to, from)
        }
        fullString = fullString.replace(lambdaRegex, "\\$\\${"$"}Lambda")
        fullString = fullString.replace(result.solutionClass.name, result.solutionClass.simpleName)
        fullString
    }
}

fun TestResults.formatSolutionTestingSequence() = solutionTestingSequence().joinToString("\n")
