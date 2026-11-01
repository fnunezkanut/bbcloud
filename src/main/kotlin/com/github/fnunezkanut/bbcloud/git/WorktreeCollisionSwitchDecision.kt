package com.github.fnunezkanut.bbcloud.git

import java.nio.file.Path

/**
 * The outcome of deciding how to resolve a work-tree branch collision: whether the current checkout can be switched to
 * the main branch automatically so the pull request's branch is freed for its own work tree, or whether the collision
 * must fall back to the explanatory modal.
 *
 * @see WorktreeCollisionSwitchDecision
 */
sealed interface WorktreeCollisionSwitchPlan {
    /**
     * The working tree is clean and a usable main branch (different from the pull request branch) is known, so the
     * current checkout can switch to [branchName] and the work tree preparation can be retried.
     */
    data class SwitchTo(val branchName: String) : WorktreeCollisionSwitchPlan

    /**
     * The working tree holds uncommitted or untracked work, so the branch must not be switched. The caller shows the
     * uncommitted-changes warning modal, naming [mainBranchName] as the branch the user would switch to after
     * committing or stashing.
     */
    data class BlockedByUncommittedChanges(val mainBranchName: String) : WorktreeCollisionSwitchPlan

    /**
     * The clone sitting on the pull request branch is not this window's own checkout, so switching its branch could
     * disturb another project or window the user does not see. The caller shows the existing collision modal.
     */
    data object NotTheCurrentCheckout : WorktreeCollisionSwitchPlan

    /**
     * No usable main branch is known (it is unknown, blank, or equal to the pull request branch), so there is nothing
     * safe to switch to. The caller shows the existing collision modal.
     */
    data object NoMainBranch : WorktreeCollisionSwitchPlan

    /**
     * The working tree status could not be read, so the current changes are unknown. This flow deliberately does not
     * switch blind (unlike the new-pull-request flow, which attempts the checkout anyway and lets git refuse) because
     * moving HEAD with unknown local changes is the unsafe choice here. The caller shows the existing collision modal.
     */
    data object StatusUnavailable : WorktreeCollisionSwitchPlan
}

/**
 * Pure decision logic for a work-tree branch collision: given the window's checkout root, the clone that holds the
 * pull request branch, the pull request branch, a candidate main branch, and the working-tree status, it returns the
 * [WorktreeCollisionSwitchPlan] the UI acts on. It holds no IntelliJ APIs (only [java.nio.file.Path]) so it is
 * unit-testable on its own.
 *
 * The work-tree reuse path ([BbCloudWorktreeService] `reuseExistingWorktree`) and the fresh-add path (`addFreshWorktree`)
 * both check out the existing local branch as-is when it already exists; neither runs `reset --hard`, `checkout -B`, or
 * `branch -f` against `origin/<branch>`, so switching the current checkout away keeps any unpushed local commits on the
 * pull request branch, and the work tree then checks out that same local branch including those commits (decision 7a).
 */
object WorktreeCollisionSwitchDecision {
    /**
     * Returns the plan for the collision. Evaluation order matters and is intentional:
     *   1. [NotTheCurrentCheckout] when [currentCheckoutRoot] is null, or it does not resolve to the same path as
     *      [sourceCheckoutPath]. Paths are compared after [Path.normalize]; a real-path resolution is attempted first
     *      (so a macOS `/private/var` symlink does not cause a false negative) and falls back to the normalized path.
     *   2. [NoMainBranch] when [mainBranchCandidate] is null, blank, or equal to [prBranch] after trimming.
     *   3. [StatusUnavailable] when [status] is null.
     *   4. [BlockedByUncommittedChanges] when the working tree has uncommitted or untracked changes.
     *   5. [SwitchTo] otherwise.
     */
    fun decide(
        currentCheckoutRoot: Path?,
        sourceCheckoutPath: Path,
        prBranch: String,
        mainBranchCandidate: String?,
        status: WorkingTreeStatus?
    ): WorktreeCollisionSwitchPlan {
        if (currentCheckoutRoot == null || !sameCheckout(left = currentCheckoutRoot, right = sourceCheckoutPath)) {
            return WorktreeCollisionSwitchPlan.NotTheCurrentCheckout
        }
        val candidate = mainBranchCandidate?.trim()
        if (candidate.isNullOrBlank() || candidate == prBranch.trim()) {
            return WorktreeCollisionSwitchPlan.NoMainBranch
        }
        if (status == null) {
            return WorktreeCollisionSwitchPlan.StatusUnavailable
        }
        if (status.hasUncommittedChanges) {
            return WorktreeCollisionSwitchPlan.BlockedByUncommittedChanges(mainBranchName = candidate)
        }
        return WorktreeCollisionSwitchPlan.SwitchTo(branchName = candidate)
    }

    private fun sameCheckout(left: Path, right: Path): Boolean {
        return canonical(path = left) == canonical(path = right)
    }

    private fun canonical(path: Path): Path {
        return runCatching { path.toRealPath() }.getOrElse { path.toAbsolutePath().normalize() }
    }
}