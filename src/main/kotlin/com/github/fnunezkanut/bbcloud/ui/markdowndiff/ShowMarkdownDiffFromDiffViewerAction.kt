package com.github.fnunezkanut.bbcloud.ui.markdowndiff

import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction

/**
 * "Show Rendered Markdown Diff" in the toolbar of the IDE diff viewer. It takes the two texts the viewer compares, so it
 * works for every two-sided diff of a Markdown file: pull request changes, local changes, commits, and branch
 * comparisons.
 */
internal class ShowMarkdownDiffFromDiffViewerAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val request = event.getData(DiffDataKeys.DIFF_REQUEST) as? ContentDiffRequest
        event.presentation.isEnabledAndVisible =
            event.project != null && request != null && MarkdownDiffFromDiffRequest.markdownContentsOf(request = request) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val request = event.getData(DiffDataKeys.DIFF_REQUEST) as? ContentDiffRequest ?: return
        LOG.info("User opened the rendered Markdown diff from the diff viewer")
        MarkdownDiffFromDiffRequest.open(project = project, request = request)
    }

    companion object {
        private val LOG = logger<ShowMarkdownDiffFromDiffViewerAction>()
    }
}