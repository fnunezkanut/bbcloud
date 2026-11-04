package com.github.fnunezkanut.bbcloud.ui.otherprojects

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.state.ToggleableState
import com.github.fnunezkanut.bbcloud.git.WorktreeCurrentBranchReader
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeListing
import com.github.fnunezkanut.bbcloud.project.WatchedProjectMapping
import com.github.fnunezkanut.bbcloud.project.WatchedProjectWorktreeRow
import com.github.fnunezkanut.bbcloud.project.WatchedProjectsDiscoveryResult
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
 * The stored data and the rescan operation the Watched Projects tab reads, backed by
 * [com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService] in production and by a fake in tests.
 */
interface WatchedProjectsDataSource {
    fun organizationUrlOrNull(): String?

    /** The persisted "Show only watched" checkbox. */
    fun showOnlyWatched(): Boolean

    fun setShowOnlyWatched(showOnlyWatched: Boolean)

    fun isProjectsDirectorySet(): Boolean

    fun watchedProjectsWithLocalPaths(): List<WatchedProjectMapping>

    fun repoAvatarUrls(): Map<String, String>

    fun workTreeDirectoryOrNull(): Path?

    fun setWatched(mapping: WatchedProjectMapping, watched: Boolean)

    fun setAllWatched(watched: Boolean)

    suspend fun rescan(clearFirst: Boolean): WatchedProjectsDiscoveryResult
}

/**
 * Holds the Watched Projects state and coordinates row discovery without depending on Swing UI components.
 *
 * The screen callbacks are supplied by the hosting coordinator so user actions remain separate from presentation
 * state. Worktree discovery stays here because it is presentation data needed to render each row. Binding the stored
 * mappings, toggling watched flags, and rescanning the projects directory are orchestrated here against
 * [dataSource]; the host only switches its card ([onShowContentCallback], [onShowNotConfiguredCallback]) and shows the
 * unreadable-directory dialog ([onProjectsDirectoryUnreadableCallback]), which needs a Swing parent. The host also shows
 * the watched-limit warning ([onWatchedLimitReachedCallback]) when a selection would exceed [MAX_WATCHED_PROJECTS].
 */
