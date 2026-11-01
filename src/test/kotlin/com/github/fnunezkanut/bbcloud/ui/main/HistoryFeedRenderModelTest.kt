package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.ActivityFeedEntry
import com.github.fnunezkanut.bbcloud.bitbucket.ActivityUser
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestActivityFeed
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestConversation
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class HistoryFeedRenderModelTest {
    private val date = Instant.parse("2026-09-30T10:00:00Z")
    private val author = ActivityUser(uuid = null, displayName = "Author", avatarUrl = null)
    private var lastId = 0L

    @Test
    fun `computeRenderModel - should return null when both feed and conversation are null`() {
        // GIVEN
        val feed: PullRequestActivityFeed? = null
        val conversation: PullRequestConversation? = null

        // WHEN
        val model = computeRenderModel(feed = feed, conversation = conversation)

        // THEN
        model.shouldBeNull()
    }

    @Test
    fun `computeRenderModel - should render no entries for an empty feed and an empty conversation`() {
        // GIVEN
        val feed = PullRequestActivityFeed(entries = emptyList(), isTruncated = false, hasPartialFailure = false)

        // WHEN
        val model = computeRenderModel(feed = feed, conversation = emptyConversation())

        // THEN
        assertSoftly(model!!) {
            entries.shouldHaveSize(0)
            showPartialFailure shouldBe false
            showTruncation shouldBe false
        }
    }

    @Test
    fun `computeRenderModel - should show partial failure notice even when the feed is empty`() {
        // GIVEN
        val feed = PullRequestActivityFeed(entries = emptyList(), isTruncated = false, hasPartialFailure = true)

        // WHEN
        val model = computeRenderModel(feed = feed, conversation = emptyConversation())

        // THEN
        assertSoftly(model!!) {
            entries.shouldHaveSize(0)
            showPartialFailure shouldBe true
            showTruncation shouldBe false
        }
    }

    @Test
    fun `computeRenderModel - should show truncation notice when the feed is truncated`() {
        // GIVEN
        val feed = PullRequestActivityFeed(entries = listOf(opened()), isTruncated = true, hasPartialFailure = false)

        // WHEN
        val model = computeRenderModel(feed = feed, conversation = emptyConversation())

        // THEN
        assertSoftly(model!!) {
            showPartialFailure shouldBe false
            showTruncation shouldBe true
            entries.shouldHaveSize(1)
        }
    }

    @Test
    fun `computeRenderModel - should carry entries and flags through unchanged`() {
        // GIVEN
        val entries = listOf(opened())
        val feed = PullRequestActivityFeed(entries = entries, isTruncated = true, hasPartialFailure = true)

        // WHEN
        val model = computeRenderModel(feed = feed, conversation = emptyConversation())

        // THEN
        assertSoftly(model!!) {
            this.entries shouldBe entries
            showPartialFailure shouldBe true
            showTruncation shouldBe true
        }
    }

    @Test
    fun `computeRenderModel - should treat all entry subtypes as ActivityFeedEntry instances`() {
        // GIVEN
        val entries = listOf(
            TimelineEntry.Opened(date = date, actor = null),
            TimelineEntry.StateChanged(date = date, actor = null, state = PullRequestState.Merged),
            TimelineEntry.Approved(date = date, actor = null)
        )
        val feed = PullRequestActivityFeed(entries = entries, isTruncated = false, hasPartialFailure = false)

        // WHEN
        val model = computeRenderModel(feed = feed, conversation = emptyConversation())

        // THEN
        model!!.entries.shouldHaveSize(3)
        model.entries.forEach { entry -> entry.shouldBeInstanceOf<ActivityFeedEntry>() }
    }

    @Test
    fun `computeRenderModel - should interleave comment threads with activity entries`() {
        // GIVEN an opening at 10:00, a thread at 11:00 and an approval at 12:00
        val opened = opened(date = Instant.parse("2026-09-30T10:00:00Z"))
        val approved = TimelineEntry.Approved(date = Instant.parse("2026-09-30T12:00:00Z"), actor = author)
        val thread = thread(date = Instant.parse("2026-09-30T11:00:00Z"))
        val feed = PullRequestActivityFeed(entries = listOf(approved, opened), isTruncated = false, hasPartialFailure = false)

        // WHEN
        val model = computeRenderModel(feed = feed, conversation = conversation(threads = listOf(thread)))

        // THEN
        model!!.entries shouldBe listOf(approved, thread, opened)
    }

    @Test
    fun `computeRenderModel - should show truncation when only the conversation is truncated`() {
        // GIVEN
        val feed = PullRequestActivityFeed(entries = emptyList(), isTruncated = false, hasPartialFailure = false)

        // WHEN
        val model = computeRenderModel(feed = feed, conversation = conversation(isTruncated = true))

        // THEN
        assertSoftly(model!!) {
            showTruncation shouldBe true
            showPartialFailure shouldBe false
        }
    }

    @Test
    fun `computeRenderModel - should show partial failure when the feed is missing but the conversation loaded`() {
        // GIVEN
        val thread = thread(date = date)

        // WHEN
        val model = computeRenderModel(feed = null, conversation = conversation(threads = listOf(thread)))

        // THEN
        assertSoftly(model!!) {
            showPartialFailure shouldBe true
            entries shouldBe listOf(thread)
        }
    }

    @Test
    fun `computeRenderModel - should show partial failure when the conversation is missing but the feed loaded`() {
        // GIVEN
        val feed = PullRequestActivityFeed(entries = listOf(opened()), isTruncated = false, hasPartialFailure = false)

        // WHEN
        val model = computeRenderModel(feed = feed, conversation = null)

        // THEN
        assertSoftly(model!!) {
            showPartialFailure shouldBe true
            entries.shouldHaveSize(1)
        }
    }

    private fun opened(date: Instant = this.date): TimelineEntry.Opened {
        return TimelineEntry.Opened(date = date, actor = author)
    }

    private fun emptyConversation(): PullRequestConversation {
        return conversation()
    }

    private fun conversation(threads: List<TimelineEntry.CommentThread> = emptyList(), isTruncated: Boolean = false): PullRequestConversation {
        return PullRequestConversation(
            threads = threads,
            currentUserUuid = null,
            currentUserAvatarUrl = null,
            isTruncated = isTruncated
        )
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