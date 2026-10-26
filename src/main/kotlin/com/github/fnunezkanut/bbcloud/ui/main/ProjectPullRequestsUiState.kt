package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewingPullRequest
import com.github.fnunezkanut.bbcloud.ui.otherprojects.WorkspacePullRequestRow
import com.github.fnunezkanut.bbcloud.ui.otherprojects.buildWorkspacePullRequestRow

/** Compose card states for the Project - Open PRs and Project - Completed PRs tabs. */
enum class ProjectPullRequestsCard {
    Hidden,
    Loading,
    Listing,
    Empty,
    Error
}

data class ProjectPullRequestsUiState(
    val card: ProjectPullRequestsCard = ProjectPullRequestsCard.Hidden,
    val rows: List<WorkspacePullRequestRow> = emptyList(),
    val avatarRevision: Int = 0
)

internal object ProjectOpenPrCountIndicator {
    fun isVisible(card: ProjectPullRequestsCard, rows: Int): Boolean {
        return card == ProjectPullRequestsCard.Listing && rows > 0
    }
}

internal fun buildProjectPullRequestRow(
    summary: PullRequestSummary,
    organizationUrl: String?,
    workspace: String,
    repoSlug: String,
    repoAvatarUrl: String?
): WorkspacePullRequestRow {
    // Project tabs show one repository, whose identity is resolved once per refresh rather than per PR row.
    return buildWorkspacePullRequestRow(
        pullRequest = ReviewingPullRequest(
            summary = summary,
            workspace = workspace,
            repoSlug = repoSlug
        ),
        organizationUrl = organizationUrl,
        authorAvatarUrl = summary.authorAvatarUrl,
        repoAvatarUrl = repoAvatarUrl
    )
}