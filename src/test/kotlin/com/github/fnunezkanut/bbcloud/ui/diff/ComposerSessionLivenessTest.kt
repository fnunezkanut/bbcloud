package com.github.fnunezkanut.bbcloud.ui.diff

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class ComposerSessionLivenessTest {
    @Test
    fun `isComposerSessionLive - should be live when the editor is open and the inlay is valid`() {
        // GIVEN / WHEN
        val result = isComposerSessionLive(isEditorDisposed = false, isInlayValid = true)

        // THEN
        result shouldBe true
    }

    @Test
    fun `isComposerSessionLive - should be stale when the inlay is gone so a new composer can open on the line`() {
        // GIVEN / WHEN
        val result = isComposerSessionLive(isEditorDisposed = false, isInlayValid = false)

        // THEN
        result shouldBe false
    }

    @Test
    fun `isComposerSessionLive - should be stale when the editor was released`() {
        // GIVEN / WHEN
        val result = isComposerSessionLive(isEditorDisposed = true, isInlayValid = true)

        // THEN
        result shouldBe false
    }
}