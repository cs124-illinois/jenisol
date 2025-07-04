@file:Suppress("InvalidPackageDeclaration")

package edu.illinois.cs.cs125.jenisol.core

import com.rits.cloning.Cloner
import edu.illinois.cs.cs125.jenisol.core.generators.Complexity
import edu.illinois.cs.cs125.jenisol.core.generators.Generators
import edu.illinois.cs.cs125.jenisol.core.generators.ObjectGenerator
import edu.illinois.cs.cs125.jenisol.core.generators.ReceiverGenerator
import edu.illinois.cs.cs125.jenisol.core.generators.TypeGeneratorGenerator
import edu.illinois.cs.cs125.jenisol.core.generators.Value
import edu.illinois.cs.cs125.jenisol.core.generators.getArrayDimension
import java.lang.RuntimeException
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.util.TreeMap
import kotlin.random.Random
import kotlin.reflect.full.memberFunctions

@Suppress("LargeClass")
class Submission(val solution: Solution, val submission: Class<*>) {
    private val isKotlin = submission.getAnnotation(Metadata::class.java) != null

    @Suppress("LongMethod", "CyclomaticComplexMethod", "ThrowsCount")
    private fun checkClassDesign(solution: Class<*>, submission: Class<*>, innerClass: Boolean = false) {
        if (!solution.visibilityMatches(submission)) {
            throw SubmissionDesignClassError(
                submission,
                "is not ${
                    solution.getVisibilityModifier() ?: if (isKotlin) {
                        "internal"
                    } else {
                        "package private"
                    }
                }",
                innerClass,
            )
        }
        if (!isKotlin && (solution.isFinal() != submission.isFinal())) {
            throw SubmissionDesignClassError(
                submission,
                if (solution.isFinal()) {
                    "is not marked as final but should be"
                } else {
                    "is marked as final but should not be"
                },
                innerClass,
            )
        }
        if (solution.isAbstract() != submission.isAbstract()) {
            throw SubmissionDesignClassError(
                submission,
                if (solution.isAbstract()) {
                    "is not marked as abstract but should be"
                } else {
                    "is marked as abstract but should not be"
                },
                innerClass,
            )
        }
        if (solution.isStatic() != submission.isStatic()) {
            throw SubmissionDesignClassError(
                submission,
                if (solution.isStatic()) {
                    "is not marked as static but should be"
                } else {
                    "is marked as static but should not be"
                },
                innerClass,
            )
        }
        if (solution.superclass != null && solution.superclass != submission.superclass) {
            throw SubmissionDesignClassError(
                submission,
                "does not extend ${solution.superclass.name}",
                innerClass,
            )
        }
        val solutionInterfaces = solution.interfaces.toSet()
        val submissionInterfaces = submission.interfaces.toSet()
        val missingInterfaces = solutionInterfaces.minus(submissionInterfaces)
        if (missingInterfaces.isNotEmpty()) {
            throw SubmissionDesignClassError(
                submission,
                "does not implement ${missingInterfaces.joinToString(separator = ", ") { it.name }}",
                innerClass,
            )
        }
        val extraInterfaces = submissionInterfaces.minus(solutionInterfaces)
        if (extraInterfaces.isNotEmpty()) {
            throw SubmissionDesignClassError(
                submission,
                "implements extra interfaces ${extraInterfaces.joinToString(separator = ", ") { it.name }}",
                innerClass,
            )
        }
        solution.typeParameters.forEachIndexed { i, type ->
            @Suppress("TooGenericExceptionCaught", "SwallowedException")
            try {
                if (!submission.typeParameters[i].bounds.contentEquals(type.bounds)) {
                    throw SubmissionTypeParameterError(submission, innerClass)
                }
            } catch (_: Exception) {
                throw SubmissionTypeParameterError(submission, innerClass)
            }
        }
        if (submission.typeParameters.size > solution.typeParameters.size) {
            throw SubmissionTypeParameterError(submission, innerClass)
        }
    }

    init {
        checkClassDesign(solution.solution, submission)
        solution.solution.declaredClasses.filter { klass -> !klass.isPrivate() }.forEach { solutionInnerClass ->
            val submissionInnerClass =
                submission.declaredClasses.find { klass -> klass.simpleName == solutionInnerClass.simpleName }
            if (submissionInnerClass == null) {
                throw SubmissionDesignMissingInnerClassError(submission, solutionInnerClass)
            }
            checkClassDesign(solutionInnerClass, submissionInnerClass)
        }
        submission.declaredClasses.filter { klass -> !klass.isPrivate() }.forEach { submissionInnerClass ->
            val solutionInnerClass = solution.solution.declaredClasses
                .find { klass -> klass.simpleName == submissionInnerClass.simpleName }
            if (solutionInnerClass == null) {
                if (!(submission.isKotlin() && submissionInnerClass.kotlin.isCompanion)) {
                    throw SubmissionDesignExtraInnerClassError(submission, submissionInnerClass)
                }
            }
        }
    }

    init {
        solution.bothExecutables.forEach {
            if (!it.parameterTypes[0].isAssignableFrom(submission)) {
                throw SubmissionDesignInheritanceError(
                    submission,
                    it.parameterTypes[0],
                )
            }
        }
    }

    private val submissionFields =
        solution.allFields.filter { it.name != "${"$"}assertionsDisabled" }.map { solutionField ->
            submission.findField(solutionField) ?: throw SubmissionDesignMissingFieldError(
                submission,
                solutionField,
            )
        }.toSet()

    private val hasInnerClasses = submission.declaredClasses.any { klass ->
        !(submission.isKotlin() && klass.kotlin.isCompanion)
    }

    val submissionExecutables = solution.allExecutables
        .filter {
            !isKotlin || (!solution.skipReceiver || it !in solution.receiverGenerators)
        }.associate { solutionExecutable ->
            when (solutionExecutable) {
                is Constructor<*> -> submission.findConstructor(solutionExecutable, solution.solution)
                is Method -> submission.findMethod(solutionExecutable, solution.solution)
            }?.let { executable ->
                executable.isAccessible = true
                solutionExecutable to executable
            } ?: run {
                @Suppress("ComplexCondition")
                if (isKotlin &&
                    solutionExecutable is Method &&
                    (solutionExecutable.looksLikeGetter() || solutionExecutable.looksLikeSetter())
                ) {
                    val field = solutionExecutable.getterOrSetterToPropertyName()
                    if (solutionExecutable.looksLikeGetter()) {
                        throw SubmissionDesignKotlinNotAccessibleError(submission, field)
                    } else {
                        throw SubmissionDesignKotlinNotModifiableError(submission, field)
                    }
                } else {
                    throw SubmissionDesignMissingMethodError(submission, solutionExecutable, hasInnerClasses)
                }
            }
        }.toMutableMap().also {
            if (solution.initializer != null) {
                it[solution.initializer] = solution.initializer
            }
        }.toMap().also { executableMap ->

            val kotlinReflectionSupported = isKotlin &&
                try {
                    submission.kotlin.memberFunctions
                    true
                } catch (_: UnsupportedOperationException) {
                    false
                }

            if (kotlinReflectionSupported) {
                @Suppress("MagicNumber")
                executableMap.keys
                    .filterIsInstance<Method>()
                    .filter { it.looksLikeGetter() || it.looksLikeSetter() }
                    .firstOrNull { method ->
                        submission.kotlin.memberFunctions.map { it.name }.contains(method.name)
                    }
                    ?.also { method ->
                        val field = method.getterOrSetterToPropertyName()
                        if (!method.hasKotlinMirrorOK()) {
                            throw KotlinBadSetterOrGetter(field, method.name)
                        }
                    }
            }
        }

    init {
        if (submission != solution.solution) {
            (submission.declaredMethods.toSet() + submission.declaredConstructors.toSet()).filter {
                !it.isPrivate() && !it.isSynthetic && !(it is Method && it.isBridge)
            }.forEach { executable ->
                if (executable !in submissionExecutables.values) {
                    if (isKotlin) {
                        // HACK to work around Kotlin's lack of setter-only syntax
                        if (executable is Method && executable.looksLikeGetter()) {
                            val setterName = executable.name.replace("get", "set")
                            if (submissionExecutables.values.map { it.name }.contains(setterName)) {
                                return@forEach
                            }
                        }
                        if (solution.skipReceiver && executable is Constructor<*>) {
                            return@forEach
                        }
                        if (executable.isKotlinCompanionAccessor()) {
                            return@forEach
                        }
                        if (executable is Constructor<*> &&
                            executable.parameterTypes.lastOrNull()?.name ==
                            "kotlin.jvm.internal.DefaultConstructorMarker"
                        ) {
                            return@forEach
                        }
                        @Suppress("EmptyCatchBlock")
                        try {
                            if (submission.kotlin.isData && executable.isDataClassGenerated()) {
                                return@forEach
                            }
                        } catch (_: UnsupportedOperationException) {
                        }
                        if (executable.name == "compareTo") {
                            return@forEach
                        }
                    }
                    @Suppress("ComplexCondition", "MagicNumber")
                    if (isKotlin &&
                        executable is Method &&
                        (executable.looksLikeGetter() || executable.looksLikeSetter())
                    ) {
                        if (executable.looksLikeSetter()) {
                            throw SubmissionDesignKotlinIsModifiableError(
                                submission,
                                executable.getterOrSetterToPropertyName(),
                            )
                        } else {
                            throw SubmissionDesignKotlinIsAccessibleError(
                                submission,
                                executable.getterOrSetterToPropertyName(),
                            )
                        }
                    }
                    throw SubmissionDesignExtraMethodError(
                        submission,
                        executable,
                    )
                }
            }
            submission.declaredFields.toSet().filter {
                it.name != "${"$"}assertionsDisabled" &&
                    !(isKotlin && it.name == "Companion")
            }.forEach {
                if (!it.isPrivate() && it !in submissionFields) {
                    throw SubmissionDesignExtraFieldError(submission, it)
                }
                if (it.isStatic()) {
                    if (!solution.skipReceiver) {
                        throw SubmissionStaticFieldError(submission, it)
                    } else if (!it.isPrivate()) {
                        throw SubmissionStaticPublicFieldError(submission, it)
                    }
                }
            }
        }
    }

    private val comparators = Comparators(
        mutableMapOf(solution.solution to solution.receiverCompare, submission to solution.receiverCompare),
    )

    fun compare(solution: Any?, submission: Any?, solutionClass: Class<*>? = null, submissionClass: Class<*>? = null) =
        when (solution) {
            null -> submission == null
            else -> solution.deepEquals(submission, comparators, solutionClass, submissionClass)
        }

    fun verify(executable: Executable, result: TestResult<*, *>) {
        solution.verifiers[executable]?.also { customVerifier ->
            @Suppress("TooGenericExceptionCaught")
            try {
                unwrap { customVerifier.invoke(null, result) }
            } catch (@Suppress("DEPRECATION") e: ThreadDeath) {
                throw e
            } catch (e: Throwable) {
                result.differs.add(TestResult.Differs.VERIFIER_THREW)
                result.verifierThrew = e
            }
        } ?: run {
            defaultVerify(result)
        }
    }

    @Suppress("ComplexMethod", "LongMethod")
    private fun defaultVerify(result: TestResult<*, *>) {
        val solution = result.solution
        val submission = result.submission

        val strictOutput = result.solutionExecutable.annotations.find { it is Configure }?.let {
            (it as Configure).strictOutput
        } ?: false

        if (!compare(solution.threw, submission.threw, result.solutionClass, result.submissionClass)) {
            result.differs.add(TestResult.Differs.THREW)
        }

        if ((strictOutput || solution.stdout.isNotBlank()) && solution.stdout != submission.stdout) {
            result.differs.add(TestResult.Differs.STDOUT)
            if (solution.stdout == submission.stdout + "\n") {
                result.message = if (result.submissionIsKotlin) {
                    "Output is missing a newline, maybe use println instead of print?"
                } else {
                    "Output is missing a newline, maybe use System.out.println instead of System.out.print?"
                }
            }
            if (solution.stdout + "\n" == submission.stdout) {
                result.message = if (result.submissionIsKotlin) {
                    "Output has an extra newline, maybe use print instead of println?"
                } else {
                    "Output has an extra newline, maybe use System.out.print instead of System.out.println?"
                }
            }
        }

        if ((strictOutput || solution.stderr.isNotBlank()) && solution.stderr != submission.stderr) {
            result.differs.add(TestResult.Differs.STDERR)
            if (solution.stderr == submission.stderr + "\n") {
                result.message =
                    "Error output is missing a newline, maybe use System.err.println instead of System.err.print?"
            }
            if (solution.stderr + "\n" == submission.stderr) {
                result.message =
                    "Error output has an extra newline, maybe use System.err.print instead of System.err.println?"
            }
        }

        @Suppress("ComplexCondition")
        if ((strictOutput || solution.stdout.isNotBlank() || solution.stderr.isNotBlank()) &&
            solution.interleavedOutput != submission.interleavedOutput
        ) {
            result.differs.add(TestResult.Differs.INTERLEAVED_OUTPUT)
        }

        if (result.existingReceiverMismatch) {
            result.differs.add(TestResult.Differs.RETURN)
        }
        if (result.type == TestResult.Type.METHOD || result.type == TestResult.Type.STATIC_METHOD) {
            val customCompare = if (solution.returned != null) {
                this.solution.customCompares.entries.find { (type, _) ->
                    type.isAssignableFrom(solution.returned::class.java)
                }
            } else {
                null
            }?.value
            if (customCompare != null && submission.returned != null) {
                @Suppress("TooGenericExceptionCaught")
                try {
                    customCompare.invoke(null, solution.returned, submission.returned)
                } catch (e: Throwable) {
                    result.differs.add(TestResult.Differs.RETURN)
                    result.message = e.message
                }
            } else {
                @Suppress("TooGenericExceptionCaught")
                try {
                    compare(
                        solution.returned,
                        submission.returned,
                        result.solutionClass,
                        result.submissionClass,
                    ).also {
                        if (!it) {
                            result.differs.add(TestResult.Differs.RETURN)
                        }
                    }
                } catch (e: Throwable) {
                    result.differs.add(TestResult.Differs.RETURN)
                    result.message = e.message
                }
            }
        }
        if (result.type == TestResult.Type.FACTORY_METHOD &&
            solution.returned != null &&
            solution.returned::class.java.isArray
        ) {
            @Suppress("ComplexCondition")
            if (submission.returned == null ||
                !submission.returned::class.java.isArray ||
                solution.returned::class.java.getArrayDimension()
                != submission.returned::class.java.getArrayDimension() ||
                (solution.returned as Array<*>).size != (submission.returned as Array<*>).size
            ) {
                result.differs.add(TestResult.Differs.RETURN)
            }
        }
        if (!compare(solution.parameters, submission.parameters, result.solutionClass, result.submissionClass)) {
            result.differs.add(TestResult.Differs.PARAMETERS)
        }
    }

    inner class ExecutablePicker(private val random: Random, private val methods: Set<Executable>) {
        private val counts: MutableMap<Executable, Int> = methods.filter {
            it in solution.limits.keys
        }.associateWith {
            0
        }.toMutableMap()
        private val finished = mutableSetOf<Executable>()

        private lateinit var executableChooser: TreeMap<Double, Executable>
        private var total: Double = 0.0
        private fun setWeights() {
            var setTotal = 0.0
            val methodsLeft = methods - finished
            executableChooser = TreeMap(
                methodsLeft.associateWith { solution.defaultTestingWeight(it) }
                    .toSortedMap { e1, e2 ->
                        e1.fullName().compareTo(e2.fullName())
                    }
                    .map { (executable, weight) ->
                        setTotal += weight
                        setTotal to executable
                    }.toMap(),
            )
            total = setTotal
        }

        init {
            setWeights()
        }

        private var previous: Executable? = null
        fun next(): Executable {
            check(more()) { "Ran out of methods to test due to @Limit annotations" }
            var next = executableChooser.higherEntry(random.nextDouble() * total).value!!
            if (next == previous && methods.size > 1) {
                next = executableChooser.higherEntry(random.nextDouble() * total).value!!
            }
            previous = next
            if (next in counts) {
                counts[next] = counts[next]!! + 1
                if (counts[next]!! == solution.limits[next]!!) {
                    finished += next
                    setWeights()
                }
            }
            return next
        }

        fun more() = methods.size > finished.size
    }

    class RecordingRandom(seed: Long, private val follow: List<Int>? = null, private val record: Boolean = false) :
        Random() {
        private val random = Random(seed)
        private val trace = mutableListOf<Int>()

        var currentIndex = 0
        var lastRandom = 0

        @Suppress("ThrowingExceptionsWithoutMessageOrCause")
        override fun nextBits(bitCount: Int): Int = random.nextBits(bitCount).also { newValue ->
            if (record) {
                trace += newValue
            }
            lastRandom = newValue
        }.also { actualValue ->
            if (follow != null) {
                val expectedValue = follow.getOrNull(currentIndex)
                if (expectedValue != actualValue) {
                    throw FollowTraceException(currentIndex, "expected $expectedValue, actual $actualValue")
                }
            }

            currentIndex++
        }

        fun finish(): List<Int> = trace.toList()
    }

    fun findReceiver(runners: List<TestRunner>, solutionReceiver: Any) = let {
        check(solutionReceiver::class.java == solution.solution) {
            "findReceiver should be passed an instance of the receiver class"
        }
        runners.find { it.receivers?.solution === solutionReceiver }
    }

    @Suppress("LongMethod", "ComplexMethod", "ReturnCount", "NestedBlockDepth", "ThrowsCount")
    fun test(
        passedSettings: Settings = Settings(),
        captureOutputControlInput: CaptureOutputControlInput = ::defaultCaptureOutputControlInput,
        followTrace: List<Int>? = null,
        testingEventListener: TestingEventListener = {},
    ): TestResults {
        if (solution.solution.isDesignOnly() || solution.solution.isAbstract()) {
            throw DesignOnlyTestingError(solution.solution)
        }
        val settings = solution.setCounts(Settings.DEFAULTS merge passedSettings)

        check(settings.runAll != null)
        check(!(settings.runAll && settings.shrink!!)) {
            "Running all tests combined with test shrinking produces inconsistent results"
        }

        val random = if (settings.seed == -1) {
            RecordingRandom(Random.nextLong(), follow = followTrace, record = settings.recordTrace!!)
        } else {
            RecordingRandom(settings.seed.toLong(), follow = followTrace, record = settings.recordTrace!!)
        }

        val runners: MutableList<TestRunner> = mutableListOf()
        var stepCount = 0

        val receiverGenerators = sequence {
            while (true) {
                yieldAll(solution.receiverGenerators.toList().shuffled(random))
            }
        }

        val cloner = Cloner.shared()

        val (receiverGenerator, generatorOverrides) = if (!solution.skipReceiver) {
            val receiverGenerator = ReceiverGenerator(random, mutableListOf(), this@Submission)
            val overrideMap = mutableMapOf(
                (solution.solution as Type)
                    to ({ _: Random, _: Cloner -> receiverGenerator } as TypeGeneratorGenerator),
            )
            if (!solution.generatorFactory.typeGenerators.containsKey(Any::class.java)) {
                overrideMap[(Any::class.java)] = { r: Random, c: Cloner ->
                    ObjectGenerator(
                        r,
                        c,
                        receiverGenerator,
                    )
                }
            }
            Pair(receiverGenerator, overrideMap.toMap())
        } else {
            Pair<ReceiverGenerator?, Map<Type, TypeGeneratorGenerator>>(null, mapOf())
        }

        val generators = solution.generatorFactory.get(random, cloner, generatorOverrides)

        fun List<TestRunner>.createdCount() =
            count { it.created && (solution.skipReceiver || it.receivers?.solution != null) }

        val neededReceivers = settings.receiverCount.coerceAtLeast(1)
        var loopCount = 0

        @Suppress("UNCHECKED_CAST", "LongParameterList", "MemberVisibilityCanBePrivate")
        fun List<TestRunner>.toResults(
            completed: Boolean = false,
            threw: Throwable? = null,
            timeout: Boolean = false,
        ) = TestResults(
            map { it.testResults as List<TestResult<Any, ParameterGroup>> }.flatten().sortedBy { it.stepCount },
            settings,
            completed,
            threw,
            timeout,
            runners.createdCount() >= neededReceivers,
            count { !it.tested },
            stepCount = stepCount,
            skippedSteps = map { it.skippedTests }.flatten().sorted(),
            loopCount = loopCount,
            randomTrace = random.finish(),
        )

        @Suppress("TooGenericExceptionCaught")
        try {
            fun addRunner(generators: Generators, receivers: Value<Any?>? = null) = TestRunner(
                runners.size,
                this@Submission,
                generators,
                receiverGenerators,
                captureOutputControlInput,
                ExecutablePicker(random, solution.methodsToTest),
                settings,
                runners,
                receivers,
                random,
                testingEventListener,
            ).also { runner ->
                if (receivers == null && !solution.skipReceiver) {
                    runner.next(stepCount++)
                }
                runners.add(runner)
            }

            var currentRunner: TestRunner? = null
            if (solution.skipReceiver) {
                addRunner(generators).also {
                    check(it.ready) { "Static method receivers should start ready" }
                    currentRunner = it
                }
            }

            var totalCount = 0
            var receiverStepCount = 0
            var testStepCount = 0
            var receiverIndex = 0

            val transitionProbability = if (solution.fauxStatic || solution.skipReceiver) {
                0.0
            } else {
                1.0 / (settings.methodCount.toDouble())
            }

            fun nextRunner(checkNull: Boolean = true) {
                currentRunner = runners.filterIndexed { index, _ -> index > receiverIndex }.find { it.ready }
                if (checkNull) {
                    check(currentRunner != null)
                }
                receiverIndex = runners.indexOf(currentRunner)
            }

            while (totalCount < settings.testCount) {
                testingEventListener(StartLoop(loopCount++))

                val finishedReceivers = runners.createdCount() >= neededReceivers

                if (Thread.interrupted()) {
                    return runners.toResults(timeout = true)
                }

                val readyLeft =
                    runners.filterIndexed { index, runner -> index > receiverIndex && runner.ready }.size

                val createReceiver = when {
                    currentRunner == null -> true
                    solution.receiverAsParameter && !finishedReceivers -> true
                    random.nextDouble() < transitionProbability -> true
                    else -> false
                }

                val switchReceivers = when {
                    createReceiver -> false
                    solution.skipReceiver -> false
                    readyLeft > 0 && random.nextDouble() < transitionProbability -> true
                    else -> false
                }

                if (createReceiver) {
                    check(!solution.skipReceiver) { "Static testing should never drop receivers" }
                    addRunner(generators).also { runner ->
                        @Suppress("UNCHECKED_CAST")
                        if (runner.ready) {
                            check(runner.receivers != null)
                            receiverGenerator?.receivers?.add(runner.receivers as Value<Any>)
                        }
                    }.also {
                        if (it.failed) {
                            if ((!settings.shrink!! || it.lastComplexity!!.level <= Complexity.MIN) &&
                                !settings.runAll
                            ) {
                                return runners.toResults()
                            }
                        }
                        if (!solution.receiverAsParameter || currentRunner == null) {
                            currentRunner = it
                            receiverIndex = runners.indexOf(currentRunner)
                        }
                        if (it.ranLastTest || it.skippedLastTest) {
                            receiverStepCount++
                            totalCount++
                        }
                    }
                } else {
                    if (switchReceivers) {
                        nextRunner()
                    }
                    currentRunner!!.next(stepCount++).also { runner ->
                        if (runner.ranLastTest || runner.skippedLastTest) {
                            testStepCount++
                            totalCount++
                        }
                    }
                }
                if (currentRunner!!.failed) {
                    if ((!settings.shrink!! || currentRunner!!.lastComplexity!!.level <= Complexity.MIN) &&
                        !settings.runAll
                    ) {
                        return runners.toResults()
                    }
                }
                if (currentRunner!!.returnedReceivers != null) {
                    currentRunner!!.returnedReceivers!!.forEach { returnedReceiver ->
                        if (findReceiver(runners, returnedReceiver.solution!!) == null) {
                            addRunner(generators, returnedReceiver)
                        } else {
                            @Suppress("UNCHECKED_CAST")
                            receiverGenerator?.receivers?.add(returnedReceiver as Value<Any>)
                        }
                    }
                    currentRunner!!.returnedReceivers = null
                }
                if (currentRunner?.ready == false) {
                    nextRunner(false)
                }
            }
            return runners.toResults(completed = true)
        } catch (e: FollowTraceException) {
            throw e
        } catch (e: Throwable) {
            if (settings.testing!!) {
                throw e
            }
            return runners.toResults(threw = e)
        }
    }
}

