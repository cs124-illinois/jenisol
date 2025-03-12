package edu.illinois.cs.cs125.jenisol.core

import java.util.Properties

val VERSION: String = Properties().also {
    it.load((object {}).javaClass.getResourceAsStream("/edu.illinois.cs.cs125.jenisol.version"))
}.getProperty("version")
