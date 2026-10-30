package com.github.fnunezkanut.bbcloud.ui.pullrequests

import com.github.fnunezkanut.bbcloud.settings.PullRequestListFilter
import com.github.fnunezkanut.bbcloud.ui.otherprojects.WorkspacePullRequestRow

/** The load state of one source of a merged pull request tab. */
sealed interface PullRequestSourceState {
    data object Idle : PullRequestSourceState

    data object Loading : PullRequestSourceState

    data class Loaded(val entries: List<PullRequestListEntry>, val isTruncated: Boolean) : PullRequestSourceState

    data object Failed : PullRequestSourceState
}

enum class PullRequestsCard { Hidden, Loading, Listing, Empty, FilteredEmpty, Error }

data class PullRequestsUiState(
    val card: PullRequestsCard = PullRequestsCard.Hidden,
    val filter: PullRequestListFilter = PullRequestListFilter(),
    // Visible rows after filters.
    val rows: List<WorkspacePullRequestRow> = emptyList(),
    // Merged rows before filters.
    val totalCount: Int = 0,
    val isAnySourceLoading: Boolean = false,
    val failedSources: Set<PullRequestListingSource> = emptySet(),
    val truncatedSources: Set<PullRequestListingSource> = emptySet(),
    val loadedSources: Set<PullRequestListingSource> = emptySet(),
    // Mine or Approved checked while the current user uuid is unknown.
    val isUserFilterUnavailable: Boolean = false,
    val avatarRevision: Int = 0
)

/**
 * Derives the card from the per-source load states and the row counts. Pure, so it is unit-testable.
 *
 * [PullRequestsCard.Hidden] matches the former `ProjectPullRequestsCard.Hidden`, which the main panel shows while it
 * checks the project repository. When some sources are Loaded and others Failed, the card still resolves to
 * Listing/Empty/FilteredEmpty from the loaded rows, and the failed sources are surfaced through the footer instead.
 */
internal fun derivePullRequestsCard(sources: Map<PullRequestListingSource, PullRequestSourceState>, totalCount: Int, visibleCount: Int): PullRequestsCard {
    val states = sources.values
    if (states.all { state -> state is PullRequestSourceState.Idle }) {
        return PullRequestsCard.Hidden
    }
    val anyLoading = states.any { state -> state is PullRequestSourceState.Loading }
    val anyLoaded = states.any { state -> state is PullRequestSourceState.Loaded }
    if (anyLoading && !anyLoaded) {
        return PullRequestsCard.Loading
    }
    if (anyLoaded) {
        return when {
            visibleCount > 0 -> PullRequestsCard.Listing
            totalCount > 0 -> PullRequestsCard.FilteredEmpty
            anyLoading -> PullRequestsCard.Loading
            else -> PullRequestsCard.Empty
        }
    }
    // No source is Idle, Loading, or Loaded here, so every non-idle source has Failed.
    return PullRequestsCard.Error
}