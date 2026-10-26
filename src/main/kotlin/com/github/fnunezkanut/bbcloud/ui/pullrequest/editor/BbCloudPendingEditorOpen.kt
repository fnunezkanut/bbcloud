package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary

data class BbCloudPendingEditorOpen(val summary: PullRequestSummary, val branchName: String, val repositoryWebUrl: String, val repositorySlug: String)