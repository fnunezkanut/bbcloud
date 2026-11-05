package com.github.fnunezkanut.bbcloud.ui.recentprojects

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeLayout
import com.github.fnunezkanut.bbcloud.project.RecentProjectsSource
import com.github.fnunezkanut.bbcloud.project.isPullRequestWorktreePath
import com.github.fnunezkanut.bbcloud.project.normalizedFolderKey
import com.github.fnunezkanut.bbcloud.project.repoSlugForRecentProject
import com.github.fnunezkanut.bbcloud.ui.main.AuthorAvatarCache
import com.github.fnunezkanut.bbcloud.ui.otherprojects.toComposeImageBitmap
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.coroutineScope as coroutineScopeBlock

/**
 * Holds the Recent Projects tab state: the IDE recent projects with their groups, the branch of each project, and the
 * project icons and repository avatars.
 *
 * The data comes from [source]; [readBranch] and [pathExists] are injected so the logic runs without the platform. A
 * refresh generation makes sure a slow load never overwrites a newer one, and platform change notifications are
 * debounced because the platform fires several of them while a project opens.
 */
@OptIn(FlowPreview::class)
class RecentProjectsViewModel internal constructor(
    private val source: RecentProjectsSource,
    private val readBranch: suspend (Path) -> String?,
    private val pathExists: (String) -> Boolean,
    private val userHome: String,
    private val coroutineScope: CoroutineScope,
    private val openProject: (RecentProjectRow) -> Unit,
    private val onLoadFailed: (Throwable) -> Unit,
    private val runOnEdt: (() -> Unit) -> Unit,
    private val avatars: RepoAvatarProvider = NoRepoAvatars,
    private val workTreeDirectory: () -> String? = { null },
    private val currentProjectBasePath: () -> String? = { null },
    private val resolveSourceRoot: (worktreePath: Path, repoSlug: String?) -> Path? = { _, _ -> null },
    private val removeWorktree: (sourceCheckoutRoot: Path, worktreePath: Path, prNumber: Long) -> Unit = { _, _, _ -> },
    private val onWorktreeRemovalUnavailable: (RecentProjectRow) -> Unit = {},
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    val state: MutableState<RecentProjectsUiState> = mutableStateOf(RecentProjectsUiState.INITIAL)

    private val refreshGeneration = AtomicInteger(0)
    private val avatarBitmaps = ConcurrentHashMap<String, ImageBitmap>()
    private val defaultRepoIconBitmap: ImageBitmap by lazy {
        AuthorAvatarCache(avatarSize = PLACEHOLDER_SIZE).repositoryPlaceholderIcon().toComposeImageBitmap()
    }

    @Volatile
    private var avatarUrlsBySlug: Map<String, String> = emptyMap()
    private val platformChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        coroutineScope.launch {
            platformChanges.debounce(timeout = CHANGE_DEBOUNCE).collect {
                LOG.debug("Reloading the recent projects after a platform change")
                reload()
            }
        }
    }

    /** Called when the platform reports a change to the Recent Projects list. Safe to call from any thread. */
    fun onPlatformChanged() {
        platformChanges.tryEmit(Unit)
    }

    /** Loads the recent projects. Shows the loading text only while there are no rows yet, so tab switches do not flicker. */
    fun reload() {
        val generation = refreshGeneration.incrementAndGet()
        if (state.value.isEmpty) {
            state.value = state.value.copy(
                isLoading = true,
                loadFailed = false
            )
        }
        coroutineScope.launch {
            try {
                val loaded = withContext(ioDispatcher) { loadRows() }
                runOnEdt { applyLoaded(generation = generation, loaded = loaded) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                runOnEdt { applyFailure(generation = generation, exception = exception) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        state.value = state.value.copy(searchQuery = query)
    }

    fun onSearchCleared() {
        LOG.info("User cleared the Recent Projects search")
        onSearchQueryChanged(query = "")
    }

    fun onGroupToggled(groupName: String) {
        val current = state.value
        val group = current.groups.firstOrNull { candidate -> candidate.name == groupName } ?: return
        val expanded = !group.isExpanded
        LOG.info("User ${if (expanded) "expanded" else "collapsed"} a recent projects group")
        state.value = current.copy(
            groups = current.groups.map { candidate ->
                if (candidate.name == groupName) candidate.copy(isExpanded = expanded) else candidate
            }
        )
        try {
            source.setGroupExpanded(
                groupName = groupName,
                expanded = expanded
            )
        } catch (exception: Exception) {
            LOG.warn("Could not store the expanded state of a recent projects group", exception)
        }
    }

    fun onProjectClicked(row: RecentProjectRow) {
        LOG.info("User opened recent project ${folderName(path = row.path)} from the Recent Projects tab")
        openProject(row)
    }

    fun onRemoveRequested(row: RecentProjectRow) {
        LOG.info("User removed recent project ${folderName(path = row.path)} from Recent Projects")
        try {
            source.removePath(path = row.path)
        } catch (exception: Exception) {
            LOG.warn("Could not remove a recent project", exception)
        }
        reload()
    }

    /**
     * Removes the PR work tree of [row]: resolves its source checkout off the EDT, then hands over to the host, which
     * asks for confirmation and removes it. Ignored for other rows and for the project hosting this tool window.
     */
    fun onRemoveWorktreeRequested(row: RecentProjectRow) {
        if (!row.isPullRequestWorktree || row.isCurrentProject) {
            return
        }
        val folderName = folderName(path = row.path)
        LOG.info("User requested removal of PR work tree $folderName from the Recent Projects tab")
        val prNumber = PullRequestWorktreeLayout.prNumberFromWorktreeFolderName(folderName = folderName)
        coroutineScope.launch {
            val worktreePath = pathOrNull(value = row.path)
            val sourceRoot = if (worktreePath == null || prNumber == null) {
                null
            } else {
                withContext(ioDispatcher) { resolveSourceRoot(worktreePath, row.repoSlug) }
            }
            runOnEdt {
                if (worktreePath == null || prNumber == null || sourceRoot == null) {
                    LOG.info("Cannot remove the PR work tree $folderName: its source repository was not found")
                    onWorktreeRemovalUnavailable(row)
                } else {
                    removeWorktree(sourceRoot, worktreePath, prNumber)
                }
            }
        }
    }

    /** The decoded repository avatar of [row], or null when the repository is unknown or its avatar has not loaded. */
    fun repoAvatarFor(row: RecentProjectRow): ImageBitmap? {
        val slug = row.repoSlug ?: return null
        val url = avatarUrlsBySlug[slug] ?: return null
        avatarBitmaps[url]?.let { cached -> return cached }
        val icon = avatars.avatarFor(url = url) ?: return null
        val bitmap = icon.toComposeImageBitmap()
        avatarBitmaps[url] = bitmap
        return bitmap
    }

    /**
     * The default repository icon, the same one the Watched Projects tab shows. Used for every row whose repository
     * avatar is unknown, not in the cache, or not loaded yet.
     */
    fun defaultRepoIcon(): ImageBitmap {
        return defaultRepoIconBitmap
    }

    /** Drops the cached avatars, for the Reset Plugin flow. */
    fun clearAvatars() {
        avatars.clear()
        avatarBitmaps.clear()
        bumpAvatarRevision()
    }

    private data class LoadedRows(val groups: List<RecentProjectGroupRow>, val ungrouped: List<RecentProjectRow>, val avatarUrls: Map<String, String>)

    /** What every row needs to know about the work tree root, the current project, and the watched mappings. */
    private class RowContext(val workTreeDirectory: String?, val currentProjectKey: String?, val mappingsByFolder: Map<String, String>)

    private suspend fun loadRows(): LoadedRows {
        val snapshot = source.snapshot()
        val context = RowContext(
            workTreeDirectory = workTreeDirectory(),
            currentProjectKey = currentProjectBasePath()?.let { base -> normalizedFolderKey(path = base) },
            mappingsByFolder = avatars.slugsByLocalFolder()
        )
        val tree = buildRecentProjectsTree(snapshot = snapshot)
        val semaphore = Semaphore(permits = BRANCH_READ_PARALLELISM)
        return coroutineScopeBlock {
            val groups = tree.groups.map { (group, paths) ->
                val rows = paths.filter(::isFileSystemPath).map { path ->
                    async { buildRow(path = path, isGrouped = true, semaphore = semaphore, context = context) }
                }
                group to rows
            }
            val ungrouped = tree.ungroupedPaths.filter(::isFileSystemPath).map { path ->
                async { buildRow(path = path, isGrouped = false, semaphore = semaphore, context = context) }
            }
            LoadedRows(
                groups = groups.mapNotNull { (group, rows) ->
                    val projects = rows.awaitAll()
                    if (projects.isEmpty()) {
                        null
                    } else {
                        RecentProjectGroupRow(
                            name = group.name,
                            isExpanded = group.isExpanded,
                            projects = projects
                        )
                    }
                },
                ungrouped = ungrouped.awaitAll(),
                avatarUrls = avatars.avatarUrlsBySlug()
            )
        }
    }

    private suspend fun buildRow(path: String, isGrouped: Boolean, semaphore: Semaphore, context: RowContext): RecentProjectRow {
        val exists = pathExists(path)
        val branch = if (exists) {
            semaphore.withPermit { readBranchOrNull(path = path) }
        } else {
            null
        }
        return RecentProjectRow(
            path = path,
            name = source.displayName(path = path),
            displayPath = shortenHomePath(path = path, userHome = userHome),
            branch = branch,
            exists = exists,
            isGrouped = isGrouped,
            repoSlug = repoSlugForRecentProject(
                path = path,
                workTreeDirectory = context.workTreeDirectory,
                mappingsByFolder = context.mappingsByFolder
            ),
            isPullRequestWorktree = isPullRequestWorktreePath(path = path, workTreeDirectory = context.workTreeDirectory),
            isCurrentProject = context.currentProjectKey != null && normalizedFolderKey(path = path) == context.currentProjectKey
        )
    }

    private suspend fun readBranchOrNull(path: String): String? {
        return try {
            readBranch(Path.of(path))
        } catch (exception: InvalidPathException) {
            LOG.debug("Not a readable path for a recent project", exception)
            null
        }
    }

    private fun applyLoaded(generation: Int, loaded: LoadedRows) {
        if (generation != refreshGeneration.get()) {
            LOG.debug("Dropping a stale recent projects load")
            return
        }
        LOG.debug("Loaded ${loaded.groups.size} recent project groups and ${loaded.ungrouped.size} ungrouped projects")
        avatarUrlsBySlug = loaded.avatarUrls
        state.value = state.value.copy(
            isLoading = false,
            groups = loaded.groups,
            ungrouped = loaded.ungrouped,
            loadFailed = false
        )
        prefetchAvatars(loaded = loaded)
    }

    private fun prefetchAvatars(loaded: LoadedRows) {
        val slugs = (loaded.groups.flatMap { group -> group.projects } + loaded.ungrouped).mapNotNull { row -> row.repoSlug }.toSet()
        val urls = slugs.mapNotNull { slug -> loaded.avatarUrls[slug] }
        if (urls.isEmpty()) {
            return
        }
        avatars.prefetch(urls = urls) { bumpAvatarRevision() }
    }

    private fun bumpAvatarRevision() {
        runOnEdt { state.value = state.value.copy(avatarRevision = state.value.avatarRevision + 1) }
    }

    private fun applyFailure(generation: Int, exception: Exception) {
        if (generation != refreshGeneration.get()) {
            return
        }
        state.value = state.value.copy(
            isLoading = false,
            loadFailed = true
        )
        onLoadFailed(exception)
    }

    /** Platform ids of remote or provider entries have no path separator; only real paths are listed. */
    private fun isFileSystemPath(path: String): Boolean {
        return path.contains('/') || path.contains('\\')
    }

    private fun pathOrNull(value: String): Path? {
        return try {
            Path.of(value)
        } catch (exception: InvalidPathException) {
            LOG.debug("Not a readable path for a recent project", exception)
            null
        }
    }

    private fun folderName(path: String): String {
        return path.trimEnd('/', '\\').substringAfterLast(delimiter = '/').substringAfterLast(delimiter = '\\')
    }

    companion object {
        private val LOG = logger<RecentProjectsViewModel>()
        private val CHANGE_DEBOUNCE = 200.milliseconds
        private const val BRANCH_READ_PARALLELISM = 4
        private const val PLACEHOLDER_SIZE = 32
    }
}