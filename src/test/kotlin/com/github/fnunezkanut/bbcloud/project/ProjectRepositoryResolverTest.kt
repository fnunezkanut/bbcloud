package com.github.fnunezkanut.bbcloud.project

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

@Tag("unit")
class ProjectRepositoryResolverTest {
    @Test
    fun `resolve - should derive the slug from a confirmed worktree path without reading git`() {
        // GIVEN a worktree directly under the configured root and a reader that must not be called
        val workTreeDirectory = "/p/.worktrees"
        val worktreeBasePath = "$workTreeDirectory/myrepo--pr-42"
        var readRemoteUrlCalled = false
        val readRemoteUrl: (Path) -> String? = {
            readRemoteUrlCalled = true
            "git@bitbucket.org:wrong-workspace/wrong-repository.git"
        }

        // WHEN the repository is resolved
        val result = ProjectRepositoryResolver.resolve(
            projectBasePath = worktreeBasePath,
            configuredWorkspace = "my-workspace",
            readRemoteUrl = readRemoteUrl,
            workTreeDirectory = workTreeDirectory
        )

        // THEN the configured workspace and parsed slug are used without consulting git
        assertSoftly {
            result shouldBe ResolvedProjectRepository(workspace = "my-workspace", repoSlug = "myrepo")
            readRemoteUrlCalled shouldBe false
        }
    }

    @Test
    fun `resolve - should resolve a normal checkout from its git remote`() {
        // GIVEN a conventional checkout whose parent is not the worktree root
        val normalBasePath = "/p/myrepo"
        val readRemoteUrl: (Path) -> String? = { "https://bitbucket.org/my-workspace/remote-repository.git" }

        // WHEN the repository is resolved
        val result = ProjectRepositoryResolver.resolve(
            projectBasePath = normalBasePath,
            configuredWorkspace = "my-workspace",
            readRemoteUrl = readRemoteUrl,
            workTreeDirectory = "/p/.worktrees"
        )

        // THEN the remote workspace and slug remain authoritative
        result shouldBe ResolvedProjectRepository(workspace = "my-workspace", repoSlug = "remote-repository")
    }

    @Test
    fun `resolve - should fall back to the folder name and configured workspace when no remote is readable`() {
        // GIVEN a checkout with no readable git remote
        val basePath = "/Users/me/Projects/examplerepo"
        val readRemoteUrl: (Path) -> String? = { null }

        // WHEN the repository is resolved
        val result = ProjectRepositoryResolver.resolve(
            projectBasePath = basePath,
            configuredWorkspace = "my-workspace",
            readRemoteUrl = readRemoteUrl,
            workTreeDirectory = "/Users/me/Projects/.worktrees"
        )

        // THEN the folder name is the slug and the configured workspace is kept
        result shouldBe ResolvedProjectRepository(workspace = "my-workspace", repoSlug = "examplerepo")
    }

    @Test
    fun `resolve - should treat a worktree-looking folder outside the configured root as a normal checkout`() {
        // GIVEN a worktree-looking folder whose parent is not the configured worktree root
        val basePath = "/p/other/myrepo--pr-42"
        val readRemoteUrl: (Path) -> String? = { null }

        // WHEN the repository is resolved
        val result = ProjectRepositoryResolver.resolve(
            projectBasePath = basePath,
            configuredWorkspace = "my-workspace",
            readRemoteUrl = readRemoteUrl,
            workTreeDirectory = "/p/.worktrees"
        )

        // THEN the normal folder-name fallback preserves the complete folder name
        result shouldBe ResolvedProjectRepository(workspace = "my-workspace", repoSlug = "myrepo--pr-42")
    }

    @Test
    fun `resolve - should read the remote when a worktree has no configured workspace`() {
        // GIVEN a confirmed worktree but no configured workspace
        val workTreeDirectory = "/p/.worktrees"
        val basePath = "$workTreeDirectory/myrepo--pr-42"
        var readRemoteUrlCalled = false
        val readRemoteUrl: (Path) -> String? = {
            readRemoteUrlCalled = true
            "https://bitbucket.org/remote-workspace/remote-repository.git"
        }

        // WHEN the repository is resolved
        val result = ProjectRepositoryResolver.resolve(
            projectBasePath = basePath,
            configuredWorkspace = " ",
            readRemoteUrl = readRemoteUrl,
            workTreeDirectory = workTreeDirectory
        )

        // THEN the remote path resolves the repository instead of returning a half-resolved target
        assertSoftly {
            result shouldBe ResolvedProjectRepository(workspace = "remote-workspace", repoSlug = "remote-repository")
            readRemoteUrlCalled shouldBe true
        }
    }

    @Test
    fun `resolve - should fall back to the folder name when the remote is not a Bitbucket URL`() {
        // GIVEN a checkout whose origin remote points at a non-Bitbucket host
        val basePath = "/Users/me/Projects/examplerepo"
        val readRemoteUrl: (Path) -> String? = { "git@github.com:someone/examplerepo.git" }

        // WHEN the repository is resolved
        val result = ProjectRepositoryResolver.resolve(
            projectBasePath = basePath,
            configuredWorkspace = "my-workspace",
            readRemoteUrl = readRemoteUrl
        )

        // THEN the folder name slug and configured workspace are used
        result shouldBe ResolvedProjectRepository(workspace = "my-workspace", repoSlug = "examplerepo")
    }

    @Test
    fun `resolve - should return null when the base path is null`() {
        // GIVEN no project base path
        val readRemoteUrl: (Path) -> String? = { null }

        // WHEN the repository is resolved
        val result = ProjectRepositoryResolver.resolve(
            projectBasePath = null,
            configuredWorkspace = "my-workspace",
            readRemoteUrl = readRemoteUrl
        )

        // THEN nothing is resolved
        result.shouldBeNull()
    }

    @Test
    fun `resolve - should return null when no remote and no configured workspace`() {
        // GIVEN a checkout with no readable remote and no configured workspace to fall back to
        val basePath = "/Users/me/Projects/examplerepo"
        val readRemoteUrl: (Path) -> String? = { null }

        // WHEN the repository is resolved
        val result = ProjectRepositoryResolver.resolve(
            projectBasePath = basePath,
            configuredWorkspace = null,
            readRemoteUrl = readRemoteUrl
        )

        // THEN nothing is resolved, matching today's behavior when the workspace is unknown
        result.shouldBeNull()
    }
}