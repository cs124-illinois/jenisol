import java.io.File
import java.io.StringWriter
import java.util.Properties
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "org.cs124"
version = "2023.12.0"

plugins {
    kotlin("jvm") version "1.9.22"
    java
    `maven-publish`
    signing
    id("org.jmailen.kotlinter") version "4.1.1"
    checkstyle
    id("com.github.sherter.google-java-format") version "0.9"
    id("com.github.ben-manes.versions") version "0.50.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.4"
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
}
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.22")
    implementation("io.github.classgraph:classgraph:4.8.165")
    implementation("design.aem:cloning:1.11.1")
    implementation("com.google.jimfs:jimfs:1.3.0")

    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("org.slf4j:slf4j-simple:2.0.10")
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
    rejectVersionIf {
        candidate.version.isNonStable()
    }
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
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withJavadocJar()
    withSourcesJar()
}
publishing {
    publications {
        create<MavenPublication>("jenisol") {
            from(components["java"])

            pom {
                name = "jenisol"
                description = "Solution-driven autograding for CS 124."
                url = "https://cs124.org"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/license/mit/"
                    }
                }
                developers {
                    developer {
                        id = "gchallen"
                        name = "Geoffrey Challen"
                        email = "challen@illinois.edu"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/cs124-illinois/jenisol.git"
                    developerConnection = "scm:git:https://github.com/cs124-illinois/jenisol.git"
                    url = "https://github.com/cs124-illinois/jenisol"
                }
            }
        }
    }
}
nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
}
signing {
    sign(publishing.publications["jenisol"])
}
