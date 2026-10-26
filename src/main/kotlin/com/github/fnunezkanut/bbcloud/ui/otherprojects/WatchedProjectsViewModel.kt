package com.github.fnunezkanut.bbcloud.ui.otherprojects

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.state.ToggleableState
import com.github.fnunezkanut.bbcloud.git.WorktreeCurrentBranchReader
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeListing
import com.github.fnunezkanut.bbcloud.project.WatchedProjectMapping
import com.github.fnunezkanut.bbcloud.project.WatchedProjectWorktreeRow
import com.github.fnunezkanut.bbcloud.ui.main.AuthorAvatarCache
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope as coroutineScopeBlock

/**
 * Holds the Watched Projects state and coordinates row discovery without depending on Swing UI components.
 *
 * The screen callbacks are supplied by the hosting coordinator so user actions remain separate from presentation
 * state. Worktree discovery stays here because it is presentation data needed to render each row.
 */
class WatchedProjectsViewModel(
    private val coroutineScope: CoroutineScope,
    private val onWatchedChangedCallback: (WatchedProjectMapping, Boolean) -> Unit,
    private val onAllWatchedChangedCallback: (Boolean) -> Unit,
    private val onSyncRequestedCallback: () -> Unit,
    private val onOpenLocalProjectCallback: (WatchedProjectMapping) -> Unit,
    private val onOpenWorktreeCallback: (WatchedProjectMapping, WatchedProjectWorktreeRow) -> Unit,
    private val onPruneWorktreesCallback: (WatchedProjectMapping) -> Unit
) {
    val state: MutableState<WatchedProjectsUiState> = mutableStateOf(WatchedProjectsUiState())

    /** In-memory decoded-icon cache for repository avatars (32px); images re-download over the public CDN, no token. */
    private val avatarCache = AuthorAvatarCache(avatarSize = 32)

    /** Repository slug -> persisted avatar URL, replaced on every [showResult]. EDT-only. */
    private val slugToAvatarUrl = mutableMapOf<String, String>()

    /** Invalidates branch-read jobs from an older table refresh. Mutated on the EDT. */
    private var worktreeLoadGeneration = 0L

    private var organizationUrl: String? = null
    private val prunesInProgress = mutableSetOf<String>()

    fun showLoading() {
        worktreeLoadGeneration++
        state.value = state.value.copy(
            card = WatchedProjectsCard.Loading,
            rows = emptyList(),
            rescanFailed = false,
            masterToggle = deriveMasterToggle(emptyList())
        )
    }

    /**
     * Binds [organizationUrl] and shows [mappings]. An empty list shows the blank empty state instead of an empty
     * listing. [rescanFailed] shows the inline warning that the previous list is kept.
     *
     * [mappings] are shown sorted alphabetically by [WatchedProjectMapping.remoteRepoSlug] (case-insensitive),
     * so the list order does not depend on filesystem or discovery iteration order.
     */
    fun showResult(
        mappings: List<WatchedProjectMapping>,
        organizationUrl: String?,
        rescanFailed: Boolean = false,
        avatarUrlBySlug: Map<String, String> = emptyMap(),
        workTreeDirectory: Path? = null,
        coroutineScope: CoroutineScope? = null
    ) {
        this.organizationUrl = organizationUrl
        slugToAvatarUrl.clear()
        slugToAvatarUrl.putAll(avatarUrlBySlug)
        val sortedMappings = sortedForTable(mappings)
        val rows = buildRows(sortedMappings)
        state.value = state.value.copy(
            card = if (sortedMappings.isEmpty()) WatchedProjectsCard.Empty else WatchedProjectsCard.Listing,
            rows = rows,
            rescanFailed = rescanFailed,
            masterToggle = deriveMasterToggle(rows)
        )
        refreshWorktreeRows(
            mappings = sortedMappings,
            workTreeDirectory = workTreeDirectory,
            coroutineScope = coroutineScope
        )
        if (coroutineScope != null) {
            avatarCache.prefetch(
                urls = avatarUrlBySlug.values.distinct(),
                coroutineScope = coroutineScope,
                onLoaded = { state.value = state.value.copy(avatarRevision = state.value.avatarRevision + 1) }
            )
        }
    }

    /**
     * Replaces the rows after a watched flag changed, keeping the current card and the rescan warning. Unlike
     * [showResult] it does not switch cards, so a warning about a failed rescan stays visible.
     */
    fun refreshRows(mappings: List<WatchedProjectMapping>, workTreeDirectory: Path? = null, coroutineScope: CoroutineScope? = null) {
        val sortedMappings = sortedForTable(mappings)
        val rows = buildRows(sortedMappings)
        state.value = state.value.copy(
            rows = rows,
            masterToggle = deriveMasterToggle(rows)
        )
        refreshWorktreeRows(
            mappings = sortedMappings,
            workTreeDirectory = workTreeDirectory,
            coroutineScope = coroutineScope
        )
    }

    fun showEmpty() {
        worktreeLoadGeneration++
        state.value = state.value.copy(
            card = WatchedProjectsCard.Empty,
            rows = emptyList(),
            rescanFailed = false,
            masterToggle = deriveMasterToggle(emptyList())
        )
    }

    fun setSyncEnabled(enabled: Boolean) {
        state.value = state.value.copy(syncEnabled = enabled)
    }

    fun setSyncVisible(visible: Boolean) {
        state.value = state.value.copy(syncVisible = visible)
    }

    /**
     * Drops the in-memory decoded icons and the slug->URL map, then updates state so every row resolves its placeholder.
     * The persisted URL map is wiped separately by the configuration service.
     */
    fun clearAvatarCache() {
        slugToAvatarUrl.clear()
        avatarCache.clear()
        state.value = state.value.copy(
            rows = state.value.rows.map { row ->
                buildWatchedProjectRow(
                    mapping = row.mapping,
                    organizationUrl = organizationUrl,
                    avatarUrl = null,
                    worktrees = row.worktrees,
                    isPruneInProgress = row.mapping.remoteRepoSlug in prunesInProgress
                )
            },
            avatarRevision = state.value.avatarRevision + 1
        )
    }

    fun setPruneInProgress(slug: String, inProgress: Boolean) {
        if (inProgress) {
            prunesInProgress += slug
        } else {
            prunesInProgress -= slug
        }
        val rows = state.value.rows.map { row ->
            if (row.mapping.remoteRepoSlug == slug) {
                row.copy(isPruneInProgress = inProgress)
            } else {
                row
            }
        }
        state.value = state.value.copy(rows = rows)
    }

    fun onWatchedChanged(mapping: WatchedProjectMapping, watched: Boolean) {
        LOG.info("User toggled the watched checkbox of ${mapping.remoteRepoSlug} to watched=$watched")
        onWatchedChangedCallback(mapping, watched)
    }

    fun onMasterToggleClicked() {
        val watched = state.value.masterToggle != ToggleableState.On
        LOG.info("User toggled the master watched checkbox to watched=$watched")
        onAllWatchedChangedCallback(watched)
    }

    fun onSyncClicked() {
        LOG.info("User requested Sync with Projects Directory")
        onSyncRequestedCallback()
    }

    fun onOpenLocalProject(mapping: WatchedProjectMapping) {
        LOG.info("User requested to open local checkout project for ${mapping.remoteRepoSlug}")
        onOpenLocalProjectCallback(mapping)
    }

    fun onOpenWorktree(mapping: WatchedProjectMapping, worktree: WatchedProjectWorktreeRow) {
        LOG.info("User requested to open PR #${worktree.prNumber} worktree for ${mapping.remoteRepoSlug}")
        onOpenWorktreeCallback(mapping, worktree)
    }

    fun onPruneWorktrees(mapping: WatchedProjectMapping) {
        LOG.info("User requested PR worktree pruning for ${mapping.remoteRepoSlug}")
        onPruneWorktreesCallback(mapping)
    }

    fun avatarIconFor(url: String?): javax.swing.Icon {
        return avatarCache.iconFor(
            url = url,
            fallback = avatarCache.repositoryPlaceholderIcon()
        )
    }

    private fun buildRows(mappings: List<WatchedProjectMapping>): List<WatchedProjectRow> {
        return mappings.map { mapping ->
            buildWatchedProjectRow(
                mapping = mapping,
                organizationUrl = organizationUrl,
                avatarUrl = slugToAvatarUrl[mapping.remoteRepoSlug],
                isPruneInProgress = mapping.remoteRepoSlug in prunesInProgress
            )
        }
    }

    private fun refreshWorktreeRows(mappings: List<WatchedProjectMapping>, workTreeDirectory: Path?, coroutineScope: CoroutineScope?) {
        val generation = ++worktreeLoadGeneration
        val discoveredRows = if (workTreeDirectory == null) {
            emptyMap()
        } else {
            mappings.associate { mapping ->
                val worktrees = PullRequestWorktreeListing.worktreesForRepo(
                    workTreeDirectory = workTreeDirectory,
                    repoSlug = mapping.remoteRepoSlug
                )
                mapping.remoteRepoSlug to worktrees.map { worktree ->
                    WatchedProjectWorktreeRow(
                        prNumber = worktree.prNumber,
                        branchName = null,
                        worktreePath = worktree.worktreePath
                    )
                }
            }
        }
        val rowsWithDiscoveredWorktrees = state.value.rows.map { row ->
            row.copy(worktrees = discoveredRows[row.mapping.remoteRepoSlug].orEmpty())
        }
        state.value = state.value.copy(rows = rowsWithDiscoveredWorktrees)
        if (coroutineScope == null || discoveredRows.isEmpty()) {
            return
        }

        coroutineScope.launch {
            try {
                val resolvedRows = withContext(Dispatchers.IO) {
                    coroutineScopeBlock {
                        discoveredRows.entries.map { (repoSlug, rows) ->
                            async {
                                repoSlug to rows.map { row ->
                                    row.copy(
                                        branchName = WorktreeCurrentBranchReader.readCurrentBranch(row.worktreePath)
                                    )
                                }
                            }
                        }.awaitAll()
                    }
                }
                ApplicationManager.getApplication().invokeLater {
                    if (generation != worktreeLoadGeneration) {
                        return@invokeLater
                    }
                    val resolvedBySlug = resolvedRows.toMap()
                    state.value = state.value.copy(
                        rows = state.value.rows.map { row ->
                            row.copy(worktrees = resolvedBySlug[row.mapping.remoteRepoSlug] ?: row.worktrees)
                        }
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            }
        }
    }

    private fun sortedForTable(mappings: List<WatchedProjectMapping>): List<WatchedProjectMapping> {
        return mappings.sortedBy { mapping -> mapping.remoteRepoSlug.lowercase() }
    }

    companion object {
        private val LOG = logger<WatchedProjectsViewModel>()
    }
}