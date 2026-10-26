@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.icon.IconKey

private val COMPACT_BUTTON_ARC = 4.dp
private val COMPACT_BUTTON_ICON_SIZE = 16.dp
private val COMPACT_BUTTON_PADDING = 2.dp
private val COMPACT_BUTTON_OUTLINE_WIDTH = 1.dp

/**
 * Compact outlined action control for dense conversation metadata rows. The icon remains 16 dp; the hit target is kept
 * intentionally compact to match the review UI density. Set [showOutline] to false for a borderless, fill-free icon
 * button while preserving the shared click and hover behavior.
 */
@Composable
internal fun BbCloudCompactOutlinedIconButton(
    icon: IconKey,
    contentDescription: String,
    outlineColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    showOutline: Boolean = true,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val disabledColor = labelDisabledForeground()
    val outline = outlinedActionButtonOutlineColor(enabled = enabled, outlineColor = outlineColor, disabledColor = disabledColor)
    val contentColor = outlinedActionButtonContentColor(enabled = enabled, contentColor = labelForeground(), disabledColor = disabledColor)
    val fillColor = outlinedActionButtonFillColor(enabled = enabled, hovered = hovered, outlineColor = outline)
    val shape = RoundedCornerShape(COMPACT_BUTTON_ARC)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (showOutline) fillColor else Color.Transparent)
            .drawBehind {
                if (showOutline) {
                    val strokeWidth = COMPACT_BUTTON_OUTLINE_WIDTH.toPx()
                    val inset = strokeWidth / 2f
                    val arc = COMPACT_BUTTON_ARC.toPx()
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(
                                    offset = Offset(inset, inset),
                                    size = Size(size.width - strokeWidth, size.height - strokeWidth)
                                ),
                                cornerRadius = CornerRadius(arc, arc)
                            )
                        )
                    }
                    drawPath(
                        path = path,
                        color = outline,
                        style = Stroke(
                            width = strokeWidth,
                            pathEffect = if (enabled) null else PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 2.dp.toPx()))
                        )
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(COMPACT_BUTTON_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            key = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(COMPACT_BUTTON_ICON_SIZE),
            tint = if (enabled) Color.Unspecified else contentColor
        )
        trailingContent?.invoke()
    }
}