sealed class SubmissionDesignError(
    message: String,
    @Suppress(
        "unused",
    ) val hint: String = "",
) : RuntimeException(message)

class SubmissionDesignMissingMethodError(klass: Class<*>, executable: Executable, hasInnerClasses: Boolean) :
    SubmissionDesignError(
        "${klass.name} doesn't provide ${
            if (executable is Method) {
                """${
                    if (executable.isStatic()) {
                        "class "
                    } else {
                        " "
                    }
                }method"""
            } else {
                "constructor"
            }
        }${executable.fullName(klass.isKotlin())}${
            if (hasInnerClasses) {
                " (submission defines inner classes)"
            } else {
                ""
            }
        }.",
        "Your submission is missing a method. Check your method signatures.",
    )

class SubmissionDesignMissingInnerClassError(klass: Class<*>, innerClass: Class<*>) :
    SubmissionDesignError(
        "${klass.name} doesn't provide inner class ${innerClass.name}.",
        "Your submission is missing an inner class.",
    )

class SubmissionDesignKotlinNotAccessibleError(klass: Class<*>, field: String) :
    SubmissionDesignError(
        "Property $field on ${klass.name} is missing or not accessible.",
        "Your submission has a missing property.",
    )

class SubmissionDesignKotlinNotModifiableError(klass: Class<*>, field: String) :
    SubmissionDesignError(
        "Property $field on ${klass.name} is not modifiable.",
        "Your submission has a misconfigured property.",
    )

