package edu.illinois.cs.cs125.jenisol.core

import io.github.classgraph.ClassGraph
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotContainAll
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Class<*>.testName() = packageName.removePrefix("examples.")

val testingStepsShouldNotContain = setOf(
    TestResult.Type.CONSTRUCTOR,
    TestResult.Type.INITIALIZER,
)

data class TestingClasses(
    val testName: String,
    val primarySolution: Class<*>,
    val otherSolutions: List<Class<*>>,
    val incorrect: List<Class<*>>,
    val badReceivers: List<Class<*>>,
    val badDesign: List<Class<*>>,
)

suspend fun Submission.testWithTimeout(settings: Settings, followTrace: List<Int>? = null): TestResults {
    val testingEvents = mutableListOf<TestingEvent>()
    val runnable = object : Runnable {
        var results: TestResults? = null
        var error: Exception? = null
        override fun run() {
            try {
                results = this@testWithTimeout.test(
                    settings.copy(recordTrace = true),
                    followTrace = followTrace,
                    testingEventListener = { event ->
                        testingEvents += event
                    },
                )
            } catch (e: Exception) {
                error = e
            }
        }
    }
    withContext(Dispatchers.Default) {
        Thread(runnable).apply {
            start()
            join(2048)
            interrupt()
            join(1024)
        }
    }
    if (runnable.error != null) {
        throw runnable.error!!
    }

    runnable.results!!.stepCount shouldBe testingEvents.filterIsInstance<StartTest>().size
    runnable.results!!.stepCount shouldBe testingEvents.filterIsInstance<EndTest>().size

    return runnable.results!!
}

fun Class<*>.testingClasses(): TestingClasses {
    val testName = packageName.removePrefix("examples.")
    val scanName = if (isKotlin() && packageName.endsWith(".correct")) {
        packageName.removeSuffix(".correct")
    } else {
        packageName
    }
    val packageClasses = ClassGraph().acceptPackages(scanName).scan().allClasses.map { it.loadClass() }
    val testingClasses = packageClasses.filter {
        !it.isInterface && (it.declaredMethods.isNotEmpty() || it.declaredFields.isNotEmpty())
    }

    val primarySolution = testingClasses
        .find { it.simpleName == "Correct" || it.simpleName == "CorrectKt" }
        ?: error("Couldn't find primary solution in package $this")
    val otherSolutions = testingClasses
        .filter { it != primarySolution && it.simpleName.startsWith("Correct") }

    val incorrect = testingClasses
        .filter { it.simpleName.startsWith("Incorrect") }
    val badReceivers = testingClasses
        .filter { it.simpleName.startsWith("BadReceivers") }
    val badDesign = testingClasses
        .filter { it.simpleName.startsWith("Design") }

    return TestingClasses(testName, primarySolution, otherSolutions, incorrect, badReceivers, badDesign)
}

