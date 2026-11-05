package com.github.fnunezkanut.bbcloud.ui.markdowndiff

import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BbCloudMarkdownDiffFileEditorProviderTest {
    private val provider = BbCloudMarkdownDiffFileEditorProvider()

    @Test
    fun `accept - should accept only the Markdown diff virtual file`() {
        // GIVEN
        val project = mockk<Project>()

        // WHEN
        val acceptsDiffFile = provider.accept(project = project, file = mockk<BbCloudMarkdownDiffVirtualFile>())
        val acceptsOtherFile = provider.accept(project = project, file = mockk<VirtualFile>())

        // THEN
        assertSoftly {
            acceptsDiffFile shouldBe true
            acceptsOtherFile shouldBe false
        }
    }

    @Test
    fun `editor - should use its own editor type id and hide the default editor`() {
        // THEN
        assertSoftly {
            provider.getEditorTypeId() shouldBe "BbCloudMarkdownDiff"
            provider.getPolicy() shouldBe FileEditorPolicy.HIDE_DEFAULT_EDITOR
        }
    }

    @Test
    fun `isMarkdownFileName - should recognize Markdown extensions case-insensitively`() {
        // THEN
        assertSoftly {
            MarkdownFiles.isMarkdownFileName(fileName = "README.MD") shouldBe true
            MarkdownFiles.isMarkdownFileName(fileName = "notes.markdown") shouldBe true
            MarkdownFiles.isMarkdownFileName(fileName = "build.gradle.kts") shouldBe false
            MarkdownFiles.isMarkdownFileName(fileName = "md") shouldBe false
            MarkdownFiles.isMarkdownFileName(fileName = null) shouldBe false
        }
    }
}