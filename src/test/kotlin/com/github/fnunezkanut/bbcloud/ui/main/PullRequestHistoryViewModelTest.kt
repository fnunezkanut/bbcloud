package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestActivityFeed
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestHistoryViewModelTest {
    @Test
    fun `state - should be loading before any activity is shown`() {
        // GIVEN
        val viewModel = PullRequestHistoryViewModel(pullRequestId = { 273L })

        // THEN
        viewModel.state.value.loadState shouldBe PullRequestHistoryLoadState.Loading
    }

    @Test
    fun `showActivity - should be in the error state when neither the feed nor the conversation loaded`() {
        // GIVEN
        val viewModel = PullRequestHistoryViewModel(pullRequestId = { 273L })

        // WHEN
        viewModel.showActivity(feed = null, conversation = null)

        // THEN
        assertSoftly {
            viewModel.state.value.loadState shouldBe PullRequestHistoryLoadState.Error
            viewModel.state.value.groups shouldHaveSize 0
        }
    }

    @Test
    fun `showActivity - should be loaded with a partial failure when only the feed loaded`() {
        // GIVEN
        val viewModel = PullRequestHistoryViewModel(pullRequestId = { 273L })
        val feed = PullRequestActivityFeed(entries = emptyList(), isTruncated = false, hasPartialFailure = false)

        // WHEN
        viewModel.showActivity(feed = feed, conversation = null)

        // THEN
        assertSoftly {
            viewModel.state.value.loadState shouldBe PullRequestHistoryLoadState.Loaded
            viewModel.state.value.showPartialFailure shouldBe true
        }
    }

    @Test
    fun `reset - should return to the loading state`() {
        // GIVEN
        val viewModel = PullRequestHistoryViewModel(pullRequestId = { 273L })
        viewModel.showActivity(feed = null, conversation = null)

        // WHEN
        viewModel.reset()

        // THEN
        viewModel.state.value.loadState shouldBe PullRequestHistoryLoadState.Loading
    }
}