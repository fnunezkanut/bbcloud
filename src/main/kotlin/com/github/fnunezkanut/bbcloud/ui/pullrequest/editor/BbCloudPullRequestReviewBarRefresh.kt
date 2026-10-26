package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestPathMatcher
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeLayout
import java.nio.file.Paths

/**
 * The gate of the refresh of the "Requires review" bar of a file.
 *
 * The bar only exists in a pull-request work tree with an active pull request, and only in the files that pull request
 * changes. The gate keeps the refresh out of the way everywhere else: an ordinary project would otherwise pay for a
 * notification refresh on every file it opens, and a file the pull request does not touch has no bar to refresh.
 */
internal object BbCloudPullRequestReviewBarRefresh {
    /**
     * Tells whether the project lives in a pull-request work tree.
     *
     * The work tree folder carries the pull request number, so its name is what tells a work tree from an ordinary
     * project.
     */
    fun isPullRequestWorkTree(projectBasePath: String?): Boolean {
        val basePath = projectBasePath ?: return false
        val folderName = Paths.get(basePath).fileName?.toString() ?: basePath
        return PullRequestWorktreeLayout.prNumberFromWorktreeFolderName(folderName) != null
    }

    /**
     * Tells whether the bar of a file has to be refreshed.
     *
     * The path of the file is the path the platform reports for it, which is an absolute local path for the files of
     * the work tree. It is turned into the project-relative path the changed files of a pull request are recorded with,
     * so the lookup is the same one the bar provider does.
     */
    fun shouldRefresh(projectBasePath: String?, activePullRequestId: Long?, changedFilePaths: Set<String>, filePath: String): Boolean {
        if (activePullRequestId == null) {
            return false
        }
        if (!isPullRequestWorkTree(projectBasePath = projectBasePath)) {
            return false
        }
        val relativePath = PullRequestPathMatcher.relativeProjectPath(
            projectBasePath = projectBasePath,
            absoluteOrRelativePath = filePath
        )
        return changedFilePaths.contains(PullRequestPathMatcher.normalize(relativePath))
    }
}