class SubmissionDesignKotlinIsAccessibleError(klass: Class<*>, field: String) :
    SubmissionDesignError(
        "Property $field on ${klass.name} is accessible but should not be.",
        "Your submission has a extra unnecessary property.",
    )

class SubmissionDesignKotlinIsModifiableError(klass: Class<*>, field: String) :
    SubmissionDesignError(
        "Property $field on ${klass.name} is modifiable but should not be.",
        "Your submission has a misconfigured property.",
    )

class SubmissionDesignExtraMethodError(klass: Class<*>, executable: Executable) :
    SubmissionDesignError(
        "${klass.name} provided extra ${
            if (executable.isStatic() && !klass.isKotlin()) {
                "static "
            } else {
                ""
            }
        }${
            if (executable is Method) {
                "method"
            } else {
                "constructor"
            }
        } ${executable.fullName(klass.isKotlin())}.",
        "Your submission provides an extra method.",
    )

class SubmissionDesignExtraInnerClassError(klass: Class<*>, innerKlass: Class<*>) :
    SubmissionDesignError(
        "${klass.name} provided extra inner class ${innerKlass.name}.",
        "Your submission provides an extra inner class.",
    )

class SubmissionDesignInheritanceError(klass: Class<*>, parent: Class<*>) :
    SubmissionDesignError(
        "${klass.name} doesn't inherit from ${parent.name}.",
    )

