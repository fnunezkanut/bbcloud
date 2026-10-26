package com.github.fnunezkanut.bbcloud.ui.worktree

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.nio.file.Path
import javax.swing.SwingUtilities

/**
 * The explanatory modal shown when a pull request work tree cannot be opened because the source clone (the main
 * checkout) is currently on the pull request branch, so git refuses to check it out a second time. This is a
 * user-resolvable precondition, not a generic git error, so it gets a warning dialog that explains how to resolve it
 * rather than the raw git-output balloon.
 *
 * Shared by both PR tables so the wording and buttons cannot drift between entry points. The dialog has no navigation
 * side effects: it only returns the user's [Choice]; the caller performs any browser navigation so the Bitbucket URL
 * stays owned by the panel that knows it.
 */
object WorktreeBranchCollisionDialog {
    /** The user's response to the branch-collision explanation modal. */
    enum class Choice { OPEN_IN_BITBUCKET, DISMISS }

    /**
     * Shows the modal on the EDT and blocks until the user responds, returning their [Choice]. [prNumber] fills the
     * title, while [branchName] and [sourceCheckoutPath] fill the body.
     */
    fun show(project: Project, prNumber: Long, branchName: String, sourceCheckoutPath: Path): Choice {
        return runOnEdtAndWait {
            val title = BbCloudBundle.message("otherProjects.worktree.branchCheckedOut.title", prNumber)
            val message = BbCloudBundle.message(
                "otherProjects.worktree.branchCheckedOut.message",
                branchName,
                sourceCheckoutPath.toString()
            )
            val options = arrayOf(
                BbCloudBundle.message("otherProjects.worktree.branchCheckedOut.openInBitbucket"),
                BbCloudBundle.message("otherProjects.worktree.branchCheckedOut.dismiss")
            )
            val chosenIndex = Messages.showDialog(
                project,
                message,
                title,
                options,
                // defaultOptionIndex =
                1,
                Messages.getWarningIcon()
            )
            val choice = if (chosenIndex == 0) Choice.OPEN_IN_BITBUCKET else Choice.DISMISS
            LOG.info("User chose $choice on the work-tree branch-collision modal for PR #$prNumber")
            choice
        }
    }

    private fun <T> runOnEdtAndWait(action: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) {
            return action()
        }
        var result: T? = null
        ApplicationManager.getApplication().invokeAndWait { result = action() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private val LOG = logger<WorktreeBranchCollisionDialog>()
}