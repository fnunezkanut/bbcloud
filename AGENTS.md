## Project Overview

- **bbcloud** is an IntelliJ IDEA plugin (`com.github.fnunezkanut.bbcloud`).
- It is a placeholder scaffold: no actions, extensions, or Kotlin entry points yet. Runtime configuration is only [`plugin.xml`](src/main/resources/META-INF/plugin.xml).
- Target IDE: IntelliJ IDEA **2026.2.3+** (`sinceBuild` `262.10968.63`). The plugin depends on `com.intellij.modules.platform` and the bundled `Git4Idea` plugin (used for branch checkout).

## Coding Standards

### Kotlin Conventions
- Keep to the code style specified in `.editorconfig` and follow the [official Kotlin style guide](https://kotlinlang.org/docs/coding-conventions.html)
- Use idiomatic Kotlin 2.4 and above
- Prefer `val` over `var` when possible
- Use data classes for immutable data structures
- Avoid single expression functions
- Use named parameters in function calls having 2 or more arguments
- Where possible use American English
- When refactoring never ever delete comments as they might be relevant

### Date and time
- Depend on **kotlinx-datetime** for ISO parsing, formatting, and calendar types (`kotlinx.datetime.*` where those types apply)
- Store instants as **`kotlin.time.Instant`** (parsed via `Instant.parse` from the kotlinx-datetime dependency); do not use the deprecated `kotlinx.datetime.Instant` type alias
- Do not use `java.time`, Joda-Time, or ThreeTen in plugin sources
- Exception: convert to `java.util.Date` (or other JDK types) only at boundaries required by IntelliJ Platform APIs, via `Instant.toEpochMilliseconds()` or equivalent, without storing `java.time` types in domain models
- Enable `-opt-in=kotlin.time.ExperimentalTime` in Gradle when the compiler requires it for `kotlin.time.Instant`

### Naming Conventions
- Package names: `com.github.fnunezkanut.bbcloud`
- Class names: PascalCase
- Function names: camelCase
- Constants: UPPER_SNAKE_CASE
- File names: PascalCase for classes, PascalCase for enum entries, camelCase for functions

### Code Organization
- Keep functions small and focused
- Do not use star imports

### Logging
- Use IntelliJ `com.intellij.openapi.diagnostic.Logger` / `thisLogger()` for plugin code
- Prefer `warn` with a throwable when catching unexpected failures
- Do not use `println`, SLF4J, or `java.util.logging`

#### Getting a logger
- Use `thisLogger()` only in plain class members where `this` is the plugin class
- `thisLogger()` binds to the innermost implicit receiver. Inside `coroutineScope.launch { }`, `withContext { }`, `apply { }`, or Kotlin UI DSL `panel { row { } }` blocks it logs under the wrong category (for example `CoroutineScope`, `HyperlinkLabel`, or `Row`)
- In those cases, and in any class that logs from such lambdas, declare `private val LOG = logger<ClassName>()` in the companion object and use `LOG` throughout the file

#### Levels
- `info`: every user-triggered UI interaction (button, link, action, and list selection), configuration changes, and early-return paths that make an operation fail silently (for example no stored token or no repository slug)
- `debug`: HTTP request and response details, credential store access, lifecycle events (tool window creation, card switches, disposal), and intermediate results such as parsed item counts
- `warn`: unexpected failures and unexpected HTTP status codes; pass the throwable when one exists
- `error`: avoid; it surfaces as an IDE error report to the user

#### What to log
- HTTP requests: log method and URL before the call, then status code and elapsed milliseconds after it; log unexpected status codes at `warn`
- User actions: state which control was used and relevant non-sensitive context (for example a pull request id)
- Log a throwable once, at the layer that decides the user-facing outcome (currently the UI panels); lower layers log the same failure at `debug` only

#### Secrets and privacy
- Never log API tokens, `Authorization` headers, passwords, or token values read from `PasswordSafe`; log presence as a boolean instead
- Never log raw HTTP response bodies; log derived values such as counts
- On unexpected HTTP status codes, do log Bitbucket's structured `error.message` and `error.detail` fields and the `X-Request-Id` response header (see `BitbucketHttpErrors`); these are allowed because they contain no credentials
- Prefer ids over user-authored text such as pull request titles

#### Viewing logs
- `info` and above go to `idea.log` by default (sandbox path listed under Troubleshooting)
- To enable `debug`, open Help > Diagnostic Tools > Debug Log Settings and add `#com.github.fnunezkanut.bbcloud`

## Build and Development

### Version Management
- Single-module project built with Gradle Kotlin DSL
- Update `version` in root [`build.gradle.kts`](build.gradle.kts) when releasing
- Read [`.sdkmanrc`](.sdkmanrc) for JDK and Gradle versions (`sdk env`)

### Common Tasks
- `./gradlew build`: Compile, lint (`check`), unit tests, and `buildPlugin` (installable ZIP under `build/distributions/`); on local machines runs `formatKotlin` first
- `./gradlew buildPlugin`: Produce installable ZIP only (also runs as part of `build`)
- `./gradlew verifyPlugin`: JetBrains Plugin Verifier (downloads IDE builds on first run)
- `./gradlew runIde`: Sandbox IDE with this plugin (for humans only; agents must never run `./gradlew runIde`)
- `./gradlew test`: Unit tests only
- `./gradlew lint` / `./gradlew format`: Kotlinter (ktlint **1.8.0**)
- `./gradlew clean build`: Clean build

### Dependency Management
- Standard Gradle plus IntelliJ Platform Gradle Plugin **2.19.0**
- `intellijIdea("2026.2.3")` for compile and sandbox runs
- Resolve version conflicts explicitly when adding dependencies
- Avoid internal apis which get flagged with `gradle verifyPlugin`

### Dependency Source and Inspection
- When inspecting a dependency (`com.example:library:1.0.0`):
  1. Attach sources in the IDE if missing
  2. Confirm `library-1.0.0-sources.jar` under `~/.gradle/caches` (or `~/.m2/repository`)
  3. Prefer reading sources over decompiled bytecode
- If sources are missing, run `./gradlew dependencies`
- When inspecting a JAR, use a temp path under `build/`, not the project root

## Testing Guidelines

### Test Structure
- Test files: `{ClassName}Test.kt`
- Test package: `com.github.fnunezkanut.bbcloud`
- Use JUnit Jupiter **6.1.3** with Kotest assertions **6.2.5**
- Mock external dependencies with MockK
- Use descriptive test names
- Unit tests usually follow Given-When-Then
- Unit tests use JUnit `@Tag("unit")`
- Group many assertions on the same object into Kotest `assertSoftly` blocks
- Avoid AssertJ; use Kotest assertions
- Avoid mocking data classes when a real instance is simple enough
- Group related tests with JUnit `@Nested` where it helps readability

### Test Example
```kotlin
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class ExampleTest {
    @Test
    fun `helper - should behave`() {
        // GIVEN
        val input = "test"

        // WHEN
        val result = helper(input)

        // THEN
        result shouldBe expectedValue
    }
}
```

Platform-heavy flows may need fixtures or integration tests tagged `@Tag("integration")` when added.

## Error Handling

- User-visible issues: balloon notifications (register notification groups in [`plugin.xml`](src/main/resources/META-INF/plugin.xml) when needed)

## Troubleshooting

- **Lint failures:** run `./gradlew format`, then `./gradlew lint`
- **Test failures:** check MockK stubs and IntelliJ API types on the test compile classpath
- **Sandbox / runIde:** logs under `.intellijPlatform/sandbox/bbcloud/IU-2026.2.3/log_runIde/idea.log`
- **Plugin Verifier:** reports in `build/reports/pluginVerifier`

## Git Workflow

- Use feature branches and descriptive commit messages
- Do not commit secrets, sandbox caches (`.intellijPlatform/`), or local IDE config you did not intend to share
