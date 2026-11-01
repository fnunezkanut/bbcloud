package com.github.fnunezkanut.bbcloud.ui.pullrequest.help

import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile

/**
 * The in-memory virtual file behind the `help.pr` Work Trees help tab. It uses the default non-persistent
 * [LightVirtualFile] file system, so the tab is not restored after an IDE restart. Reopening the help is one click.
 */
class BbCloudWorktreesHelpVirtualFile(private val project: Project) : LightVirtualFile(FILE_NAME, BbCloudWorktreesHelpFileType, "") {
    init {
        putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
    }

    override fun getFileType(): FileType {
        return BbCloudWorktreesHelpFileType
    }

    override fun getName(): String {
        return FILE_NAME
    }

    override fun isWritable(): Boolean {
        return false
    }

    override fun isValid(): Boolean {
        return project.isOpen && !project.isDisposed
    }

    companion object {
        const val FILE_NAME = "help.pr"
    }
}