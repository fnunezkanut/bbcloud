package com.github.fnunezkanut.bbcloud.ui.pullrequest.create

/**
 * The repository context the new pull request form is opened for. [currentLocalBranch] is the current checkout's branch
 * name (null for a detached HEAD or when it could not be read), used only to preselect the source branch.
 *
 * [prefilledTitle] and [prefilledDescription] come from the first line and the remaining body of the current checkout's
 * last commit. They are empty when no commit could be read. [worktreePath] is the checkout root used to re-read the last
 * commit of a branch the user selects as the source; null when the project has no base path.
 */
data class NewPullRequestContext(
    val organizationUrl: String,
    val workspace: String,
    val repositorySlug: String,
    val repositoryWebUrl: String,
    val currentLocalBranch: String?,
    val prefilledTitle: String = "",
    val prefilledDescription: String = "",
    val worktreePath: String? = null
)