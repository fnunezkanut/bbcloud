import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    // Compose MP 1.12.0 matches Jewel 0.41, the Jewel release whose min IJP is 2026.2.3.
    id("org.jetbrains.compose") version "1.12.0"
    id("org.jetbrains.intellij.platform") version "2.19.0"
    id("org.jmailen.kotlinter") version "5.7.0"
}

group = "com.github.fnunezkanut"
version = "1.3.0"

val isLocalBuild = providers.environmentVariable("CI").orNull.isNullOrEmpty()

repositories {
    google()
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.2.3")
        bundledPlugin("Git4Idea")
        bundledModule("intellij.libraries.compose.foundation.desktop")
        bundledModule("intellij.libraries.skiko")
        bundledModule("intellij.platform.collaborationTools")
        bundledModule("intellij.platform.jewel.foundation")
        bundledModule("intellij.platform.jewel.ideLafBridge")
        bundledModule("intellij.platform.jewel.markdown.core")
        bundledModule("intellij.platform.jewel.markdown.ideLafBridgeStyling")
        bundledModule("intellij.platform.jewel.ui")
        bundledModule("intellij.platform.vcs.dvcs")
        bundledModule("intellij.platform.vcs.dvcs.impl")
        bundledModule("intellij.platform.vcs.impl")
        bundledModule("intellij.platform.vcs.impl.shared")
    }
    // Compose runtime is compile-only because IntelliJ's bundled Jewel/Compose modules provide it at runtime.
    compileOnly("org.jetbrains.compose.runtime:runtime:1.12.0")
    testCompileOnly("org.jetbrains.compose.runtime:runtime:1.12.0")
    // The test compiler also applies the Compose compiler plugin, so it needs the API on its compile classpath.

    implementation("com.squareup.okhttp3:okhttp:5.4.0") {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation("io.kotest:kotest-assertions-core-jvm:6.2.5")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        // Emits no compatibility stubs for platform interface default methods, so the plugin does not override
        // deprecated or experimental members it never implements (verifyPlugin warnings).
        freeCompilerArgs.add("-jvm-default=no-compatibility")
    }
}

// Fills bbcloud-build.properties with the plugin version so the About section does not depend on PluginManager.
tasks.processResources {
    filesMatching("bbcloud-build.properties") {
        expand(mapOf("version" to project.version))
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.github.fnunezkanut.bbcloud"
        name = "Bitbucket Cloud PR Review"
        ideaVersion {
            sinceBuild = "262.10968.63"
            untilBuild = "263.*"
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks.test {
    jvmArgs("-Xshare:off", "-XX:+EnableDynamicAgentLoading")
    useJUnitPlatform {
        includeTags("unit")
        excludeTags("integration")
    }
    testLogging {
        events = setOf(
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
        )
        showStandardStreams = false
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    val testLogger = logger
    addTestListener(
        object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {}

            override fun beforeTest(testDescriptor: TestDescriptor) {}

            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}

            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.parent != null) return // reports the root suite only
                val suiteName = suite.displayName.substringAfterLast(":")
                val failedIcon = if (result.failedTestCount > 0) "❌ " else ""
                val skippedIcon = if (result.skippedTestCount > 0) "⚠ " else ""
                testLogger.lifecycle("TestSuite [$suiteName] Result: ${result.resultType}")
                testLogger.lifecycle(
                    "TestSuite [$suiteName] Summary: ${result.testCount} tests [ " +
                        "${result.successfulTestCount} OK ✅ , " +
                        "${result.failedTestCount} FAIL $failedIcon, " +
                        "${result.skippedTestCount} SKIP $skippedIcon ]",
                )
            }
        },
    )
}

kotlinter {
    ktlintVersion = "1.8.0"
    ignoreLintFailures = isLocalBuild
    ignoreFormatFailures = false
}
tasks.named("build") {
    dependsOn(tasks.named("buildPlugin"))
    if (isLocalBuild) {
        dependsOn(tasks.named("formatKotlin"))
    }
}

// TODO cleanup once 2.4.20+ is supported in kotlinter plugin https://github.com/jeremymailen/kotlinter-gradle/issues/562
configurations.named("ktlint") {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-compiler-embeddable") {
            useVersion("2.4.10")
        }
    }
}

tasks.register("lint") { dependsOn("lintKotlin") }
tasks.register("format") { dependsOn("formatKotlin") }
