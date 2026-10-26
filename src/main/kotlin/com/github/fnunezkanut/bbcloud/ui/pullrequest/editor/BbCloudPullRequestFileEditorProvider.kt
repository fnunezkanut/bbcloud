package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudOpenPullRequestService
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.credentials.BbCloudCredentialStore
import com.github.fnunezkanut.bbcloud.git.BbCloudBranchCheckoutService
import com.github.fnunezkanut.bbcloud.git.BbCloudPullRequestBranchDiffService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class BbCloudPullRequestFileEditorProvider :
    FileEditorProvider,
    DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file is BbCloudPullRequestVirtualFile && file.isValid
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val virtualFile = file as BbCloudPullRequestVirtualFile
        val configurationService = BbCloudConfigurationService.getInstance()
        return BbCloudPullRequestFileEditor(
            project = project,
            file = virtualFile,
            configurationService = configurationService,
            openPullRequestService = createOpenPullRequestService(configurationService),
            branchCheckoutService = BbCloudBranchCheckoutService(),
            branchDiffService = BbCloudPullRequestBranchDiffService()
        )
    }

    override fun getEditorTypeId(): String {
        return EDITOR_TYPE_ID
    }

    override fun getPolicy(): FileEditorPolicy {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR
    }

    private fun createOpenPullRequestService(configurationService: BbCloudConfigurationService): BbCloudOpenPullRequestService {
        val application = ApplicationManager.getApplication()
        return BbCloudOpenPullRequestService(
            credentialStore = application.service<BbCloudCredentialStore>(),
            pullRequestClient = com.github.fnunezkanut.bbcloud.bitbucket.OkHttpBitbucketPullRequestClient(),
            configurationService = configurationService
        )
    }

    companion object {
        const val EDITOR_TYPE_ID = "BbCloudPullRequest"
    }
}