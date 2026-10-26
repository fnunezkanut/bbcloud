package com.github.fnunezkanut.bbcloud.ui.diff

import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentSide
import com.intellij.ui.scale.JBUIScale
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestAddCommentGutterTest {
    @Test
    fun `addCommentGutterReserveWidthPx - should scale the icon width`() {
        // WHEN
        val result = addCommentGutterReserveWidthPx()

        // THEN
        result shouldBe JBUIScale.scale(ADD_COMMENT_GUTTER_ICON_WIDTH)
    }

    @Test
    fun `shouldOfferAddCommentGutter - should offer the icon only on kept editor sides`() {
        assertSoftly {
            // normal file editor
            shouldOfferAddCommentGutter(side = InlineCommentSide.BEFORE, isFileEditor = true) shouldBe true
            shouldOfferAddCommentGutter(side = InlineCommentSide.AFTER, isFileEditor = true) shouldBe true

            // side-by-side diff
            shouldOfferAddCommentGutter(side = InlineCommentSide.AFTER, isFileEditor = false) shouldBe true
            shouldOfferAddCommentGutter(side = InlineCommentSide.BEFORE, isFileEditor = false) shouldBe false
        }
    }
}