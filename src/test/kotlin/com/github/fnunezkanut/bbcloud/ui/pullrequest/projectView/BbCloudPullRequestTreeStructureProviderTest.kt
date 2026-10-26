package com.github.fnunezkanut.bbcloud.ui.pullrequest.projectView

import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestPath
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestVirtualFile
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestVirtualFileSystem
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BbCloudPullRequestTreeStructureProviderTest {
    @Test
    fun `injectPullRequestNode - appends a node when no child represents the pull request file`() {
        // GIVEN
        val pullRequestFile = pullRequestFile(pullRequestId = 42L)
        val existingChild = otherChild()
        val pullRequestNode = dummyNode()
        var factoryInvocations = 0

        // WHEN
        val result = BbCloudPullRequestTreeStructureProvider.injectPullRequestNode(
            children = listOf(existingChild),
            pullRequestFile = pullRequestFile,
            nodeFactory = {
                factoryInvocations += 1
                pullRequestNode
            }
        )

        // THEN
        result shouldHaveSize 2
        result shouldContain existingChild
        result shouldContain pullRequestNode
        factoryInvocations shouldBe 1
    }

    @Test
    fun `injectPullRequestNode - leaves children unchanged when the pull request file is already present`() {
        // GIVEN
        val pullRequestFile = pullRequestFile(pullRequestId = 42L)
        val existingPullRequestChild = childFor(pullRequestFile)
        var factoryInvocations = 0

        // WHEN
        val result = BbCloudPullRequestTreeStructureProvider.injectPullRequestNode(
            children = listOf(existingPullRequestChild),
            pullRequestFile = pullRequestFile,
            nodeFactory = {
                factoryInvocations += 1
                dummyNode()
            }
        )

        // THEN
        result shouldHaveSize 1
        result shouldContain existingPullRequestChild
        factoryInvocations shouldBe 0
    }

    private fun pullRequestFile(pullRequestId: Long): BbCloudPullRequestVirtualFile {
        val project = mockk<Project> {
            every { locationHash } returns "project-1"
            every { name } returns "Test Project"
            every { isOpen } returns true
            every { isDisposed } returns false
        }
        val path = BbCloudPullRequestPath(
            sessionId = "session-1",
            projectHash = "project-1",
            workspaceUrl = "https://bitbucket.org/acme/",
            repositorySlug = "myrepo",
            pullRequestId = pullRequestId
        )
        return BbCloudPullRequestVirtualFileSystem().findOrCreateFile(
            project = project,
            path = path
        ) as BbCloudPullRequestVirtualFile
    }

    private fun childFor(file: BbCloudPullRequestVirtualFile): AbstractTreeNode<*> {
        return mockk<ProjectViewNode<*>> {
            every { virtualFile } returns file
        }
    }

    private fun otherChild(): AbstractTreeNode<*> {
        return mockk<ProjectViewNode<*>> {
            every { virtualFile } returns mockk<VirtualFile>()
        }
    }

    private fun dummyNode(): AbstractTreeNode<*> {
        return mockk<AbstractTreeNode<*>>()
    }
}