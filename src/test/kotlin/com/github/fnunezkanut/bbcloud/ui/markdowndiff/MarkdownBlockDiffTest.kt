package com.github.fnunezkanut.bbcloud.ui.markdowndiff

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class MarkdownBlockDiffTest {
    @Test
    fun `compute - should mark every block unchanged for equal documents`() {
        // GIVEN
        val markdown = "# Title\n\nBody text"

        // WHEN
        val rows = MarkdownBlockDiff.compute(oldMarkdown = markdown, newMarkdown = markdown)

        // THEN
        rows.map { row -> row.kind } shouldContainExactly listOf(MarkdownDiffKind.UNCHANGED, MarkdownDiffKind.UNCHANGED)
    }

    @Test
    fun `compute - should align an added block with an empty left side`() {
        // GIVEN
        val oldMarkdown = "# Title\n\nEnd"
        val newMarkdown = "# Title\n\nNew paragraph\n\nEnd"

        // WHEN
        val rows = MarkdownBlockDiff.compute(oldMarkdown = oldMarkdown, newMarkdown = newMarkdown)

        // THEN
        assertSoftly {
            rows.map { row -> row.kind } shouldContainExactly listOf(
                MarkdownDiffKind.UNCHANGED,
                MarkdownDiffKind.ADDED,
                MarkdownDiffKind.UNCHANGED
            )
            rows[1].left shouldBe null
            rows[1].right?.text shouldBe "New paragraph"
        }
    }

    @Test
    fun `compute - should mark a removed block`() {
        // GIVEN
        val oldMarkdown = "Keep\n\nDrop me entirely\n\nKeep too"
        val newMarkdown = "Keep\n\nKeep too"

        // WHEN
        val rows = MarkdownBlockDiff.compute(oldMarkdown = oldMarkdown, newMarkdown = newMarkdown)

        // THEN
        assertSoftly {
            rows.map { row -> row.kind } shouldContainExactly listOf(
                MarkdownDiffKind.UNCHANGED,
                MarkdownDiffKind.REMOVED,
                MarkdownDiffKind.UNCHANGED
            )
            rows[1].right shouldBe null
        }
    }

    @Test
    fun `compute - should pair a similar edited block as modified with word changes`() {
        // GIVEN
        val oldMarkdown = "The quick brown fox jumps over the lazy dog"
        val newMarkdown = "The quick red fox jumps over the lazy dog"

        // WHEN
        val row = MarkdownBlockDiff.compute(oldMarkdown = oldMarkdown, newMarkdown = newMarkdown).single()

        // THEN
        assertSoftly {
            row.kind shouldBe MarkdownDiffKind.MODIFIED
            row.leftWords.filter { segment -> segment.changed }.map { segment -> segment.text } shouldContainExactly listOf("brown")
            row.rightWords.filter { segment -> segment.changed }.map { segment -> segment.text } shouldContainExactly listOf("red")
        }
    }

    @Test
    fun `compute - should keep unrelated replacements as separate removed and added rows`() {
        // GIVEN
        val oldMarkdown = "Completely different words here"
        val newMarkdown = "Nothing alike at all"

        // WHEN
        val rows = MarkdownBlockDiff.compute(oldMarkdown = oldMarkdown, newMarkdown = newMarkdown)

        // THEN
        rows.map { row -> row.kind } shouldContainExactly listOf(MarkdownDiffKind.REMOVED, MarkdownDiffKind.ADDED)
    }

    @Test
    fun `compute - should show a new file as fully added`() {
        // WHEN
        val rows = MarkdownBlockDiff.compute(oldMarkdown = "", newMarkdown = "# Title\n\nBody")

        // THEN
        rows.map { row -> row.kind } shouldContainExactly listOf(MarkdownDiffKind.ADDED, MarkdownDiffKind.ADDED)
    }

    @Test
    fun `changeRowIndicesOf - should point at the first row of each run of changes`() {
        // GIVEN
        val rows = MarkdownBlockDiff.compute(
            oldMarkdown = "A\n\nB one\n\nC\n\nD",
            newMarkdown = "A\n\nB two\n\nInserted\n\nC\n\nE"
        )

        // WHEN
        val indices = changeRowIndicesOf(rows = rows)

        // THEN
        assertSoftly {
            rows.map { row -> row.kind } shouldContainExactly listOf(
                MarkdownDiffKind.UNCHANGED,
                MarkdownDiffKind.MODIFIED,
                MarkdownDiffKind.ADDED,
                MarkdownDiffKind.UNCHANGED,
                MarkdownDiffKind.REMOVED,
                MarkdownDiffKind.ADDED
            )
            indices shouldContainExactly listOf(1, 4)
        }
    }
}