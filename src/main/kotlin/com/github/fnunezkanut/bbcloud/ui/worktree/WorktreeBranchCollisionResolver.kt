package com.github.fnunezkanut.bbcloud.ui.worktree

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudOpenPullRequestService
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.git.BbCloudBranchCheckoutService
import com.github.fnunezkanut.bbcloud.git.BranchCheckoutResult
import com.github.fnunezkanut.bbcloud.git.WorktreeCollisionSwitchDecision
import com.github.fnunezkanut.bbcloud.git.WorktreeCollisionSwitchPlan
import com.github.fnunezkanut.bbcloud.git.collisionMainBranch
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.nio.file.Path

/**
 * Shared resolution of a work-tree branch collision for both PR tables. When the clone sitting on the pull request
 * branch is this window's own checkout and the working tree is clean, it switches the checkout to the repository main
 * branch so the branch is freed, then reports [CollisionResolution.Switched] so the caller retries the work tree
 * preparation. When the checkout is dirty it shows the uncommitted-changes modal, when the switch fails it shows the
 * switch-failed balloon, and when neither applies it reports [CollisionResolution.ShowDialog] so the caller shows the
 * existing [WorktreeBranchCollisionDialog].
 *
 * The resolver owns all three side effects (the uncommitted-changes modal, the switch-failed balloon, and the
 * switched info balloon) so the wording cannot drift between entry points, and it performs any "Open PR in Bitbucket"
 * navigation itself. The caller only refreshes its own listing on [CollisionResolution.Switched] and shows the
 * fallback dialog on [CollisionResolution.ShowDialog].
 */
