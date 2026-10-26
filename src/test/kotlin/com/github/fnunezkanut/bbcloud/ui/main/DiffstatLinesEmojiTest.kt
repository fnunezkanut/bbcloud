package com.github.fnunezkanut.bbcloud.ui.main

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class DiffstatLinesEmojiTest {
    @Test
    fun `forLinesAdded - should select emoji at each threshold`() {
        assertSoftly {
            DiffstatLinesEmoji.forLinesAdded(0) shouldBe "🙂"
            DiffstatLinesEmoji.forLinesAdded(99) shouldBe "🙂"
            DiffstatLinesEmoji.forLinesAdded(100) shouldBe "😐"
            DiffstatLinesEmoji.forLinesAdded(499) shouldBe "😐"
            DiffstatLinesEmoji.forLinesAdded(500) shouldBe "🙁"
            DiffstatLinesEmoji.forLinesAdded(999) shouldBe "🙁"
            DiffstatLinesEmoji.forLinesAdded(1000) shouldBe "😠"
            DiffstatLinesEmoji.forLinesAdded(5000) shouldBe "😠"
        }
    }
}