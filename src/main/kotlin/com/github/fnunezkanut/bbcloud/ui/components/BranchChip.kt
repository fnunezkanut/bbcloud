@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/** The corner radius of a branch chip. */
private val BRANCH_CHIP_ARC = 4.dp

/** The arrow drawn between a source and a destination branch chip. */
const val BRANCH_ARROW = "\u2192"

/**
 * A gray rounded label naming a branch, shared by the pull-request listing/information card and the worktree banner.
 *
 * Bridging the Swing theme color [JBUI.CurrentTheme.ActionButton.hoverBackground] into Compose keeps the pill a
 * visible, theme-aware gray in both light and dark themes.
 */
@Composable
internal fun BranchChip(text: String, modifier: Modifier = Modifier) {
    val chipBackground = Color(JBUI.CurrentTheme.ActionButton.hoverBackground().rgb)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(BRANCH_CHIP_ARC))
            .background(color = chipBackground)
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            color = JewelTheme.globalColors.text.info,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}