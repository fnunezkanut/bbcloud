package com.github.fnunezkanut.bbcloud.ui.pullrequest.projectView

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BbCloudPullRequestVirtualFilesTest {
    @Test
    fun `pathForWorktreeProject - builds a path from a worktree folder name, slug and org url`() {
        // GIVEN
        val basePath = "/home/dev/.worktrees/myrepo--pr-42"

        // WHEN
        val path = BbCloudPullRequestVirtualFiles.pathForWorktreeProject(
            projectBasePath = basePath,
            projectHash = "project-1",
            sessionId = "session-1",
            organizationUrl = "https://bitbucket.org/acme/"
        )

        // THEN
        path.shouldNotBeNull()
        assertSoftly(path) {
            pullRequestId shouldBe 42L
            repositorySlug shouldBe "myrepo"
            workspaceUrl shouldBe "https://bitbucket.org/acme/"
            projectHash shouldBe "project-1"
            sessionId shouldBe "session-1"
        }
    }

    @Test
    fun `pathForWorktreeProject - returns null for an ordinary (non-worktree) project`() {
        // GIVEN
        val basePath = "/home/dev/projects/myrepo"

        // WHEN
        val path = BbCloudPullRequestVirtualFiles.pathForWorktreeProject(
            projectBasePath = basePath,
            projectHash = "project-1",
            sessionId = "session-1",
            organizationUrl = "https://bitbucket.org/acme/"
        )

        // THEN
        path shouldBe null
    }

    @Test
    fun `pathForWorktreeProject - returns null when the organization URL is missing`() {
        // GIVEN
        val basePath = "/home/dev/.worktrees/myrepo--pr-42"

        // WHEN
        val path = BbCloudPullRequestVirtualFiles.pathForWorktreeProject(
            projectBasePath = basePath,
            projectHash = "project-1",
            sessionId = "session-1",
            organizationUrl = null
        )

        // THEN
        path shouldBe null
    }

    @Test
    fun `pathForWorktreeProject - returns null for a malformed worktree folder name`() {
        // GIVEN
        val basePath = "/home/dev/.worktrees/myrepo--pr-"

        // WHEN
        val path = BbCloudPullRequestVirtualFiles.pathForWorktreeProject(
            projectBasePath = basePath,
            projectHash = "project-1",
            sessionId = "session-1",
            organizationUrl = "https://bitbucket.org/acme/"
        )

        // THEN
        path shouldBe null
    }

    @Test
    fun `pathForWorktreeProject - returns null when the base path is blank`() {
        // WHEN
        val path = BbCloudPullRequestVirtualFiles.pathForWorktreeProject(
            projectBasePath = "   ",
            projectHash = "project-1",
            sessionId = "session-1",
            organizationUrl = "https://bitbucket.org/acme/"
        )

        // THEN
        path shouldBe null
    }
}