class WatchedProjectsViewModel(
    private val coroutineScope: CoroutineScope,
    private val dataSource: WatchedProjectsDataSource,
    private val onShowContentCallback: () -> Unit,
    private val onShowNotConfiguredCallback: () -> Unit,
    private val onProjectsDirectoryUnreadableCallback: () -> Unit,
    private val onWatchedLimitReachedCallback: () -> Unit,
    private val onOpenLocalProjectCallback: (WatchedProjectMapping) -> Unit,
    private val onOpenWorktreeCallback: (WatchedProjectMapping, WatchedProjectWorktreeRow) -> Unit,
    private val onRemoveWorktreeCallback: (WatchedProjectMapping, WatchedProjectWorktreeRow) -> Unit,
    private val onPruneWorktreesCallback: () -> Unit,
    private val onOpenWorktreesHelpCallback: () -> Unit,
    private val onGoToWorkspaceCallback: () -> Unit,
    private val runOnEdt: (() -> Unit) -> Unit = { block -> ApplicationManager.getApplication().invokeLater(block) }
) {
    val state: MutableState<WatchedProjectsUiState> = mutableStateOf(
        WatchedProjectsUiState(showOnlyWatched = dataSource.showOnlyWatched())
    )

    /** In-memory decoded-icon cache for repository avatars (32px); images re-download over the public CDN, no token. */
    private val avatarCache = AuthorAvatarCache(avatarSize = 32)

    /** Repository slug -> persisted avatar URL, replaced on every [showResult]. EDT-only. */
    private val slugToAvatarUrl = mutableMapOf<String, String>()

    /** Invalidates branch-read jobs from an older table refresh. Mutated on the EDT. */
    private var worktreeLoadGeneration = 0L

    private var organizationUrl: String? = null

    /**
     * Shows the persisted mappings of the current workspace on the Watched Projects tab, or routes to the generic
     * not-configured card when no projects directory is set. Does not rescan: a rescan runs only after a directory is
     * chosen, or a workspace slug change is saved in Settings (see [rescanWatchedProjects] and
     * [com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService.saveSetup]).
     */
    fun bindWatchedProjects() {
        LOG.debug("Binding the Workspace - Watched Projects tab")
        if (!dataSource.isProjectsDirectorySet()) {
            LOG.info("Watched Projects: projects directory not set; prompting for Settings")
            // Route to the generic centered not-configured card (R2) rather than the directory-specific prompt with
            // the status row still visible. Hide the Sync button as well, since the content card is not shown.
            setSyncVisible(false)
            onShowNotConfiguredCallback()
            return
        }
        setSyncVisible(true)
        setSyncEnabled(true)
        // Avatar URLs come from plugin storage (written on Sync), so this bind makes no Bitbucket API call. The only
        // network is the unauthenticated public-CDN image prefetch kicked off inside showResult.
        showStoredResult(organizationUrl = dataSource.organizationUrlOrNull())
        onShowContentCallback()
    }

    /**
     * Scans and verifies the projects directory, then shows the outcome of the rescan on the Workspace - Watched
     * Projects tab.
     *
     * Pass [clearFirst] = true when the scan follows a configuration change (a chosen projects directory), so the
     * watched map is emptied before discovery and rediscovered repositories come in unwatched. The Sync button leaves
     * it false, which carries over the user's existing watched choices for repositories that are found again.
     */
    fun rescanWatchedProjects(clearFirst: Boolean = false) {
        val organizationUrl = dataSource.organizationUrlOrNull()
        if (organizationUrl == null || !dataSource.isProjectsDirectorySet()) {
            bindWatchedProjects()
            return
        }

        setSyncEnabled(false)
        showLoading()
        coroutineScope.launch {
            val result = dataSource.rescan(clearFirst = clearFirst)
            runOnEdt {
                showRescanResult(result = result, organizationUrl = organizationUrl)
            }
        }
    }

    private fun showRescanResult(result: WatchedProjectsDiscoveryResult, organizationUrl: String) {
        when (result) {
            is WatchedProjectsDiscoveryResult.Success -> {
                LOG.info("Watched projects rescan succeeded: ${dataSource.watchedProjectsWithLocalPaths().size} projects")
                // The persisted avatar-URL map was already updated by rescanWatchedProjects before this UI read.
                showStoredResult(organizationUrl = organizationUrl)
            }
            is WatchedProjectsDiscoveryResult.VerifyFailed -> {
                LOG.info("Watched projects rescan could not be verified; previous projects kept")
                showStoredResult(organizationUrl = organizationUrl, rescanFailed = true)
            }
            is WatchedProjectsDiscoveryResult.ProjectsDirectoryUnreadable -> {
                LOG.warn("Projects directory is not readable; watched projects cleared")
                onProjectsDirectoryUnreadableCallback()
                showStoredResult(organizationUrl = organizationUrl)
            }
            is WatchedProjectsDiscoveryResult.NotConfigured -> {
                LOG.info("Cannot rescan watched projects: not configured")
                showEmpty()
            }
        }
        setSyncEnabled(true)
    }

    private fun showStoredResult(organizationUrl: String?, rescanFailed: Boolean = false) {
        showResult(
            mappings = dataSource.watchedProjectsWithLocalPaths(),
            organizationUrl = organizationUrl,
            rescanFailed = rescanFailed,
            avatarUrlBySlug = dataSource.repoAvatarUrls(),
            coroutineScope = coroutineScope,
            workTreeDirectory = dataSource.workTreeDirectoryOrNull()
        )
    }

    /**
     * Reloads the rows of the Watched Projects table from the stored list after a watched flag changed. No rescan runs:
     * the flag is a local choice and the list itself does not change.
     */
    private fun refreshWatchedRows() {
        // A watched-flag toggle does not change the avatar-URL set, so no avatar rebind is needed here; the ViewModel
        // keeps resolving icons from the already-bound slug-to-URL map.
        refreshRows(
            mappings = dataSource.watchedProjectsWithLocalPaths(),
            workTreeDirectory = dataSource.workTreeDirectoryOrNull(),
            coroutineScope = coroutineScope
        )
    }

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
            masterToggle = masterToggleFor(rows = rows, query = state.value.searchQuery)
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
            masterToggle = masterToggleFor(rows = rows, query = state.value.searchQuery)
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
                    worktrees = row.worktrees
                )
            },
            avatarRevision = state.value.avatarRevision + 1
        )
    }

    /** Sets whether a prune of all watched projects is running; the Prune and Sync buttons are disabled meanwhile. */
    fun setPruneInProgress(inProgress: Boolean) {
        state.value = state.value.copy(pruneEnabled = !inProgress)
    }

    /**
     * Sets the watched flag of one project. Ticking a project on is refused when [MAX_WATCHED_PROJECTS] projects are
     * already watched; the host then shows the limit warning and the row stays unchecked. Unticking is always allowed.
     */
    fun onWatchedChanged(mapping: WatchedProjectMapping, watched: Boolean) {
        LOG.info("User toggled the watched checkbox of ${mapping.remoteRepoSlug} to watched=$watched")
        if (watched && !canWatchProject(slug = mapping.remoteRepoSlug)) {
            LOG.info("Watched project limit of $MAX_WATCHED_PROJECTS reached; refusing to watch ${mapping.remoteRepoSlug}")
            onWatchedLimitReachedCallback()
            refreshWatchedRows()
            return
        }
        dataSource.setWatched(mapping = mapping, watched = watched)
        refreshWatchedRows()
    }

    /**
     * Watches or unwatches the projects the master checkbox covers. Without a search query that is every project. With
     * an active query it is only the visible rows, so hidden projects keep their watched flag. Watching never takes the
     * watched count above [MAX_WATCHED_PROJECTS]; the excess is left unwatched and the limit warning is shown.
     */
    fun onMasterToggleClicked() {
        val query = state.value.searchQuery
        val watched = state.value.masterToggle != ToggleableState.On
        if (query.isBlank()) {
            LOG.info("User toggled the master watched checkbox to watched=$watched")
            setAllWatchedWithinLimit(watched = watched)
            refreshWatchedRows()
            return
        }
        val targets = state.value.visibleRows
        if (targets.isEmpty()) {
            LOG.info("Master toggle ignored: no rows match the search filter")
            return
        }
        LOG.info("User toggled the master watched checkbox of ${targets.size} filtered projects to watched=$watched")
        if (watched) {
            watchVisibleWithinLimit(targets = targets)
        } else {
            targets.forEach { row ->
                dataSource.setWatched(mapping = row.mapping, watched = false)
            }
        }
        refreshWatchedRows()
    }

    /** Whether the project [slug] may be watched now: it already is, or fewer than [MAX_WATCHED_PROJECTS] are watched. */
    private fun canWatchProject(slug: String): Boolean {
        val stored = dataSource.watchedProjectsWithLocalPaths()
        val alreadyWatched = stored.any { mapping -> mapping.remoteRepoSlug == slug && mapping.watched }
        return alreadyWatched || stored.count { mapping -> mapping.watched } < MAX_WATCHED_PROJECTS
    }

    /**
     * Select-all without a filter. Unwatching clears every project. Watching sets every project when there are at most
     * [MAX_WATCHED_PROJECTS]; otherwise the first [MAX_WATCHED_PROJECTS] in stored (slug) order are watched, the rest
     * are left unwatched, and the limit warning is shown.
     */
    private fun setAllWatchedWithinLimit(watched: Boolean) {
        if (!watched) {
            dataSource.setAllWatched(watched = false)
            return
        }
        val stored = dataSource.watchedProjectsWithLocalPaths()
        if (stored.size <= MAX_WATCHED_PROJECTS) {
            dataSource.setAllWatched(watched = true)
            return
        }
        LOG.info("Watched project limit of $MAX_WATCHED_PROJECTS reached on select-all; watching the first $MAX_WATCHED_PROJECTS")
        stored.forEachIndexed { index, mapping ->
            dataSource.setWatched(mapping = mapping, watched = index < MAX_WATCHED_PROJECTS)
        }
        onWatchedLimitReachedCallback()
    }

    /**
     * Filtered select-all. Watches the visible projects in display order until the remaining budget
     * ([MAX_WATCHED_PROJECTS] minus the projects already watched) is used up. Projects already watched cost nothing. The
     * limit warning is shown when at least one visible project could not be watched.
     */
    private fun watchVisibleWithinLimit(targets: List<WatchedProjectRow>) {
        val stored = dataSource.watchedProjectsWithLocalPaths()
        val watchedBySlug = stored.associate { mapping -> mapping.remoteRepoSlug to mapping.watched }
        var remaining = MAX_WATCHED_PROJECTS - stored.count { mapping -> mapping.watched }
        var limitReached = false
        targets.forEach { row ->
            val alreadyWatched = watchedBySlug[row.mapping.remoteRepoSlug] == true
            if (alreadyWatched) {
                return@forEach
            }
            if (remaining > 0) {
                dataSource.setWatched(mapping = row.mapping, watched = true)
                remaining--
            } else {
                limitReached = true
            }
        }
        if (limitReached) {
            LOG.info("Watched project limit of $MAX_WATCHED_PROJECTS reached on filtered select-all")
            onWatchedLimitReachedCallback()
        }
    }

    /**
     * Stores the filter text typed in the Watched Projects search field. The query is session-only and is not persisted.
     * The log records only its length, because the text is user-authored.
     */
    fun onSearchQueryChanged(query: String) {
        if (query == state.value.searchQuery) {
            return
        }
        LOG.debug("Watched Projects search filter changed (length=${query.length})")
        state.value = state.value.copy(
            searchQuery = query,
            masterToggle = masterToggleFor(rows = state.value.rows, query = query)
        )
    }

    /** Persists and applies the "Show only watched" checkbox. */
    fun onShowOnlyWatchedChanged(showOnlyWatched: Boolean) {
        if (showOnlyWatched == state.value.showOnlyWatched) {
            return
        }
        LOG.info("User set Watched Projects 'Show only watched' to $showOnlyWatched")
        dataSource.setShowOnlyWatched(showOnlyWatched = showOnlyWatched)
        state.value = state.value.copy(showOnlyWatched = showOnlyWatched)
    }

    fun onSearchCleared() {
        LOG.info("User cleared the Watched Projects search filter")
        onSearchQueryChanged(query = "")
    }

    fun onSyncClicked() {
        LOG.info("User requested Sync with Projects Directory")
        rescanWatchedProjects()
    }

    fun onOpenLocalProject(mapping: WatchedProjectMapping) {
        LOG.info("User requested to open local checkout project for ${mapping.remoteRepoSlug}")
        onOpenLocalProjectCallback(mapping)
    }

    fun onOpenWorktree(mapping: WatchedProjectMapping, worktree: WatchedProjectWorktreeRow) {
        LOG.info("User requested to open PR #${worktree.prNumber} worktree for ${mapping.remoteRepoSlug}")
        onOpenWorktreeCallback(mapping, worktree)
    }

    fun onRemoveWorktree(mapping: WatchedProjectMapping, worktree: WatchedProjectWorktreeRow) {
        LOG.info("User requested to remove PR #${worktree.prNumber} worktree for ${mapping.remoteRepoSlug}")
        onRemoveWorktreeCallback(mapping, worktree)
    }

    /**
     * Re-reads the PR work trees of every row from disk without a rescan. The search query, the "Show only watched"
     * choice and the card stay as they are, so the table does not flash the loading state after a removal.
     */
    fun reloadWorktrees() {
        refreshWatchedRows()
    }

    fun onPruneWorktreesClicked() {
        LOG.info("User requested PR worktree pruning for all watched projects")
        onPruneWorktreesCallback()
    }

    fun onWorktreesHelpClicked() {
        LOG.info("User clicked the What are Work Trees? link")
        onOpenWorktreesHelpCallback()
    }

    fun onGoToWorkspaceClicked() {
        LOG.info("User opened the Workspace tab from the Watched Projects footer link")
        onGoToWorkspaceCallback()
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
                avatarUrl = slugToAvatarUrl[mapping.remoteRepoSlug]
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
        updateRows(rows = rowsWithDiscoveredWorktrees)
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
                runOnEdt {
                    if (generation != worktreeLoadGeneration) {
                        return@runOnEdt
                    }
                    val resolvedBySlug = resolvedRows.toMap()
                    updateRows(
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

    /**
     * Replaces [WatchedProjectsUiState.rows] and recomputes the master toggle for the current query. Worktree and branch
     * names are searchable, so a worktree update can change which rows match.
     */
    private fun updateRows(rows: List<WatchedProjectRow>) {
        state.value = state.value.copy(
            rows = rows,
            masterToggle = masterToggleFor(rows = rows, query = state.value.searchQuery)
        )
    }

    /** The master checkbox state for [rows] narrowed to the rows that match [query]. */
    private fun masterToggleFor(rows: List<WatchedProjectRow>, query: String): ToggleableState {
        return deriveMasterToggle(filterWatchedProjectRows(rows = rows, query = query))
    }

    companion object {
        private val LOG = logger<WatchedProjectsViewModel>()

        /** The most projects that can be watched at once, to bound the Bitbucket API calls the Workspace tab makes. */
        const val MAX_WATCHED_PROJECTS = 20
    }
}