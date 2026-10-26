package com.github.fnunezkanut.bbcloud.review

import com.github.fnunezkanut.bbcloud.bitbucket.ActivityUser
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestInlineCommentIndex
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class PullRequestCommentDecorationContextTest {
    @Test
    fun `resolvePullRequestId - should prefer review state then open tab then worktree binding`() {
        PullRequestCommentDecorationContext.resolvePullRequestId(
            activeReviewPullRequestId = 12L,
            openPullRequestTabId = 34L,
            worktreeBindingPullRequestId = 56L
        ) shouldBe 12L

        PullRequestCommentDecorationContext.resolvePullRequestId(
            activeReviewPullRequestId = null,
            openPullRequestTabId = 34L,
            worktreeBindingPullRequestId = 56L
        ) shouldBe 34L

        PullRequestCommentDecorationContext.resolvePullRequestId(
            activeReviewPullRequestId = null,
            openPullRequestTabId = null,
            worktreeBindingPullRequestId = 56L
        ) shouldBe 56L
    }

    @Test
    fun `resolveInlineCommentIndex - should rebuild from worktree comments when review index was cleared`() {
        val comments = listOf(fileComment(id = 1L, path = "test.txt"))
        val index = PullRequestCommentDecorationContext.resolveInlineCommentIndex(
            pullRequestId = 9L,
            reviewStatePullRequestId = null,
            reviewStateIndex = null,
            bindingPullRequestId = 9L,
            comments = comments
        )

        index.shouldNotBeNull()
        index.anchorsForFileView(projectBasePath = "/repo", fileRelativePath = "test.txt").single().thread.root.id shouldBe 1L
    }

    @Test
    fun `resolveInlineCommentIndex - should keep review state index when ids match`() {
        val fromState = PullRequestInlineCommentIndex.from(
            comments = listOf(fileComment(id = 2L, path = "README.md"))
        )
        val index = PullRequestCommentDecorationContext.resolveInlineCommentIndex(
            pullRequestId = 9L,
            reviewStatePullRequestId = 9L,
            reviewStateIndex = fromState,
            bindingPullRequestId = 9L,
            comments = emptyList()
        )

        index shouldBe fromState
    }

    @Test
    fun `resolveInlineCommentIndex - should refuse another pull request binding`() {
        PullRequestCommentDecorationContext.resolveInlineCommentIndex(
            pullRequestId = 9L,
            reviewStatePullRequestId = null,
            reviewStateIndex = null,
            bindingPullRequestId = 10L,
            comments = listOf(fileComment(id = 1L, path = "test.txt"))
        ).shouldBeNull()
    }

    private fun fileComment(id: Long, path: String): PullRequestComment {
        return PullRequestComment(
            id = id,
            parentId = null,
            createdOn = Instant.parse("2026-09-30T10:00:00Z"),
            author = ActivityUser(uuid = "{author}", displayName = "Author", avatarUrl = null),
            html = "<p>Comment</p>",
            inlinePath = path,
            inlineLine = null,
            inlineSide = null,
            isResolved = false,
            isDeleted = false,
            isPending = false
        )
    }
}