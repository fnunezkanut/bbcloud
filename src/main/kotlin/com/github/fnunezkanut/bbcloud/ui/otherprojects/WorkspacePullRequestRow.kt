package com.github.fnunezkanut.bbcloud.ui.otherprojects

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryUrls
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewingPullRequest
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestRelativeTime
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestStateStyle
import com.github.fnunezkanut.bbcloud.ui.pullrequests.ReviewerStack
import com.github.fnunezkanut.bbcloud.ui.pullrequests.buildReviewerStack

data class WorkspacePullRequestRow(
    val pullRequest: ReviewingPullRequest,
    val repositoryWebUrl: String?,
    val authorAvatarUrl: String?,
    val repoAvatarUrl: String?,
    val stateLabel: String,
    val state: PullRequestState,
    val isDraft: Boolean,
    /** True when the current user has approved the pull request; the card shows the Approved badge for open rows. */
    val isApprovedByMe: Boolean,
    val title: String,
    val prId: String,
    val authorDisplayName: String,
    val repoSlug: String,
    val sourceBranch: String,
    val destinationBranch: String,
    val branches: String,
    val updatedRelative: String,
    val createdRelative: String,
    val commentCount: Int,
    /** Reviewer avatars for the listing card; empty on the detail info card and for merged or declined rows. */
    val reviewerStack: ReviewerStack = ReviewerStack.EMPTY
)

internal fun buildWorkspacePullRequestRow(
    pullRequest: ReviewingPullRequest,
    organizationUrl: String?,
    authorAvatarUrl: String?,
    repoAvatarUrl: String?,
    isApprovedByMe: Boolean = false
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
        isApprovedByMe = isApprovedByMe,
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
        commentCount = summary.commentCount,
        // Open pull requests only, matching the Approved badge rule; merged and declined rows show no reviewer stack.
        reviewerStack = if (summary.state == PullRequestState.Open) {
            buildReviewerStack(reviewers = summary.reviewers)
        } else {
            ReviewerStack.EMPTY
        }
    )
}