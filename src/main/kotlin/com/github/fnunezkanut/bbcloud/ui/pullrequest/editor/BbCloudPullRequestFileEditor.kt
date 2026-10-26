package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudOpenPullRequestService
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.git.BbCloudBranchCheckoutService
import com.github.fnunezkanut.bbcloud.git.BbCloudPullRequestBranchDiffService
import com.github.fnunezkanut.bbcloud.ui.pullrequest.session.BbCloudPullRequestEditorSession
import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JComponent

class BbCloudPullRequestFileEditor(
    project: Project,
    private val file: BbCloudPullRequestVirtualFile,
    configurationService: BbCloudConfigurationService,
    openPullRequestService: BbCloudOpenPullRequestService,
    branchCheckoutService: BbCloudBranchCheckoutService,
    branchDiffService: BbCloudPullRequestBranchDiffService
) : FileEditorBase() {
    private val session = BbCloudPullRequestEditorSession(
        project = project,
        file = file,
        configurationService = configurationService,
        openPullRequestService = openPullRequestService,
        branchCheckoutService = branchCheckoutService,
        branchDiffService = branchDiffService,
        parentDisposable = this
    )

    override fun getComponent(): JComponent {
        return session.component
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return session.component
    }

    override fun getName(): String {
        return file.name
    }

    override fun getFile(): VirtualFile {
        return file
    }

    override fun isValid(): Boolean {
        return file.isValid
    }

    override fun selectNotify() {
        session.select()
    }

    override fun dispose() {
        session.dispose()
        super.dispose()
    }
}