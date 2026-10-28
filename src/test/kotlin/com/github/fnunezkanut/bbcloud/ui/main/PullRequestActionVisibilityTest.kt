package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestAction
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestActionVisibilityTest {
    @Test
    fun `visibleActions - should show merge approve request changes mark as draft and decline for open ready pull request`() {
        // WHEN
        val actions = PullRequestActionVisibility.visibleActions(
            state = PullRequestState.Open,
            isDraft = false,
            isApprovedByCurrentUser = false,
            isChangesRequestedByCurrentUser = false
        )

        // THEN
        actions shouldBe listOf(
            PullRequestAction.Merge,
            PullRequestAction.Approve,
            PullRequestAction.RequestChanges,
            PullRequestAction.MarkAsDraft,
            PullRequestAction.Decline
        )
    }

    @Test
    fun `visibleActions - should show unapprove when current user already approved`() {
        // WHEN
        val actions = PullRequestActionVisibility.visibleActions(
            state = PullRequestState.Open,
            isDraft = false,
            isApprovedByCurrentUser = true,
            isChangesRequestedByCurrentUser = false
        )

        // THEN
        actions shouldBe listOf(
            PullRequestAction.Merge,
            PullRequestAction.Unapprove,
            PullRequestAction.RequestChanges,
            PullRequestAction.MarkAsDraft,
            PullRequestAction.Decline
        )
    }

    @Test
    fun `visibleActions - should show remove changes request when current user already requested changes`() {
        // WHEN
        val actions = PullRequestActionVisibility.visibleActions(
            state = PullRequestState.Open,
            isDraft = false,
            isApprovedByCurrentUser = false,
            isChangesRequestedByCurrentUser = true
        )

        // THEN
        actions shouldBe listOf(
            PullRequestAction.Merge,
            PullRequestAction.Approve,
            PullRequestAction.RemoveChangesRequest,
            PullRequestAction.MarkAsDraft,
            PullRequestAction.Decline
        )
    }

    @Test
    fun `visibleActions - should hide approve actions and show request changes for open draft pull request`() {
        // WHEN
        val actions = PullRequestActionVisibility.visibleActions(
            state = PullRequestState.Open,
            isDraft = true,
            isApprovedByCurrentUser = false,
            isChangesRequestedByCurrentUser = false
        )

        // THEN
        assertSoftly {
            actions shouldNotContain PullRequestAction.Merge
            actions shouldNotContain PullRequestAction.Approve
            actions shouldNotContain PullRequestAction.Unapprove
            actions shouldBe listOf(PullRequestAction.RequestChanges, PullRequestAction.MarkAsReady, PullRequestAction.Decline)
        }
    }

    @Test
    fun `visibleActions - should hide approve actions but show remove changes request for draft pull request with changes requested`() {
        // WHEN
        val actions = PullRequestActionVisibility.visibleActions(
            state = PullRequestState.Open,
            isDraft = true,
            isApprovedByCurrentUser = true,
            isChangesRequestedByCurrentUser = true
        )

        // THEN
        assertSoftly {
            actions shouldNotContain PullRequestAction.Approve
            actions shouldNotContain PullRequestAction.Unapprove
            actions shouldBe listOf(PullRequestAction.RemoveChangesRequest, PullRequestAction.MarkAsReady, PullRequestAction.Decline)
        }
    }

    @Test
    fun `visibleActions - should hide all actions for closed pull request`() {
        // GIVEN
        val closedStates = listOf(PullRequestState.Merged, PullRequestState.Declined)
        val flags = listOf(false, true)

        assertSoftly {
            closedStates.forEach { state ->
                flags.forEach { isDraft ->
                    flags.forEach { isApproved ->
                        flags.forEach { isChangesRequested ->
                            // WHEN
                            val actions = PullRequestActionVisibility.visibleActions(
                                state = state,
                                isDraft = isDraft,
                                isApprovedByCurrentUser = isApproved,
                                isChangesRequestedByCurrentUser = isChangesRequested
                            )

                            // THEN
                            withClue("state=$state, isDraft=$isDraft, isApproved=$isApproved, isChangesRequested=$isChangesRequested") {
                                actions.shouldBeEmpty()
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `isDottedOutline - should dot only the changes requested action`() {
        // WHEN / THEN
        assertSoftly {
            PullRequestActionVisibility.isDottedOutline(PullRequestAction.RemoveChangesRequest) shouldBe true
            PullRequestActionVisibility.isDottedOutline(PullRequestAction.RequestChanges) shouldBe false
            PullRequestActionVisibility.isDottedOutline(PullRequestAction.Approve) shouldBe false
        }
    }
}