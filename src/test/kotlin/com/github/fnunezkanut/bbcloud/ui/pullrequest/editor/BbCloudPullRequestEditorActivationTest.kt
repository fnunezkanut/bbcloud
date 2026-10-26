package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.intellij.openapi.vfs.VirtualFile
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BbCloudPullRequestEditorActivationTest {
    @Test
    fun `activation - should clear when no custom editor is selected`() {
        val result = BbCloudPullRequestEditorActivation.activePullRequestId(
            selectedFile = null,
            openFiles = emptyList(),
            fallbackToOpenFile = false
        )

        result.shouldBeNull()
    }

    @Test
    fun `activation - should select the only open pull request after close`() {
        val remaining = file(id = 12)

        val result = BbCloudPullRequestEditorActivation.activePullRequestId(
            selectedFile = null,
            openFiles = listOf(remaining),
            fallbackToOpenFile = true
        )

        result shouldBe 12
    }

    @Test
    fun `activation - should follow the selected pull request`() {
        val first = file(id = 12)
        val selected = file(id = 34)

        val result = BbCloudPullRequestEditorActivation.activePullRequestId(
            selectedFile = selected,
            openFiles = listOf(first, selected),
            fallbackToOpenFile = false
        )

        result shouldBe 34
    }

    @Test
    fun `activation - should rebind to a remaining file when the selected file closes`() {
        val closed = file(id = 12)
        val remaining = file(id = 34)

        val result = BbCloudPullRequestEditorActivation.activePullRequestId(
            selectedFile = closed,
            openFiles = listOf(remaining),
            fallbackToOpenFile = true
        )

        result shouldBe 34
    }

    @Test
    fun `close handling - should not fall back when an unrelated file closes`() {
        // GIVEN
        val unrelatedFile = mockk<VirtualFile>()
        val remaining = file(id = 34)

        // WHEN
        val shouldFallback = BbCloudPullRequestEditorActivation.shouldFallbackToOpenFile(
            closedFile = unrelatedFile,
            selectedFile = unrelatedFile
        )
        val activePullRequestId = BbCloudPullRequestEditorActivation.activePullRequestId(
            selectedFile = null,
            openFiles = listOf(remaining),
            fallbackToOpenFile = shouldFallback
        )

        // THEN
        shouldFallback shouldBe false
        activePullRequestId.shouldBeNull()
    }

    @Test
    fun `close handling - should not fall back when the closed file is the new pull request form`() {
        // GIVEN closing the new.pr form never rebinds review state
        val newPrFile = mockk<com.github.fnunezkanut.bbcloud.ui.pullrequest.create.BbCloudNewPullRequestVirtualFile>()

        // WHEN
        val shouldFallback = BbCloudPullRequestEditorActivation.shouldFallbackToOpenFile(
            closedFile = newPrFile,
            selectedFile = newPrFile
        )

        // THEN
        shouldFallback shouldBe false
    }

    private fun file(id: Long): BbCloudPullRequestVirtualFile {
        return mockk<BbCloudPullRequestVirtualFile>().also { file ->
            every { file.pullRequestPath } returns BbCloudPullRequestPath(
                sessionId = "session",
                projectHash = "project",
                workspaceUrl = "https://bitbucket.org/workspace",
                repositorySlug = "repository",
                pullRequestId = id
            )
        }
    }
}