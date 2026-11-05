package com.github.fnunezkanut.bbcloud.ui.markdowndiff

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorOpenRequest
import com.intellij.openapi.project.Project

/**
 * Opens a Markdown diff tab for a [MarkdownDiffContentSource]. When a tab for the same source key is already open it is
 * focused and reloaded instead, so a second request shows the current content.
 */
@Service(Service.Level.PROJECT)
class BbCloudMarkdownDiffEditorOpener(private val project: Project) {
    fun open(source: MarkdownDiffContentSource) {
        val fileManager = FileEditorManager.getInstance(project)
        val existing = fileManager.openFiles
            .filterIsInstance<BbCloudMarkdownDiffVirtualFile>()
            .firstOrNull { file -> file.source.key == source.key }
        if (existing != null) {
            LOG.debug("Focusing the existing Markdown diff tab")
            fileManager.requestOpenFile(existing, FileEditorOpenRequest.withFocus(requestFocus = true))
            fileManager.getEditors(existing)
                .filterIsInstance<BbCloudMarkdownDiffFileEditor>()
                .forEach { editor -> editor.reload() }
            return
        }
        LOG.debug("Opening a new Markdown diff tab")
        fileManager.requestOpenFile(
            BbCloudMarkdownDiffVirtualFile(project = project, source = source),
            FileEditorOpenRequest.withFocus(requestFocus = true)
        )
    }

    companion object {
        private val LOG = logger<BbCloudMarkdownDiffEditorOpener>()

        fun getInstance(project: Project): BbCloudMarkdownDiffEditorOpener {
            return project.service()
        }
    }
}