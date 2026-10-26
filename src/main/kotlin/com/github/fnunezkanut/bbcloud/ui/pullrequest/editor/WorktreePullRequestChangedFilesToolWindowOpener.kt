package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.git.BbCloudPullRequestBranchDiffService
import com.github.fnunezkanut.bbcloud.git.BranchDiffResult
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.CompareWithLocalDialog

/**
 * Opens IntelliJ's left-docked Version Control changes browser (tool window id `VcsChanges`) with the pull request's
 * three-dot branch diff (`destination...source`), the same file set the plugin's own Changes tab and review bar use.
 *
 * The platform's [CompareWithLocalDialog] loading panel runs [BbCloudPullRequestBranchDiffService.loadPullRequestChanges]
 * on a background thread through its own loader, so the click handler stays synchronous on the EDT; only the
 * [ThrowableComputable] body touches Git.
 */
internal object WorktreePullRequestChangedFilesToolWindowOpener {
    private val LOG = logger<WorktreePullRequestChangedFilesToolWindowOpener>()

    /**
     * Shows the Version Control changes browser for the pull request diff between [destinationBranch] (merge target)
     * and [sourceBranch] (the checked-out pull request branch). Must be called on the EDT; returns without opening
     * anything when [project] is already disposed.
     */
    @RequiresEdt
    fun open(
        project: Project,
        destinationBranch: String,
        sourceBranch: String,
        diffService: BbCloudPullRequestBranchDiffService = BbCloudPullRequestBranchDiffService()
    ) {
        if (project.isDisposed) {
            LOG.info("Not opening the changed files tool window: project ${project.name} is disposed")
            return
        }
        val tabTitle = BbCloudBundle.message(
            "worktree.todoRow.openChangedFiles.tabTitle",
            destinationBranch,
            sourceBranch
        )
        val changesLoader = ThrowableComputable<Collection<Change>, VcsException> {
            val result = diffService.loadPullRequestChanges(
                project = project,
                destinationBranch = destinationBranch,
                sourceBranch = sourceBranch
            )
            branchDiffChangesOrThrow(result = result)
        }
        CompareWithLocalDialog.showChanges(
            project,
            tabTitle,
            CompareWithLocalDialog.LocalContent.NONE,
            changesLoader
        )
    }

    /**
     * Maps a [BranchDiffResult] to the change list the platform changes browser expects, translating the failure
     * variants into a [VcsException] carrying a user-safe message drawn from the shared bundle (reusing the Changes
     * tab's `main.branch.filesChanged.*` strings). Pure and unit-testable: it performs no Git or UI work.
     */
    @Throws(VcsException::class)
    fun branchDiffChangesOrThrow(result: BranchDiffResult): Collection<Change> {
        return when (result) {
            is BranchDiffResult.Success -> {
                result.changes
            }
            is BranchDiffResult.NoRepository -> {
                throw VcsException(BbCloudBundle.message("main.branch.filesChanged.noRepository"))
            }
            is BranchDiffResult.BranchNotFound -> {
                throw VcsException(BbCloudBundle.message("main.branch.filesChanged.branchNotFound", result.branchName))
            }
            is BranchDiffResult.Failed -> {
                val message = if (result.message.isBlank()) {
                    BbCloudBundle.message("main.branch.filesChanged.failed")
                } else {
                    BbCloudBundle.message("main.branch.filesChanged.failedWithDetail", result.message)
                }
                throw VcsException(message)
            }
        }
    }
}