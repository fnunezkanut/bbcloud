package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.git.BranchDiffLineTotals
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class HeaderLinesChangedTest {
    @Test
    fun `fromTotals - should map normal totals to exact added and removed texts`() {
        // GIVEN
        val totals = BranchDiffLineTotals(linesAdded = 12, linesRemoved = 3, hasBinaryFiles = false)

        // WHEN
        val lines = HeaderLinesChanged.fromTotals(totals)

        // THEN
        assertSoftly(lines) {
            emoji shouldBe DiffstatLinesEmoji.forLinesAdded(12)
            addedText shouldBe "+12"
            removedText shouldBe "-3"
            tooltip shouldBe "12 lines added, 3 lines removed"
        }
    }

    @Test
    fun `fromTotals - should map binary-file totals to approximate texts`() {
        // GIVEN
        val totals = BranchDiffLineTotals(linesAdded = 40, linesRemoved = 5, hasBinaryFiles = true)

        // WHEN
        val lines = HeaderLinesChanged.fromTotals(totals)

        // THEN
        assertSoftly(lines) {
            emoji shouldBe DiffstatLinesEmoji.forLinesAdded(40)
            addedText shouldBe "+40+"
            removedText shouldBe "-5+"
            tooltip shouldBe "At least 40 lines added, 5 lines removed (binary files excluded)"
        }
    }
}