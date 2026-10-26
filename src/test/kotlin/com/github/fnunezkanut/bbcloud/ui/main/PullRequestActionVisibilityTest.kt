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
    fun `visibleActions - should show merge approve mark as draft and decline for open ready pull request`() {
        // WHEN
        val actions = PullRequestActionVisibility.visibleActions(state = PullRequestState.Open, isDraft = false, isApprovedByCurrentUser = false)

        // THEN
        actions shouldBe listOf(PullRequestAction.Merge, PullRequestAction.Approve, PullRequestAction.MarkAsDraft, PullRequestAction.Decline)
    }

    @Test
    fun `visibleActions - should show unapprove when current user already approved`() {
        // WHEN
        val actions = PullRequestActionVisibility.visibleActions(state = PullRequestState.Open, isDraft = false, isApprovedByCurrentUser = true)

        // THEN
        actions shouldBe listOf(PullRequestAction.Merge, PullRequestAction.Unapprove, PullRequestAction.MarkAsDraft, PullRequestAction.Decline)
    }

    @Test
    fun `visibleActions - should hide merge and review actions and show mark as ready for open draft pull request`() {
        // WHEN
        val actions = PullRequestActionVisibility.visibleActions(state = PullRequestState.Open, isDraft = true, isApprovedByCurrentUser = false)

        // THEN
        assertSoftly {
            actions shouldNotContain PullRequestAction.Merge
            actions shouldNotContain PullRequestAction.Approve
            actions shouldNotContain PullRequestAction.Unapprove
            actions shouldBe listOf(PullRequestAction.MarkAsReady, PullRequestAction.Decline)
        }
    }

    @Test
    fun `visibleActions - should hide review actions even when draft pull request is approved by current user`() {
        // WHEN
        val actions = PullRequestActionVisibility.visibleActions(state = PullRequestState.Open, isDraft = true, isApprovedByCurrentUser = true)

        // THEN
        assertSoftly {
            actions shouldNotContain PullRequestAction.Approve
            actions shouldNotContain PullRequestAction.Unapprove
            actions shouldBe listOf(PullRequestAction.MarkAsReady, PullRequestAction.Decline)
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
                        // WHEN
                        val actions = PullRequestActionVisibility.visibleActions(state = state, isDraft = isDraft, isApprovedByCurrentUser = isApproved)

                        // THEN
                        withClue("state=$state, isDraft=$isDraft, isApproved=$isApproved") {
                            actions.shouldBeEmpty()
                        }
                    }
                }
            }
        }
    }
}