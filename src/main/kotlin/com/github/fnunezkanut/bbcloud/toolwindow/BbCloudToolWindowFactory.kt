package com.github.fnunezkanut.bbcloud.toolwindow

import com.github.fnunezkanut.bbcloud.ui.BbCloudToolWindowPanel
import com.github.fnunezkanut.bbcloud.ui.components.BbCloudPluginIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

class BbCloudToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        thisLogger().debug("Creating Bitbucket Cloud PR Review tool window content for project ${project.name}")
        toolWindow.setIcon(BbCloudPluginIcons.prIcon)
        val panel = project.service<BbCloudToolWindowPanel>()
        // Must run before any content is added: the platform evaluates this only while rebuilding the header, so setting
        // it later leaves the tool window name in the header until an unrelated event rebuilds it.
        BbCloudToolWindowIdLabel.hide(toolWindow.component)
        val contentManager = toolWindow.contentManager
        val contents = panel.createContents()
        val currentProjectContent = contents.first()
        contents.forEach { content ->
            contentManager.addContent(content)
        }
        panel.bind(contentManager = contentManager, parentDisposable = currentProjectContent)
        // All three contents share the one panel and are disposed together with the tool window, so a single
        // registration covers the whole header.
        Disposer.register(currentProjectContent) {
            panel.disposePanel()
        }
    }
}