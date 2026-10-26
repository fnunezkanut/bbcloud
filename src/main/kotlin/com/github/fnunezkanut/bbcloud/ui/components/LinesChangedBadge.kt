@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.ui.main.DiffstatLinesEmoji
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

/**
 * The per-file added and removed line counts.
 *
 * The review bar shows the leading emoji so it reads like the branch header; the Changes file tree passes
 * [showEmoji] = false for a compact green/red badge that fits a tree row. The emoji is omitted entirely when
 * [showEmoji] is false, so the badge starts at the green `+` count.
 */
@Composable
internal fun LinesChangedBadge(linesAdded: Int, linesRemoved: Int, modifier: Modifier = Modifier, showEmoji: Boolean = true) {
    Tooltip(
        tooltip = {
            Text(
                text = BbCloudBundle.message(
                    "main.branch.header.linesChanged.tooltip",
                    linesAdded,
                    linesRemoved
                )
            )
        }
    ) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showEmoji) {
                Text(text = DiffstatLinesEmoji.forLinesAdded(linesAdded))
            }
            Text(
                text = BbCloudBundle.message("main.branch.header.linesAdded", linesAdded),
                color = addedLinesColor()
            )
            Text(
                text = BbCloudBundle.message("main.branch.header.linesRemoved", linesRemoved),
                color = removedLinesColor()
            )
        }
    }
}