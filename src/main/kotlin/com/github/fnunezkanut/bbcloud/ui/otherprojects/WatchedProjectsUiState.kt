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
    val avatarRevision: Int = 0
)

internal fun deriveMasterToggle(rows: List<WatchedProjectRow>): ToggleableState {
    val watchedCount = rows.count { row -> row.mapping.watched }
    return when {
        watchedCount == 0 -> ToggleableState.Off
        watchedCount == rows.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
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