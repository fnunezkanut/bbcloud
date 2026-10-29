package com.github.fnunezkanut.bbcloud.ui.diff

import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentSide
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestAddCommentGutterTest {
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

    @Test
    fun `PullRequestAddCommentGutterModel - should delegate line commentability to canComment`() {
        // GIVEN
        val model = PullRequestAddCommentGutterModel(
            canComment = { line -> line == 2 },
            onRequestNewComment = {}
        )

        // WHEN
        val state = model.gutterControlsState.value.shouldNotBeNull()

        // THEN
        assertSoftly {
            state.isLineCommentable(2) shouldBe true
            state.isLineCommentable(3) shouldBe false
            model.canCreateComment(2) shouldBe true
        }
    }

    @Test
    fun `PullRequestAddCommentGutterModel - should pass the zero-based line to the request callback`() {
        // GIVEN
        val requested = mutableListOf<Int>()
        val model = PullRequestAddCommentGutterModel(
            canComment = { true },
            onRequestNewComment = { line -> requested.add(line) }
        )

        // WHEN
        model.requestNewComment(4)

        // THEN
        requested shouldContainExactly listOf(4)
    }

    @Test
    fun `PullRequestAddCommentGutterModel - should report no lines with comments`() {
        // GIVEN
        val model = PullRequestAddCommentGutterModel(
            canComment = { true },
            onRequestNewComment = {}
        )

        // WHEN
        val state = model.gutterControlsState.value.shouldNotBeNull()

        // THEN
        state.linesWithComments.shouldBeEmpty()
    }
}