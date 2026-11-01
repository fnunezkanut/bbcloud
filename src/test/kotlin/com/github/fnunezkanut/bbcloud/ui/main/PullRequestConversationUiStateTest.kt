package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.ActivityUser
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestConversation
import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class PullRequestConversationUiStateTest {
    private var lastId = 0L
    private val author = ActivityUser(uuid = "{author}", displayName = "Author", avatarUrl = null)
    private val now = Instant.parse("2026-09-30T10:00:00Z")

    @Nested
    inner class IsEmptyTests {
        @Test
        fun `isEmpty - should be true when the conversation holds no thread`() {
            // GIVEN
            val state = PullRequestConversationUiState(loadState = PullRequestConversationLoadState.Loaded)

            // WHEN
            val isEmpty = state.isEmpty()

            // THEN
            isEmpty shouldBe true
        }

        @Test
        fun `isEmpty - should be false when the conversation holds a thread`() {
            // GIVEN
            val state = PullRequestConversationUiState(
                loadState = PullRequestConversationLoadState.Loaded,
                threads = listOf(thread(date = now))
            )

            // WHEN
            val isEmpty = state.isEmpty()

            // THEN
            isEmpty shouldBe false
        }
    }

    @Nested
    inner class FailedConversationStateTests {
        @Test
        fun `failedConversationState - should be in the error state without any thread`() {
            // WHEN
            val state = failedConversationState()

            // THEN
            assertSoftly {
                state.loadState shouldBe PullRequestConversationLoadState.Error
                state.threads shouldBe emptyList()
                state.isTruncated shouldBe false
            }
        }
    }

    @Nested
    inner class EmptyConversationStateTests {
        @Test
        fun `emptyConversationState - should be loaded and without any thread`() {
            // GIVEN
            val conversation = PullRequestConversation(
                threads = emptyList(),
                currentUserUuid = "{user}",
                currentUserAvatarUrl = null,
                isTruncated = false
            )

            // WHEN
            val state = emptyConversationState(conversation = conversation)

            // THEN
            assertSoftly {
                state.loadState shouldBe PullRequestConversationLoadState.Loaded
                state.threads shouldBe emptyList()
                state.isTruncated shouldBe false
            }
        }

        @Test
        fun `emptyConversationState - should keep the truncation notice`() {
            // GIVEN
            val conversation = PullRequestConversation(
                threads = emptyList(),
                currentUserUuid = "{user}",
                currentUserAvatarUrl = null,
                isTruncated = true
            )

            // WHEN
            val state = emptyConversationState(conversation = conversation)

            // THEN
            state.isTruncated shouldBe true
        }
    }

    private fun thread(date: Instant): TimelineEntry.CommentThread {
        return TimelineEntry.CommentThread(root = comment(date = date), replies = emptyList())
    }

    private fun comment(date: Instant): PullRequestComment {
        return PullRequestComment(
            id = nextId(),
            parentId = null,
            createdOn = date,
            author = author,
            html = "<p>Comment</p>",
            inlinePath = null,
            inlineLine = null,
            inlineSide = null,
            isResolved = false,
            isDeleted = false,
            isPending = false
        )
    }

    private fun nextId(): Long {
        lastId++
        return lastId
    }
}