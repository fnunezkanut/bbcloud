package com.github.fnunezkanut.bbcloud.ui.pullrequest.create

/** Pure rules for whether the create buttons of the new pull request form are visible and enabled. */
object NewPullRequestSubmitRules {
    /** The create buttons are hidden only when an OPEN pull request already exists for the selected branches. */
    fun areCreateButtonsVisible(existing: ExistingPullRequestCheck): Boolean {
        return existing !is ExistingPullRequestCheck.Found
    }

    /** The create buttons are enabled only when the form holds a complete, submittable, non-blocked selection. */
    fun areCreateButtonsEnabled(state: NewPullRequestUiState): Boolean {
        if (state.title.isBlank()) {
            return false
        }
        val source = state.selectedSource
        val destination = state.selectedDestination
        if (source == null || destination == null || source == destination) {
            return false
        }
        if (state.isSubmitting) {
            return false
        }
        if (state.branchesLoad != BranchesLoad.Loaded) {
            return false
        }
        if (state.existingPullRequest is ExistingPullRequestCheck.Checking) {
            return false
        }
        val workingTree = state.workingTree
        if (workingTree is WorkingTreeCheck.Checking || workingTree is WorkingTreeCheck.BlockedUncommittedChanges) {
            return false
        }
        return true
    }
}