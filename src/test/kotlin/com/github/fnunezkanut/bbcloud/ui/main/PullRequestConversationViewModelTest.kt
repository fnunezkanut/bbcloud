package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestConversation
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestConversationViewModelTest {
    @Test
    fun `showConversation - should not force scroll for an in-place refresh`() {
        // GIVEN
        val scope = CoroutineScope(SupervisorJob())
        val pullRequestId = 273L
        val viewModel = PullRequestConversationViewModel(
            pullRequestId = { pullRequestId },
            commentEditor = mockk(relaxed = true)
        )
        val conversation = emptyConversation()

        try {
            // WHEN
            viewModel.showConversation(conversation = conversation, coroutineScope = scope)
            viewModel.showConversation(
                conversation = conversation,
                coroutineScope = scope,
                inPlaceRefresh = true
            )

            // THEN
            assertSoftly {
                viewModel.state.value.scrollRequest?.force shouldBe false
                pullRequestId shouldBe 273L
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `showConversation - should force scroll after a full reset`() {
        // GIVEN
        val scope = CoroutineScope(SupervisorJob())
        val viewModel = PullRequestConversationViewModel(
            pullRequestId = { 273L },
            commentEditor = mockk(relaxed = true)
        )
        val conversation = emptyConversation()

        try {
            viewModel.showConversation(conversation = conversation, coroutineScope = scope)
            viewModel.reset()

            // WHEN
            viewModel.showConversation(conversation = conversation, coroutineScope = scope)

            // THEN
            viewModel.state.value.scrollRequest?.force shouldBe true
        } finally {
            scope.cancel()
        }
    }

    private fun emptyConversation(): PullRequestConversation {
        return PullRequestConversation(
            threads = emptyList(),
            currentUserUuid = null,
            currentUserAvatarUrl = null,
            isTruncated = false
        )
    }
}