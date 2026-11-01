package com.github.fnunezkanut.bbcloud.ui.worktree

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.git.WorktreeSetupResult
import java.nio.file.Path

/**
 * The outcome of preparing a pull request's git work tree. Shared by the Workspace and Current Project panels and by the
 * browser-triggered REST open path, so every entry point reports the same results.
 */
sealed interface WorktreeOpenOutcome {
    /** The work tree at [worktreePath] is ready. [summary] is the pull request as Bitbucket reported it during the open check. */
    data class Prepared(val summary: PullRequestSummary, val worktreePath: Path) : WorktreeOpenOutcome

    /**
     * Preparation failed. [content] is the already-localized body for the user: a bundle message for a precondition
     * failure, or the real git output for a git failure. Callers show it and never redirect automatically.
     */
    data class Failed(val content: String) : WorktreeOpenOutcome

    /**
     * The source clone is on the pull request branch, so git refuses a second worktree on it. The panels resolve this
     * with a branch switch or the explanatory modal. The REST path only reports it.
     */
    data class BranchCollision(val branchName: String, val sourceCheckoutPath: Path) : WorktreeOpenOutcome
}

/** Pure mapping from git and Bitbucket results to [WorktreeOpenOutcome] values, shared by every open entry point. */
object WorktreeOpenMapping {
    /** The failure for a pull request that Bitbucket reports as merged or declined. */
    fun notOpenFailure(prNumber: Long, state: PullRequestState): WorktreeOpenOutcome.Failed {
        return WorktreeOpenOutcome.Failed(
            content = BbCloudBundle.message("otherProjects.worktree.failed.prNotOpen", prNumber, state)
        )
    }

    /** The failure for a pull request that Bitbucket does not return. */
    fun notFoundFailure(prNumber: Long): WorktreeOpenOutcome.Failed {
        return WorktreeOpenOutcome.Failed(content = BbCloudBundle.message("otherProjects.worktree.failed.prNotFound", prNumber))
    }

    /** The failure for a pull request whose state could not be verified with Bitbucket. */
    fun stateCheckFailure(prNumber: Long): WorktreeOpenOutcome.Failed {
        return WorktreeOpenOutcome.Failed(
            content = BbCloudBundle.message("otherProjects.worktree.failed.prStateCheckFailed", prNumber)
        )
    }

    /** Maps the result of [com.github.fnunezkanut.bbcloud.git.BbCloudWorktreeService.ensureWorktree] to an outcome. */
    fun setupOutcome(summary: PullRequestSummary, setup: WorktreeSetupResult): WorktreeOpenOutcome {
        return when (setup) {
            is WorktreeSetupResult.Success -> {
                WorktreeOpenOutcome.Prepared(summary = summary, worktreePath = setup.worktreePath)
            }
            is WorktreeSetupResult.Failed -> {
                WorktreeOpenOutcome.Failed(content = setup.output)
            }
            is WorktreeSetupResult.BranchCheckedOutInSource -> {
                WorktreeOpenOutcome.BranchCollision(
                    branchName = setup.branchName,
                    sourceCheckoutPath = setup.sourceCheckoutPath
                )
            }
        }
    }
}