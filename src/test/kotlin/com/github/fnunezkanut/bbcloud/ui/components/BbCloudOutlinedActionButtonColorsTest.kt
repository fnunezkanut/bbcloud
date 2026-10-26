package com.github.fnunezkanut.bbcloud.ui.components

import androidx.compose.ui.graphics.Color
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BbCloudOutlinedActionButtonColorsTest {
    private val outlineColor = Color(0xFF3574F0)
    private val contentColor = Color(0xFF000000)
    private val disabledColor = Color(0xFF808080)

    @Test
    fun `outline color - should use the caller color while the button is enabled`() {
        // GIVEN
        val enabled = true

        // WHEN
        val result = outlinedActionButtonOutlineColor(enabled = enabled, outlineColor = outlineColor, disabledColor = disabledColor)

        // THEN
        result shouldBe outlineColor
    }

    @Test
    fun `outline color - should use the disabled color while the button is disabled`() {
        // GIVEN
        val enabled = false

        // WHEN
        val result = outlinedActionButtonOutlineColor(enabled = enabled, outlineColor = outlineColor, disabledColor = disabledColor)

        // THEN
        result shouldBe disabledColor
    }

    @Test
    fun `content color - should use the label color while the button is enabled`() {
        // GIVEN
        val enabled = true

        // WHEN
        val result = outlinedActionButtonContentColor(enabled = enabled, contentColor = contentColor, disabledColor = disabledColor)

        // THEN
        result shouldBe contentColor
    }

    @Test
    fun `content color - should use the disabled color while the button is disabled`() {
        // GIVEN
        val enabled = false

        // WHEN
        val result = outlinedActionButtonContentColor(enabled = enabled, contentColor = contentColor, disabledColor = disabledColor)

        // THEN
        result shouldBe disabledColor
    }

    @Test
    fun `fill color - should tint the outline color while an enabled button is hovered`() {
        // GIVEN
        val enabled = true
        val hovered = true

        // WHEN
        val result = outlinedActionButtonFillColor(enabled = enabled, hovered = hovered, outlineColor = outlineColor)

        // THEN
        withClue("The hover fill keeps the outline hue at the hover alpha") {
            result shouldBe outlineColor.copy(alpha = OUTLINED_ACTION_BUTTON_HOVER_FILL_ALPHA)
        }
    }

    @Test
    fun `fill color - should stay transparent while an enabled button is not hovered`() {
        // GIVEN
        val enabled = true
        val hovered = false

        // WHEN
        val result = outlinedActionButtonFillColor(enabled = enabled, hovered = hovered, outlineColor = outlineColor)

        // THEN
        result shouldBe Color.Transparent
    }

    @Test
    fun `fill color - should stay transparent while a disabled button is hovered`() {
        // GIVEN
        val enabled = false
        val hovered = true

        // WHEN
        val result = outlinedActionButtonFillColor(enabled = enabled, hovered = hovered, outlineColor = outlineColor)

        // THEN
        result shouldBe Color.Transparent
    }
}