# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Jenisol is a solution-driven autograding library for CS 124. It tests student submissions against reference solutions by:
1. Generating test inputs using annotations-based configuration
2. Running both solution and submission code with those inputs
3. Comparing outputs, return values, exceptions, and side effects

The library supports both Java and Kotlin, with special handling for Kotlin-specific features.

## Version

Uses date-based versioning: YEAR.MONTH.MINOR (e.g., 2025.6.0)

## Build System & Commands

This is a Gradle/Kotlin project (build.gradle.kts). Java 17 required.

### Essential Commands
- `./gradlew build` - Full build with tests
- `./gradlew test` - Run test suite (uses Kotest)
- `./gradlew clean` - Clean build artifacts

### Code Quality
- `./gradlew check` - Run all checks (includes linting and detekt)
- `./gradlew formatKotlin` - Auto-format Kotlin code
- `./gradlew lintKotlin` - Lint Kotlin code
- `./gradlew detekt` - Run detekt static analysis

### Running Single Tests
Use Kotest test filtering via command line or IDE integration.

### Publishing
- `./gradlew publish` - Publish to Maven repository
- Configured for Sonatype Maven Central (s01.oss.sonatype.org)

## Architecture

### Core Components

**Solution** (`src/main/kotlin/Solution.kt`)
- Wraps reference solution class
- Discovers methods to test vs. methods that generate receiver instances
- Validates design constraints (no modifiable static fields, etc.)
- Handles both executables (methods/constructors) and fields

**Submission** (`src/main/kotlin/Submission.kt`)
- Wraps student submission class
- Validates design matches solution (visibility, inheritance, interfaces)
- Executes submission methods and compares with solution
- Handles Java/Kotlin interop edge cases

**Testing** (`src/main/kotlin/Testing.kt`)
- Main testing orchestration and result comparison
- `Result<T, P>` data class captures execution results (returned value, exceptions, stdout/stderr/stdin, timing)
- `TestResult<T, P>` contains both solution and submission results for comparison

### Generator System (`src/main/kotlin/generators/`)

Controls how test inputs are created:
- **Type generators**: `@SimpleType`, `@EdgeType`, `@RandomType` - define sets or generators for parameter types
- **Parameter generators**: `@FixedParameters` (static lists), `@RandomParameters` (dynamic generation)
- **Receiver generators**: Methods/constructors that create instances for testing instance methods

### Annotation-Based Configuration (`src/main/kotlin/Annotations.kt`)

Key annotations:
- `@FixedParameters` / `@RandomParameters` - define test inputs
- `@SimpleType` / `@EdgeType` / `@RandomType` - type value generation
- `@Verify` - custom result verification logic
- `@Compare` - custom comparison for complex types
- `@Both` - test method on interface/superclass that both solution and submission implement
- `@Initializer` - setup code before test execution
- `@FilterParameters` - filter out unwanted parameter combinations
- `@DesignOnly` - class design validation without execution testing
- `@NotNull` - parameter nullability constraints
- `@ProvideSystemIn` / `@ProvideFileSystem` - inject I/O for testing

### Parameter Groups

Strongly-typed parameter containers: `None`, `One<I>`, `Two<I,J>`, `Three<I,J,K>`, `Four<I,J,K,L>`
- Used throughout to avoid array/list type erasure
- Deep equality/hashing support for arrays and objects

## Testing Structure

Tests are in `src/test/java/` organized as:
- `edu/illinois/cs/cs125/jenisol/core/` - core library tests
- `examples/java/` - Java example test scenarios (receiver, noreceiver, designonly, submissiondesign)
- `examples/kotlin/` - Kotlin example test scenarios

Uses Kotest's StringSpec style.

## Key Design Patterns

1. **Reflection-heavy**: Extensive use of Java reflection to inspect and invoke solution/submission code
2. **Cloning for isolation**: Uses cloning library to detect parameter mutation
3. **Captured I/O**: Intercepts stdout/stderr/stdin during execution
4. **Type-safe parameters**: Parameter groups avoid type erasure issues
5. **Annotation-driven**: Configuration via annotations on solution classes rather than external config files

## Java/Kotlin Interop

Special handling for:
- Kotlin properties vs. Java getter/setter methods
- Kotlin null safety (`@NotNull` annotation)
- Kotlin data classes
- Kotlin synthetic methods (compiler-generated)
- Kotlin default parameters
- Kotlin companion objects

## Dependencies

Main runtime:
- `kotlin-reflect` - Kotlin reflection
- `classgraph` - Fast classpath scanning
- `cloning` - Deep object cloning
- `jimfs` - In-memory filesystem for testing

Test:
- `kotest` - Test framework
- `slf4j-simple` - Logging
