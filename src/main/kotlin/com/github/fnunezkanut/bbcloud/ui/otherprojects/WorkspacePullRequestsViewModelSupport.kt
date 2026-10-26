package com.github.fnunezkanut.bbcloud.ui.otherprojects

import androidx.compose.runtime.MutableState
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewingPullRequest

interface WorkspacePullRequestsViewModelSupport {
    val state: MutableState<WorkspacePullRequestsUiState>

    fun avatarIconFor(url: String?, fallback: javax.swing.Icon): javax.swing.Icon

    fun onOpenLocalProject(pullRequest: ReviewingPullRequest)

    fun onOpenPrOnWeb(pullRequest: ReviewingPullRequest)

    fun onOpenRepository(pullRequest: ReviewingPullRequest)
}