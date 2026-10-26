package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.ActivityUser
import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentSide
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class CommentThreadActionVisibilityTest {
    private val date = Instant.parse("2026-09-30T10:00:00Z")
    private val currentUserUuid = "{current-user}"
    private val otherUserUuid = "{other-user}"

    @Test
    fun `metaHeaderActions - should not show resolve for an open PR-level root`() {
        val actions = CommentThreadActionVisibility.metaHeaderActions(
            comment = comment(authorUuid = otherUserUuid, inlinePath = null),
            headlineKind = ConversationMetaHeadlineKind.CommentedOn,
            currentUserUuid = currentUserUuid
        )

        actions shouldBe emptyList()
    }

    @Test
    fun `metaHeaderActions - should show no actions for a reply by another user`() {
        val actions = CommentThreadActionVisibility.metaHeaderActions(
            comment = comment(authorUuid = otherUserUuid, inlinePath = "README.md"),
            headlineKind = ConversationMetaHeadlineKind.RepliedOn,
            currentUserUuid = currentUserUuid
        )

        actions shouldBe emptyList()
    }

    @Test
    fun `metaHeaderActions - should show edit and delete for the owner including replies`() {
        val actions = CommentThreadActionVisibility.metaHeaderActions(
            comment = comment(authorUuid = currentUserUuid, inlinePath = "README.md"),
            headlineKind = ConversationMetaHeadlineKind.RepliedOn,
            currentUserUuid = currentUserUuid
        )

        actions shouldBe listOf(CommentThreadAction.Edit, CommentThreadAction.Delete)
    }

    @Test
    fun `metaHeaderActions - should hide all meta actions without an authenticated user or for deleted comments`() {
        val missingUser = CommentThreadActionVisibility.metaHeaderActions(
            comment = comment(authorUuid = currentUserUuid, inlinePath = "README.md"),
            headlineKind = ConversationMetaHeadlineKind.CommentedOn,
            currentUserUuid = null
        )
        val deleted = CommentThreadActionVisibility.metaHeaderActions(
            comment = comment(authorUuid = currentUserUuid, inlinePath = "README.md", isDeleted = true),
            headlineKind = ConversationMetaHeadlineKind.CommentedOn,
            currentUserUuid = currentUserUuid
        )

        missingUser shouldBe emptyList()
        deleted shouldBe emptyList()
    }

    private fun comment(authorUuid: String, inlinePath: String?, isDeleted: Boolean = false): PullRequestComment {
        return PullRequestComment(
            id = 1,
            parentId = null,
            createdOn = date,
            author = ActivityUser(uuid = authorUuid, displayName = "Author", avatarUrl = null),
            html = "<p>Comment</p>",
            inlinePath = inlinePath,
            inlineLine = inlinePath?.let { 10 },
            inlineSide = inlinePath?.let { InlineCommentSide.AFTER },
            isResolved = false,
            isDeleted = isDeleted,
            isPending = false
        )
    }
}