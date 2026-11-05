package com.github.fnunezkanut.bbcloud.project

import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Whether [path] is a PR work tree: a folder directly under [workTreeDirectory] named `<repoSlug>--pr-<n>`.
 *
 * This mirrors the safety check in `BbCloudWorktreeService.removeWorktree` (same normalization, same layout check), so
 * the Remove work tree button is shown exactly when the removal will be accepted.
 */
fun isPullRequestWorktreePath(path: String, workTreeDirectory: String?): Boolean {
    if (workTreeDirectory.isNullOrBlank()) {
        return false
    }
    val worktree = normalizedPathOrNull(path = path) ?: return false
    val root = normalizedPathOrNull(path = workTreeDirectory) ?: return false
    val folderName = worktree.fileName?.toString() ?: return false
    return worktree.parent == root && PullRequestWorktreeLayout.repoSlugFromWorktreeFolderName(folderName = folderName) != null
}

/**
 * The Bitbucket repository slug of a recent project, or null when it is unknown.
 *
 * A PR work tree gives its slug through its folder name. Otherwise [mappingsByFolder] (normalized absolute local folder
 * to remote slug, see [normalizedFolderKey]) is consulted. The folder name is never used as a guess, because a local
 * clone folder can differ from the Bitbucket slug.
 */
fun repoSlugForRecentProject(path: String, workTreeDirectory: String?, mappingsByFolder: Map<String, String>): String? {
    if (isPullRequestWorktreePath(path = path, workTreeDirectory = workTreeDirectory)) {
        val folderName = normalizedPathOrNull(path = path)?.fileName?.toString()
        val slug = folderName?.let { name -> PullRequestWorktreeLayout.repoSlugFromWorktreeFolderName(folderName = name) }
        if (slug != null) {
            return slug
        }
    }
    val key = normalizedFolderKey(path = path) ?: return null
    return mappingsByFolder[key]
}

/** The normalized absolute form of [path] with forward slashes, or null when [path] is not a valid path. */
fun normalizedFolderKey(path: String): String? {
    return normalizedPathOrNull(path = path)?.toString()?.replace('\\', '/')
}

private fun normalizedPathOrNull(path: String): Path? {
    if (path.isBlank()) {
        return null
    }
    return try {
        Path.of(path).toAbsolutePath().normalize()
    } catch (exception: InvalidPathException) {
        null
    }
}