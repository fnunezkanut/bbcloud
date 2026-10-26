package com.github.fnunezkanut.bbcloud.ui.pullrequest.session

import com.github.fnunezkanut.bbcloud.bitbucket.ActivityUser
import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentSide
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class PullRequestFileCommentCountsLoadStateTest {
    private val author = ActivityUser(uuid = "{author}", displayName = "Author", avatarUrl = null)

    @Test
    fun `comments before files - should retain counts for the displayed pull request`() {
        // GIVEN
        val state = PullRequestFileCommentCountsLoadState()
        state.bind(pullRequestId = 1)
        state.request(pullRequestId = 1, force = false)

        // WHEN
        val completion = state.complete(
            pullRequestId = 1,
            comments = listOf(comment(id = 1, path = "src/Main.kt"))
        )
        val fileCompletion = state.filesChangedCompleted(pullRequestId = 1)

        // THEN
        assertSoftly {
            completion.isForDisplayedPullRequest shouldBe true
            completion.shouldReload shouldBe false
            fileCompletion?.countForChangePath(projectBasePath = null, changeRelativePath = "src/Main.kt") shouldBe 1
            state.countsForDisplayedPullRequest()
                ?.countForChangePath(projectBasePath = null, changeRelativePath = "src/Main.kt") shouldBe 1
        }
    }

    @Test
    fun `files before comments - should expose no count until comments complete`() {
        // GIVEN
        val state = PullRequestFileCommentCountsLoadState()
        state.bind(pullRequestId = 1)

        // WHEN
        val beforeComments = state.countsForDisplayedPullRequest()
        state.request(pullRequestId = 1, force = false)
        val beforeCommentCompletion = state.filesChangedCompleted(pullRequestId = 1)
        val afterComments = state.complete(
            pullRequestId = 1,
            comments = listOf(comment(id = 1, path = "src/Main.kt"))
        )
        val afterCommentCompletion = state.filesChangedCompleted(pullRequestId = 1)

        // THEN
        assertSoftly {
            beforeComments shouldBe null
            beforeCommentCompletion shouldBe null
            afterComments.counts?.countForChangePath(projectBasePath = null, changeRelativePath = "src/Main.kt") shouldBe 1
            afterCommentCompletion?.countForChangePath(projectBasePath = null, changeRelativePath = "src/Main.kt") shouldBe 1
        }
    }

    @Test
    fun `late completion from a previous pull request - should not replace displayed counts`() {
        // GIVEN
        val state = PullRequestFileCommentCountsLoadState()
        state.bind(pullRequestId = 1)
        state.request(pullRequestId = 1, force = false)
        state.bind(pullRequestId = 2)
        state.request(pullRequestId = 2, force = false)

        // WHEN
        val oldCompletion = state.complete(
            pullRequestId = 1,
            comments = listOf(comment(id = 1, path = "old.kt"))
        )
        val currentCompletion = state.complete(
            pullRequestId = 2,
            comments = listOf(comment(id = 2, path = "current.kt"))
        )

        // THEN
        assertSoftly {
            oldCompletion.isForDisplayedPullRequest shouldBe false
            oldCompletion.counts shouldBe null
            currentCompletion.isForDisplayedPullRequest shouldBe true
            state.countsForDisplayedPullRequest()
                ?.countForChangePath(projectBasePath = null, changeRelativePath = "current.kt") shouldBe 1
            state.countsForDisplayedPullRequest()
                ?.countForChangePath(projectBasePath = null, changeRelativePath = "old.kt") shouldBe 0
        }
    }

    @Test
    fun `forced refresh during an in-flight request - should queue one reload`() {
        // GIVEN
        val state = PullRequestFileCommentCountsLoadState()
        state.bind(pullRequestId = 1)
        val initialRequest = state.request(pullRequestId = 1, force = false)

        // WHEN
        val forcedRequest = state.request(pullRequestId = 1, force = true)
        val firstCompletion = state.complete(
            pullRequestId = 1,
            comments = listOf(comment(id = 1, path = "src/Main.kt"))
        )
        val reloadRequest = state.request(pullRequestId = 1, force = true)

        // THEN
        assertSoftly {
            initialRequest shouldBe PullRequestFileCommentCountsLoadState.Request.Started
            forcedRequest shouldBe PullRequestFileCommentCountsLoadState.Request.AlreadyInProgress
            firstCompletion.shouldReload shouldBe true
            reloadRequest shouldBe PullRequestFileCommentCountsLoadState.Request.Started
        }
    }

    private fun comment(id: Long, path: String): PullRequestComment {
        return PullRequestComment(
            id = id,
            parentId = null,
            createdOn = Instant.parse("2026-09-30T10:00:00Z"),
            author = author,
            html = "<p>comment $id</p>",
            inlinePath = path,
            inlineLine = null,
            inlineSide = InlineCommentSide.AFTER,
            isResolved = false,
            isDeleted = false,
            isPending = false
        )
    }
}