package com.github.fnunezkanut.bbcloud.ui.markdowndiff

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile

/**
 * The in-memory virtual file behind one Markdown diff tab. It carries the [source] that supplies the two texts. It uses
 * the default non-persistent [LightVirtualFile] file system, so the tab is not restored after an IDE restart.
 */
class BbCloudMarkdownDiffVirtualFile(private val project: Project, val source: MarkdownDiffContentSource) :
    LightVirtualFile(BbCloudBundle.message("markdownDiff.tab.title", source.fileName), BbCloudMarkdownDiffFileType, "") {
    init {
        putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
    }

    override fun getFileType(): FileType {
        return BbCloudMarkdownDiffFileType
    }

    override fun isWritable(): Boolean {
        return false
    }

    override fun isValid(): Boolean {
        return project.isOpen && !project.isDisposed
    }
}