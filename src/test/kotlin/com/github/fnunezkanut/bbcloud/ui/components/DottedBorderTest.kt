package com.github.fnunezkanut.bbcloud.ui.components

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.floats.shouldBeExactly
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class DottedBorderTest {
    @Test
    fun `dottedBorderStrokeRect - should inset the stroke by half its width`() {
        // GIVEN
        val width = 200f
        val height = 100f
        val strokeWidth = 2f

        // WHEN
        val rect = dottedBorderStrokeRect(width = width, height = height, strokeWidth = strokeWidth)

        // THEN
        assertSoftly(rect) {
            left shouldBeExactly 1f
            top shouldBeExactly 1f
        }
    }

    @Test
    fun `dottedBorderStrokeRect - should reduce the size by the full stroke width`() {
        // GIVEN
        val width = 200f
        val height = 100f
        val strokeWidth = 2f

        // WHEN
        val rect = dottedBorderStrokeRect(width = width, height = height, strokeWidth = strokeWidth)

        // THEN
        assertSoftly(rect) {
            this.width shouldBeExactly 198f
            this.height shouldBeExactly 98f
        }
    }
}