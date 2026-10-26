@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text

/** The default corner radius of a rounded badge. */
internal val ROUNDED_BADGE_ARC = 6.dp

/** The horizontal padding between the badge outline and its text. */
private val BADGE_HORIZONTAL_PADDING = 6.dp

/** The gap between the leading content of a badge and its text. */
private val BADGE_ICON_TEXT_GAP = 4.dp

/**
 * A rounded, translucent pill used for the counters of the pull-request tabs.
 *
 * The colours are resolved on every composition from the current theme, so the badge follows theme and state changes.
 *
 * An optional [leadingContent] slot lets a badge carry an icon next to its text, the way the comment count of a changed
 * file carries a chat balloon. Without it the badge keeps its text-only layout.
 */
@Composable
internal fun RoundedBadge(
    text: String,
    fillColor: Color,
    outlineColor: Color? = null,
    arc: Dp = ROUNDED_BADGE_ARC,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(arc)
    Box(
        modifier = modifier
            .clip(shape)
            .background(fillColor)
            .then(
                if (outlineColor == null) {
                    Modifier
                } else {
                    Modifier.border(width = 1.dp, color = outlineColor, shape = shape)
                }
            )
            .padding(horizontal = BADGE_HORIZONTAL_PADDING),
        contentAlignment = Alignment.Center
    ) {
        if (leadingContent == null) {
            Text(text = text)
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BADGE_ICON_TEXT_GAP)
            ) {
                leadingContent()
                Text(text = text)
            }
        }
    }
}