package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestConversation
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

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

    @Test
    fun `showConversation - should force the scroll request when forceScrollToTop is set on an in-place refresh`() {
        // GIVEN a pull request that is already displayed
        val scope = CoroutineScope(SupervisorJob())
        val pullRequestId = 273L
        val viewModel = PullRequestConversationViewModel(
            pullRequestId = { pullRequestId },
            commentEditor = mockk(relaxed = true)
        )
        val conversation = emptyConversation()

        try {
            viewModel.showConversation(conversation = conversation, coroutineScope = scope)

            // WHEN an in-place refresh of the same pull request forces the scroll
            viewModel.showConversation(
                conversation = conversation,
                coroutineScope = scope,
                inPlaceRefresh = true,
                forceScrollToTop = true
            )

            // THEN the scroll is still forced
            viewModel.state.value.scrollRequest?.force shouldBe true
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `showConversation - should not force the scroll request on an in-place refresh of the same pull request`() {
        // GIVEN a pull request that is already displayed
        val scope = CoroutineScope(SupervisorJob())
        val pullRequestId = 273L
        val viewModel = PullRequestConversationViewModel(
            pullRequestId = { pullRequestId },
            commentEditor = mockk(relaxed = true)
        )
        val conversation = emptyConversation()

        try {
            viewModel.showConversation(conversation = conversation, coroutineScope = scope)

            // WHEN an in-place refresh of the same pull request does not force the scroll
            viewModel.showConversation(
                conversation = conversation,
                coroutineScope = scope,
                inPlaceRefresh = true,
                forceScrollToTop = false
            )

            // THEN the scroll is not forced
            viewModel.state.value.scrollRequest?.force shouldBe false
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `composerViewModel - should return the same instance for the same target until it closes`() {
        // GIVEN a conversation view model with an open edit composer
        val viewModel = PullRequestConversationViewModel(
            pullRequestId = { 273L },
            commentEditor = mockk(relaxed = true)
        )
        viewModel.openEdit(commentId = 5)
        val target = requireNotNull(viewModel.openComposer)
        val comment = comment(id = 5, raw = "hello")

        // WHEN the view model is requested twice for the same target
        val first = viewModel.composerViewModel(target = target, comment = comment, scope = Unit)
        val second = viewModel.composerViewModel(target = target, comment = comment, scope = Unit)

        // THEN the same instance is returned, so the draft survives a recomposition
        first shouldBe second
    }

    @Test
    fun `composerViewModel - should return a new instance after closeComposer`() {
        // GIVEN a conversation view model with a cached edit composer
        val viewModel = PullRequestConversationViewModel(
            pullRequestId = { 273L },
            commentEditor = mockk(relaxed = true)
        )
        viewModel.openEdit(commentId = 5)
        val target = requireNotNull(viewModel.openComposer)
        val comment = comment(id = 5, raw = "hello")
        val first = viewModel.composerViewModel(target = target, comment = comment, scope = Unit)

        // WHEN the composer closes and is opened again
        viewModel.closeComposer()
        viewModel.openEdit(commentId = 5)
        val reopened = requireNotNull(viewModel.openComposer)
        val second = viewModel.composerViewModel(target = reopened, comment = comment, scope = Unit)

        // THEN a fresh instance is returned, so the submitted or cancelled draft is not reused
        first shouldNotBe second
    }

    @Test
    fun `fileExists - should be true for a path that has not been resolved yet`() {
        // GIVEN
        val viewModel = PullRequestConversationViewModel(
            pullRequestId = { 273L },
            commentEditor = mockk(relaxed = true),
            isLocalFile = { _, _ -> false }
        )

        // WHEN
        val result = viewModel.fileExists(path = "src/Main.kt")

        // THEN
        result shouldBe true
    }

    private fun emptyConversation(): PullRequestConversation {
        return PullRequestConversation(
            threads = emptyList(),
            currentUserUuid = null,
            currentUserAvatarUrl = null,
            isTruncated = false
        )
    }

    private fun comment(id: Long, raw: String?): PullRequestComment {
        return PullRequestComment(
            id = id,
            parentId = null,
            createdOn = Instant.parse("2026-10-31T00:00:00Z"),
            author = null,
            html = "<p>$raw</p>",
            inlinePath = null,
            inlineLine = null,
            inlineSide = null,
            isResolved = false,
            isDeleted = false,
            isPending = false,
            raw = raw
        )
    }
}