import java.io.File
import java.io.StringWriter
import java.util.Properties
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "com.github.cs124-illinois"
version = "2023.7.0"

plugins {
    kotlin("jvm") version "1.8.22"
    java
    `maven-publish`

    id("org.jmailen.kotlinter") version "3.15.0"
    checkstyle
    id("com.github.sherter.google-java-format") version "0.9"

    id("com.github.ben-manes.versions") version "0.47.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.0"
}
repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.22")
    implementation("io.github.classgraph:classgraph:4.8.160")
    implementation("io.github.kostaskougios:cloning:master-SNAPSHOT")

    testImplementation("io.kotest:kotest-runner-junit5:5.6.2")
    testImplementation("org.slf4j:slf4j-simple:2.0.7")
}
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}
tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}
tasks.withType<Test> {
    useJUnitPlatform()
    enableAssertions = true
    tasks.withType<Test> {
        useJUnitPlatform()
        jvmArgs(
            "-ea", "-Xmx1G", "-Xss256k",
            "-Dfile.encoding=UTF-8",
            "-XX:-OmitStackTraceInFastThrow",
            "--add-opens", "java.base/java.util=ALL-UNNAMED"
        )
    }
}
tasks.dependencyUpdates {
    fun String.isNonStable() = !(
        listOf("RELEASE", "FINAL", "GA").any { uppercase().contains(it) }
            || "^[0-9,.v-]+(-r)?$".toRegex().matches(this)
        )
    rejectVersionIf { candidate.version.isNonStable() }
    gradleReleaseChannel = "current"
}
detekt {
    buildUponDefaultConfig = true
}
tasks.check {
    dependsOn("detekt")
}
googleJavaFormat {
    toolVersion = "1.15.0"
}
tasks.compileKotlin {
    dependsOn("createProperties")
}
tasks.compileTestKotlin {
    kotlinOptions {
        javaParameters = true
    }
}
task("createProperties") {
    dependsOn(tasks.processResources)
    doLast {
        val properties = Properties().also {
            it["version"] = project.version.toString()
        }
        File(projectDir, "src/main/resources/edu.illinois.cs.cs125.jenisol.core.version")
            .printWriter().use { printWriter ->
                printWriter.print(
                    StringWriter().also { properties.store(it, null) }.buffer.toString()
                        .lines().drop(1).joinToString(separator = "\n").trim()
                )
            }
    }
}
tasks {
    val sourcesJar by creating(Jar::class) {
        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource)
    }
    artifacts {
        add("archives", sourcesJar)
    }
}
java {
    withSourcesJar()
}
publishing {
    publications {
        create<MavenPublication>("jenisol") {
            from(components["java"])
        }
    }
}