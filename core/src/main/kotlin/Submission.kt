package edu.illinois.cs.cs125.jenisol.core

import edu.illinois.cs.cs125.jenisol.core.generators.ObjectGenerator
import edu.illinois.cs.cs125.jenisol.core.generators.ReceiverGenerator
import edu.illinois.cs.cs125.jenisol.core.generators.TypeGeneratorGenerator
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Type
import kotlin.random.Random

class Submission(val solution: Solution, val submission: Class<*>) {
    init {
        solution.bothExecutables.forEach {
            if (!it.parameterTypes[0].isAssignableFrom(submission)) {
                throw SubmissionDesignInheritanceError(
                    submission,
                    it.parameterTypes[0]
                )
            }
        }
    }

    val submissionExecutables = solution.allExecutables
        .map { solutionExecutable ->
            when (solutionExecutable) {
                is Constructor<*> -> submission.findConstructor(solutionExecutable, solution.solution)
                is Method -> submission.findMethod(solutionExecutable, solution.solution)
                else -> error("Encountered unexpected executable type: $solutionExecutable")
            }?.let { executable ->
                solutionExecutable to executable
            } ?: throw SubmissionDesignMissingMethodError(
                submission,
                solutionExecutable
            )
        }.toMap().toMutableMap().also {
            if (solution.initializer != null) {
                it[solution.initializer] = solution.initializer
            }
        }.toMap()

    init {
        if (submission != solution.solution) {
            (submission.declaredMethods.toSet() + submission.declaredConstructors.toSet()).filter {
                it.isPublic()
            }.forEach {
                if (it !in submissionExecutables.values) {
                    throw SubmissionDesignExtraMethodError(
                        submission,
                        it
                    )
                }
            }
        }
    }

    private fun MutableList<TestRunner>.readyCount() = filter { it.ready }.count()

    private val comparators = Comparators(
        mutableMapOf(solution.solution to solution.receiverCompare, submission to solution.receiverCompare)
    )

    fun compare(solution: Any?, submission: Any?) = when (solution) {
        null -> submission == null
        else -> solution.deepEquals(submission, comparators)
    }

    fun verify(result: TestResult<*, *>) {
        solution.verifier?.also { customVerifier ->
            @Suppress("TooGenericExceptionCaught")
            try {
                unwrap { customVerifier.invoke(null, result) }
            } catch (e: Throwable) {
                result.differs.add(TestResult.Differs.VERIFIER_THREW)
                result.verifierThrew = e
            }
        } ?: defaultVerify(result)
    }

    private fun defaultVerify(result: TestResult<*, *>) {
        val solution = result.solution
        val submission = result.submission

        if (solution.stdout.isNotBlank() && solution.stdout != submission.stdout) {
            result.differs.add(TestResult.Differs.STDOUT)
        }
        if (solution.stderr.isNotBlank() && solution.stderr != submission.stderr) {
            result.differs.add(TestResult.Differs.STDERR)
        }

        if (result.type == TestResult.Type.METHOD && !compare(solution.returned, submission.returned)) {
            result.differs.add(TestResult.Differs.RETURN)
        }
        if (!compare(solution.threw, submission.threw)) {
            result.differs.add(TestResult.Differs.THREW)
        }
        if (!compare(solution.parameters, submission.parameters)) {
            result.differs.add(TestResult.Differs.PARAMETERS)
        }
    }

    @Suppress("LongMethod", "ComplexMethod")
    fun test(
        passedSettings: Solution.Settings = Solution.Settings(),
        captureOutput: CaptureOutput = ::defaultCaptureOutput
    ): TestResults {
        val settings = solution.setCounts(Solution.Settings.DEFAULTS merge passedSettings)

        val random = if (passedSettings.seed == -1) {
            Random
        } else {
            Random(passedSettings.seed.toLong())
        }

        val runners: MutableList<TestRunner> = mutableListOf()
        var stepCount = 0
        var totalCount = 0

        val receiverGenerators = sequence {
            while (true) {
                yieldAll(solution.receiverGenerators.toList().shuffled(random))
            }
        }

        val (receiverGenerator, initialGenerators) = if (!solution.skipReceiver) {
            check(settings.receiverCount > 1) { "Incorrect receiver count" }

            val generators = solution.generatorFactory.get(
                random, settings, null, solution.receiversAndInitializers
            )
            while (runners.readyCount() < settings.receiverCount) {
                TestRunner(
                    runners.size,
                    this,
                    generators,
                    receiverGenerators,
                    captureOutput
                ).also { runner ->
                    runner.next(stepCount++)
                    runners.add(runner)
                }
            }
            Pair(
                ReceiverGenerator(
                    random,
                    runners.filter { it.ready }.toMutableList()
                ),
                generators
            )
        } else {
            Pair(null, null)
        }

        @Suppress("UNCHECKED_CAST")
        val generatorOverrides = if (receiverGenerator != null) {
            mutableMapOf(
                (solution.solution as Type) to ({ _: Random -> receiverGenerator } as TypeGeneratorGenerator),
                (Any::class.java as Type) to { r: Random ->
                    ObjectGenerator(
                        r,
                        receiverGenerator
                    )
                }
            )
        } else {
            mapOf<Type, TypeGeneratorGenerator>()
        }

        val generators = solution.generatorFactory.get(random, settings, generatorOverrides, from = initialGenerators)
        runners.filter { it.ready }.forEach {
            it.generators = generators
        }

        val totalTests = settings.receiverCount * settings.methodCount
        while (true) {
            val usedRunner = if (runners.readyCount() < settings.receiverCount) {
                TestRunner(
                    runners.size,
                    this,
                    generators,
                    receiverGenerators,
                    captureOutput
                ).also { runner ->
                    runner.next(stepCount++)
                    runners.add(runner)
                    receiverGenerator?.runners?.add(runner)
                }
            } else {
                runners.filter { it.ready }.shuffled(random).first().also {
                    it.next(stepCount++)
                }
            }

            if (usedRunner.returnedReceivers != null) {
                runners.add(
                    TestRunner(
                        runners.size,
                        this,
                        generators,
                        receiverGenerators,
                        captureOutput,
                        usedRunner.returnedReceivers
                    )
                )
                usedRunner.returnedReceivers = null
            }

            totalCount++
            if (totalCount == totalTests) {
                break
            }
        }
        @Suppress("UNCHECKED_CAST")
        return TestResults(
            runners.map { it.testResults as List<TestResult<Any, ParameterGroup>> }
                .flatten()
        )
    }
}

sealed class SubmissionDesignError(message: String) : Exception(message)
class SubmissionDesignMissingMethodError(klass: Class<*>, executable: Executable) : SubmissionDesignError(
    "Submission class ${klass.name} didn't provide ${if (executable.isStatic()) {
        "static "
    } else {
        ""
    }}${if (executable is Method) {
        "method"
    } else {
        "constructor"
    }} ${executable.fullName()}"
)

class SubmissionDesignExtraMethodError(klass: Class<*>, executable: Executable) : SubmissionDesignError(
    "Submission class ${klass.name} provided extra ${if (executable is Method) {
        "method"
    } else {
        "constructor"
    }} ${executable.fullName()}"
)

class SubmissionDesignInheritanceError(klass: Class<*>, parent: Class<*>) : SubmissionDesignError(
    "Submission class ${klass.name} didn't inherit from ${parent.name}"
)

fun unwrap(run: () -> Any?): Any? = try {
    run()
} catch (e: InvocationTargetException) {
    throw e.cause ?: error("InvocationTargetException should have a cause")
}
