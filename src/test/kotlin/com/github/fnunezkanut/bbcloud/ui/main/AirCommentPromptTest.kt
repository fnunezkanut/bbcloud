package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentSide
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class AirCommentPromptTest {
    @Nested
    inner class BuildTests {
        @Test
        fun `build - should produce the location and fenced comment`() {
            // GIVEN
            val location = "/work/src/App.kt line: 12"
            val commentText = "Please rename this."

            // WHEN
            val result = AirCommentPrompt.build(location = location, commentText = commentText)

            // THEN
            result shouldBe "/work/src/App.kt line: 12\ncomment left\n\"\"\"\nPlease rename this.\n\"\"\""
        }

        @Test
        fun `build - should keep a multi-line comment verbatim`() {
            // GIVEN
            val commentText = "First line\n\nThird line with `code`"

            // WHEN
            val result = AirCommentPrompt.build(location = "/work/App.kt", commentText = commentText)

            // THEN
            result shouldBe "/work/App.kt\ncomment left\n\"\"\"\nFirst line\n\nThird line with `code`\n\"\"\""
        }

        @Test
        fun `build - should lengthen the fence when the comment contains triple quotes`() {
            // GIVEN
            val commentText = "Quote: \"\"\" inside"

            // WHEN
            val result = AirCommentPrompt.build(location = "/work/App.kt", commentText = commentText)

            // THEN
            result shouldBe "/work/App.kt\ncomment left\n\"\"\"\"\nQuote: \"\"\" inside\n\"\"\"\""
        }
    }

    @Nested
    inner class FenceForTests {
        @Test
        fun `fenceFor - should use triple quotes when the comment has none`() {
            // GIVEN
            val commentText = "plain text"

            // WHEN
            val result = AirCommentPrompt.fenceFor(commentText = commentText)

            // THEN
            result shouldBe "\"\"\""
        }

        @Test
        fun `fenceFor - should add quotes until the fence is absent from the comment`() {
            // GIVEN
            val commentText = "\"\"\" and \"\"\"\""

            // WHEN
            val result = AirCommentPrompt.fenceFor(commentText = commentText)

            // THEN
            result shouldBe "\"\"\"\"\""
        }
    }

    @Nested
    inner class LocationLineTests {
        @Test
        fun `locationLine - should join the path and line label`() {
            // GIVEN
            val absolutePath = "/work/src/App.kt"

            // WHEN
            val result = AirCommentPrompt.locationLine(absolutePath = absolutePath, lineLabel = "12", pullRequestId = 7L)

            // THEN
            result shouldBe "/work/src/App.kt line: 12"
        }

        @Test
        fun `locationLine - should use only the path for a file-level comment`() {
            // GIVEN
            val absolutePath = "/work/src/App.kt"

            // WHEN
            val result = AirCommentPrompt.locationLine(absolutePath = absolutePath, lineLabel = null, pullRequestId = 7L)

            // THEN
            result shouldBe "/work/src/App.kt"
        }

        @Test
        fun `locationLine - should name the pull request for a general comment`() {
            // GIVEN
            val absolutePath: String? = null

            // WHEN
            val result = AirCommentPrompt.locationLine(absolutePath = absolutePath, lineLabel = null, pullRequestId = 123L)

            // THEN
            result shouldBe "Pull request #123 general comment"
        }

        @Test
        fun `locationLine - should omit the id when the pull request is unknown`() {
            // GIVEN
            val absolutePath: String? = null

            // WHEN
            val result = AirCommentPrompt.locationLine(absolutePath = absolutePath, lineLabel = null, pullRequestId = null)

            // THEN
            result shouldBe "Pull request general comment"
        }
    }

    @Nested
    inner class LineLabelTests {
        @Test
        fun `lineLabel - should return the single line`() {
            // WHEN
            val result = AirCommentPrompt.lineLabel(inlineLine = 12, inlineLineEnd = null, side = InlineCommentSide.AFTER)

            // THEN
            result shouldBe "12"
        }

        @Test
        fun `lineLabel - should return the range of a multi-line comment`() {
            // WHEN
            val result = AirCommentPrompt.lineLabel(inlineLine = 10, inlineLineEnd = 12, side = null)

            // THEN
            result shouldBe "10-12"
        }

        @Test
        fun `lineLabel - should mark a comment on the removed side as old side`() {
            // WHEN
            val result = AirCommentPrompt.lineLabel(inlineLine = 4, inlineLineEnd = null, side = InlineCommentSide.BEFORE)

            // THEN
            result shouldBe "4 (old side)"
        }

        @Test
        fun `lineLabel - should return null when the comment has no line`() {
            // WHEN
            val result = AirCommentPrompt.lineLabel(inlineLine = null, inlineLineEnd = null, side = null)

            // THEN
            result shouldBe null
        }

        @Test
        fun `lineLabel - should treat an end line equal to the start as a single line`() {
            // WHEN
            val result = AirCommentPrompt.lineLabel(inlineLine = 9, inlineLineEnd = 9, side = null)

            // THEN
            result shouldBe "9"
        }
    }
}