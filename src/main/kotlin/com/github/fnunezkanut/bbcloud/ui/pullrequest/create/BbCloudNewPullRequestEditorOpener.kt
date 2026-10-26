package com.github.fnunezkanut.bbcloud.ui.pullrequest.create

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

/** Opens or focuses the single `new.pr` create-pull-request tab for one project and repository. */
@Service(Service.Level.PROJECT)
class BbCloudNewPullRequestEditorOpener(private val project: Project) {
    /** Opens the form, or focuses the existing tab for the same repository (keeping its draft). Returns true. */
    fun open(context: NewPullRequestContext): Boolean {
        val fileManager = FileEditorManager.getInstance(project)
        val existing = fileManager.openFiles.filterIsInstance<BbCloudNewPullRequestVirtualFile>().firstOrNull { file ->
            file.context.workspace == context.workspace && file.context.repositorySlug == context.repositorySlug
        }
        if (existing != null) {
            LOG.debug("Focusing the existing new pull request tab for ${context.workspace}/${context.repositorySlug}")
            fileManager.openFile(existing, true)
            return true
        }
        LOG.debug("Opening a new pull request tab for ${context.workspace}/${context.repositorySlug}")
        val file = BbCloudNewPullRequestVirtualFile(project = project, context = context)
        fileManager.openFile(file, true)
        return true
    }

    companion object {
        private val LOG = logger<BbCloudNewPullRequestEditorOpener>()
    }
}