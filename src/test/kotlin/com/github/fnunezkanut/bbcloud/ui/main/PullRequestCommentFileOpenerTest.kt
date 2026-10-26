package com.github.fnunezkanut.bbcloud.ui.main

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestCommentFileOpenerTest {
    @Nested
    inner class ProjectRelativePathTests {
        @Test
        fun `projectRelativePath - should keep a repository relative path`() {
            // GIVEN
            val inlinePath = "README.md"

            // WHEN
            val result = PullRequestCommentFileOpener.projectRelativePath(inlinePath = inlinePath)

            // THEN
            result shouldBe "README.md"
        }

        @Test
        fun `projectRelativePath - should normalize a leading dot slash`() {
            // GIVEN
            val inlinePath = "./README.md"

            // WHEN
            val result = PullRequestCommentFileOpener.projectRelativePath(inlinePath = inlinePath)

            // THEN
            result shouldBe "README.md"
        }

        @Test
        fun `projectRelativePath - should normalize backslashes and surrounding slashes`() {
            // GIVEN
            val inlinePath = " /src\\main/README.md/ "

            // WHEN
            val result = PullRequestCommentFileOpener.projectRelativePath(inlinePath = inlinePath)

            // THEN
            result shouldBe "src/main/README.md"
        }

        @Test
        fun `projectRelativePath - should be null when there is no path`() {
            // GIVEN
            val empty = ""
            val blank = "   "

            // WHEN
            val emptyResult = PullRequestCommentFileOpener.projectRelativePath(inlinePath = empty)
            val blankResult = PullRequestCommentFileOpener.projectRelativePath(inlinePath = blank)

            // THEN
            assertSoftly {
                emptyResult shouldBe null
                blankResult shouldBe null
            }
        }

        @Test
        fun `projectRelativePath - should be null for a path escaping the project root`() {
            // GIVEN Bitbucket paths are untrusted input to a file system lookup
            val leading = "../secret.txt"
            val nested = "a/../../secret.txt"

            // WHEN
            val leadingResult = PullRequestCommentFileOpener.projectRelativePath(inlinePath = leading)
            val nestedResult = PullRequestCommentFileOpener.projectRelativePath(inlinePath = nested)

            // THEN
            assertSoftly {
                leadingResult shouldBe null
                nestedResult shouldBe null
            }
        }
    }

    @Nested
    inner class TargetEditorLineTests {
        @Test
        fun `targetEditorLine - should convert a one based line`() {
            // GIVEN
            val fileLine = 3
            val lineCount = 10

            // WHEN
            val result = PullRequestCommentFileOpener.targetEditorLine(fileLine = fileLine, lineCount = lineCount)

            // THEN
            result shouldBe 2
        }

        @Test
        fun `targetEditorLine - should return the first line for line one`() {
            // GIVEN
            val fileLine = 1
            val lineCount = 10

            // WHEN
            val result = PullRequestCommentFileOpener.targetEditorLine(fileLine = fileLine, lineCount = lineCount)

            // THEN
            result shouldBe 0
        }

        @Test
        fun `targetEditorLine - should return the first line without a comment line`() {
            // GIVEN
            val fileLine = null
            val lineCount = 10

            // WHEN
            val result = PullRequestCommentFileOpener.targetEditorLine(fileLine = fileLine, lineCount = lineCount)

            // THEN
            result shouldBe 0
        }

        @Test
        fun `targetEditorLine - should return the first line for a line past the end`() {
            // GIVEN the branch moved on since the comment was written
            val fileLine = 11
            val lineCount = 10

            // WHEN
            val result = PullRequestCommentFileOpener.targetEditorLine(fileLine = fileLine, lineCount = lineCount)

            // THEN
            result shouldBe 0
        }

        @Test
        fun `targetEditorLine - should return the first line for a non positive line`() {
            // GIVEN
            val zero = 0
            val negative = -5
            val lineCount = 10

            // WHEN
            val zeroResult = PullRequestCommentFileOpener.targetEditorLine(fileLine = zero, lineCount = lineCount)
            val negativeResult = PullRequestCommentFileOpener.targetEditorLine(fileLine = negative, lineCount = lineCount)

            // THEN
            assertSoftly {
                zeroResult shouldBe 0
                negativeResult shouldBe 0
            }
        }
    }
}