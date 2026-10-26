package com.github.fnunezkanut.bbcloud.ui.pullrequest.create

import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudOpenPullRequestService
import com.github.fnunezkanut.bbcloud.bitbucket.OkHttpBitbucketPullRequestClient
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.credentials.BbCloudCredentialStore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class BbCloudNewPullRequestFileEditorProvider :
    FileEditorProvider,
    DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        if (file !is BbCloudNewPullRequestVirtualFile) {
            LOG.debug("Rejecting file editor for ${file.name}: not a BbCloudNewPullRequestVirtualFile")
            return false
        }
        return true
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        LOG.info("Opening BbCloudNewPullRequest editor for new.pr")
        val virtualFile = file as BbCloudNewPullRequestVirtualFile
        return BbCloudNewPullRequestFileEditor(
            project = project,
            file = virtualFile,
            openPullRequestService = createOpenPullRequestService(BbCloudConfigurationService.getInstance())
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
            pullRequestClient = OkHttpBitbucketPullRequestClient(),
            configurationService = configurationService
        )
    }

    companion object {
        private val LOG = logger<BbCloudNewPullRequestFileEditorProvider>()
        const val EDITOR_TYPE_ID = "BbCloudNewPullRequest"
    }
}