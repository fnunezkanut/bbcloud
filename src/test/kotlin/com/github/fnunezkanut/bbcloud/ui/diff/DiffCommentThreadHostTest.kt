@file:OptIn(androidx.compose.ui.unit.ExperimentalUnitApi::class)

package com.github.fnunezkanut.bbcloud.ui.diff

import androidx.compose.ui.awt.UNSPECIFIED_DIMENSION_VALUE
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformDataKeys
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
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
    inner class ResolveContentScale {
        @Test
        fun `resolveContentScale - should use the Compose density when it is positive`() {
            // GIVEN a window on a 1x monitor while the primary monitor is 2x
            // WHEN the content scale is resolved from the Compose density
            val scale = resolveContentScale(composeDensity = 1.0f, componentScale = { 2.0f })

            // THEN the Compose density wins over the primary monitor scale
            scale shouldBe 1.0f
        }

        @Test
        fun `resolveContentScale - should use the Compose density on a Retina monitor when the primary monitor is 1x`() {
            // GIVEN a window on a 2x monitor while the primary monitor is 1x
            // WHEN the content scale is resolved from the Compose density
            val scale = resolveContentScale(composeDensity = 2.0f, componentScale = { 1.0f })

            // THEN the Compose density is used
            scale shouldBe 2.0f
        }

        @Test
        fun `resolveContentScale - should fall back to the component scale when the density is zero`() {
            // GIVEN a Compose density that is not usable yet
            // WHEN the content scale is resolved
            val scale = resolveContentScale(composeDensity = 0f, componentScale = { 1.5f })

            // THEN the component scale is used
            scale shouldBe 1.5f
        }

        @Test
        fun `resolveContentScale - should fall back to the component scale when the density is not finite`() {
            // GIVEN non-finite Compose densities
            // WHEN the content scale is resolved
            val nanScale = resolveContentScale(composeDensity = Float.NaN, componentScale = { 1.5f })
            val infiniteScale = resolveContentScale(composeDensity = Float.POSITIVE_INFINITY, componentScale = { 1.5f })

            // THEN the component scale is used for both
            assertSoftly {
                nanScale shouldBe 1.5f
                infiniteScale shouldBe 1.5f
            }
        }

        @Test
        fun `resolveContentScale - should not query the component scale when the density is usable`() {
            // GIVEN a usable Compose density and a component scale that counts its queries
            var componentScaleQueries = 0

            // WHEN the content scale is resolved
            resolveContentScale(composeDensity = 1.25f, componentScale = {
                componentScaleQueries++
                2.0f
            })

            // THEN the component scale is never queried
            componentScaleQueries shouldBe 0
        }
    }

    @Nested
    inner class MixedDpiLogicalHeight {
        @Test
        fun `mixed DPI - should give the same logical height on 1x and 2x monitors`() {
            // GIVEN the same logical height of 210 rendered on a 2x monitor (420 px) and on a 1x monitor (210 px),
            // while the primary monitor is at the opposite scale
            // WHEN the heights are converted with the density of the monitor the panel is on
            val retinaHeight = composePixelsToSwingPixels(
                pixels = 420,
                scale = resolveContentScale(composeDensity = 2f, componentScale = { 1f })
            )
            val standardHeight = composePixelsToSwingPixels(
                pixels = 210,
                scale = resolveContentScale(composeDensity = 1f, componentScale = { 2f })
            )

            // THEN both monitors give the same logical height
            assertSoftly {
                retinaHeight shouldBe 210
                standardHeight shouldBe 210
            }
        }

        @Test
        fun `mixed DPI - should round a fractional 125 percent height up to avoid clipping`() {
            // GIVEN 263 physical pixels at 125 % density while the primary monitor is at 2x
            // WHEN the height is converted
            val height = composePixelsToSwingPixels(
                pixels = 263,
                scale = resolveContentScale(composeDensity = 1.25f, componentScale = { 2f })
            )

            // THEN the logical height is rounded up (210.4 becomes 211)
            height shouldBe 211
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

    @Nested
    inner class InlayHeightOrFallback {
        @Test
        fun `inlayHeightOrFallback - should return the resolved height without reporting a failure`() {
            // GIVEN a resolver that yields a measured height
            var failures = 0

            // WHEN the height is resolved
            val height = inlayHeightOrFallback(
                resolve = { 120 },
                fallback = { 40 },
                onFailure = { failures++ }
            )

            // THEN the measured height is returned and no failure is reported
            assertSoftly {
                height shouldBe 120
                failures shouldBe 0
            }
        }

        @Test
        fun `inlayHeightOrFallback - should use the fallback when the resolver yields no value`() {
            // GIVEN a resolver that has no height yet
            val height = inlayHeightOrFallback(
                resolve = { null },
                fallback = { 40 },
                onFailure = { throw AssertionError("no failure expected") }
            )

            // THEN the fallback height is returned
            height shouldBe 40
        }

        @Test
        fun `inlayHeightOrFallback - should report and replace an IllegalStateException with the fallback`() {
            // GIVEN a measurement that throws the way ComposeContainer.actualizeSize does
            var reported: IllegalStateException? = null

            // WHEN the height is resolved
            val height = inlayHeightOrFallback(
                resolve = { throw IllegalStateException("Unable to actualize size of Compose content") },
                fallback = { 40 },
                onFailure = { exception -> reported = exception }
            )

            // THEN the fallback is returned and the exception is handed to the reporter
            assertSoftly {
                height shouldBe 40
                reported?.message shouldBe "Unable to actualize size of Compose content"
            }
        }

        @Test
        fun `inlayHeightOrFallback - should propagate exceptions other than IllegalStateException`() {
            // GIVEN a measurement that fails with an unrelated exception
            val act = { inlayHeightOrFallback(resolve = { throw IllegalArgumentException("unexpected") }, fallback = { 40 }, onFailure = {}) }

            // WHEN the height is resolved, THEN the unrelated exception is not swallowed
            shouldThrow<IllegalArgumentException> { act() }
        }
    }

    @Nested
    inner class HideHostEditorData {
        @Test
        fun `hideHostEditorData - should hide the editor so editor actions do not consume keystrokes`() {
            // GIVEN a relaxed data sink
            val sink = mockk<DataSink>(relaxed = true)

            // WHEN the host editor data is hidden
            hideHostEditorData(sink = sink)

            // THEN the editor and its host editor are explicitly null
            verify(exactly = 1) { sink.setNull(CommonDataKeys.EDITOR) }
            verify(exactly = 1) { sink.setNull(CommonDataKeys.HOST_EDITOR) }
        }

        @Test
        fun `hideHostEditorData - should hide the caret and virtual space derived from the editor`() {
            // GIVEN a relaxed data sink
            val sink = mockk<DataSink>(relaxed = true)

            // WHEN the host editor data is hidden
            hideHostEditorData(sink = sink)

            // THEN the caret and the virtual-space flag are explicitly null
            verify(exactly = 1) { sink.setNull(CommonDataKeys.CARET) }
            verify(exactly = 1) { sink.setNull(CommonDataKeys.EDITOR_VIRTUAL_SPACE) }
        }

        @Test
        fun `hideHostEditorData - should hide the copy, cut and delete providers of the editor`() {
            // GIVEN a relaxed data sink
            val sink = mockk<DataSink>(relaxed = true)

            // WHEN the host editor data is hidden
            hideHostEditorData(sink = sink)

            // THEN the editor's copy, cut and delete providers are explicitly null
            verify(exactly = 1) { sink.setNull(PlatformDataKeys.COPY_PROVIDER) }
            verify(exactly = 1) { sink.setNull(PlatformDataKeys.CUT_PROVIDER) }
            verify(exactly = 1) { sink.setNull(PlatformDataKeys.DELETE_ELEMENT_PROVIDER) }
        }

        @Test
        fun `hideHostEditorData - should not hide or inject the paste provider`() {
            // GIVEN a relaxed data sink
            val sink = mockk<DataSink>(relaxed = true)

            // WHEN the host editor data is hidden
            hideHostEditorData(sink = sink)

            // THEN no call touches the paste provider, which the Jewel wrapper inside the host supplies
            verify(exactly = 0) { sink.setNull(PlatformDataKeys.PASTE_PROVIDER) }
            verify(exactly = 0) { sink.set(PlatformDataKeys.PASTE_PROVIDER, any()) }
        }

        @Test
        fun `hideHostEditorData - should only call setNull and never inject data`() {
            // GIVEN a relaxed data sink
            val sink = mockk<DataSink>(relaxed = true)

            // WHEN the host editor data is hidden
            hideHostEditorData(sink = sink)

            // THEN every recorded call is one of the seven explicit setNull calls
            verify(exactly = 1) { sink.setNull(CommonDataKeys.EDITOR) }
            verify(exactly = 1) { sink.setNull(CommonDataKeys.HOST_EDITOR) }
            verify(exactly = 1) { sink.setNull(CommonDataKeys.CARET) }
            verify(exactly = 1) { sink.setNull(CommonDataKeys.EDITOR_VIRTUAL_SPACE) }
            verify(exactly = 1) { sink.setNull(PlatformDataKeys.COPY_PROVIDER) }
            verify(exactly = 1) { sink.setNull(PlatformDataKeys.CUT_PROVIDER) }
            verify(exactly = 1) { sink.setNull(PlatformDataKeys.DELETE_ELEMENT_PROVIDER) }
            confirmVerified(sink)
        }
    }
}