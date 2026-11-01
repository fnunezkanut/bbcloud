package com.github.fnunezkanut.bbcloud.ui.otherprojects

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestReviewer
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerStatus
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewingPullRequest
import com.github.fnunezkanut.bbcloud.ui.pullrequests.ReviewerStack
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Clock

@Tag("unit")
class WorkspacePullRequestRowTest {
    @Test
    fun `buildWorkspacePullRequestRow - should flag the row as approved by me when requested`() {
        // GIVEN an open pull request that the current user approved
        val pullRequest = reviewingPullRequest()

        // WHEN the row is built with the approval flag
        val row = buildWorkspacePullRequestRow(
            pullRequest = pullRequest,
            organizationUrl = null,
            authorAvatarUrl = null,
            repoAvatarUrl = null,
            isApprovedByMe = true
        )

        // THEN the row carries the approval
        row.isApprovedByMe shouldBe true
    }

    @Test
    fun `buildWorkspacePullRequestRow - should not flag the row as approved by me by default`() {
        // GIVEN an open pull request
        val pullRequest = reviewingPullRequest()

        // WHEN the row is built without the approval flag
        val row = buildWorkspacePullRequestRow(
            pullRequest = pullRequest,
            organizationUrl = null,
            authorAvatarUrl = null,
            repoAvatarUrl = null
        )

        // THEN the row is not flagged
        row.isApprovedByMe shouldBe false
    }

    @Test
    fun `buildWorkspacePullRequestRow - should build a reviewer stack for an open pull request`() {
        // GIVEN an open pull request with three reviewers
        val reviewers = listOf(reviewer(uuid = "{a}"), reviewer(uuid = "{b}"), reviewer(uuid = "{c}"))
        val pullRequest = reviewingPullRequest(reviewers = reviewers)

        // WHEN
        val row = buildWorkspacePullRequestRow(
            pullRequest = pullRequest,
            organizationUrl = null,
            authorAvatarUrl = null,
            repoAvatarUrl = null
        )

        // THEN the stack shows two and folds the third
        assertSoftly(row.reviewerStack) {
            visible.map { it.uuid } shouldBe listOf("{a}", "{b}")
            hiddenCount shouldBe 1
        }
    }

    @Test
    fun `buildWorkspacePullRequestRow - should yield an empty stack when there are no reviewers`() {
        // GIVEN an open pull request with no reviewers
        val pullRequest = reviewingPullRequest()

        // WHEN
        val row = buildWorkspacePullRequestRow(
            pullRequest = pullRequest,
            organizationUrl = null,
            authorAvatarUrl = null,
            repoAvatarUrl = null
        )

        // THEN
        row.reviewerStack shouldBe ReviewerStack.EMPTY
    }

    @Test
    fun `buildWorkspacePullRequestRow - should drop the reviewer stack for a merged pull request`() {
        // GIVEN a merged pull request that still carries reviewers
        val pullRequest = reviewingPullRequest(state = PullRequestState.Merged, reviewers = listOf(reviewer(uuid = "{a}")))

        // WHEN
        val row = buildWorkspacePullRequestRow(
            pullRequest = pullRequest,
            organizationUrl = null,
            authorAvatarUrl = null,
            repoAvatarUrl = null
        )

        // THEN the stack is empty, matching the Approved badge rule
        row.reviewerStack shouldBe ReviewerStack.EMPTY
    }

    @Test
    fun `buildWorkspacePullRequestRow - should drop the reviewer stack for a declined pull request`() {
        // GIVEN a declined pull request that still carries reviewers
        val pullRequest = reviewingPullRequest(state = PullRequestState.Declined, reviewers = listOf(reviewer(uuid = "{a}")))

        // WHEN
        val row = buildWorkspacePullRequestRow(
            pullRequest = pullRequest,
            organizationUrl = null,
            authorAvatarUrl = null,
            repoAvatarUrl = null
        )

        // THEN the stack is empty
        row.reviewerStack shouldBe ReviewerStack.EMPTY
    }

    private fun reviewer(uuid: String): PullRequestReviewer {
        return PullRequestReviewer(uuid = uuid, displayName = "Reviewer $uuid", avatarUrl = null, status = ReviewerStatus.None)
    }

    private fun reviewingPullRequest(
        state: PullRequestState = PullRequestState.Open,
        reviewers: List<PullRequestReviewer> = emptyList()
    ): ReviewingPullRequest {
        val now = Clock.System.now()
        val summary = PullRequestSummary(
            id = 5,
            title = "Approved change",
            authorDisplayName = "Jane Smith",
            authorAvatarUrl = null,
            createdOn = now,
            updatedOn = now,
            sourceBranch = "feature/approved",
            destinationBranch = "main",
            commentCount = 0,
            webUrl = "https://bitbucket.org/acme/repo/pull-requests/5",
            state = state,
            isDraft = false,
            reviewers = reviewers
        )
        return ReviewingPullRequest(summary = summary, workspace = "acme", repoSlug = "repo")
    }
}