package com.github.fnunezkanut.bbcloud.ui.pullrequest.create

/** The loading state of the repository branch list. */
sealed interface BranchesLoad {
    data object Loading : BranchesLoad

    data object Loaded : BranchesLoad

    data object Failed : BranchesLoad
}

/** The result of checking whether an OPEN pull request already exists for the selected source and destination. */
sealed interface ExistingPullRequestCheck {
    data object Idle : ExistingPullRequestCheck

    data object Checking : ExistingPullRequestCheck

    data object None : ExistingPullRequestCheck

    data class Found(val pullRequestId: Long, val webUrl: String) : ExistingPullRequestCheck

    data object Failed : ExistingPullRequestCheck
}

/**
 * The result of checking the current project's working tree before allowing a create, which would switch the checkout
 * to the main branch.
 */
sealed interface WorkingTreeCheck {
    data object Checking : WorkingTreeCheck

    data object Clean : WorkingTreeCheck

    data object Unknown : WorkingTreeCheck

    data class BlockedUncommittedChanges(val currentBranch: String?) : WorkingTreeCheck
}

/** One reviewer row of the new pull request form. */
data class NewPullRequestReviewerRow(val uuid: String, val displayName: String, val avatarUrl: String?)

/** The full immutable state the new pull request form renders from. */
data class NewPullRequestUiState(
    val title: String = "",
    val branchesLoad: BranchesLoad = BranchesLoad.Loading,
    val sourceOptions: List<String> = emptyList(),
    val selectedSource: String? = null,
    val destinationOptions: List<String> = emptyList(),
    val selectedDestination: String? = null,
    val isBranchListTruncated: Boolean = false,
    val reviewers: List<NewPullRequestReviewerRow> = emptyList(),
    val isReviewersLoading: Boolean = false,
    val closeSourceBranch: Boolean = true,
    val existingPullRequest: ExistingPullRequestCheck = ExistingPullRequestCheck.Idle,
    val workingTree: WorkingTreeCheck = WorkingTreeCheck.Checking,
    val isSubmitting: Boolean = false,
    val failureMessage: String? = null,
    val avatarRevision: Int = 0
)