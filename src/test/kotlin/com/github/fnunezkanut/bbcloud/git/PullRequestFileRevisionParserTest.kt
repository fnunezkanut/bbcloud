package com.github.fnunezkanut.bbcloud.git

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestFileRevisionParserTest {
    @Test
    fun `requestLines - should build source revision requests`() {
        // GIVEN
        val paths = listOf("src/a.kt", "docs/read me.md")

        // WHEN
        val result = PullRequestFileRevisionParser.requestLines(sourceRef = "refs/remotes/origin/main", repositoryRelativePaths = paths)

        // THEN
        result shouldBe listOf("refs/remotes/origin/main:src/a.kt", "refs/remotes/origin/main:docs/read me.md")
    }

    @Test
    fun `parse - should map existing blobs to their revisions`() {
        // GIVEN
        val requests = PullRequestFileRevisionParser.requestLines("main", listOf("src/a.kt"))

        // WHEN
        val result = PullRequestFileRevisionParser.parse(
            requestLines = requests,
            output = listOf("0123456789abcdef blob 12")
        )

        // THEN
        result["src/a.kt"] shouldBe "0123456789abcdef"
    }

    @Test
    fun `parse - should omit missing and non-blob objects`() {
        // GIVEN
        val requests = PullRequestFileRevisionParser.requestLines("main", listOf("src/missing.kt", "submodule"))

        // WHEN
        val result = PullRequestFileRevisionParser.parse(
            requestLines = requests,
            output = listOf("main:src/missing.kt missing", "0123456789abcdef commit 42")
        )

        // THEN
        result shouldBe emptyMap()
    }

    @Test
    fun `parse - should return an empty map for empty output`() {
        // GIVEN
        val requests = PullRequestFileRevisionParser.requestLines("main", listOf("src/a.kt"))

        // WHEN
        val result = PullRequestFileRevisionParser.parse(requestLines = requests, output = emptyList())

        // THEN
        assertSoftly {
            result shouldBe emptyMap()
            result.size shouldBe 0
        }
    }
}