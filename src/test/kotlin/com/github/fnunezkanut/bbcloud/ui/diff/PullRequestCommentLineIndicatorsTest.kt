package com.github.fnunezkanut.bbcloud.ui.diff

import com.github.fnunezkanut.bbcloud.bitbucket.ActivityUser
import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentAnchor
import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentSide
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class PullRequestCommentLineIndicatorsTest {
    @Test
    fun `indicatorRangesForAnchors - should map a single line`() {
        val anchor = anchor(startLine = 3, endLine = 3)

        indicatorRangesForAnchors(anchors = listOf(anchor)) { it.startLine - 1..it.endLine - 1 } shouldBe listOf(
            CommentLineIndicatorRange(startLine = 2, endLine = 2)
        )
    }

    @Test
    fun `indicatorRangesForAnchors - should map a multi-line range`() {
        val anchor = anchor(startLine = 5, endLine = 7)

        indicatorRangesForAnchors(anchors = listOf(anchor)) { it.startLine - 1..it.endLine - 1 } shouldBe listOf(
            CommentLineIndicatorRange(startLine = 4, endLine = 6)
        )
    }

    @Test
    fun `indicatorRangesForAnchors - should use line zero for file-scoped anchors`() {
        val anchor = anchor(startLine = 1, endLine = 1, isFileScoped = true)

        indicatorRangesForAnchors(anchors = listOf(anchor)) { null } shouldBe listOf(
            CommentLineIndicatorRange(startLine = 0, endLine = 0)
        )
    }

    @Test
    fun `indicatorRangesForAnchors - should use line zero when mapping fails`() {
        val anchor = anchor(startLine = 20, endLine = 21)

        indicatorRangesForAnchors(anchors = listOf(anchor)) { null } shouldBe listOf(
            CommentLineIndicatorRange(startLine = 0, endLine = 0)
        )
    }

    @Test
    fun `indicatorRangesForAnchors - should merge adjacent ranges`() {
        val anchors = listOf(anchor(startLine = 5, endLine = 5), anchor(startLine = 6, endLine = 7))

        indicatorRangesForAnchors(anchors = anchors) { it.startLine - 1..it.endLine - 1 } shouldBe listOf(
            CommentLineIndicatorRange(startLine = 4, endLine = 6)
        )
    }

    private fun anchor(startLine: Int, endLine: Int, isFileScoped: Boolean = false): InlineCommentAnchor {
        val comment = PullRequestComment(
            id = startLine.toLong(),
            parentId = null,
            createdOn = Instant.parse("2026-09-30T10:00:00Z"),
            author = ActivityUser(uuid = "{author}", displayName = "Author", avatarUrl = null),
            html = "<p>Comment</p>",
            inlinePath = "README.md",
            inlineLine = startLine,
            inlineSide = InlineCommentSide.AFTER,
            isResolved = false,
            isDeleted = false,
            isPending = false
        )
        return InlineCommentAnchor(
            startLine = startLine,
            endLine = endLine,
            side = InlineCommentSide.AFTER,
            thread = TimelineEntry.CommentThread(root = comment, replies = emptyList()),
            isFileScoped = isFileScoped
        )
    }
}