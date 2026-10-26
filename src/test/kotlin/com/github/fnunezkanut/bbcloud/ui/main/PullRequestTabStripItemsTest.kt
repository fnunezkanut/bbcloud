package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.ItemCount
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestTabStripItemsTest {
    @Test
    fun `pullRequestTabStripItems - should select only the tab that matches the selected tab`() {
        // GIVEN
        val selectedTab = PullRequestDetailTab.Conversation

        // WHEN
        val items = pullRequestTabStripItems(
            selectedTab = selectedTab,
            conversationCount = null,
            changesCount = null
        )

        // THEN
        assertSoftly(items.associate { it.tab to it.selected }) {
            this[PullRequestDetailTab.Overview] shouldBe false
            this[PullRequestDetailTab.Conversation] shouldBe true
            this[PullRequestDetailTab.Changes] shouldBe false
        }
    }

    @Test
    fun `pullRequestTabStripItems - should show no count on the Overview tab`() {
        // GIVEN
        val conversationCount = ItemCount(value = 7, isTruncated = false)
        val changesCount = ItemCount(value = 3, isTruncated = false)

        // WHEN
        val items = pullRequestTabStripItems(
            selectedTab = PullRequestDetailTab.Overview,
            conversationCount = conversationCount,
            changesCount = changesCount
        )

        // THEN
        items.first { it.tab == PullRequestDetailTab.Overview }.count shouldBe null
    }

    @Test
    fun `pullRequestTabStripItems - should show the conversation count only on the Conversation tab`() {
        // GIVEN
        val conversationCount = ItemCount(value = 12, isTruncated = true)

        // WHEN
        val items = pullRequestTabStripItems(
            selectedTab = PullRequestDetailTab.Overview,
            conversationCount = conversationCount,
            changesCount = null
        )

        // THEN
        items.first { it.tab == PullRequestDetailTab.Conversation }.count shouldBe conversationCount
    }

    @Test
    fun `pullRequestTabStripItems - should show the changes count only on the Changes tab`() {
        // GIVEN
        val changesCount = ItemCount(value = 5, isTruncated = false)

        // WHEN
        val items = pullRequestTabStripItems(
            selectedTab = PullRequestDetailTab.Overview,
            conversationCount = null,
            changesCount = changesCount
        )

        // THEN
        items.first { it.tab == PullRequestDetailTab.Changes }.count shouldBe changesCount
    }

    @Test
    fun `pullRequestTabStripItems - should keep the Overview, Conversation, Changes order`() {
        // GIVEN
        val selectedTab = PullRequestDetailTab.Overview

        // WHEN
        val items = pullRequestTabStripItems(
            selectedTab = selectedTab,
            conversationCount = null,
            changesCount = null
        )

        // THEN
        items.map { it.tab } shouldBe listOf(
            PullRequestDetailTab.Overview,
            PullRequestDetailTab.Conversation,
            PullRequestDetailTab.Changes
        )
    }
}