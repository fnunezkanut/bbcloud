package com.github.fnunezkanut.bbcloud.project

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestDetails
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class PullRequestOpenCheckTest {
    private fun details(state: PullRequestState): PullRequestDetails {
        val summary = PullRequestSummary(
            id = 42L,
            title = "PR 42",
            authorDisplayName = "John Smith",
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
        return PullRequestDetails(summary = summary, isApprovedByCurrentUser = false)
    }

    @Test
    fun `evaluate - open details gives StillOpen`() {
        // GIVEN
        val open = details(state = PullRequestState.Open)

        // WHEN
        val result = PullRequestOpenCheck.evaluate(details = open)

        // THEN
        val stillOpen = result.shouldBeInstanceOf<PullRequestOpenCheckResult.StillOpen>()
        stillOpen.details shouldBe open
    }

    @Test
    fun `evaluate - merged details gives NotOpen with merged state`() {
        // GIVEN
        val merged = details(state = PullRequestState.Merged)

        // WHEN
        val result = PullRequestOpenCheck.evaluate(details = merged)

        // THEN
        val notOpen = result.shouldBeInstanceOf<PullRequestOpenCheckResult.NotOpen>()
        notOpen.state shouldBe PullRequestState.Merged
    }

    @Test
    fun `evaluate - declined details gives NotOpen with declined state`() {
        // GIVEN
        val declined = details(state = PullRequestState.Declined)

        // WHEN
        val result = PullRequestOpenCheck.evaluate(details = declined)

        // THEN
        val notOpen = result.shouldBeInstanceOf<PullRequestOpenCheckResult.NotOpen>()
        notOpen.state shouldBe PullRequestState.Declined
    }

    @Test
    fun `evaluate - null details gives NotFound`() {
        // GIVEN
        val missing: PullRequestDetails? = null

        // WHEN
        val result = PullRequestOpenCheck.evaluate(details = missing)

        // THEN
        assertSoftly {
            result shouldBe PullRequestOpenCheckResult.NotFound
        }
    }
}