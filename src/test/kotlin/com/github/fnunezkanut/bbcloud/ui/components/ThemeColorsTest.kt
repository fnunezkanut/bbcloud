package com.github.fnunezkanut.bbcloud.ui.components

import androidx.compose.ui.graphics.Color
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class ThemeColorsTest {
    @Test
    fun `mixColors - should return the second colour when ratio is one`() {
        val first = Color(red = 0.2f, green = 0.3f, blue = 0.4f, alpha = 1f)
        val second = Color(red = 0.8f, green = 0.1f, blue = 0.9f, alpha = 0.5f)

        mixColors(first = first, second = second, ratio = 1f) shouldBe second
    }

    @Test
    fun `mixColors - should return the first colour when ratio is zero`() {
        val first = Color(red = 0.2f, green = 0.3f, blue = 0.4f, alpha = 1f)
        val second = Color(red = 0.8f, green = 0.1f, blue = 0.9f, alpha = 0.5f)

        val mixed = mixColors(first = first, second = second, ratio = 0f)

        mixed shouldBe first
    }
}