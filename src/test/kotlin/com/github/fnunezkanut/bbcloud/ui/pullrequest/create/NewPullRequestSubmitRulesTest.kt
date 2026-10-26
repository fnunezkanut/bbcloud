package com.github.fnunezkanut.bbcloud.ui.pullrequest.create

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class NewPullRequestSubmitRulesTest {
    private fun validState(): NewPullRequestUiState {
        return NewPullRequestUiState(
            title = "a title",
            branchesLoad = BranchesLoad.Loaded,
            sourceOptions = listOf("feature/x"),
            selectedSource = "feature/x",
            destinationOptions = listOf("main"),
            selectedDestination = "main",
            existingPullRequest = ExistingPullRequestCheck.None,
            workingTree = WorkingTreeCheck.Clean
        )
    }

    @Test
    fun `areCreateButtonsVisible - should be hidden only when a pull request already exists`() {
        // THEN
        assertSoftly {
            NewPullRequestSubmitRules.areCreateButtonsVisible(ExistingPullRequestCheck.Found(pullRequestId = 1, webUrl = "url")) shouldBe false
            NewPullRequestSubmitRules.areCreateButtonsVisible(ExistingPullRequestCheck.None) shouldBe true
            NewPullRequestSubmitRules.areCreateButtonsVisible(ExistingPullRequestCheck.Checking) shouldBe true
            NewPullRequestSubmitRules.areCreateButtonsVisible(ExistingPullRequestCheck.Failed) shouldBe true
            NewPullRequestSubmitRules.areCreateButtonsVisible(ExistingPullRequestCheck.Idle) shouldBe true
        }
    }

    @Test
    fun `areCreateButtonsEnabled - should be enabled for a valid state including unknown working tree`() {
        // THEN
        assertSoftly {
            NewPullRequestSubmitRules.areCreateButtonsEnabled(validState()) shouldBe true
            NewPullRequestSubmitRules.areCreateButtonsEnabled(validState().copy(workingTree = WorkingTreeCheck.Unknown)) shouldBe true
        }
    }

    @Test
    fun `areCreateButtonsEnabled - should be disabled for a blank title`() {
        NewPullRequestSubmitRules.areCreateButtonsEnabled(validState().copy(title = "   ")) shouldBe false
    }

    @Test
    fun `areCreateButtonsEnabled - should be disabled when a branch is missing or equal`() {
        assertSoftly {
            NewPullRequestSubmitRules.areCreateButtonsEnabled(validState().copy(selectedSource = null)) shouldBe false
            NewPullRequestSubmitRules.areCreateButtonsEnabled(validState().copy(selectedDestination = null)) shouldBe false
            NewPullRequestSubmitRules.areCreateButtonsEnabled(validState().copy(selectedSource = "main", selectedDestination = "main")) shouldBe false
        }
    }

    @Test
    fun `areCreateButtonsEnabled - should be disabled while checking submitting or branches not loaded`() {
        assertSoftly {
            NewPullRequestSubmitRules.areCreateButtonsEnabled(validState().copy(existingPullRequest = ExistingPullRequestCheck.Checking)) shouldBe false
            NewPullRequestSubmitRules.areCreateButtonsEnabled(validState().copy(isSubmitting = true)) shouldBe false
            NewPullRequestSubmitRules.areCreateButtonsEnabled(validState().copy(branchesLoad = BranchesLoad.Failed)) shouldBe false
        }
    }

    @Test
    fun `areCreateButtonsEnabled - should be disabled while the working tree is checking or blocked`() {
        assertSoftly {
            NewPullRequestSubmitRules.areCreateButtonsEnabled(validState().copy(workingTree = WorkingTreeCheck.Checking)) shouldBe false
            NewPullRequestSubmitRules.areCreateButtonsEnabled(
                validState().copy(workingTree = WorkingTreeCheck.BlockedUncommittedChanges(currentBranch = "feature/x"))
            ) shouldBe false
        }
    }
}