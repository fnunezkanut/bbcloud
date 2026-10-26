package com.github.fnunezkanut.bbcloud.ui.otherprojects

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.intellij.ui.scale.JBUIScale
import org.jetbrains.skia.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.ceil

/** Paints an IntelliJ icon into a Compose bitmap while preserving theme-specific icon colors. */
internal fun javax.swing.Icon.toComposeImageBitmap(scale: Double = JBUIScale.sysScale().toDouble()): ImageBitmap {
    // Allocate in device pixels so HiDPI avatar detail is not lost at the Swing-to-Compose boundary.
    val deviceScale = scale.coerceAtLeast(1.0)
    val deviceWidth = ceil(iconWidth * deviceScale).toInt().coerceAtLeast(1)
    val deviceHeight = ceil(iconHeight * deviceScale).toInt().coerceAtLeast(1)
    val image = BufferedImage(deviceWidth, deviceHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        graphics.scale(deviceScale, deviceScale)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        paintIcon(null, graphics, 0, 0)
    } finally {
        graphics.dispose()
    }
    return Image.makeFromEncoded(
        ByteArrayOutputStream().also { output ->
            ImageIO.write(image, "png", output)
        }.toByteArray()
    ).toComposeImageBitmap()
}