@Suppress("LongMethod", "ComplexMethod", "LongParameterList")
suspend fun Solution.fullTest(
    klass: Class<*>,
    seed: Int,
    isCorrect: Boolean,
    solutionResults: TestResults? = null,
    overrideMaxCount: Int = 0,
    ignoreSolutionSequenceMismatch: Boolean = false,
): Pair<TestResults, TestResults> {
    val baseSettings = Settings(
        shrink = true,
        seed = seed,
        testing = true,
        minTestCount = 64.coerceAtMost(maxCount).coerceAtLeast(overrideMaxCount),
        maxTestCount = 1024.coerceAtMost(maxCount).coerceAtLeast(overrideMaxCount),
    )

    @Suppress("RethrowCaughtException")
    fun TestResults.checkResults() = try {
        filter { testResult -> !receiverAsParameter || testResult.type != TestResult.Type.CONSTRUCTOR }
            .map { testResult -> testResult.runnerID }
            .zipWithNext()
            .all { (first, second) -> first <= second } shouldBe true

        forEach { testResult ->
            testResult.timeNanos shouldBeGreaterThan 0
            testResult.solutionTimeNanos shouldBeGreaterThan 0
            testResult.submissionTimeNanos shouldBeGreaterThan 0
            testResult.solutionTimeNanos shouldBeLessThan testResult.timeNanos
            testResult.submissionTimeNanos shouldBeLessThan testResult.timeNanos
        }

        if (isCorrect) {
            succeeded shouldBe true
        } else {
            succeeded shouldBe false
            threw shouldBe null
            timeout shouldBe false
            failed shouldBe true
            results.filter { it.failed }
                .map { it.type }
                .distinct() shouldNotContainAll testingStepsShouldNotContain
        }
        this
    } catch (e: Throwable) {
        println(explain())
        println("-".repeat(80))
        printTrace()
        println("-".repeat(80))
        throw e
    }

    val submissionKlass = submission(klass)
    val original = submissionKlass.testWithTimeout(baseSettings).checkResults()
    val originalTestingSequence = original.solutionTestingSequence()
    val formattedTestingSequenc = originalTestingSequence.joinToString("\n")

    run {
        val second = submissionKlass.testWithTimeout(baseSettings, followTrace = original.randomTrace).checkResults()
        second.formatSolutionTestingSequence() shouldBe formattedTestingSequenc
        if (!isCorrect) {
            original.size shouldBeLessThanOrEqual baseSettings.maxTestCount
        }
        original.size shouldBe second.size
        original.forEachIndexed { index, firstResult ->
            val secondResult = second[index]
            submissionKlass.compare(firstResult.parameters, secondResult.parameters) shouldBe true
            firstResult.runnerID shouldBe secondResult.runnerID
        }
    }

    var failingTestCount = -1
    run {
        val noShrinkSettings = baseSettings.copy(shrink = false)
        val first = submissionKlass.testWithTimeout(noShrinkSettings).checkResults()
        val second = submissionKlass.testWithTimeout(noShrinkSettings).checkResults()

        second.formatSolutionTestingSequence() shouldBe first.formatSolutionTestingSequence()

        if (!isCorrect) {
            first.indexOfFirst { it.failed } shouldBe first.size - 1
            failingTestCount = first.size
        }

        first.size shouldBe second.size
        first.forEachIndexed { index, firstResult ->
            val secondResult = second[index]
            submissionKlass.compare(firstResult.parameters, secondResult.parameters) shouldBe true
            firstResult.runnerID shouldBe secondResult.runnerID
        }
    }
    if (!isCorrect) {
        check(failingTestCount != -1)
        val reducedSettings = baseSettings.copy(testCount = failingTestCount, minTestCount = -1, maxTestCount = -1)
        submissionKlass.testWithTimeout(reducedSettings).checkResults().also {
            it.solutionTestingSequence().syncedUntilEnd(originalTestingSequence)
        }
    }
    val testAllCounts = solutionResults?.size ?: 256.coerceAtLeast(original.size).coerceAtMost(maxCount)
    val testAllSettings =
        baseSettings.copy(
            shrink = false,
            runAll = !isCorrect,
            testCount = testAllCounts,
            minTestCount = -1,
            maxTestCount = -1,
        )

    val first = submissionKlass.testWithTimeout(testAllSettings, followTrace = solutionResults?.randomTrace)
        .checkResults()
    val second = submissionKlass.testWithTimeout(testAllSettings, followTrace = solutionResults?.randomTrace)
        .checkResults()

    first.formatSolutionTestingSequence() shouldBe second.formatSolutionTestingSequence()

    solutionResults?.randomTrace?.also { solutionRandomTrace ->
        solutionRandomTrace.size shouldBe first.randomTrace!!.size
        solutionRandomTrace.size shouldBe second.randomTrace!!.size
    }

    first.size + first.skippedSteps.size shouldBe testAllCounts
    first.size shouldBe second.size
    first.forEachIndexed { index, firstResult ->
        val secondResult = second[index]
        submissionKlass.compare(firstResult.parameters, secondResult.parameters) shouldBe true
        firstResult.runnerID shouldBe secondResult.runnerID
    }
    if (solutionResults != null) {
        solutionResults.size shouldBe first.size + first.skippedSteps.size
        solutionResults.forEach { solutionResult ->
            val firstResult = first.find { it.stepCount == solutionResult.stepCount }
            val secondResult = second.find { it.stepCount == solutionResult.stepCount }
            if (firstResult == null) {
                secondResult shouldBe null
                first.skippedSteps.find { it == solutionResult.stepCount } shouldNotBe null
                second.skippedSteps.find { it == solutionResult.stepCount } shouldNotBe null
                return@forEach
            }
            check(secondResult != null)
            submissionKlass.compare(firstResult.parameters, secondResult.parameters) shouldBe true
            firstResult.runnerID shouldBe secondResult.runnerID
            if (isCorrect) {
                solutionResults.formatSolutionTestingSequence() shouldBe first.formatSolutionTestingSequence()
                submissionKlass.compare(solutionResult.parameters, firstResult.parameters) shouldBe true
            } else if (!ignoreSolutionSequenceMismatch) {
                solutionResults.solutionTestingSequence().syncedUntilEnd(first.solutionTestingSequence())
            }
            solutionResult.runnerID shouldBe firstResult.runnerID
        }
    }
    return Pair(original, first)
}

