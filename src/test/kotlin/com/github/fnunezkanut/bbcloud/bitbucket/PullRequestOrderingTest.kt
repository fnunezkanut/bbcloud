package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class PullRequestOrderingTest {
    @Test
    fun `partitionForDisplay - should split open pull requests from merged and declined ones`() {
        // GIVEN
        val pullRequests = listOf(
            pullRequest(id = 1, state = PullRequestState.Merged, updatedOn = "2026-09-30T12:00:00Z"),
            pullRequest(id = 2, state = PullRequestState.Open, updatedOn = "2026-09-01T12:00:00Z"),
            pullRequest(id = 3, state = PullRequestState.Declined, updatedOn = "2026-09-29T12:00:00Z")
        )

        // WHEN
        val listing = PullRequestOrdering.partitionForDisplay(pullRequests)

        // THEN
        listing.open.map { summary -> summary.id } shouldBe listOf(2L)
        listing.closed.map { summary -> summary.id } shouldBe listOf(1L, 3L)
    }

    @Test
    fun `partitionForDisplay - should order each group by last updated newest first`() {
        // GIVEN
        val pullRequests = listOf(
            pullRequest(id = 1, state = PullRequestState.Open, updatedOn = "2026-09-10T12:00:00Z"),
            pullRequest(id = 2, state = PullRequestState.Declined, updatedOn = "2026-09-05T12:00:00Z"),
            pullRequest(id = 3, state = PullRequestState.Open, updatedOn = "2026-09-20T12:00:00Z"),
            pullRequest(id = 4, state = PullRequestState.Merged, updatedOn = "2026-09-15T12:00:00Z")
        )

        // WHEN
        val listing = PullRequestOrdering.partitionForDisplay(pullRequests)

        // THEN
        listing.open.map { summary -> summary.id } shouldBe listOf(3L, 1L)
        listing.closed.map { summary -> summary.id } shouldBe listOf(4L, 2L)
    }

    @Test
    fun `partitionForDisplay - should return two empty groups for empty input`() {
        // GIVEN
        val pullRequests = emptyList<PullRequestSummary>()

        // WHEN
        val listing = PullRequestOrdering.partitionForDisplay(pullRequests)

        // THEN
        listing.open.shouldBeEmpty()
        listing.closed.shouldBeEmpty()
        listing.isEmpty() shouldBe true
    }

    @Test
    fun `partitionForDisplay - should leave the closed group empty when every pull request is open`() {
        // GIVEN
        val pullRequests = listOf(
            pullRequest(id = 1, state = PullRequestState.Open, updatedOn = "2026-09-10T12:00:00Z"),
            pullRequest(id = 2, state = PullRequestState.Open, updatedOn = "2026-09-20T12:00:00Z")
        )

        // WHEN
        val listing = PullRequestOrdering.partitionForDisplay(pullRequests)

        // THEN
        listing.open.map { summary -> summary.id } shouldBe listOf(2L, 1L)
        listing.closed.shouldBeEmpty()
    }

    @Test
    fun `partitionForDisplay - should leave the open group empty when no pull request is open`() {
        // GIVEN
        val pullRequests = listOf(
            pullRequest(id = 1, state = PullRequestState.Merged, updatedOn = "2026-09-10T12:00:00Z"),
            pullRequest(id = 2, state = PullRequestState.Declined, updatedOn = "2026-09-20T12:00:00Z")
        )

        // WHEN
        val listing = PullRequestOrdering.partitionForDisplay(pullRequests)

        // THEN
        listing.open.shouldBeEmpty()
        listing.closed.map { summary -> summary.id } shouldBe listOf(2L, 1L)
    }

    @Test
    fun `partitionForDisplay - should keep every pull request in exactly one group`() {
        // GIVEN
        val pullRequests = listOf(
            pullRequest(id = 1, state = PullRequestState.Open, updatedOn = "2026-09-10T12:00:00Z"),
            pullRequest(id = 2, state = PullRequestState.Merged, updatedOn = "2026-09-20T12:00:00Z"),
            pullRequest(id = 3, state = PullRequestState.Declined, updatedOn = "2026-09-15T12:00:00Z")
        )

        // WHEN
        val listing = PullRequestOrdering.partitionForDisplay(pullRequests)

        // THEN
        (listing.open + listing.closed).map { summary -> summary.id }.sorted() shouldBe listOf(1L, 2L, 3L)
    }

    private fun pullRequest(id: Long, state: PullRequestState, updatedOn: String): PullRequestSummary {
        return PullRequestSummary(
            id = id,
            title = "PR $id",
            authorDisplayName = "John Smith",
            authorAvatarUrl = null,
            createdOn = Instant.parse(updatedOn),
            updatedOn = Instant.parse(updatedOn),
            sourceBranch = "feature/$id",
            destinationBranch = "main",
            commentCount = 0,
            webUrl = "https://bitbucket.org/workspace/repo/pull-requests/$id",
            state = state,
            isDraft = false
        )
    }
}