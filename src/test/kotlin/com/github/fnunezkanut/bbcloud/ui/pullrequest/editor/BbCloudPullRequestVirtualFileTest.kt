package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.intellij.openapi.project.Project
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class BbCloudPullRequestVirtualFileTest {
    @Test
    fun `virtual file - should expose a read-only id name and sanitized title`() {
        // GIVEN
        val project = mockProject()
        val file = createFile(project = project, pullRequestId = 42L)

        // WHEN
        file.setPullRequestTitle("  Fix <b>the</b> tabs\nnow  ")

        // THEN
        assertSoftly {
            file.name shouldBe "42.pr"
            file.isWritable shouldBe false
            file.isValid shouldBe true
            file.pullRequestTitle() shouldBe "Fix the tabs now"
        }
    }

    @Test
    fun `virtual file - should compare equal for the same project repository and pull request`() {
        // GIVEN
        val project = mockProject()
        val first = createFile(project = project, pullRequestId = 42L)
        val second = createFile(project = project, pullRequestId = 42L)
        val differentPullRequest = createFile(project = project, pullRequestId = 43L)

        // THEN
        first shouldBe second
        first.hashCode() shouldBe second.hashCode()
        first.equals(differentPullRequest) shouldBe false
    }

    @Test
    fun `virtual file - should keep the summary and sanitized title as metadata`() {
        // GIVEN
        val project = mockProject()
        val file = createFile(project = project, pullRequestId = 42L)
        val summary = PullRequestSummary(
            id = 42L,
            title = "Review <em>this</em>",
            authorDisplayName = "Author",
            authorAvatarUrl = null,
            createdOn = Instant.parse("2026-01-01T00:00:00Z"),
            updatedOn = Instant.parse("2026-01-02T00:00:00Z"),
            sourceBranch = "feature",
            destinationBranch = "main",
            commentCount = 0,
            webUrl = "https://bitbucket.org/acme/repo/pull-requests/42",
            state = PullRequestState.Open,
            isDraft = false
        )

        // WHEN
        file.setPullRequestSummary(summary)

        // THEN
        file.getUserData(BbCloudPullRequestVirtualFile.PULL_REQUEST_SUMMARY_KEY) shouldBe summary
        file.pullRequestTitle().shouldNotBeEmpty()
    }

    @Test
    fun `restored virtual file - should provide identity for session hydration without opener metadata`() {
        // GIVEN
        val project = mockProject()
        val path = BbCloudPullRequestPath(
            sessionId = "session-1",
            projectHash = "project-1",
            workspaceUrl = "https://bitbucket.org/acme",
            repositorySlug = "sample-repository",
            pullRequestId = 42L
        )
        val file = BbCloudPullRequestVirtualFileSystem().findOrCreateFile(
            project = project,
            path = path
        ) as BbCloudPullRequestVirtualFile

        // WHEN
        val request = BbCloudPullRequestEditorRestoration.request(
            projectBasePath = project.basePath,
            file = file
        )

        // THEN
        request?.projectBasePath shouldBe "/workspace"
        request?.organizationUrl shouldBe "https://bitbucket.org/acme"
        request?.pullRequestId shouldBe 42L
        file.getUserData(BbCloudPullRequestVirtualFile.PULL_REQUEST_SUMMARY_KEY) shouldBe null
    }

    @Test
    fun `virtual file - should expose a readable presentable path instead of the serialized path`() {
        // GIVEN
        val project = mockProject()
        val file = createFile(project = project, pullRequestId = 42L)

        // THEN
        assertSoftly {
            file.presentablePath shouldBe "42.pr"
            file.presentablePath shouldNotContain "{"
            file.presentablePath shouldNotContain "\""
            file.presentablePath shouldNotContain "sessionId"
        }
    }

    @Test
    fun `virtual file - should keep the serialized path for file system lookup`() {
        // GIVEN
        val project = mockProject()
        val file = createFile(project = project, pullRequestId = 42L)

        // THEN
        val serializedPath = BbCloudPullRequestPathSerializer.serialize(
            BbCloudPullRequestPath(
                sessionId = "session-1",
                projectHash = "project-1",
                workspaceUrl = "https://bitbucket.org/acme",
                repositorySlug = "sample-repository",
                pullRequestId = 42L
            )
        )
        file.path shouldBe serializedPath
    }

    @Test
    fun `virtual file - should report the enforcePresentableName as false`() {
        // GIVEN
        val project = mockProject()
        val file = createFile(project = project, pullRequestId = 42L)

        // THEN
        file.enforcePresentableName() shouldBe false
    }

    private fun createFile(project: Project, pullRequestId: Long): BbCloudPullRequestVirtualFile {
        return BbCloudPullRequestVirtualFile(
            project = project,
            pullRequestPath = BbCloudPullRequestPath(
                sessionId = "session-1",
                projectHash = "project-1",
                workspaceUrl = "https://bitbucket.org/acme",
                repositorySlug = "sample-repository",
                pullRequestId = pullRequestId
            ),
            fileSystem = BbCloudPullRequestVirtualFileSystem()
        )
    }

    private fun mockProject(): Project {
        return mockk<Project> {
            every { locationHash } returns "project-1"
            every { name } returns "Test Project"
            every { basePath } returns "/workspace"
            every { isOpen } returns true
            every { isDisposed } returns false
        }
    }
}