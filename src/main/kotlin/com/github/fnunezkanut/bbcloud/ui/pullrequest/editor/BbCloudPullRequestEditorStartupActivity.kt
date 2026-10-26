package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class BbCloudPullRequestEditorStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        LOG.debug("Pull request editor startup activity ran for project ${project.locationHash}")
        project.service<BbCloudPullRequestEditorOpenQueue>().drainAndOpen(project)
    }

    companion object {
        private val LOG = logger<BbCloudPullRequestEditorStartupActivity>()
    }
}