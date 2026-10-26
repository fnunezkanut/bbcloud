package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestDetails
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class PullRequestOverviewViewModelTest {
    @Test
    fun `showReviewers - should render a present description`() {
        // GIVEN
        val viewModel = createViewModel()
        val scope = CoroutineScope(SupervisorJob())
        val details = pullRequestDetails(description = "## Description")

        try {
            // WHEN
            viewModel.showReviewers(details = details, coroutineScope = scope)

            // THEN
            assertSoftly(viewModel.state.value.description) {
                markdown shouldBe "## Description"
                rawMarkdown shouldBe "## Description"
                emptyMessage.shouldBeNull()
                isEditable shouldBe true
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `description editing - should open and cancel without changing raw Markdown`() {
        // GIVEN
        val viewModel = createViewModel()
        val scope = CoroutineScope(SupervisorJob())
        viewModel.showReviewers(details = pullRequestDetails(description = "Original"), coroutineScope = scope)

        try {
            // WHEN
            viewModel.onEditDescription()

            // THEN
            viewModel.state.value.description.isEditing shouldBe true
            viewModel.descriptionInput().initialText shouldBe "Original"

            // WHEN
            viewModel.onCancelDescriptionEdit()

            // THEN
            viewModel.state.value.description.isEditing shouldBe false
            viewModel.state.value.description.rawMarkdown shouldBe "Original"
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `showReviewers - should hide description editing for a closed pull request`() {
        // GIVEN
        val viewModel = createViewModel()
        val scope = CoroutineScope(SupervisorJob())
        val details = pullRequestDetails(description = "Closed", state = PullRequestState.Merged)

        try {
            // WHEN
            viewModel.showReviewers(details = details, coroutineScope = scope)

            // THEN
            viewModel.state.value.description.isEditable shouldBe false
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `showReviewers and reset - should render empty state and clear description`() {
        // GIVEN
        val viewModel = createViewModel()
        val scope = CoroutineScope(SupervisorJob())
        val details = pullRequestDetails(description = null)

        try {
            // WHEN
            viewModel.showReviewers(details = details, coroutineScope = scope)

            // THEN
            assertSoftly(viewModel.state.value.description) {
                markdown.shouldBeNull()
                rawMarkdown shouldBe ""
                emptyMessage shouldBe "No description provided."
                isEditable shouldBe true
            }

            // WHEN
            viewModel.reset()

            // THEN
            assertSoftly(viewModel.state.value.description) {
                markdown.shouldBeNull()
                rawMarkdown shouldBe ""
                emptyMessage shouldBe "No description provided."
                isEditable shouldBe false
            }
        } finally {
            scope.cancel()
        }
    }

    private fun createViewModel(): PullRequestOverviewViewModel {
        return PullRequestOverviewViewModel(
            reviewerEditor = mockk(relaxed = true),
            taskEditor = mockk(relaxed = true),
            descriptionEditor = mockk(relaxed = true),
            openSearchPopup = {}
        )
    }

    private fun pullRequestDetails(description: String?, state: PullRequestState = PullRequestState.Open): PullRequestDetails {
        return PullRequestDetails(
            summary = PullRequestSummary(
                id = 273,
                title = "Test pull request",
                authorDisplayName = "Author",
                authorAvatarUrl = null,
                createdOn = Instant.parse("2026-09-29T19:28:03.123456Z"),
                updatedOn = Instant.parse("2026-09-29T19:28:03.123456Z"),
                sourceBranch = "feature",
                destinationBranch = "main",
                commentCount = 0,
                webUrl = "https://bitbucket.org/workspace/repository/pull-requests/273",
                state = state,
                isDraft = false
            ),
            isApprovedByCurrentUser = false,
            description = description
        )
    }
}