package com.github.fnunezkanut.bbcloud.ui.diff

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestReviewCommentTargetTest {
    @Test
    fun `canOfferPullRequestLineComment - should allow a reviewable work-tree file with a loaded index`() {
        // WHEN
        val result = PullRequestReviewCommentTarget.canOfferPullRequestLineComment(
            isPullRequestVirtualFile = false,
            hasFile = true,
            isReviewable = true,
            hasInlineIndex = true
        )

        // THEN
        result shouldBe true
    }

    @Test
    fun `canOfferPullRequestLineComment - should refuse the pull request summary virtual file`() {
        // WHEN
        val result = PullRequestReviewCommentTarget.canOfferPullRequestLineComment(
            isPullRequestVirtualFile = true,
            hasFile = true,
            isReviewable = true,
            hasInlineIndex = true
        )

        // THEN
        result shouldBe false
    }

    @Test
    fun `canOfferPullRequestLineComment - should refuse when no index is loaded or the pull request is not reviewable`() {
        assertSoftly {
            // index not loaded yet (decision 4b)
            PullRequestReviewCommentTarget.canOfferPullRequestLineComment(
                isPullRequestVirtualFile = false,
                hasFile = true,
                isReviewable = true,
                hasInlineIndex = false
            ) shouldBe false

            // pull request not reviewable (decision 6a)
            PullRequestReviewCommentTarget.canOfferPullRequestLineComment(
                isPullRequestVirtualFile = false,
                hasFile = true,
                isReviewable = false,
                hasInlineIndex = true
            ) shouldBe false

            // no editor file
            PullRequestReviewCommentTarget.canOfferPullRequestLineComment(
                isPullRequestVirtualFile = false,
                hasFile = false,
                isReviewable = true,
                hasInlineIndex = true
            ) shouldBe false
        }
    }
}