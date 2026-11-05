package com.github.fnunezkanut.bbcloud.ui.markdowndiff

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction

/**
 * "Show Rendered Markdown Diff" for a Markdown file in the editor, the editor tab, the Project view, or the Commit
 * changes list. Opens a diff tab that compares the last commit, the staged version, and the working tree.
 */
internal class ShowMarkdownDiffAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.project
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)
        event.presentation.isEnabledAndVisible = project != null && file != null && MarkdownFileEligibility.isEligibleFile(project = project, file = file)
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: run {
            LOG.info("Show rendered Markdown diff skipped: no file in the action context")
            return
        }
        LOG.info("User opened the rendered Markdown diff for a file from ${event.place}")
        BbCloudMarkdownDiffEditorOpener.getInstance(project).open(
            source = GitMarkdownDiffContentSource(project = project, file = file)
        )
    }

    companion object {
        private val LOG = logger<ShowMarkdownDiffAction>()
    }
}