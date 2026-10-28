package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.ItemCount
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestAction
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestDetails
import com.intellij.openapi.diagnostic.logger

/** The three tabs of the pull-request detail view. */
enum class PullRequestDetailTab {
    Overview,
    Conversation,
    Changes
}

/** The merge-check loading result used to gate the Merge action. */
enum class MergeChecksState {
    /** Merge checks have not loaded yet, so Merge is disabled while they are being checked. */
    Unknown,

    /** At least one merge check blocks merging, so Merge is disabled. */
    Blocked,

    /** No merge check blocks merging, or the merge-check load failed or returned no checks. */
    NotBlocked
}

/**
 * The immutable state of the pull-request detail view: the selected tab, the action buttons, the refresh button, and
 * the counters of the Conversation and Changes tabs.
 *
 * An action is effectively enabled only when [actionsEnabled] is true and it is not in [disabledActions].
 */
data class PullRequestDetailUiState(
    val selectedTab: PullRequestDetailTab = PullRequestDetailTab.Overview,
    val actions: List<PullRequestAction> = emptyList(),
    val actionsEnabled: Boolean = true,
    val disabledActions: Set<PullRequestAction> = emptySet(),
    val actionTooltips: Map<PullRequestAction, String> = emptyMap(),
    val refreshEnabled: Boolean = true,
    val conversationCount: ItemCount? = null,
    val changesCount: ItemCount? = null
)

/**
 * Owns the state of the pull-request detail view: the selected tab, the action buttons and their enabled state, the
 * refresh button, and the counters of the Conversation and Changes tabs.
 *
 * The id of the pull request the view currently reflects is tracked so stale responses for a different pull request
 * are ignored.
 */
