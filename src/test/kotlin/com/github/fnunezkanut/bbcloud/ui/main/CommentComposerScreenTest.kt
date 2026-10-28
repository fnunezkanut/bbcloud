package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class CommentComposerScreenTest {
    @Nested
    inner class ComposerEditorMaxHeight {
        @Test
        fun `composerEditorMaxHeight - should keep a finite request above the minimum`() {
            // GIVEN a finite request larger than the minimum editor height
            val requested = 200.dp

            // WHEN the cap is computed
            val cap = composerEditorMaxHeight(requested = requested)

            // THEN the requested cap is used unchanged
            cap shouldBe 200.dp
        }

        @Test
        fun `composerEditorMaxHeight - should raise a request below the minimum to the minimum`() {
            // GIVEN a request smaller than the minimum editor height
            val requested = 10.dp

            // WHEN the cap is computed
            val cap = composerEditorMaxHeight(requested = requested)

            // THEN the cap is raised so heightIn always receives a valid range
            cap shouldBe 80.dp
        }

        @Test
        fun `composerEditorMaxHeight - should replace an unspecified request with the default cap`() {
            // GIVEN an unspecified request, the value a caller gets when it passes no height
            val requested = Dp.Unspecified

            // WHEN the cap is computed
            val cap = composerEditorMaxHeight(requested = requested)

            // THEN the default finite cap is used
            cap shouldBe COMPOSER_EDITOR_MAX_HEIGHT
        }

        @Test
        fun `composerEditorMaxHeight - should replace an infinite request with the default cap`() {
            // GIVEN an infinite request, which would reproduce the unbounded measurement
            val requested = Dp.Infinity

            // WHEN the cap is computed
            val cap = composerEditorMaxHeight(requested = requested)

            // THEN the result is finite and equals the default cap
            assertSoftly {
                cap shouldBe COMPOSER_EDITOR_MAX_HEIGHT
                cap.value.isFinite() shouldBe true
            }
        }

        @Test
        fun `COMPOSER_EDITOR_MAX_HEIGHT - should be finite`() {
            // GIVEN the default cap constant

            // THEN it is a finite value so the editor never measures against an unbounded height
            COMPOSER_EDITOR_MAX_HEIGHT.value.isFinite() shouldBe true
        }
    }
}