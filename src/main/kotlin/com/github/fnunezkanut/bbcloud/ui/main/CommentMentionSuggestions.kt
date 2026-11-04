package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidate
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The state of the @mention suggestion list of one composer. */
internal data class CommentMentionSuggestionsUiState(
    val isOpen: Boolean = false,
    val query: MentionQuery? = null,
    val items: List<MentionCandidate> = emptyList(),
    val selectedIndex: Int = 0,
    val isLoadingWorkspace: Boolean = false,
    val workspaceLoadFailed: Boolean = false,
    val isWorkspaceTruncated: Boolean = false
)

/**
 * Drives the @mention suggestion list of a single composer: detects the query under the caret, ranks the local and
 * loaded workspace candidates, and loads the workspace members on demand. EDT-only, plain, with no platform calls.
 */
internal class CommentMentionSuggestions(private val source: CommentMentionSource, private val label: String) {
    val state: MutableState<CommentMentionSuggestionsUiState> = mutableStateOf(CommentMentionSuggestionsUiState())

    private var workspaceCandidates: List<MentionCandidate> = emptyList()

    private var workspaceLoaded = false

    private var dismissedAnchor: Int? = null

    /** Re-evaluates the suggestion list after the editor text or selection changed. */
    fun onEditorChanged(text: String, selectionStart: Int, selectionEnd: Int, coroutineScope: CoroutineScope) {
        val query = MentionQueryDetector.find(text = text, selectionStart = selectionStart, selectionEnd = selectionEnd)
        if (query == null) {
            closeKeepingState()
            return
        }
        if (query.anchorStart != dismissedAnchor) {
            dismissedAnchor = null
        }
        if (query.anchorStart == dismissedAnchor) {
            closeKeepingState()
            return
        }
        if (query.query.endsWith(" ") && rankFor(query = query).isEmpty()) {
            closeKeepingState()
            return
        }
        maybeLoadWorkspace(query = query, coroutineScope = coroutineScope)
        applyQuery(query = query)
    }

    /** Moves the selection by [delta], wrapping around within the items. */
    fun moveSelection(delta: Int) {
        val current = state.value
        if (current.items.isEmpty()) {
            return
        }
        val size = current.items.size
        val next = ((current.selectedIndex + delta) % size + size) % size
        state.value = current.copy(selectedIndex = next)
    }

    /** The currently selected candidate, or null when the list is empty. */
    fun selected(): MentionCandidate? {
        val current = state.value
        return current.items.getOrNull(current.selectedIndex)
    }

    /** Closes the list. When [byUser], the current `@` is remembered so it does not reopen for the same anchor. */
    fun dismiss(byUser: Boolean) {
        if (byUser) {
            dismissedAnchor = state.value.query?.anchorStart
            LOG.info("User dismissed mention suggestions in the $label editor")
        }
        closeKeepingState()
    }

    /** A silent close for focus loss, preview, collapse, and submit. Does not set the dismissed anchor. */
    fun close() {
        closeKeepingState()
    }

    /** Logs the pick (no names) and closes the list. */
    fun onPicked(candidate: MentionCandidate, via: String) {
        LOG.info("User picked a ${candidate.tier} mention suggestion via $via in the $label editor")
        closeKeepingState()
    }

    private fun applyQuery(query: MentionQuery) {
        val items = rankFor(query = query)
        val wasOpen = state.value.isOpen
        val isLoading = state.value.isLoadingWorkspace
        val isOpen = items.isNotEmpty() || isLoading
        if (isOpen && !wasOpen) {
            LOG.info("User opened mention suggestions in the $label editor")
        }
        state.value = state.value.copy(
            isOpen = isOpen,
            query = query,
            items = items,
            selectedIndex = 0,
            isWorkspaceTruncated = state.value.isWorkspaceTruncated
        )
    }

    private fun rankFor(query: MentionQuery): List<MentionCandidate> {
        return MentionCandidateRanker.rank(
            query = query.query,
            local = source.localCandidates(),
            workspace = workspaceCandidates,
            excludedUuid = source.currentUserUuid
        )
    }

    private fun maybeLoadWorkspace(query: MentionQuery, coroutineScope: CoroutineScope) {
        val localResults = rankFor(query = query)
        val shouldLoad = localResults.size < MentionCandidateRanker.MAX_SUGGESTIONS &&
            query.query.isNotBlank() &&
            !workspaceLoaded &&
            !state.value.isLoadingWorkspace
        if (!shouldLoad) {
            return
        }
        val anchorAtLoad = query.anchorStart
        state.value = state.value.copy(isLoadingWorkspace = true, workspaceLoadFailed = false)
        coroutineScope.launch {
            val result = source.workspaceCandidates()
            workspaceLoaded = result != null
            workspaceCandidates = result?.candidates.orEmpty()
            val failed = result == null
            val truncated = result?.isTruncated ?: false
            val currentQuery = state.value.query
            if (currentQuery != null && currentQuery.anchorStart == anchorAtLoad) {
                val items = rankFor(query = currentQuery)
                state.value = state.value.copy(
                    isOpen = items.isNotEmpty(),
                    items = items,
                    selectedIndex = 0,
                    isLoadingWorkspace = false,
                    workspaceLoadFailed = failed,
                    isWorkspaceTruncated = truncated
                )
            } else {
                state.value = state.value.copy(
                    isLoadingWorkspace = false,
                    workspaceLoadFailed = failed,
                    isWorkspaceTruncated = truncated
                )
            }
        }
    }

    private fun closeKeepingState() {
        if (!state.value.isOpen) {
            return
        }
        state.value = state.value.copy(isOpen = false)
    }

    companion object {
        private val LOG = logger<CommentMentionSuggestions>()
    }
}