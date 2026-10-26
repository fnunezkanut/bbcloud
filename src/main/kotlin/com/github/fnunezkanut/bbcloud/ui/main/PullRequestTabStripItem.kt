package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.ItemCount

/**
 * One item of the pull-request tab strip: the tab, whether it is the selected tab, and the optional counter shown in
 * its badge. The value is immutable, so it is a data class (AGENTS.md).
 */
internal data class PullRequestTabStripItem(val tab: PullRequestDetailTab, val selected: Boolean, val count: ItemCount?)

/**
 * Maps the selected tab and the two counters to the ordered list of tab-strip items. The order matches
 * [PullRequestDetailTab.entries] (Overview, Conversation, Changes). Overview never shows a counter; Conversation shows
 * the conversation counter; Changes shows the changes counter.
 *
 * This is a pure function with no Compose dependency, so the mapping can be unit-tested on its own.
 */
internal fun pullRequestTabStripItems(
    selectedTab: PullRequestDetailTab,
    conversationCount: ItemCount?,
    changesCount: ItemCount?
): List<PullRequestTabStripItem> {
    return PullRequestDetailTab.entries.map { tab ->
        val count = when (tab) {
            PullRequestDetailTab.Overview -> null
            PullRequestDetailTab.Conversation -> conversationCount
            PullRequestDetailTab.Changes -> changesCount
        }
        PullRequestTabStripItem(
            tab = tab,
            selected = tab == selectedTab,
            count = count
        )
    }
}