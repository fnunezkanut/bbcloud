package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerCandidates
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope

/**
 * The immutable state of the reviewer search: the query, the matching candidates, and the status message shown under
 * the list.
 */
data class ReviewerSearchUiState(
    val query: String = "",
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val candidates: List<ReviewerCandidate> = emptyList(),
    val defaultReviewerUuids: Set<String> = emptySet(),
    val statusMessage: String? = null,
    val avatarRevision: Int = 0
)

/**
 * Owns the state of the reviewer search: the query, the loaded workspace members, the candidates that match the query,
 * and the status message under the list.
 *
 * The avatars of the matching candidates are prefetched in the background, and every load bumps the avatar revision so
 * the list is repainted with the images that arrived.
 */
class ReviewerSearchViewModel(
    private val excludedUuids: Set<String>,
    val avatarCache: AuthorAvatarCache,
    private val coroutineScope: CoroutineScope,
    private val onSelected: (ReviewerCandidate) -> Unit
) {
    val state: MutableState<ReviewerSearchUiState> = mutableStateOf(ReviewerSearchUiState())

    /** Closes the popup that hosts the search; set by the popup before it is shown. */
    var onDismiss: (() -> Unit)? = null

    private var loaded: ReviewerCandidates? = null

    /** A null [result] means the workspace members could not be loaded. */
    fun showCandidates(result: ReviewerCandidates?) {
        if (result == null) {
            state.value = state.value.copy(
                isLoading = false,
                hasError = true,
                candidates = emptyList(),
                statusMessage = BbCloudBundle.message("main.branch.reviewers.search.error")
            )
            return
        }
        loaded = result
        state.value = state.value.copy(
            isLoading = false,
            hasError = false,
            defaultReviewerUuids = result.defaultReviewerUuids
        )
        applyFilter()
    }

    fun onQueryChanged(query: String) {
        state.value = state.value.copy(query = query)
        applyFilter()
    }

    /** Chooses a candidate and closes the search. */
    fun onCandidateSelected(candidate: ReviewerCandidate, source: String) {
        LOG.debug("Reviewer candidate chosen via $source")
        onDismiss?.invoke()
        onSelected(candidate)
    }

    /** Chooses the first matching candidate, the way the Enter key did in the Swing list. */
    fun selectFirstCandidate() {
        val candidate = state.value.candidates.firstOrNull() ?: return
        onCandidateSelected(candidate, source = "Enter key")
    }

    private fun applyFilter() {
        val candidates = loaded ?: return
        val matches = ReviewerCandidateFilter.filter(
            candidates = candidates,
            query = state.value.query,
            excludedUuids = excludedUuids
        )
        state.value = state.value.copy(
            candidates = matches,
            statusMessage = statusMessage(matches = matches, loaded = candidates)
        )
        avatarCache.prefetch(
            urls = matches.mapNotNull { candidate -> candidate.avatarUrl },
            coroutineScope = coroutineScope,
            onLoaded = {
                state.value = state.value.copy(avatarRevision = state.value.avatarRevision + 1)
            }
        )
    }

    private fun statusMessage(matches: List<ReviewerCandidate>, loaded: ReviewerCandidates): String? {
        val messages = listOfNotNull(
            BbCloudBundle.message("main.branch.reviewers.search.empty").takeIf { matches.isEmpty() },
            BbCloudBundle.message("main.branch.reviewers.search.truncated", loaded.members.size).takeIf { loaded.isTruncated }
        )
        return messages.joinToString(separator = " ").takeIf { messages.isNotEmpty() }
    }

    companion object {
        private val LOG = logger<ReviewerSearchViewModel>()
    }
}