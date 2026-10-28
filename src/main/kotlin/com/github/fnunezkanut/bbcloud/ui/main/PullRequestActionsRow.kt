@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestAction
import com.github.fnunezkanut.bbcloud.ui.components.BbCloudOutlinedActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

/**
 * The row of pull request action buttons (Merge, Approve / Unapprove, Request Changes / Changes Requested, Mark as
 * draft / ready, Decline).
 *
 * The row is mounted in the trailing column of the detail information card, aligned to the top-right of the same row as
 * the avatar, title, and author. It is not a separate header band, and the page Refresh control lives on the work tree
 * banner above the editor (see `WorktreePrViewBlueBar`). The buttons wrap onto a new line when the column is too narrow.
 */
@Composable
internal fun PullRequestActionsRow(
    actions: List<PullRequestAction>,
    enabled: Boolean,
    disabledActions: Set<PullRequestAction>,
    actionTooltips: Map<PullRequestAction, String>,
    onAction: (PullRequestAction) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        actions.forEach { action ->
            val actionEnabled = enabled && action !in disabledActions
            val tooltip = actionTooltips[action]
            if (tooltip == null) {
                BbCloudOutlinedActionButton(
                    text = PullRequestActionVisibility.label(action),
                    icon = PullRequestActionVisibility.iconKey(action),
                    outlineColor = PullRequestActionVisibility.outlineColor(action),
                    enabled = actionEnabled,
                    onClick = { onAction(action) },
                    dotted = PullRequestActionVisibility.isDottedOutline(action)
                )
            } else {
                Tooltip(tooltip = { Text(text = tooltip) }) {
                    BbCloudOutlinedActionButton(
                        text = PullRequestActionVisibility.label(action),
                        icon = PullRequestActionVisibility.iconKey(action),
                        outlineColor = PullRequestActionVisibility.outlineColor(action),
                        enabled = actionEnabled,
                        onClick = { onAction(action) },
                        dotted = PullRequestActionVisibility.isDottedOutline(action)
                    )
                }
            }
        }
    }
}