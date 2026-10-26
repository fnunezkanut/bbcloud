package com.github.fnunezkanut.bbcloud.review

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestPathMatcher
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil

/**
 * The project-relative paths of the files a pull request changes.
 *
 * The paths are the ones the review bar looks up, so the pull-request editor and the work tree context have to
 * derive them the same way: the absolute path of the change is turned into the path relative to the project folder
 * and then normalized.
 */
object PullRequestChangedFilePaths {
    /**
     * The normalized project-relative paths of the changed files.
     *
     * @param projectBasePath the base path of the project the changes belong to.
     * @param changes the changes of the pull request.
     */
    fun from(projectBasePath: String?, changes: Collection<Change>): Set<String> {
        return changes
            .map { change -> ChangesUtil.getFilePath(change).path }
            .map { path ->
                PullRequestPathMatcher.relativeProjectPath(
                    projectBasePath = projectBasePath,
                    absoluteOrRelativePath = path
                )
            }
            .map { path -> PullRequestPathMatcher.normalize(path) }
            .toSet()
    }
}