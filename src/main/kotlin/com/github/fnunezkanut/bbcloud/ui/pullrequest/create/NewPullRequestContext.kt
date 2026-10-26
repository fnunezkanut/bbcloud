package com.github.fnunezkanut.bbcloud.ui.pullrequest.create

/**
 * The repository context the new pull request form is opened for. [currentLocalBranch] is the current checkout's branch
 * name (null for a detached HEAD or when it could not be read), used only to preselect the source branch.
 */
data class NewPullRequestContext(
    val organizationUrl: String,
    val workspace: String,
    val repositorySlug: String,
    val repositoryWebUrl: String,
    val currentLocalBranch: String?
)