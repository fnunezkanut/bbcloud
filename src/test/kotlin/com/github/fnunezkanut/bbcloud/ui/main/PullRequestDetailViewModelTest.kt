package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestAction
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestDetails
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class PullRequestDetailViewModelTest {
    @Test
    fun `selectTab - should update the selected tab and notify tab-specific loaders`() {
        // GIVEN
        var conversationSelections = 0
        var changesSelections = 0
        val viewModel = createViewModel(
            onConversationTabSelected = { conversationSelections++ },
            onChangesTabSelected = { changesSelections++ }
        )

        // WHEN
        viewModel.selectTab(PullRequestDetailTab.Conversation)
        viewModel.selectTab(PullRequestDetailTab.Changes)

        // THEN
        assertSoftly {
            viewModel.state.value.selectedTab shouldBe PullRequestDetailTab.Changes
            conversationSelections shouldBe 1
            changesSelections shouldBe 1
        }
    }

    @Test
    fun `resetTab - should return to Overview`() {
        // GIVEN
        val viewModel = createViewModel()
        viewModel.selectTab(PullRequestDetailTab.Changes)

        // WHEN
        viewModel.resetTab()

        // THEN
        viewModel.state.value.selectedTab shouldBe PullRequestDetailTab.Overview
    }

    @Test
    fun `onRefreshClicked - should notify refresh without changing the selected tab`() {
        // GIVEN
        var refreshes = 0
        val viewModel = createViewModel(onRefreshRequested = { refreshes++ })
        viewModel.selectTab(PullRequestDetailTab.Conversation)

        // WHEN
        viewModel.onRefreshClicked()

        // THEN
        assertSoftly {
            refreshes shouldBe 1
            viewModel.state.value.selectedTab shouldBe PullRequestDetailTab.Conversation
        }
    }

    @Test
    fun `showActions - should disable Merge while merge checks are still loading`() {
        // GIVEN
        val viewModel = createViewModel()
        viewModel.bind(pullRequestId = 42L)

        // WHEN
        viewModel.showActions(details())

        // THEN
        assertSoftly {
            viewModel.state.value.actions shouldContain PullRequestAction.Merge
            viewModel.state.value.disabledActions shouldContain PullRequestAction.Merge
            viewModel.state.value.actionTooltips[PullRequestAction.Merge] shouldBe "Checking merge requirements…"
        }
    }

    @Test
    fun `setMergeChecksState - should enable Merge when no check blocks merging`() {
        // GIVEN
        val viewModel = createViewModel()
        viewModel.bind(pullRequestId = 42L)
        viewModel.showActions(details())

        // WHEN
        viewModel.setMergeChecksState(pullRequestId = 42L, state = MergeChecksState.NotBlocked)

        // THEN
        assertSoftly {
            viewModel.state.value.actions shouldContain PullRequestAction.Merge
            viewModel.state.value.disabledActions shouldNotContain PullRequestAction.Merge
            viewModel.state.value.actionTooltips shouldBe emptyMap()
        }
    }

    @Test
    fun `setMergeChecksState - should keep Merge visible and disabled for a blocking check`() {
        // GIVEN
        val viewModel = createViewModel()
        viewModel.bind(pullRequestId = 42L)
        viewModel.showActions(details())

        // WHEN
        viewModel.setMergeChecksState(pullRequestId = 42L, state = MergeChecksState.Blocked)

        // THEN
        assertSoftly {
            viewModel.state.value.actions shouldContain PullRequestAction.Merge
            viewModel.state.value.disabledActions shouldContain PullRequestAction.Merge
            viewModel.state.value.actionTooltips[PullRequestAction.Merge] shouldBe "Merging is blocked by a merge check"
        }
    }

    @Test
    fun `setMergeChecksState - should apply when checks arrive before details`() {
        // GIVEN
        val viewModel = createViewModel()
        viewModel.bind(pullRequestId = 42L)
        viewModel.setMergeChecksState(pullRequestId = 42L, state = MergeChecksState.Blocked)

        // WHEN
        viewModel.showActions(details())

        // THEN
        assertSoftly {
            viewModel.state.value.actions shouldContain PullRequestAction.Merge
            viewModel.state.value.disabledActions shouldContain PullRequestAction.Merge
            viewModel.state.value.actionTooltips[PullRequestAction.Merge] shouldBe "Merging is blocked by a merge check"
        }
    }

    @Test
    fun `setMergeChecksState - should ignore a stale pull request`() {
        // GIVEN
        val viewModel = createViewModel()
        viewModel.bind(pullRequestId = 42L)
        viewModel.showActions(details())
        viewModel.setMergeChecksState(pullRequestId = 42L, state = MergeChecksState.NotBlocked)

        // WHEN
        viewModel.setMergeChecksState(pullRequestId = 99L, state = MergeChecksState.Blocked)

        // THEN
        assertSoftly {
            viewModel.state.value.disabledActions shouldNotContain PullRequestAction.Merge
            viewModel.state.value.actionTooltips shouldBe emptyMap()
        }
    }

    @Test
    fun `bind - should preserve merge state during an in-place refresh`() {
        // GIVEN
        val viewModel = createViewModel()
        viewModel.bind(pullRequestId = 42L)
        viewModel.showActions(details())
        viewModel.setMergeChecksState(pullRequestId = 42L, state = MergeChecksState.Blocked)

        // WHEN
        viewModel.bind(pullRequestId = 42L)
        viewModel.showActions(details())

        // THEN
        assertSoftly {
            viewModel.state.value.actions shouldContain PullRequestAction.Merge
            viewModel.state.value.disabledActions shouldContain PullRequestAction.Merge
            viewModel.state.value.actionTooltips[PullRequestAction.Merge] shouldBe "Merging is blocked by a merge check"
        }
    }

    @Test
    fun `bind - should reset merge state for a new pull request`() {
        // GIVEN
        val viewModel = createViewModel()
        viewModel.bind(pullRequestId = 42L)
        viewModel.showActions(details(id = 42L))
        viewModel.setMergeChecksState(pullRequestId = 42L, state = MergeChecksState.Blocked)

        // WHEN
        viewModel.bind(pullRequestId = 99L)
        viewModel.showActions(details(id = 99L))

        // THEN
        assertSoftly {
            viewModel.state.value.actions shouldContain PullRequestAction.Merge
            viewModel.state.value.disabledActions shouldContain PullRequestAction.Merge
            viewModel.state.value.actionTooltips[PullRequestAction.Merge] shouldBe "Checking merge requirements…"
        }
    }

    private fun createViewModel(
        onRefreshRequested: () -> Unit = {},
        onConversationTabSelected: () -> Unit = {},
        onChangesTabSelected: () -> Unit = {}
    ): PullRequestDetailViewModel {
        return PullRequestDetailViewModel(
            onActionRequested = {},
            onRefreshRequested = onRefreshRequested,
            onConversationTabSelected = onConversationTabSelected,
            onChangesTabSelected = onChangesTabSelected
        )
    }

    private fun details(id: Long = 42L): PullRequestDetails {
        return PullRequestDetails(
            summary = PullRequestSummary(
                id = id,
                title = "Test pull request",
                authorDisplayName = "Author",
                authorAvatarUrl = null,
                createdOn = Instant.parse("2026-09-01T12:00:00Z"),
                updatedOn = Instant.parse("2026-09-01T12:00:00Z"),
                sourceBranch = "feature",
                destinationBranch = "main",
                commentCount = 0,
                webUrl = "https://example.test/pull-request/$id",
                state = PullRequestState.Open,
                isDraft = false
            ),
            isApprovedByCurrentUser = false
        )
    }
}