@Suppress("NestedBlockDepth", "ComplexMethod", "LongMethod")
suspend fun Class<*>.test(overrideMaxCount: Int = 0, ignoreSolutionSequenceMismatch: Boolean = false) =
    this.testingClasses().apply {
        solution(primarySolution).apply {
            val (_, solutionResults) = submission(primarySolution).let {
                if (!primarySolution.isDesignOnly()) {
                    fullTest(
                        primarySolution,
                        seed = 124,
                        isCorrect = true,
                        overrideMaxCount = overrideMaxCount,
                        ignoreSolutionSequenceMismatch = ignoreSolutionSequenceMismatch,
                    ).also { (results) ->
                        check(results.succeeded) { "Solution did not pass testing: ${results.explain()}" }
                    }
                } else {
                    Pair(null, null)
                }
            }
            otherSolutions.forEach { correct ->
                submission(correct).also {
                    if (!primarySolution.isDesignOnly()) {
                        fullTest(
                            correct,
                            seed = 124,
                            isCorrect = true,
                            solutionResults = solutionResults,
                            overrideMaxCount = overrideMaxCount,
                            ignoreSolutionSequenceMismatch = ignoreSolutionSequenceMismatch,
                        ).first.also { results ->
                            check(!results.timeout)
                            check(results.succeeded) {
                                "Class marked as correct did not pass testing: ${results.explain(stacktrace = true)}"
                            }
                        }
                    }
                }
            }
            (incorrect + badDesign + badReceivers)
                .apply {
                    check(isNotEmpty()) { "No incorrect examples.java.examples for $testName" }
                }.forEach { incorrect ->
                    if (incorrect in badDesign) {
                        @Suppress("RethrowCaughtException")
                        shouldThrow<SubmissionDesignError> {
                            try {
                                submission(incorrect)
                            } catch (e: Exception) {
                                throw e
                            }
                        }
                    } else {
                        check(!primarySolution.isDesignOnly()) {
                            "Can't test Incorrect* examples when solution is design only"
                        }
                        fullTest(
                            incorrect,
                            seed = 124,
                            isCorrect = false,
                            solutionResults = solutionResults,
                            overrideMaxCount = overrideMaxCount,
                            ignoreSolutionSequenceMismatch = ignoreSolutionSequenceMismatch,
                        ).first.also { results ->
                            results.threw shouldBe null
                            results.timeout shouldBe false
                            results.failed shouldBe true
                            results.filter { it.failed }
                                .map { it.type }
                                .distinct() shouldNotContainAll testingStepsShouldNotContain
                        }
                    }
                }
        }
    }

fun List<String>.syncedUntilEnd(other: List<String>) {
    val smaller = listOf(this.size, other.size).min()
    this.take(smaller).joinToString("\n") shouldBe other.take(smaller).joinToString("\n")
}
