package com.github.fnunezkanut.bbcloud.ui.markdowndiff

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepositoryManager

/** Decides whether the rendered Markdown diff of a file can be opened. */
internal object MarkdownFileEligibility {
    /** True for a local, non-directory Markdown file inside a Git repository of [project]. */
    fun isEligibleFile(project: Project, file: VirtualFile): Boolean {
        if (!isLocalMarkdownFile(file = file)) {
            return false
        }
        return GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(file) != null
    }

    /** The platform-free part of the rule: not a directory, in the local file system, and with a Markdown extension. */
    fun isLocalMarkdownFile(file: VirtualFile): Boolean {
        return !file.isDirectory && file.isInLocalFileSystem && MarkdownFiles.isMarkdownFileName(fileName = file.name)
    }
}