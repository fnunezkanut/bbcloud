package com.github.fnunezkanut.bbcloud.ui.pullrequest.create

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BbCloudNewPullRequestFileEditorProviderTest {
    private val provider = BbCloudNewPullRequestFileEditorProvider()

    @Test
    fun `accept - should accept a new pull request virtual file`() {
        val project = mockk<Project>()
        val file = mockk<BbCloudNewPullRequestVirtualFile>()

        provider.accept(project = project, file = file) shouldBe true
    }

    @Test
    fun `accept - should reject unrelated virtual files`() {
        val project = mockk<Project>()
        val file = mockk<VirtualFile> {
            every { name } returns "README.md"
        }

        provider.accept(project = project, file = file) shouldBe false
    }

    @Test
    fun `accept - should accept the virtual file even when the project is disposed`() {
        val project = mockk<Project>()
        val file = mockk<BbCloudNewPullRequestVirtualFile> {
            every { isValid } returns false
        }

        provider.accept(project = project, file = file) shouldBe true
    }
}