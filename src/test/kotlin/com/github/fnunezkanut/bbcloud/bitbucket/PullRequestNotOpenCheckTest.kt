package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class PullRequestNotOpenCheckTest {
    @Test
    fun `check - takes over only pull requests that are not Open`() {
        // GIVEN
        val open = summary(state = PullRequestState.Open, isDraft = false)
        val draft = summary(state = PullRequestState.Open, isDraft = true)
        val merged = summary(state = PullRequestState.Merged, isDraft = false)
        val declined = summary(state = PullRequestState.Declined, isDraft = false)

        // WHEN
        val openResult = PullRequestNotOpenCheck.check(open)
        val draftResult = PullRequestNotOpenCheck.check(draft)
        val mergedResult = PullRequestNotOpenCheck.check(merged)
        val declinedResult = PullRequestNotOpenCheck.check(declined)

        // THEN
        assertSoftly {
            openResult shouldBe false
            draftResult shouldBe false
            mergedResult shouldBe true
            declinedResult shouldBe true
        }
    }

    private fun summary(state: PullRequestState, isDraft: Boolean): PullRequestSummary {
        val timestamp = Instant.parse("2026-09-01T12:00:00Z")
        return PullRequestSummary(
            id = 42L,
            title = "PR 42",
            authorDisplayName = "John Smith",
            authorAvatarUrl = null,
            createdOn = timestamp,
            updatedOn = timestamp,
            sourceBranch = "feature/42",
            destinationBranch = "main",
            commentCount = 0,
            webUrl = "https://bitbucket.org/acme/my-repo/pull-requests/42",
            state = state,
            isDraft = isDraft
        )
    }
}