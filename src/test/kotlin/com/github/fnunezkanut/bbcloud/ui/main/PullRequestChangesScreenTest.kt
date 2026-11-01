package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.git.FileLineCount
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestChangesScreenTest {
    @Nested
    inner class ShouldShowLinesBadge {
        @Test
        fun `shouldShowLinesBadge - should be false for a viewed file even with changed lines`() {
            // GIVEN
            val lineCount = FileLineCount(linesAdded = 4, linesRemoved = 1, isBinary = false)

            // WHEN
            val show = shouldShowLinesBadge(lineCount = lineCount, viewed = true)

            // THEN
            show shouldBe false
        }

        @Test
        fun `shouldShowLinesBadge - should be true for a file that is not viewed and has changed lines`() {
            // GIVEN
            val lineCount = FileLineCount(linesAdded = 4, linesRemoved = 0, isBinary = false)

            // WHEN
            val show = shouldShowLinesBadge(lineCount = lineCount, viewed = false)

            // THEN
            show shouldBe true
        }

        @Test
        fun `shouldShowLinesBadge - should be false for a binary file`() {
            // GIVEN
            val lineCount = FileLineCount(linesAdded = 0, linesRemoved = 0, isBinary = true)

            // WHEN
            val show = shouldShowLinesBadge(lineCount = lineCount, viewed = false)

            // THEN
            show shouldBe false
        }

        @Test
        fun `shouldShowLinesBadge - should be false when both counts are zero`() {
            // GIVEN
            val lineCount = FileLineCount(linesAdded = 0, linesRemoved = 0, isBinary = false)

            // WHEN
            val show = shouldShowLinesBadge(lineCount = lineCount, viewed = false)

            // THEN
            show shouldBe false
        }

        @Test
        fun `shouldShowLinesBadge - should be false when the counts are unknown`() {
            // WHEN
            val show = shouldShowLinesBadge(lineCount = null, viewed = false)

            // THEN
            show shouldBe false
        }
    }

    @Nested
    inner class ViewedTooltip {
        @Test
        fun `viewedTooltip - should be plain Viewed when the counts are unknown`() {
            // WHEN
            val tooltip = viewedTooltip(lineCount = null)

            // THEN
            tooltip shouldBe "Viewed"
        }

        @Test
        fun `viewedTooltip - should be plain Viewed for a binary file`() {
            // GIVEN
            val lineCount = FileLineCount(linesAdded = 0, linesRemoved = 0, isBinary = true)

            // WHEN
            val tooltip = viewedTooltip(lineCount = lineCount)

            // THEN
            tooltip shouldBe "Viewed"
        }

        @Test
        fun `viewedTooltip - should be plain Viewed when both counts are zero`() {
            // GIVEN
            val lineCount = FileLineCount(linesAdded = 0, linesRemoved = 0, isBinary = false)

            // WHEN
            val tooltip = viewedTooltip(lineCount = lineCount)

            // THEN
            tooltip shouldBe "Viewed"
        }

        @Test
        fun `viewedTooltip - should name the line counts when the file has changed lines`() {
            // GIVEN
            val lineCount = FileLineCount(linesAdded = 40, linesRemoved = 0, isBinary = false)

            // WHEN
            val tooltip = viewedTooltip(lineCount = lineCount)

            // THEN
            tooltip shouldBe "Viewed (+40 -0)"
        }
    }
}