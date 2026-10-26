package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.ActivityFeedEntry
import com.github.fnunezkanut.bbcloud.bitbucket.ActivityUser
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestActivityFeed
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
class ActivityFeedRenderModelTest {
    private val date = Instant.parse("2026-09-30T10:00:00Z")

    private fun opened(date: Instant = this.date): TimelineEntry.Opened {
        return TimelineEntry.Opened(
            date = date,
            actor = ActivityUser(uuid = null, displayName = "Author", avatarUrl = null)
        )
    }

    @Test
    fun `computeRenderModel - should return null for a null feed`() {
        // GIVEN
        val feed: PullRequestActivityFeed? = null

        // WHEN
        val model = computeRenderModel(feed)

        // THEN
        model.shouldBeNull()
    }

    @Test
    fun `computeRenderModel - should render no entries for an empty feed`() {
        // GIVEN
        val feed = PullRequestActivityFeed(entries = emptyList(), isTruncated = false, hasPartialFailure = false)

        // WHEN
        val model = computeRenderModel(feed)

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
        val model = computeRenderModel(feed)

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
        val model = computeRenderModel(feed)

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
        val model = computeRenderModel(feed)

        // THEN
        assertSoftly(model!!) {
            entries shouldBe entries
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
        val model = computeRenderModel(feed)

        // THEN
        model!!.entries.shouldHaveSize(3)
        model.entries.forEach { entry -> entry.shouldBeInstanceOf<ActivityFeedEntry>() }
    }
}