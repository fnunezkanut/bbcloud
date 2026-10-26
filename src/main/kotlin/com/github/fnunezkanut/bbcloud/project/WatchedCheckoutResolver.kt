package com.github.fnunezkanut.bbcloud.project

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The outcome of looking up the local checkout of a Bitbucket repository in the watched projects.
 */
sealed interface WatchedCheckoutResolution {
    /**
     * The watched entry has a folder that exists on disk as a directory.
     */
    data class Available(val checkoutPath: Path) : WatchedCheckoutResolution

    /**
     * No watched project entry exists for the repository slug in the workspace.
     */
    data object NoEntry : WatchedCheckoutResolution

    /**
     * The watched entry exists, but its stored folder is blank, invalid, or not a directory on disk.
     */
    data class FolderMissing(val localFolderPath: String) : WatchedCheckoutResolution
}

/**
 * Decides whether the local checkout of a repository can be opened. The stored watched project entry is the only
 * source of the folder. The projects directory is never used as a fallback.
 */
object WatchedCheckoutResolver {
    fun resolve(
        mappings: List<WatchedProjectMapping>,
        workspace: String,
        repoSlug: String,
        isDirectory: (Path) -> Boolean = { path -> Files.isDirectory(path) }
    ): WatchedCheckoutResolution {
        val mapping = mappings.firstOrNull { entry ->
            entry.workspace == workspace && entry.remoteRepoSlug == repoSlug
        } ?: return WatchedCheckoutResolution.NoEntry
        val storedPath = mapping.localFolderPath.trim()
        if (storedPath.isEmpty()) {
            return WatchedCheckoutResolution.FolderMissing(localFolderPath = mapping.localFolderPath)
        }
        val checkoutPath = try {
            Paths.get(storedPath).toAbsolutePath().normalize()
        } catch (_: InvalidPathException) {
            return WatchedCheckoutResolution.FolderMissing(localFolderPath = mapping.localFolderPath)
        }
        if (!isDirectory(checkoutPath)) {
            return WatchedCheckoutResolution.FolderMissing(localFolderPath = mapping.localFolderPath)
        }
        return WatchedCheckoutResolution.Available(checkoutPath = checkoutPath)
    }
}