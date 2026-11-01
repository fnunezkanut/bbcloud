package com.github.fnunezkanut.bbcloud.ui.pullrequest.help

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class BbCloudWorktreesHelpFileEditorProvider :
    FileEditorProvider,
    DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file is BbCloudWorktreesHelpVirtualFile
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        LOG.debug("Creating the Work Trees help editor")
        return BbCloudWorktreesHelpFileEditor(file = file as BbCloudWorktreesHelpVirtualFile)
    }

    override fun getEditorTypeId(): String {
        return EDITOR_TYPE_ID
    }

    override fun getPolicy(): FileEditorPolicy {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR
    }

    companion object {
        private val LOG = logger<BbCloudWorktreesHelpFileEditorProvider>()
        const val EDITOR_TYPE_ID = "BbCloudWorktreesHelp"
    }
}