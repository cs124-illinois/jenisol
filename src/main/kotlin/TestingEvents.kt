@file:Suppress("unused")

package edu.illinois.cs.cs125.jenisol.core

typealias TestingEventListener = (event: TestingEvent) -> Unit

sealed class TestingEvent

class StartTest(val stepCount: Int) : TestingEvent()
class EndTest(val stepCount: Int) : TestingEvent()
