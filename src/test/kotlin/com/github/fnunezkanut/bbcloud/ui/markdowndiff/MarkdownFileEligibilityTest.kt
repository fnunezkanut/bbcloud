package com.github.fnunezkanut.bbcloud.ui.markdowndiff

import com.intellij.openapi.vfs.VirtualFile
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class MarkdownFileEligibilityTest {
    private fun file(name: String, directory: Boolean = false, local: Boolean = true): VirtualFile {
        val file = mockk<VirtualFile>()
        every { file.name } returns name
        every { file.isDirectory } returns directory
        every { file.isInLocalFileSystem } returns local
        return file
    }

    @Test
    fun `isLocalMarkdownFile - should accept a local Markdown file`() {
        // GIVEN
        val file = file(name = "README.md")

        // WHEN
        val result = MarkdownFileEligibility.isLocalMarkdownFile(file = file)

        // THEN
        result shouldBe true
    }

    @Test
    fun `isLocalMarkdownFile - should reject directories, non local files and other extensions`() {
        // GIVEN
        val directory = file(name = "docs.md", directory = true)
        val remote = file(name = "a.md", local = false)
        val code = file(name = "a.kt")

        // WHEN / THEN
        MarkdownFileEligibility.isLocalMarkdownFile(file = directory) shouldBe false
        MarkdownFileEligibility.isLocalMarkdownFile(file = remote) shouldBe false
        MarkdownFileEligibility.isLocalMarkdownFile(file = code) shouldBe false
    }
}