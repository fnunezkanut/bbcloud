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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.ui.main.HeaderLinesChanged
import com.intellij.ui.JBColor
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

/**
 * The whole-pull-request "+12 -3" lines-changed indicator (emoji, green added count, red removed count) with a tooltip
 * spelling out the exact counts. Renders nothing when [lines] is null, so callers can pass the still-loading or
 * unavailable state through without a placeholder.
 *
 * This is the shared presentation used by the work tree banner. The per-file review bar keeps its own
 * [LinesChangedBadge] because that badge renders raw per-file ints rather than a prepared [HeaderLinesChanged].
 */
@Composable
internal fun HeaderLinesChangedIndicator(lines: HeaderLinesChanged?, modifier: Modifier = Modifier) {
    if (lines == null) {
        return
    }
    Tooltip(tooltip = { Text(text = lines.tooltip) }) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = lines.emoji)
            Text(text = lines.addedText, color = addedLinesColor())
            Text(text = lines.removedText, color = removedLinesColor())
        }
    }
}

/** The colour of the added lines of a lines-changed indicator. */
internal fun addedLinesColor(): Color = JBColor(0x208A3C, 0x5FB865).toComposeColor()

/** The colour of the removed lines of a lines-changed indicator. */
internal fun removedLinesColor(): Color = JBColor(0xDB3B4B, 0xDB5C5C).toComposeColor()