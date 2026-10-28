package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.ActivityUser
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestConversation
import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class PullRequestConversationUiStateTest {
    private var lastId = 0L
    private val author = ActivityUser(uuid = "{author}", displayName = "Author", avatarUrl = null)
    private val now = Instant.parse("2026-09-30T10:00:00Z")
    private val timeZone = TimeZone.UTC

    @Nested
    inner class GroupConversationThreadsTests {
        @Test
        fun `groupConversationThreads - should group the threads by the day they were written on`() {
            // GIVEN one thread from today and one from two days ago
            val threads = listOf(
                thread(date = now),
                thread(date = Instant.parse("2026-09-28T10:00:00Z"))
            )

            // WHEN
            val groups = groupConversationThreads(threads = threads, now = now, timeZone = timeZone)

            // THEN
            assertSoftly {
                groups.size shouldBe 2
                groups.map { group -> group.day } shouldBe listOf(HistoryDay.Today, HistoryDay.Earlier(LocalDate.parse("2026-09-28")))
                groups.map { group -> group.entries.size } shouldBe listOf(1, 1)
            }
        }

        @Test
        fun `groupConversationThreads - should keep the order of the threads inside a group`() {
            // GIVEN two threads of the same day
            val first = thread(date = now)
            val second = thread(date = now)
            val threads = listOf(first, second)

            // WHEN
            val groups = groupConversationThreads(threads = threads, now = now, timeZone = timeZone)

            // THEN Bitbucket returns the threads newest first, and the grouping keeps that order
            assertSoftly {
                groups.size shouldBe 1
                groups.first().entries shouldBe listOf(first, second)
            }
        }

        @Test
        fun `groupConversationThreads - should return no group for no thread`() {
            // WHEN
            val groups = groupConversationThreads(threads = emptyList(), now = now, timeZone = timeZone)

            // THEN
            groups shouldBe emptyList()
        }
    }

    @Nested
    inner class ThreadsTests {
        @Test
        fun `threads - should flatten the day groups`() {
            // GIVEN
            val first = thread(date = now)
            val second = thread(date = Instant.parse("2026-09-28T10:00:00Z"))
            val state = PullRequestConversationUiState(
                loadState = PullRequestConversationLoadState.Loaded,
                groups = groupConversationThreads(threads = listOf(first, second), now = now, timeZone = timeZone)
            )

            // WHEN
            val threads = state.threads()

            // THEN
            threads shouldBe listOf(first, second)
        }

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
                groups = groupConversationThreads(threads = listOf(thread(date = now)), now = now, timeZone = timeZone)
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
                state.groups shouldBe emptyList()
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
                state.groups shouldBe emptyList()
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