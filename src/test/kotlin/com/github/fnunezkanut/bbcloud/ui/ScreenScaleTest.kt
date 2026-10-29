package com.github.fnunezkanut.bbcloud.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class ScreenScaleTest {
    @Nested
    inner class MaxScreenScale {
        @Test
        fun `maxScreenScale - should return the highest scale of several monitors`() {
            // GIVEN monitors at 1x, 2x, and 1.25x
            // WHEN the maximum scale is resolved
            val scale = ScreenScale.maxScreenScale(
                screenScales = { listOf(1f, 2f, 1.25f) },
                fallbackScale = { 1f }
            )

            // THEN the highest scale is returned
            scale shouldBe 2f
        }

        @Test
        fun `maxScreenScale - should ignore unusable scales`() {
            // GIVEN a monitor that reports zero and one that reports NaN next to a usable 1.5x monitor
            // WHEN the maximum scale is resolved
            val scale = ScreenScale.maxScreenScale(
                screenScales = { listOf(0f, Float.NaN, 1.5f) },
                fallbackScale = { 1f }
            )

            // THEN only the usable scale is considered
            scale shouldBe 1.5f
        }

        @Test
        fun `maxScreenScale - should use the fallback scale when no monitor is reported`() {
            // GIVEN no screen device information, as in headless runs
            // WHEN the maximum scale is resolved
            val scale = ScreenScale.maxScreenScale(
                screenScales = { emptyList() },
                fallbackScale = { 1.75f }
            )

            // THEN the fallback scale is returned
            scale shouldBe 1.75f
        }
    }
}