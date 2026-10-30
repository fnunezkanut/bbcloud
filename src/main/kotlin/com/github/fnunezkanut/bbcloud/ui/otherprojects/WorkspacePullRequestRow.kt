package com.github.fnunezkanut.bbcloud.ui.otherprojects

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryUrls
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewingPullRequest
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestRelativeTime
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestStateStyle

data class WorkspacePullRequestRow(
    val pullRequest: ReviewingPullRequest,
    val repositoryWebUrl: String?,
    val authorAvatarUrl: String?,
    val repoAvatarUrl: String?,
    val stateLabel: String,
    val state: PullRequestState,
    val isDraft: Boolean,
    val title: String,
    val prId: String,
    val authorDisplayName: String,
    val repoSlug: String,
    val sourceBranch: String,
    val destinationBranch: String,
    val branches: String,
    val updatedRelative: String,
    val createdRelative: String,
    val commentCount: Int
)

internal fun buildWorkspacePullRequestRow(
    pullRequest: ReviewingPullRequest,
    organizationUrl: String?,
    authorAvatarUrl: String?,
    repoAvatarUrl: String?
): WorkspacePullRequestRow {
    val summary = pullRequest.summary
    val repositoryWebUrl = if (pullRequest.workspace.isBlank()) {
        organizationUrl?.let { url ->
            BitbucketRepositoryUrls.repositoryWebUrl(
                organizationUrl = url,
                repoSlug = pullRequest.repoSlug
            )
        }
    } else {
        BitbucketRepositoryUrls.repositoryWebUrlForWorkspace(
            workspace = pullRequest.workspace,
            repoSlug = pullRequest.repoSlug
        )
    }
    return WorkspacePullRequestRow(
        pullRequest = pullRequest,
        repositoryWebUrl = repositoryWebUrl,
        authorAvatarUrl = authorAvatarUrl,
        repoAvatarUrl = repoAvatarUrl,
        stateLabel = PullRequestStateStyle.label(summary.state),
        state = summary.state,
        isDraft = summary.isDraft,
        title = summary.title,
        prId = "#${summary.id}",
        authorDisplayName = summary.authorDisplayName,
        repoSlug = pullRequest.repositoryName(),
        sourceBranch = summary.sourceBranch,
        destinationBranch = summary.destinationBranch,
        branches = BbCloudBundle.message(
            "main.pullRequests.branches",
            summary.sourceBranch,
            summary.destinationBranch
        ),
        updatedRelative = PullRequestRelativeTime.formatRelativeThenAbsolute(date = summary.updatedOn),
        createdRelative = PullRequestRelativeTime.formatRelativeThenAbsolute(date = summary.createdOn),
        commentCount = summary.commentCount
    )
}