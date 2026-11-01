package com.github.fnunezkanut.bbcloud.ui.pullrequest.help

import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BbCloudWorktreesHelpFileEditorProviderTest {
    private val provider = BbCloudWorktreesHelpFileEditorProvider()

    @Test
    fun `accept - should accept the work trees help virtual file`() {
        // GIVEN
        val project = mockk<Project>()
        val file = mockk<BbCloudWorktreesHelpVirtualFile>()

        // WHEN
        val accepted = provider.accept(project = project, file = file)

        // THEN
        accepted shouldBe true
    }

    @Test
    fun `accept - should reject unrelated virtual files`() {
        // GIVEN
        val project = mockk<Project>()
        val file = mockk<VirtualFile> {
            every { name } returns "README.md"
        }

        // WHEN
        val accepted = provider.accept(project = project, file = file)

        // THEN
        accepted shouldBe false
    }

    @Test
    fun `editor - should use the work trees editor type id and hide the default editor`() {
        // WHEN
        val editorTypeId = provider.getEditorTypeId()
        val policy = provider.getPolicy()

        // THEN
        editorTypeId shouldBe "BbCloudWorktreesHelp"
        policy shouldBe FileEditorPolicy.HIDE_DEFAULT_EDITOR
    }
}