class SubmissionTypeParameterError(klass: Class<*>, innerClass: Boolean = false) :
    SubmissionDesignError(
        "${
            if (innerClass) {
                "Inner class "
            } else {
                ""
            }
        }${klass.name} has missing, unnecessary, or incorrectly-bounded type parameters.",
    )

class SubmissionDesignMissingFieldError(klass: Class<*>, field: Field) :
    SubmissionDesignError(
        "Field ${field.fullName()} is not accessible in ${klass.name} but should be.",
        "Your submission has a missing field.",
    )

class SubmissionDesignExtraFieldError(klass: Class<*>, field: Field) :
    SubmissionDesignError(
        "Field ${field.fullName()} is accessible in ${klass.name} but should not be.",
        "Your submission has a misconfigured field",
    )

class SubmissionStaticFieldError(klass: Class<*>, field: Field) :
    SubmissionDesignError(
        "Field ${field.fullName()} is static in ${klass.name}, " +
            "but static fields are not permitted for this problem.",
        "Your submission has an incorrect static field.",
    )

class SubmissionStaticPublicFieldError(klass: Class<*>, field: Field) :
    SubmissionDesignError(
        "Static field ${field.fullName()} in ${klass.name} must be private.",
        "Your submission has a misconfigured static field.",
    )

