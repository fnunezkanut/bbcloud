package com.github.fnunezkanut.bbcloud.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a dotted, rounded outline along the edges of the composable it is applied to. The dash pattern matches the dotted
 * outline of `BbCloudOutlinedActionButton`, so the two read as the same control style.
 *
 * The stroke is drawn inside the bounds, inset by half its width, so the outer edge of the stroke is not clipped by the
 * bounds of the composable or of its parent.
 */
internal fun Modifier.dottedBorder(color: Color, width: Dp = 1.dp, cornerRadius: Dp = 8.dp, dashLength: Dp = 1.dp, dashGap: Dp = 2.dp): Modifier {
    return drawBehind {
        val strokeWidth = width.toPx()
        val strokeRect = dottedBorderStrokeRect(
            width = size.width,
            height = size.height,
            strokeWidth = strokeWidth
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(strokeRect.left, strokeRect.top),
            size = Size(strokeRect.width, strokeRect.height),
            cornerRadius = CornerRadius(cornerRadius.toPx()),
            style = Stroke(
                width = strokeWidth,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength.toPx(), dashGap.toPx()))
            )
        )
    }
}

/**
 * Returns the rectangle the centre line of a stroke of [strokeWidth] follows inside a box of [width] by [height]. The
 * rectangle starts at half the stroke width and is reduced by the full stroke width on each axis, so the stroke stays
 * within the box.
 */
internal fun dottedBorderStrokeRect(width: Float, height: Float, strokeWidth: Float): Rect {
    val inset = strokeWidth / 2f
    return Rect(
        offset = Offset(inset, inset),
        size = Size(width - strokeWidth, height - strokeWidth)
    )
}