package com.github.fnunezkanut.bbcloud.ui.pullrequests

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestsUiStateTest {
    private val open = PullRequestListingSource.ProjectOpen
    private val resolved = PullRequestListingSource.ProjectResolved

    private fun loaded(count: Int): PullRequestSourceState.Loaded {
        val entries = (1..count).map { id ->
            listEntry(id = id.toLong(), source = PullRequestListingSource.ProjectOpen)
        }
        return PullRequestSourceState.Loaded(entries = entries, isTruncated = false)
    }

    @Test
    fun `derivePullRequestsCard - should be Hidden when every source is idle`() {
        val sources = mapOf(open to PullRequestSourceState.Idle, resolved to PullRequestSourceState.Idle)
        derivePullRequestsCard(sources = sources, totalCount = 0, visibleCount = 0) shouldBe PullRequestsCard.Hidden
    }

    @Test
    fun `derivePullRequestsCard - should be Loading when a source is loading and none loaded`() {
        val sources = mapOf(open to PullRequestSourceState.Loading, resolved to PullRequestSourceState.Idle)
        derivePullRequestsCard(sources = sources, totalCount = 0, visibleCount = 0) shouldBe PullRequestsCard.Loading
    }

    @Test
    fun `derivePullRequestsCard - should be Listing when a source is loaded and rows are visible`() {
        val sources = mapOf(open to loaded(count = 2), resolved to PullRequestSourceState.Idle)
        derivePullRequestsCard(sources = sources, totalCount = 2, visibleCount = 2) shouldBe PullRequestsCard.Listing
    }

    @Test
    fun `derivePullRequestsCard - should be Empty when loaded with no rows and no source loading`() {
        val sources = mapOf(open to loaded(count = 0), resolved to loaded(count = 0))
        derivePullRequestsCard(sources = sources, totalCount = 0, visibleCount = 0) shouldBe PullRequestsCard.Empty
    }

    @Test
    fun `derivePullRequestsCard - should be FilteredEmpty when rows exist but none are visible`() {
        val sources = mapOf(open to loaded(count = 3), resolved to PullRequestSourceState.Idle)
        derivePullRequestsCard(sources = sources, totalCount = 3, visibleCount = 0) shouldBe PullRequestsCard.FilteredEmpty
    }

    @Test
    fun `derivePullRequestsCard - should be Error when every non-idle source failed`() {
        val sources = mapOf(open to PullRequestSourceState.Failed, resolved to PullRequestSourceState.Failed)
        derivePullRequestsCard(sources = sources, totalCount = 0, visibleCount = 0) shouldBe PullRequestsCard.Error
    }

    @Test
    fun `derivePullRequestsCard - should still be Loading when one source loaded empty and another is loading`() {
        val sources = mapOf(open to loaded(count = 0), resolved to PullRequestSourceState.Loading)
        derivePullRequestsCard(sources = sources, totalCount = 0, visibleCount = 0) shouldBe PullRequestsCard.Loading
    }

    @Test
    fun `derivePullRequestsCard - should be Listing when one source loaded with rows and another failed`() {
        val sources = mapOf(open to loaded(count = 2), resolved to PullRequestSourceState.Failed)
        derivePullRequestsCard(sources = sources, totalCount = 2, visibleCount = 2) shouldBe PullRequestsCard.Listing
    }
}