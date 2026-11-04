package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.ui.geometry.Rect
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class CommentMentionPopupLocationTest {
    @Nested
    inner class Location {
        private val bounds = Rect(left = 100f, top = 50f, right = 500f, bottom = 250f)

        @Test
        fun `mentionPopupLocation - should place below the cursor rect`() {
            // GIVEN a cursor near the top-left of the text area
            val cursor = Rect(left = 10f, top = 0f, right = 12f, bottom = 18f)

            // WHEN
            val point = mentionPopupLocation(
                textAreaBoundsInRoot = bounds,
                cursorRect = cursor,
                contentPaddingLeftPx = 4f,
                contentPaddingTopPx = 4f,
                scrollOffsetPx = 0f,
                density = 1f
            )

            // THEN x = left + pad + cursor.left; y = top + pad + cursor.bottom
            point.x shouldBe 114
            point.y shouldBe 72
        }

        @Test
        fun `mentionPopupLocation - should fall back to the bottom-left without a cursor rect`() {
            // WHEN there is no cursor rect
            val point = mentionPopupLocation(
                textAreaBoundsInRoot = bounds,
                cursorRect = null,
                contentPaddingLeftPx = 0f,
                contentPaddingTopPx = 0f,
                scrollOffsetPx = 0f,
                density = 1f
            )

            // THEN x = left; y = clamped(top + height) = bottom
            point.x shouldBe 100
            point.y shouldBe 250
        }

        @Test
        fun `mentionPopupLocation - should clamp above the top`() {
            // GIVEN a cursor whose computed y would be above the text area (large scroll offset)
            val cursor = Rect(left = 0f, top = 0f, right = 2f, bottom = 18f)

            // WHEN
            val point = mentionPopupLocation(
                textAreaBoundsInRoot = bounds,
                cursorRect = cursor,
                contentPaddingLeftPx = 0f,
                contentPaddingTopPx = 0f,
                scrollOffsetPx = 1000f,
                density = 1f
            )

            // THEN y is clamped to the top
            point.y shouldBe 50
        }

        @Test
        fun `mentionPopupLocation - should clamp below the bottom`() {
            // GIVEN a cursor far below the text area
            val cursor = Rect(left = 0f, top = 400f, right = 2f, bottom = 418f)

            // WHEN
            val point = mentionPopupLocation(
                textAreaBoundsInRoot = bounds,
                cursorRect = cursor,
                contentPaddingLeftPx = 0f,
                contentPaddingTopPx = 0f,
                scrollOffsetPx = 0f,
                density = 1f
            )

            // THEN y is clamped to the bottom
            point.y shouldBe 250
        }

        @Test
        fun `mentionPopupLocation - should halve the values at density 2`() {
            // GIVEN a cursor
            val cursor = Rect(left = 10f, top = 0f, right = 12f, bottom = 18f)

            // WHEN density is 2.0
            val point = mentionPopupLocation(
                textAreaBoundsInRoot = bounds,
                cursorRect = cursor,
                contentPaddingLeftPx = 4f,
                contentPaddingTopPx = 4f,
                scrollOffsetPx = 0f,
                density = 2f
            )

            // THEN both values are halved (x floored, y ceiled)
            point.x shouldBe 57
            point.y shouldBe 36
        }

        @Test
        fun `mentionPopupLocation - should treat density 0 as 1`() {
            // GIVEN
            val cursor = Rect(left = 10f, top = 0f, right = 12f, bottom = 18f)

            // WHEN density is 0
            val point = mentionPopupLocation(
                textAreaBoundsInRoot = bounds,
                cursorRect = cursor,
                contentPaddingLeftPx = 4f,
                contentPaddingTopPx = 4f,
                scrollOffsetPx = 0f,
                density = 0f
            )

            // THEN the same values as density 1
            point.x shouldBe 114
            point.y shouldBe 72
        }
    }

    @Nested
    inner class Height {
        @Test
        fun `mentionPopupHeight - should size rows plus the status row`() {
            // WHEN three rows and a status row
            val height = mentionPopupHeight(rowCount = 3, hasStatusRow = true)

            // THEN four rows of 28
            height shouldBe 4 * 28
        }

        @Test
        fun `mentionPopupHeight - should cap at the maximum suggestions`() {
            // WHEN more rows than the cap, with a status row
            val height = mentionPopupHeight(rowCount = 20, hasStatusRow = true)

            // THEN capped rows plus the status row
            height shouldBe (MentionCandidateRanker.MAX_SUGGESTIONS + 1) * 28
        }

        @Test
        fun `mentionPopupHeight - should keep at least one row`() {
            // WHEN no rows and no status row
            val height = mentionPopupHeight(rowCount = 0, hasStatusRow = false)

            // THEN one row tall
            height shouldBe 28
        }
    }
}