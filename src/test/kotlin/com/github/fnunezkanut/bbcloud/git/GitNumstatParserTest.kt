package com.github.fnunezkanut.bbcloud.git

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class GitNumstatParserTest {
    @Test
    fun `sum - should total added and removed lines from normal output`() {
        // GIVEN
        val output = listOf(
            "12\t4\tsrc/Main.kt",
            "3\t8\tdocs/README file.md"
        )

        // WHEN
        val result = GitNumstatParser.sum(output)

        // THEN
        assertSoftly {
            result.linesAdded shouldBe 15
            result.linesRemoved shouldBe 12
            result.hasBinaryFiles shouldBe false
        }
    }

    @Test
    fun `sum - should exclude binary files and mark totals as approximate`() {
        // GIVEN
        val output = listOf(
            "10\t2\tsrc/Main.kt",
            "-\t-\timage with spaces.png"
        )

        // WHEN
        val result = GitNumstatParser.sum(output)

        // THEN
        assertSoftly {
            result.linesAdded shouldBe 10
            result.linesRemoved shouldBe 2
            result.hasBinaryFiles shouldBe true
        }
    }

    @Test
    fun `sum - should ignore blank and malformed lines`() {
        // GIVEN
        val output = listOf(
            "",
            "   ",
            "malformed",
            "not\tnumeric\tfile.txt",
            "7\t1\tfile.txt"
        )

        // WHEN
        val result = GitNumstatParser.sum(output)

        // THEN
        result shouldBe BranchDiffLineTotals(linesAdded = 7, linesRemoved = 1, hasBinaryFiles = false)
    }

    @Test
    fun `sum - should return zero totals for empty output`() {
        // WHEN
        val result = GitNumstatParser.sum(emptyList())

        // THEN
        result shouldBe BranchDiffLineTotals(linesAdded = 0, linesRemoved = 0, hasBinaryFiles = false)
    }

    @Test
    fun `perFile - should parse counts for each path`() {
        // GIVEN
        val output = listOf(
            "12\t4\tsrc/Main.kt",
            "3\t8\tdocs/README file.md"
        )

        // WHEN
        val result = GitNumstatParser.perFile(output)

        // THEN
        assertSoftly {
            result["src/Main.kt"] shouldBe FileLineCount(linesAdded = 12, linesRemoved = 4, isBinary = false)
            result["docs/README file.md"] shouldBe FileLineCount(
                linesAdded = 3,
                linesRemoved = 8,
                isBinary = false
            )
        }
    }

    @Test
    fun `perFile - should retain binary files with unknown counts`() {
        // GIVEN
        val output = listOf("-\t-\timage.png")

        // WHEN
        val result = GitNumstatParser.perFile(output)

        // THEN
        result["image.png"] shouldBe FileLineCount(linesAdded = 0, linesRemoved = 0, isBinary = true)
    }

    @Test
    fun `perFile - should ignore blank and malformed lines`() {
        // GIVEN
        val output = listOf(
            "",
            "   ",
            "malformed",
            "not\tnumeric\tfile.txt",
            "7\t1\tfile.txt"
        )

        // WHEN
        val result = GitNumstatParser.perFile(output)

        // THEN
        result shouldBe mapOf("file.txt" to FileLineCount(linesAdded = 7, linesRemoved = 1, isBinary = false))
    }

    @Test
    fun `perFile - should return an empty map for empty output`() {
        // WHEN
        val result = GitNumstatParser.perFile(emptyList())

        // THEN
        result shouldBe emptyMap()
    }

    @Test
    fun `perFile - should normalize paths`() {
        // GIVEN
        val output = listOf("2\t1\t./src/A.kt")

        // WHEN
        val result = GitNumstatParser.perFile(output)

        // THEN
        result["src/A.kt"] shouldBe FileLineCount(linesAdded = 2, linesRemoved = 1, isBinary = false)
    }
}