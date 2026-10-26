package com.github.fnunezkanut.bbcloud.bitbucket

/**
 * One pull request of the workspace-wide authored listing, paired with the repository it belongs to.
 *
 * [repoSlug] is null when neither of the pull request links named a repository. Such a row is dropped by the
 * caller rather than shown against an unknown repository, since the repository is what tells two rows apart.
 */
data class AuthoredPullRequestEntry(val summary: PullRequestSummary, val repoSlug: String?)

/**
 * One page of the workspace-wide authored listing. [isTruncated] means more pages were on offer than were read,
 * so the listing is knowingly incomplete rather than the whole set of pull requests the user opened.
 */
data class AuthoredPullRequestPage(val entries: List<AuthoredPullRequestEntry>, val isTruncated: Boolean)