class PullRequestDetailViewModel(
    private val onActionRequested: (PullRequestAction) -> Unit,
    private val onRefreshRequested: () -> Unit,
    private val onConversationTabSelected: () -> Unit,
    private val onChangesTabSelected: () -> Unit
) {
    val state: MutableState<PullRequestDetailUiState> = mutableStateOf(PullRequestDetailUiState())

    private var displayedPullRequestId: Long? = null
    private var mergeChecksState: MergeChecksState = MergeChecksState.Unknown
    private var displayedDetails: PullRequestDetails? = null

    /** Records which pull request the view reflects so [showActions] can reject stale responses. */
    fun bind(pullRequestId: Long) {
        val pullRequestChanged = pullRequestId != displayedPullRequestId
        if (pullRequestChanged) {
            // Preserve the last merge-check signal during an in-place refresh of the same PR to avoid button flicker;
            // this follows the tab-preserving editor refresh behavior.
            mergeChecksState = MergeChecksState.Unknown
            displayedDetails = null
        }
        displayedPullRequestId = pullRequestId
        state.value = state.value.copy(
            actions = emptyList(),
            actionsEnabled = true,
            disabledActions = emptySet(),
            actionTooltips = emptyMap()
        )
    }

    /** Shows the actions offered by a pull request, ignoring the ones of a pull request that is no longer displayed. */
    fun showActions(details: PullRequestDetails) {
        if (details.summary.id != displayedPullRequestId) {
            LOG.debug("Ignoring actions for pull request #${details.summary.id}; the view shows #$displayedPullRequestId")
            return
        }
        displayedDetails = details
        recomputeActions()
    }

    /** Applies the merge-check result only when it belongs to the pull request currently displayed. */
    fun setMergeChecksState(pullRequestId: Long, state: MergeChecksState) {
        if (pullRequestId != displayedPullRequestId) {
            LOG.debug("Ignoring merge checks for pull request #$pullRequestId; the view shows #$displayedPullRequestId")
            return
        }
        if (mergeChecksState != state) {
            when (state) {
                MergeChecksState.Blocked -> LOG.info("Merge checks block merging on pull request #$pullRequestId")
                MergeChecksState.NotBlocked -> LOG.info("Merge checks no longer block merging on pull request #$pullRequestId")
                MergeChecksState.Unknown -> LOG.info("Merge checks are still loading for pull request #$pullRequestId")
            }
        }
        mergeChecksState = state
        recomputeActions()
    }

    private fun recomputeActions() {
        val details = displayedDetails ?: return
        val actions = PullRequestActionVisibility.visibleActions(
            state = details.summary.state,
            isDraft = details.summary.isDraft,
            isApprovedByCurrentUser = details.isApprovedByCurrentUser,
            isChangesRequestedByCurrentUser = details.isChangesRequestedByCurrentUser
        )
        val mergeTooltip = when {
            PullRequestAction.Merge !in actions -> null
            mergeChecksState == MergeChecksState.Unknown -> BbCloudBundle.message("main.branch.action.merge.checking")
            mergeChecksState == MergeChecksState.Blocked -> BbCloudBundle.message("main.branch.action.merge.blocked")
            else -> null
        }
        val changesRequestActions = actions.filter { action ->
            action == PullRequestAction.RequestChanges || action == PullRequestAction.RemoveChangesRequest
        }
        val disabledActions = buildSet {
            if (mergeTooltip != null) {
                add(PullRequestAction.Merge)
            }
            if (details.isAuthoredByCurrentUser) {
                addAll(changesRequestActions)
            }
        }
        val actionTooltips = buildMap {
            if (mergeTooltip != null) {
                put(PullRequestAction.Merge, mergeTooltip)
            }
            if (details.isAuthoredByCurrentUser) {
                changesRequestActions.forEach { action ->
                    put(action, BbCloudBundle.message("main.branch.action.requestChanges.ownPullRequest"))
                }
            }
        }
        state.value = state.value.copy(
            actions = actions,
            actionsEnabled = true,
            disabledActions = disabledActions,
            actionTooltips = actionTooltips
        )
    }

    fun setActionsEnabled(enabled: Boolean) {
        state.value = state.value.copy(actionsEnabled = enabled)
    }

    fun setRefreshEnabled(enabled: Boolean) {
        state.value = state.value.copy(refreshEnabled = enabled)
    }

    fun setConversationCount(count: ItemCount?) {
        state.value = state.value.copy(conversationCount = count)
    }

    fun setChangesCount(count: ItemCount?) {
        state.value = state.value.copy(changesCount = count)
    }

    /** Returns to Overview when a new pull request is displayed, without running tab-selection work. */
    fun resetTab() {
        state.value = state.value.copy(selectedTab = PullRequestDetailTab.Overview)
    }

    /** Selects a tab and runs the work that tab needs when it becomes visible. */
    fun selectTab(tab: PullRequestDetailTab) {
        if (state.value.selectedTab == tab) {
            return
        }
        state.value = state.value.copy(selectedTab = tab)
        LOG.info("User selected the ${tab.title()} tab on the checked-out branch view")
        when (tab) {
            PullRequestDetailTab.Conversation -> onConversationTabSelected()
            PullRequestDetailTab.Changes -> onChangesTabSelected()
            // Overview: no scroll-to-top behavior.
            PullRequestDetailTab.Overview -> Unit
        }
    }

    /** Reloads the pull request. */
    fun onRefreshClicked() {
        LOG.info("User clicked refresh on the pull request page")
        onRefreshRequested()
    }

    /** Runs an action and disables the action buttons until the action finishes. */
    fun onActionClicked(action: PullRequestAction) {
        if (action in state.value.disabledActions) {
            LOG.debug("Ignoring click on disabled $action for pull request #$displayedPullRequestId")
            return
        }
        LOG.info("User clicked the $action button on the tab row of pull request #$displayedPullRequestId")
        state.value = state.value.copy(actionsEnabled = false)
        onActionRequested(action)
    }

    companion object {
        private val LOG = logger<PullRequestDetailViewModel>()
    }
}

/** The title of a tab, as it is shown in the tab strip. */
internal fun PullRequestDetailTab.title(): String {
    return when (this) {
        PullRequestDetailTab.Overview -> BbCloudBundle.message("main.branch.tab.overview")
        PullRequestDetailTab.Conversation -> BbCloudBundle.message("main.branch.tab.conversation")
        PullRequestDetailTab.Changes -> BbCloudBundle.message("main.branch.tab.changes")
    }
}