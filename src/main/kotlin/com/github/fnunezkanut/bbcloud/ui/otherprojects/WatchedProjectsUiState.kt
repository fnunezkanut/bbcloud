package com.github.fnunezkanut.bbcloud.ui.otherprojects

import androidx.compose.ui.state.ToggleableState
import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryUrls
import com.github.fnunezkanut.bbcloud.project.LocalProjectRepositorySlug
import com.github.fnunezkanut.bbcloud.project.WatchedProjectMapping
import com.github.fnunezkanut.bbcloud.project.WatchedProjectWorktreeRow
import java.nio.file.Files
import java.nio.file.Path

data class WatchedProjectRow(
    val mapping: WatchedProjectMapping,
    val worktrees: List<WatchedProjectWorktreeRow>,
    val remoteUrl: String?,
    val localSlug: String,
    val isLocalOpenable: Boolean,
    val avatarUrl: String?
)

enum class WatchedProjectsCard {
    Loading,
    Listing,
    Empty
}

data class WatchedProjectsUiState(
    val card: WatchedProjectsCard = WatchedProjectsCard.Empty,
    val rows: List<WatchedProjectRow> = emptyList(),
    val rescanFailed: Boolean = false,
    val syncVisible: Boolean = false,
    val syncEnabled: Boolean = true,
    val pruneEnabled: Boolean = true,
    val masterToggle: ToggleableState = ToggleableState.Off,
    val avatarRevision: Int = 0,
    /** Session-only filter text for the table; [rows] stays complete and [visibleRows] applies this query. */
    val searchQuery: String = "",
    /** Persisted "Show only watched" checkbox; seeded by the ViewModel from the data source. */
    val showOnlyWatched: Boolean = false
) {
    /** The rows that are watched (when [showOnlyWatched]) and match [searchQuery], in the same order as [rows]. */
    val visibleRows: List<WatchedProjectRow>
        get() {
            val candidates = if (showOnlyWatched) rows.filter { row -> row.mapping.watched } else rows
            return filterWatchedProjectRows(rows = candidates, query = searchQuery)
        }
}

internal fun deriveMasterToggle(rows: List<WatchedProjectRow>): ToggleableState {
    val watchedCount = rows.count { row -> row.mapping.watched }
    return when {
        watchedCount == 0 -> ToggleableState.Off
        watchedCount == rows.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
}

private val WHITESPACE = Regex("\\s+")

/**
 * Keeps the rows where every whitespace-separated token of [query] is contained, ignoring case, in at least one of the
 * row's searchable texts (see [searchableText]). A blank query returns [rows] unchanged.
 */
internal fun filterWatchedProjectRows(rows: List<WatchedProjectRow>, query: String): List<WatchedProjectRow> {
    val tokens = query.trim().lowercase().split(WHITESPACE).filter { token -> token.isNotEmpty() }
    if (tokens.isEmpty()) {
        return rows
    }
    return rows.filter { row ->
        val entries = searchableText(row)
        tokens.all { token -> entries.any { entry -> entry.contains(token) } }
    }
}

/**
 * The lower-case texts a search token can match: the remote slug, the local slug, the local folder path, and for each
 * worktree its `#<PR number>` and branch name. A missing branch name contributes an empty entry, never the text "null".
 */
private fun searchableText(row: WatchedProjectRow): List<String> {
    val entries = mutableListOf(row.mapping.remoteRepoSlug, row.localSlug, row.mapping.localFolderPath)
    row.worktrees.forEach { worktree ->
        entries.add("#${worktree.prNumber}")
        entries.add(worktree.branchName.orEmpty())
    }
    return entries.map { entry -> entry.lowercase() }
}

internal fun buildWatchedProjectRow(
    mapping: WatchedProjectMapping,
    organizationUrl: String?,
    avatarUrl: String?,
    worktrees: List<WatchedProjectWorktreeRow> = emptyList()
): WatchedProjectRow {
    return WatchedProjectRow(
        mapping = mapping,
        worktrees = worktrees,
        remoteUrl = organizationUrl?.let { url ->
            BitbucketRepositoryUrls.repositoryWebUrl(
                organizationUrl = url,
                repoSlug = mapping.remoteRepoSlug
            )
        },
        localSlug = LocalProjectRepositorySlug.fromBasePath(mapping.localFolderPath) ?: mapping.remoteRepoSlug,
        isLocalOpenable = mapping.localFolderPath.isNotBlank() && runCatching {
            Files.isDirectory(Path.of(mapping.localFolderPath))
        }.getOrDefault(false),
        avatarUrl = avatarUrl
    )
}