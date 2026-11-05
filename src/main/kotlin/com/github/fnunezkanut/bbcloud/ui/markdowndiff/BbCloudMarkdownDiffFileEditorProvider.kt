package com.github.fnunezkanut.bbcloud.ui.markdowndiff

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class BbCloudMarkdownDiffFileEditorProvider :
    FileEditorProvider,
    DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file is BbCloudMarkdownDiffVirtualFile
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        LOG.debug("Creating the Markdown diff editor")
        return BbCloudMarkdownDiffFileEditor(file = file as BbCloudMarkdownDiffVirtualFile)
    }

    override fun getEditorTypeId(): String {
        return EDITOR_TYPE_ID
    }

    override fun getPolicy(): FileEditorPolicy {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR
    }

    companion object {
        private val LOG = logger<BbCloudMarkdownDiffFileEditorProvider>()
        const val EDITOR_TYPE_ID = "BbCloudMarkdownDiff"
    }
}