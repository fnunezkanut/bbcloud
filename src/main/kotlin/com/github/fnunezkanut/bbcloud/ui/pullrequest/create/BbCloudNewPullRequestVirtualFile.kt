package com.github.fnunezkanut.bbcloud.ui.pullrequest.create

import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile

/**
 * The in-memory virtual file behind the `new.pr` create-pull-request tab. Unlike the `<id>.pr` review tab, this uses the
 * default non-persistent [com.intellij.testFramework.LightVirtualFile] file system, so the tab is not restored after an
 * IDE restart (a half-filled create form is not worth persisting). It is a distinct class with its own file type
 * ([BbCloudNewPullRequestFileType]), so the platform always routes it to the create-form editor rather than the review
 * tab's editor or the default text editor.
 */
class BbCloudNewPullRequestVirtualFile(val project: Project, val context: NewPullRequestContext) :
    LightVirtualFile(FILE_NAME, BbCloudNewPullRequestFileType, "") {
    init {
        putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
    }

    override fun getFileType(): FileType {
        return BbCloudNewPullRequestFileType
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
        const val FILE_NAME = "new.pr"
    }
}