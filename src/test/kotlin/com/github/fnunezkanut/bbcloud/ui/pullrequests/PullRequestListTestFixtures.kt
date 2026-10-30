package com.github.fnunezkanut.bbcloud.ui.pullrequests

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewingPullRequest
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Builds a real [PullRequestListEntry] for the pure merge/filter/view-model tests; no mocks, as the plan requires.
 *
 * [updatedOn] defaults to null, which anchors the created/updated instants to "now" so they stay inside
 * PullRequestRelativeTime's 7-day relative window: the absolute fallback (DateFormatUtil) needs a running IntelliJ
 * Application, which a plain unit test does not have. Tests that care about ordering pass an explicit timestamp.
 */
internal fun listEntry(
    id: Long,
    source: PullRequestListingSource,
    workspace: String = "acme",
    repoSlug: String = "repo",
    state: PullRequestState = PullRequestState.Open,
    isDraft: Boolean = false,
    authorUuid: String? = null,
    approverUuids: Set<String> = emptySet(),
    updatedOn: String? = null
): PullRequestListEntry {
    val updatedInstant = updatedOn?.let { Instant.parse(it) } ?: Clock.System.now()
    val summary = PullRequestSummary(
        id = id,
        title = "PR $id",
        authorDisplayName = "Author $id",
        authorAvatarUrl = null,
        createdOn = updatedInstant,
        updatedOn = updatedInstant,
        sourceBranch = "feature/$id",
        destinationBranch = "main",
        commentCount = 0,
        webUrl = "https://bitbucket.org/$workspace/$repoSlug/pull-requests/$id",
        state = state,
        isDraft = isDraft,
        authorUuid = authorUuid,
        approverUuids = approverUuids
    )
    return PullRequestListEntry(
        pullRequest = ReviewingPullRequest(summary = summary, workspace = workspace, repoSlug = repoSlug),
        source = source,
        repoAvatarUrl = null
    )
}