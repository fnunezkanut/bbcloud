@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey

/** The corner radius of the outlined action button. */
internal val OUTLINED_ACTION_BUTTON_ARC = 8.dp

/** The width of the outline the outlined action button draws. */
private val OUTLINE_STROKE_WIDTH = 1.dp

/** The length of a dash of the dotted outline a disabled outlined action button draws. */
private val DASH_LENGTH = 1.dp

/** The gap between two dashes of the dotted outline a disabled outlined action button draws. */
private val DASH_GAP = 2.dp

/** The horizontal padding between the outline and the content of an outlined action button. */
private val BUTTON_HORIZONTAL_PADDING = 8.dp

/** The vertical padding between the outline and the content of an outlined action button. */
private val BUTTON_VERTICAL_PADDING = 3.dp

/** The gap between the icon and the label of an outlined action button. */
private val ICON_TEXT_GAP = 4.dp

/**
 * The coloured-outline action button the plugin uses for its primary, secondary, and destructive actions.
 *
 * The outline follows the theme: it is drawn with the colour the caller supplies while the button is enabled, and with
 * the theme-aware disabled colour, as a dotted line, while it is disabled. Hovering the button fills it with a faint
 * wash of the outline colour.
 */
@Composable
internal fun BbCloudOutlinedActionButton(
    text: String,
    icon: IconKey?,
    outlineColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val disabledColor = labelDisabledForeground()
    val outline = outlinedActionButtonOutlineColor(enabled = enabled, outlineColor = outlineColor, disabledColor = disabledColor)
    val contentColor = outlinedActionButtonContentColor(enabled = enabled, contentColor = labelForeground(), disabledColor = disabledColor)
    val fillColor = outlinedActionButtonFillColor(enabled = enabled, hovered = hovered, outlineColor = outline)
    val shape = RoundedCornerShape(OUTLINED_ACTION_BUTTON_ARC)
    Row(
        modifier = modifier
            .clip(shape)
            .background(fillColor)
            .drawBehind {
                val strokeWidth = OUTLINE_STROKE_WIDTH.toPx()
                val inset = strokeWidth / 2f
                val arc = OUTLINED_ACTION_BUTTON_ARC.toPx()
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
                        pathEffect = if (enabled) {
                            null
                        } else {
                            PathEffect.dashPathEffect(floatArrayOf(DASH_LENGTH.toPx(), DASH_GAP.toPx()))
                        }
                    )
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = BUTTON_HORIZONTAL_PADDING, vertical = BUTTON_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ICON_TEXT_GAP)
    ) {
        if (icon != null) {
            Icon(
                key = icon,
                contentDescription = null,
                tint = if (enabled) Color.Unspecified else contentColor
            )
        }
        Text(text = text, color = contentColor)
    }
}