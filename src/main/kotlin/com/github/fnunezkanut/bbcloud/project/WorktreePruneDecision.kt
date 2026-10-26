package com.github.fnunezkanut.bbcloud.project

/** The safe action to take for a discovered pull-request worktree after checking its pull-request state. */
enum class WorktreePruneAction {
    Remove,
    Keep,
    Skip
}

/** Maps pull-request state-check outcomes to safe worktree pruning decisions. */
object WorktreePruneDecision {
    /**
     * Selects the safe action for a worktree based on its pull-request check result. A failed check always skips
     * removal so transient network or authentication failures can never delete a worktree whose pull request may still
     * be open.
     */
    fun actionFor(result: PullRequestOpenCheckResult): WorktreePruneAction {
        return when (result) {
            is PullRequestOpenCheckResult.StillOpen -> WorktreePruneAction.Keep
            is PullRequestOpenCheckResult.NotOpen -> WorktreePruneAction.Remove
            PullRequestOpenCheckResult.NotFound -> WorktreePruneAction.Remove
            is PullRequestOpenCheckResult.Failed -> WorktreePruneAction.Skip
        }
    }
}