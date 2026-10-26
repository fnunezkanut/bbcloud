package com.github.fnunezkanut.bbcloud.ui.otherprojects

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

@Tag("unit")
class ComposeIconBitmapsTest {
    @Test
    fun `toComposeImageBitmap - should preserve device pixel dimensions`() {
        // GIVEN
        val icon = FlatColorIcon(size = 48)

        // WHEN
        val bitmap = icon.toComposeImageBitmap(scale = 2.0)

        // THEN
        bitmap.width shouldBe 96
        bitmap.height shouldBe 96
    }

    private class FlatColorIcon(private val size: Int) : Icon {
        override fun getIconWidth(): Int {
            return size
        }

        override fun getIconHeight(): Int {
            return size
        }

        override fun paintIcon(component: Component?, graphics: Graphics, x: Int, y: Int) {
            graphics.color = Color.BLUE
            graphics.fillRect(x, y, size, size)
        }
    }
}