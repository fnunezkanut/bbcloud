package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestFileCommentCounts
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestPathMatcher
import com.github.fnunezkanut.bbcloud.git.FileLineCount
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil

/**
 * Maps the platform changes into the plain row model rendered by the Jewel file tree.
 *
 * The comment count is the same one the old Swing `ChangeNodeDecorator` appended after the file name, and the status
 * color is the one the platform browser used for the file name.
 *
 * The per-file line counts come from the local Git `--numstat` diff and are keyed by the normalized relative path,
 * so each row is resolved through [PullRequestPathMatcher.normalize] only, the same lookup the work tree review bar
 * uses. A missing or empty map leaves [PullRequestFileChangeRow.lineCount] null, which hides the line badge.
 */
internal fun toFileChangeRows(
    changes: List<Change>,
    commentCounts: PullRequestFileCommentCounts?,
    projectBasePath: String?,
    fileLineCounts: Map<String, FileLineCount>? = null
): List<PullRequestFileChangeRow> {
    return changes.mapIndexed { index, change ->
        val absolutePath = ChangesUtil.getFilePath(change).path
        val relativePath = PullRequestPathMatcher.relativeProjectPath(
            projectBasePath = projectBasePath,
            absoluteOrRelativePath = absolutePath
        )
        val commentCount = commentCounts?.countForChangePath(
            projectBasePath = projectBasePath,
            changeRelativePath = relativePath
        ) ?: 0
        val lineCount = fileLineCounts?.get(PullRequestPathMatcher.normalize(relativePath))
        PullRequestFileChangeRow(
            changeId = index,
            relativePath = relativePath,
            fileName = relativePath.substringAfterLast('/'),
            commentCount = commentCount,
            statusColorArgb = change.fileStatus.color?.rgb,
            lineCount = lineCount
        )
    }
}