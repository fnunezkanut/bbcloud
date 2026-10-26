package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.ui.pullrequest.create.BbCloudNewPullRequestVirtualFile
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.VirtualFile

class BbCloudPullRequestEditorTabTitleProvider :
    EditorTabTitleProvider,
    DumbAware {
    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
        return null
    }

    override fun getEditorTabTooltipHtml(project: Project, virtualFile: VirtualFile): HtmlChunk? {
        if (virtualFile is BbCloudNewPullRequestVirtualFile) {
            return HtmlChunk.text(BbCloudBundle.message("newPr.tab.tooltip", virtualFile.context.repositorySlug))
        }
        val pullRequestFile = virtualFile as? BbCloudPullRequestVirtualFile ?: return null
        val title = pullRequestFile.pullRequestTitle() ?: return null
        return HtmlChunk.text(title)
    }
}