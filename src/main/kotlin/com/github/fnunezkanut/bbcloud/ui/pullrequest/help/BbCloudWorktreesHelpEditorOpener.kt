package com.github.fnunezkanut.bbcloud.ui.pullrequest.help

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorOpenRequest
import com.intellij.openapi.project.Project

/** Opens the single `help.pr` Work Trees help tab for one project, or focuses the tab when it is already open. */
@Service(Service.Level.PROJECT)
class BbCloudWorktreesHelpEditorOpener(private val project: Project) {
    fun open() {
        val fileManager = FileEditorManager.getInstance(project)
        val existing = fileManager.openFiles.filterIsInstance<BbCloudWorktreesHelpVirtualFile>().firstOrNull()
        if (existing != null) {
            LOG.debug("Focusing the existing Work Trees help tab")
            fileManager.requestOpenFile(
                existing,
                FileEditorOpenRequest.withFocus(requestFocus = true)
            )
            return
        }
        LOG.debug("Opening a new Work Trees help tab")
        fileManager.requestOpenFile(
            BbCloudWorktreesHelpVirtualFile(project = project),
            FileEditorOpenRequest.withFocus(requestFocus = true)
        )
    }

    companion object {
        private val LOG = logger<BbCloudWorktreesHelpEditorOpener>()
    }
}