class SubmissionDesignClassError(klass: Class<*>, message: String, innerClass: Boolean = false) :
    SubmissionDesignError(
        "${
            if (innerClass) {
                "Inner class "
            } else {
                ""
            }
        }${klass.name} $message.",
        "Your submission class is designed incorrectly.",
    )

class DesignOnlyTestingError(klass: Class<*>) :
    Exception(
        "Solution class ${klass.name} is marked as design only.",
    )

class KotlinBadSetterOrGetter(property: String, bad: String) :
    SubmissionDesignError(
        "Kotlin class should declare a property $property and not manually implement $bad",
        "Your submission provides an unnecessary Java-style getter or setter.",
    )

@Suppress("SwallowedException")
fun unwrap(run: () -> Any?): Any? = try {
    run()
} catch (e: InvocationTargetException) {
    throw e.cause ?: error("InvocationTargetException should have a cause")
}

fun Class<*>.isKotlin() = getAnnotation(Metadata::class.java) != null

class FollowTraceException(index: Int, message: String) :
    RuntimeException(
        "Random generator out of sync at index $index: $message",
    )

@Suppress("MagicNumber")
fun Executable.looksLikeGetter() =
    this is Method && name.length > 3 && name.startsWith("get") && parameters.isEmpty() && returnType.name != "void"

@Suppress("MagicNumber")
fun Executable.looksLikeSetter() =
    this is Method && name.length > 3 && name.startsWith("set") && parameters.size == 1 && returnType.name == "void"

fun Method.getterOrSetterToPropertyName() = name
    .removePrefix("set")
    .removePrefix("get")
    .let { it[0].lowercase() + it.substring(1) }
