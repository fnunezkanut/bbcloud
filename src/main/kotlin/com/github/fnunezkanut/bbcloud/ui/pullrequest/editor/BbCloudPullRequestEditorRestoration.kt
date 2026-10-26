package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

data class BbCloudPullRequestEditorRestorationRequest(val projectBasePath: String, val organizationUrl: String, val pullRequestId: Long)

object BbCloudPullRequestEditorRestoration {
    fun request(projectBasePath: String?, file: BbCloudPullRequestVirtualFile): BbCloudPullRequestEditorRestorationRequest? {
        val basePath = projectBasePath ?: return null
        return BbCloudPullRequestEditorRestorationRequest(
            projectBasePath = basePath,
            organizationUrl = file.pullRequestPath.workspaceUrl,
            pullRequestId = file.pullRequestPath.pullRequestId
        )
    }
}