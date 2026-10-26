@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.ItemCount
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.ui.components.RoundedBadge
import com.github.fnunezkanut.bbcloud.ui.components.actionButtonHoverBackground
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.SimpleTabContent
import org.jetbrains.jewel.ui.component.TabContentScope
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabState
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.LocalDefaultTabStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * The pull-request detail view: a single information row (avatar, title link, draft/state badges, author, updated/
 * created timestamps with an absolute-date tooltip) that hosts the action buttons (Merge, Approve / Unapprove, Mark as
 * draft / ready, Decline) on its trailing edge, followed by the commits-behind line, the tab strip, and the content of
 * the selected tab. Conversation volume shows on the Conversation tab badge, not under the author.
 *
 * Whole-pull-request line totals are not shown here; they live in the work tree banner above the editor (see
 * `WorktreePrViewBlueBar`). The whole view is one Compose tree, so the tab switch no longer goes through a Swing `CardLayout`.
 */
@Composable
internal fun CheckedOutBranchScreen(
    detailViewModel: PullRequestDetailViewModel,
    overviewViewModel: PullRequestOverviewViewModel,
    conversationViewModel: PullRequestConversationViewModel,
    changesViewModel: PullRequestChangesViewModel,
    activityViewModel: PullRequestActivityViewModel,
    infoViewModel: PullRequestInfoViewModel,
    headerViewModel: PullRequestHeaderViewModel,
    configurationService: BbCloudConfigurationService,
    coroutineScope: CoroutineScope,
    onUpdateLocalBranch: () -> Unit
) {
    val detailState by detailViewModel.state
    Column(modifier = Modifier.fillMaxSize()) {
        PullRequestInfoTab(
            viewModel = infoViewModel,
            configurationService = configurationService,
            coroutineScope = coroutineScope,
            actions = detailState.actions,
            actionsEnabled = detailState.actionsEnabled,
            disabledActions = detailState.disabledActions,
            actionTooltips = detailState.actionTooltips,
            onAction = detailViewModel::onActionClicked
        )
        // The orange "behind destination" line, then the red "local behind remote" line below it (decision 1a).
        PullRequestCommitsBehindLine(viewModel = headerViewModel)
        PullRequestLocalBehindRemoteLine(viewModel = headerViewModel, onUpdateLocalBranch = onUpdateLocalBranch)
        PullRequestTabStrip(
            selectedTab = detailState.selectedTab,
            conversationCount = detailState.conversationCount,
            changesCount = detailState.changesCount,
            onTabSelected = detailViewModel::selectTab
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (detailState.selectedTab) {
                PullRequestDetailTab.Overview -> PullRequestOverviewScreen(viewModel = overviewViewModel)
                PullRequestDetailTab.Conversation -> PullRequestConversationScreen(viewModel = conversationViewModel)
                PullRequestDetailTab.Changes -> PullRequestChangesScreen(
                    filesViewModel = changesViewModel,
                    activityViewModel = activityViewModel
                )
            }
        }
    }
}

/**
 * The strip of tabs that switches between the Overview, Conversation, and Changes tabs. It is the Jewel `TabStrip`, so
 * the tabs follow the IDE theme colors, states (selected, hovered, pressed, focused), and keyboard navigation.
 */
@Composable
private fun PullRequestTabStrip(
    selectedTab: PullRequestDetailTab,
    conversationCount: ItemCount?,
    changesCount: ItemCount?,
    onTabSelected: (PullRequestDetailTab) -> Unit
) {
    val items = pullRequestTabStripItems(
        selectedTab = selectedTab,
        conversationCount = conversationCount,
        changesCount = changesCount
    )
    val tabs = items.map { item ->
        TabData.Default(
            selected = item.selected,
            closable = false,
            onClose = {},
            onClick = { onTabSelected(item.tab) },
            content = {
                PullRequestTabContent(tab = item.tab, count = item.count, tabState = it)
            }
        )
    }
    TabStrip(
        tabs = tabs,
        style = LocalDefaultTabStyle.current,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * The content of one tab of the pull-request tab strip: the tab icon, the tab title, and the optional counter badge.
 * The icon and the title are rendered through the Jewel `SimpleTabContent` helper so they follow the tab's state
 * colors; the badge keeps the plugin's own `actionButtonHoverBackground()` fill.
 */
@Composable
private fun TabContentScope.PullRequestTabContent(tab: PullRequestDetailTab, count: ItemCount?, tabState: TabState) {
    SimpleTabContent(
        state = tabState,
        icon = { Icon(key = tab.iconKey(), contentDescription = null) },
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = tab.title())
                if (count != null) {
                    RoundedBadge(
                        text = if (count.isTruncated) {
                            BbCloudBundle.message("main.branch.count.truncated", count.value)
                        } else {
                            count.value.toString()
                        },
                        fillColor = actionButtonHoverBackground()
                    )
                }
            }
        }
    )
}

/** The icon of a tab of the pull-request tab strip. */
private fun PullRequestDetailTab.iconKey(): IconKey {
    return when (this) {
        PullRequestDetailTab.Overview -> AllIconsKeys.Actions.Preview
        PullRequestDetailTab.Conversation -> AllIconsKeys.General.Balloon
        PullRequestDetailTab.Changes -> AllIconsKeys.Actions.ListFiles
    }
}