class WorktreeBranchCollisionResolver(
    private val branchCheckoutService: BbCloudBranchCheckoutService,
    private val openPullRequestService: BbCloudOpenPullRequestService,
    private val notificationGroupId: String = "BbCloud"
) {
    /** The outcome of resolving a collision, driving what the caller does next. */
    sealed interface CollisionResolution {
        /** The current checkout was switched to the main branch; the caller should retry the work tree preparation. */
        data object Switched : CollisionResolution

        /**
         * The resolver already handled the collision: it showed the uncommitted-changes modal or the switch-failed
         * balloon and performed any "Open PR in Bitbucket" navigation. The caller does nothing further.
         */
        data object Handled : CollisionResolution

        /** The collision cannot be resolved automatically; the caller shows the existing collision modal. */
        data object ShowDialog : CollisionResolution
    }

    /**
     * Resolves the collision for [summary] whose branch [branchName] is checked out at [sourceCheckoutPath]. Reads the
     * window's checkout root, the Bitbucket main branch, and the working-tree status, then acts on the
     * [WorktreeCollisionSwitchDecision]. A failed checkout throws nothing; it shows the switch-failed balloon and
     * returns [CollisionResolution.Handled]. Cancellation propagates to the caller.
     */
    suspend fun resolve(
        project: Project,
        workspace: String,
        repoSlug: String,
        summary: PullRequestSummary,
        branchName: String,
        sourceCheckoutPath: Path
    ): CollisionResolution {
        LOG.info("Pull request #${summary.id} branch is checked out in the current project; trying to switch it to the main branch")
        val currentCheckoutRoot = branchCheckoutService.currentCheckoutRoot(project = project)
        val bitbucketMainBranch = openPullRequestService.mainBranchNameFor(workspace = workspace, repoSlug = repoSlug)
        val mainBranchCandidate = collisionMainBranch(
            bitbucketMainBranch = bitbucketMainBranch,
            destinationBranch = summary.destinationBranch,
            prBranch = branchName
        )
        val status = branchCheckoutService.readWorkingTreeStatus(project = project, checkoutRoot = sourceCheckoutPath)
        val plan = WorktreeCollisionSwitchDecision.decide(
            currentCheckoutRoot = currentCheckoutRoot,
            sourceCheckoutPath = sourceCheckoutPath,
            prBranch = branchName,
            mainBranchCandidate = mainBranchCandidate,
            status = status
        )
        return when (plan) {
            is WorktreeCollisionSwitchPlan.SwitchTo -> {
                performSwitch(project = project, summary = summary, branchName = plan.branchName)
            }
            is WorktreeCollisionSwitchPlan.BlockedByUncommittedChanges -> {
                LOG.info("Not switching to ${plan.mainBranchName} for pull request #${summary.id}: uncommitted changes")
                val choice = WorktreeBranchCollisionDialog.showUncommittedChanges(
                    project = project,
                    prNumber = summary.id,
                    branchName = branchName,
                    mainBranchName = plan.mainBranchName
                )
                if (choice == WorktreeBranchCollisionDialog.Choice.OPEN_IN_BITBUCKET) {
                    LOG.info("User opened Bitbucket PR #${summary.id} from the uncommitted-changes modal")
                    BrowserUtil.browse(summary.webUrl)
                }
                CollisionResolution.Handled
            }
            is WorktreeCollisionSwitchPlan.NotTheCurrentCheckout -> {
                LOG.info("Not auto-switching for pull request #${summary.id}: the clone on the PR branch is not this window's checkout")
                CollisionResolution.ShowDialog
            }
            is WorktreeCollisionSwitchPlan.NoMainBranch -> {
                LOG.info("Not auto-switching for pull request #${summary.id}: no usable main branch to switch to")
                CollisionResolution.ShowDialog
            }
            is WorktreeCollisionSwitchPlan.StatusUnavailable -> {
                LOG.info("Not auto-switching for pull request #${summary.id}: the working tree status could not be read")
                CollisionResolution.ShowDialog
            }
        }
    }

    private suspend fun performSwitch(project: Project, summary: PullRequestSummary, branchName: String): CollisionResolution {
        return when (val result = branchCheckoutService.checkout(project = project, branchName = branchName)) {
            is BranchCheckoutResult.Success -> {
                LOG.info("Switched the current checkout to $branchName to free pull request #${summary.id} branch for its work tree")
                showSwitchedNotification(project = project, mainBranchName = branchName, prNumber = summary.id)
                CollisionResolution.Switched
            }
            is BranchCheckoutResult.Failed -> {
                LOG.warn("Could not switch the current checkout to the main branch for pull request #${summary.id}", result.cause)
                showSwitchFailedNotification(project = project, summary = summary, output = result.output)
                CollisionResolution.Handled
            }
        }
    }

    private fun showSwitchedNotification(project: Project, mainBranchName: String, prNumber: Long) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(notificationGroupId)
            .createNotification(
                BbCloudBundle.message("otherProjects.worktree.switchMain.switched", mainBranchName, prNumber),
                NotificationType.INFORMATION
            )
            .notify(project)
    }

    private fun showSwitchFailedNotification(project: Project, summary: PullRequestSummary, output: String) {
        val trimmed = output.trim()
        val balloonContent = when {
            trimmed.isBlank() -> BbCloudBundle.message("newPr.switchMain.failed.generic")
            trimmed.length > MAX_FAILURE_POPUP_LENGTH -> trimmed.substring(0, MAX_FAILURE_POPUP_LENGTH) + "\u2026"
            else -> trimmed
        }
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(notificationGroupId)
            .createNotification(
                BbCloudBundle.message("newPr.switchMain.failed.title", summary.id),
                balloonContent,
                NotificationType.ERROR
            )
        notification.addAction(
            NotificationAction.createSimpleExpiring(BbCloudBundle.message("otherProjects.worktree.branchCheckedOut.openInBitbucket")) {
                BrowserUtil.browse(summary.webUrl)
            }
        )
        notification.notify(project)
    }

    companion object {
        private val LOG = logger<WorktreeBranchCollisionResolver>()

        /** Upper bound on the git error text shown in the switch-failed balloon so it stays readable. */
        private const val MAX_FAILURE_POPUP_LENGTH = 2_000
    }
}