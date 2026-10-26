package com.github.fnunezkanut.bbcloud.project

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestDetails
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class WorktreePruneDecisionTest {
    @Test
    fun `actionFor - maps pull request outcomes to safe actions`() {
        // GIVEN
        val outcomes = listOf(
            PullRequestOpenCheckResult.StillOpen(details = details(state = PullRequestState.Open)),
            PullRequestOpenCheckResult.NotOpen(state = PullRequestState.Merged),
            PullRequestOpenCheckResult.NotOpen(state = PullRequestState.Declined),
            PullRequestOpenCheckResult.NotFound,
            PullRequestOpenCheckResult.Failed(cause = IllegalStateException("lookup failed"))
        )

        // WHEN
        val actions = outcomes.map { result -> WorktreePruneDecision.actionFor(result) }

        // THEN
        assertSoftly {
            actions shouldBe listOf(
                WorktreePruneAction.Keep,
                WorktreePruneAction.Remove,
                WorktreePruneAction.Remove,
                WorktreePruneAction.Remove,
                WorktreePruneAction.Skip
            )
        }
    }

    private fun details(state: PullRequestState): PullRequestDetails {
        val summary = PullRequestSummary(
            id = 42L,
            title = "PR 42",
            authorDisplayName = "Test author",
            authorAvatarUrl = null,
            createdOn = Instant.parse("2026-09-01T12:00:00Z"),
            updatedOn = Instant.parse("2026-09-01T12:00:00Z"),
            sourceBranch = "feature/42",
            destinationBranch = "main",
            commentCount = 0,
            webUrl = "https://bitbucket.org/acme/my-repo/pull-requests/42",
            state = state,
            isDraft = false
        )
        return PullRequestDetails(
            summary = summary,
            isApprovedByCurrentUser = false
        )
    }
}