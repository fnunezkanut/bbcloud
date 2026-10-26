@file:OptIn(androidx.compose.ui.unit.ExperimentalUnitApi::class)

package com.github.fnunezkanut.bbcloud.ui.diff

import androidx.compose.ui.awt.UNSPECIFIED_DIMENSION_VALUE
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.DimensionRestrictions
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.awt.BorderLayout
import javax.swing.JPanel

@Tag("unit")
class DiffCommentThreadHostTest {
    @Nested
    inner class EditorInlayContentWidth {
        @Test
        fun `editorInlayContentWidth - should deduct scrollbar allowance from the viewport`() {
            // GIVEN a laid-out viewport with an unflipped scrollbar (gutter gap not applied)
            val viewportWidth = 1000

            // WHEN the content width is derived
            val width = editorInlayContentWidth(
                viewportWidth = viewportWidth,
                verticalScrollbarWidth = 24,
                gutterTextGap = 0
            )

            // THEN the result is the viewport minus the scrollbar allowance, with no 42 em cap
            width shouldBe 976
        }

        @Test
        fun `editorInlayContentWidth - should deduct the gutter text gap when the scrollbar is flipped`() {
            // GIVEN a flipped scrollbar contributes a gutter text gap
            val width = editorInlayContentWidth(
                viewportWidth = 1000,
                verticalScrollbarWidth = 12,
                gutterTextGap = 40
            )

            // THEN both the scrollbar allowance and the gutter gap are deducted
            width shouldBe 948
        }

        @Test
        fun `editorInlayContentWidth - should use the full derived width on a wide viewport without a max cap`() {
            // GIVEN a very wide viewport (clarification 1b: no bbcloud 42 em cap)
            val width = editorInlayContentWidth(
                viewportWidth = 4000,
                verticalScrollbarWidth = 24,
                gutterTextGap = 0
            )

            // THEN the full visible text width is returned, far beyond any 42 em + 52 cap
            assertSoftly {
                width shouldBe 3976
                (width > 42 * 16 + 52) shouldBe true
            }
        }

        @Test
        fun `editorInlayContentWidth - should clamp a narrow viewport to zero only`() {
            // GIVEN a narrow viewport where deductions exceed the viewport (clarification 9a)
            val width = editorInlayContentWidth(
                viewportWidth = 20,
                verticalScrollbarWidth = 24,
                gutterTextGap = 10
            )

            // THEN the result clamps to zero, never negative, and no artificial minimum width is applied
            width shouldBe 0
        }

        @Test
        fun `editorInlayContentWidth - should return zero when the viewport is not laid out`() {
            // GIVEN a viewport width of 0 (editor not laid out yet)
            val width = editorInlayContentWidth(
                viewportWidth = 0,
                verticalScrollbarWidth = 0,
                gutterTextGap = 0
            )

            // THEN the function returns 0 so the host defers the remeasure; no 42 em fallback width
            width shouldBe 0
        }
    }

    @Nested
    inner class PreferredComposeDimensionForWidthUpdate {
        @Test
        fun `preferredComposeDimensionForWidthUpdate - should not pin height to zero when Compose is not measured yet`() {
            // GIVEN a width update before Compose has a preferred height
            val dimension = preferredComposeDimensionForWidthUpdate(width = 800, existingPreferredHeight = 0)

            // THEN width is set and height stays unspecified so the inlay can grow after layout
            assertSoftly {
                dimension.width shouldBe 800
                dimension.height shouldBe UNSPECIFIED_DIMENSION_VALUE
            }
        }

        @Test
        fun `preferredComposeDimensionForWidthUpdate - should preserve a positive measured height on width refresh`() {
            // GIVEN Compose already reported a height (for example after Reply expanded)
            val dimension = preferredComposeDimensionForWidthUpdate(width = 900, existingPreferredHeight = 240)

            // THEN the height is kept while the width updates
            assertSoftly {
                dimension.width shouldBe 900
                dimension.height shouldBe 240
            }
        }
    }

    @Nested
    inner class ComposePixelsToSwingPixels {
        @Test
        fun `composePixelsToSwingPixels - should halve physical pixels at Retina scale 2`() {
            composePixelsToSwingPixels(pixels = 420, scale = 2.0f) shouldBe 210
        }

        @Test
        fun `composePixelsToSwingPixels - should leave pixels unchanged at scale 1`() {
            composePixelsToSwingPixels(pixels = 420, scale = 1.0f) shouldBe 420
        }

        @Test
        fun `composePixelsToSwingPixels - should round up fractional logical heights`() {
            composePixelsToSwingPixels(pixels = 301, scale = 1.5f) shouldBe 201
        }
    }

    @Nested
    inner class LiftInlayWrapperWidthCap {
        @Test
        fun `liftInlayWrapperWidthCap - should clear pref and max width on the platform wrapper layout`() {
            // GIVEN the same wrapper insertComponentAfter creates around inline comment hosts
            val wrapper = JPanel(SizeRestrictedSingleComponentLayout()).apply {
                val cap = DimensionRestrictions.ScalingConstant(width = 724, height = null)
                (layout as SizeRestrictedSingleComponentLayout).prefSize = cap
                (layout as SizeRestrictedSingleComponentLayout).maxSize = cap
            }

            // WHEN the cap is lifted
            val lifted = liftInlayWrapperWidthCap(wrapper = wrapper)

            // THEN both restrictions are removed so the host preferred width can span the editor
            val layout = wrapper.layout as SizeRestrictedSingleComponentLayout
            assertSoftly {
                lifted shouldBe true
                layout.prefSize shouldBe DimensionRestrictions.None
                layout.maxSize shouldBe DimensionRestrictions.None
            }
        }

        @Test
        fun `liftInlayWrapperWidthCap - should not change a panel with a different layout`() {
            val wrapper = JPanel(BorderLayout())

            liftInlayWrapperWidthCap(wrapper = wrapper) shouldBe false
        }
    }
}