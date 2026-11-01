package com.github.fnunezkanut.bbcloud.ui.pullrequest.help

import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.project.Project
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BbCloudWorktreesHelpVirtualFileTest {
    @Test
    fun `virtual file - should expose a read-only help pr name and the dedicated help file type`() {
        // GIVEN
        val project = mockk<Project> {
            every { isOpen } returns true
            every { isDisposed } returns false
        }

        // WHEN
        val file = BbCloudWorktreesHelpVirtualFile(project = project)

        // THEN
        assertSoftly {
            file.name shouldBe "help.pr"
            file.isWritable shouldBe false
            file.isValid shouldBe true
            file.fileType shouldBe BbCloudWorktreesHelpFileType
            file.getUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT) shouldBe true
        }
    }

    @Test
    fun `virtual file - should be invalid when the project is disposed`() {
        // GIVEN
        val project = mockk<Project> {
            every { isOpen } returns true
            every { isDisposed } returns true
        }

        // WHEN
        val file = BbCloudWorktreesHelpVirtualFile(project = project)

        // THEN
        file.isValid shouldBe false
    }

    @Test
    fun `virtual file - should be invalid when the project is closed`() {
        // GIVEN
        val project = mockk<Project> {
            every { isOpen } returns false
            every { isDisposed } returns false
        }

        // WHEN
        val file = BbCloudWorktreesHelpVirtualFile(project = project)

        // THEN
        file.isValid shouldBe false
    }
}