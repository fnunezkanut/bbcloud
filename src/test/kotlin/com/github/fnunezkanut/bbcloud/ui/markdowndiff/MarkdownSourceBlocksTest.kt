package com.github.fnunezkanut.bbcloud.ui.markdowndiff

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class MarkdownSourceBlocksTest {
    @Test
    fun `split - should separate blocks on blank lines and keep headings on their own`() {
        // GIVEN
        val markdown = "# Title\nFirst paragraph\nstill first\n\nSecond paragraph\n## Section\nAfter heading"

        // WHEN
        val blocks = MarkdownSourceBlocks.split(markdown = markdown)

        // THEN
        blocks.map { block -> block.text } shouldContainExactly listOf(
            "# Title",
            "First paragraph\nstill first",
            "Second paragraph",
            "## Section",
            "After heading"
        )
    }

    @Test
    fun `split - should keep a fenced code block with blank lines as one block`() {
        // GIVEN
        val markdown = "Intro\n\n```kotlin\nval a = 1\n\nval b = 2\n```\n\nOutro"

        // WHEN
        val blocks = MarkdownSourceBlocks.split(markdown = markdown)

        // THEN
        assertSoftly {
            blocks.map { block -> block.text } shouldContainExactly listOf(
                "Intro",
                "```kotlin\nval a = 1\n\nval b = 2\n```",
                "Outro"
            )
            blocks.map { block -> block.startLine } shouldContainExactly listOf(0, 2, 8)
        }
    }

    @Test
    fun `split - should not treat a heading marker inside a fence as a heading`() {
        // GIVEN
        val markdown = "~~~\n# not a heading\n~~~"

        // WHEN
        val blocks = MarkdownSourceBlocks.split(markdown = markdown)

        // THEN
        blocks.map { block -> block.text } shouldContainExactly listOf("~~~\n# not a heading\n~~~")
    }

    @Test
    fun `split - should keep an indented continuation after a blank line in the list block`() {
        // GIVEN
        val markdown = "- item one\n\n  more about one\n- item two\n\nParagraph"

        // WHEN
        val blocks = MarkdownSourceBlocks.split(markdown = markdown)

        // THEN
        blocks.map { block -> block.text } shouldContainExactly listOf(
            "- item one\n\n  more about one\n- item two",
            "Paragraph"
        )
    }

    @Test
    fun `split - should keep front matter and a table as single blocks`() {
        // GIVEN
        val markdown = "---\ntitle: x\n---\n| a | b |\n| - | - |\n| 1 | 2 |"

        // WHEN
        val blocks = MarkdownSourceBlocks.split(markdown = markdown)

        // THEN
        blocks.map { block -> block.text } shouldContainExactly listOf(
            "---\ntitle: x\n---",
            "| a | b |\n| - | - |\n| 1 | 2 |"
        )
    }

    @Test
    fun `normalizedText - should ignore trailing whitespace and CRLF line endings`() {
        // GIVEN
        val crlf = MarkdownSourceBlocks.split(markdown = "line one  \r\nline two\r\n").single()
        val lf = MarkdownSourceBlocks.split(markdown = "line one\nline two\n").single()

        // WHEN
        val equal = crlf.normalizedText == lf.normalizedText

        // THEN
        equal shouldBe true
    }

    @Test
    fun `split - should return no blocks for blank input`() {
        // WHEN
        val blocks = MarkdownSourceBlocks.split(markdown = "\n\n  \n")

        // THEN
        blocks shouldBe emptyList()
    }
}