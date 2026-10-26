package com.github.fnunezkanut.bbcloud.project

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.nio.file.Paths

@Tag("unit")
class PullRequestWorktreeResolverTest {
    private val workspace = "acme"
    private val repoSlug = "my-repo"
    private val prNumber = 42L
    private val workTreeDirectory = "/tmp/worktrees"
    private val projectsDirectory = "/tmp/projects"
    private val watchedClonePath = Paths.get("/tmp/projects/my-repo").toAbsolutePath().normalize()
    private val conventionalClonePath = watchedClonePath

    private fun mapping(localFolderPath: String): WatchedProjectMapping {
        return WatchedProjectMapping(
            localFolderPath = localFolderPath,
            remoteRepoSlug = repoSlug,
            workspace = workspace,
            watched = true
        )
    }

    @Test
    fun `resolve - work tree directory blank gives WorkTreeDirMissing`() {
        // GIVEN
        val mappings = listOf(mapping(localFolderPath = watchedClonePath.toString()))

        // WHEN
        val result = PullRequestWorktreeResolver.resolve(
            mappings = mappings,
            workTreeDirectory = "",
            projectsDirectory = projectsDirectory,
            workspace = workspace,
            repoSlug = repoSlug,
            prNumber = prNumber,
            isDirectory = { true }
        )

        // THEN
        result shouldBe PullRequestWorktreeResolution.WorkTreeDirMissing
    }

    @Test
    fun `resolve - work tree directory that does not exist yet still resolves because it is created on demand`() {
        // GIVEN only the source checkout exists on disk; the work tree root does NOT yet exist (it is created on demand
        // by BbCloudWorktreeService), so the resolver must not gate on its existence.
        val mappings = listOf(mapping(localFolderPath = watchedClonePath.toString()))

        // WHEN the work tree root is NOT a directory, but the source checkout is
        val result = PullRequestWorktreeResolver.resolve(
            mappings = mappings,
            workTreeDirectory = workTreeDirectory,
            projectsDirectory = projectsDirectory,
            workspace = workspace,
            repoSlug = repoSlug,
            prNumber = prNumber,
            isDirectory = { path -> path == watchedClonePath }
        )

        // THEN
        val resolved = result.shouldBeInstanceOf<PullRequestWorktreeResolution.Resolved>()
        assertSoftly {
            resolved.sourceCheckoutPath shouldBe watchedClonePath
            resolved.worktreePath shouldBe PullRequestWorktreeLayout.worktreePath(
                workTreeDirectory = workTreeDirectory,
                repoSlug = repoSlug,
                prNumber = prNumber
            )
        }
    }

    @Test
    fun `resolve - no watched mapping and no conventional clone gives SourceCheckoutMissing`() {
        // GIVEN: only the work tree directory exists
        val workTreePath = Paths.get(workTreeDirectory).toAbsolutePath().normalize()

        // WHEN
        val result = PullRequestWorktreeResolver.resolve(
            mappings = emptyList(),
            workTreeDirectory = workTreeDirectory,
            projectsDirectory = projectsDirectory,
            workspace = workspace,
            repoSlug = repoSlug,
            prNumber = prNumber,
            isDirectory = { path -> path == workTreePath }
        )

        // THEN
        result shouldBe PullRequestWorktreeResolution.SourceCheckoutMissing
    }

    @Test
    fun `resolve - watched mapping folder exists gives Resolved using the watched path`() {
        // GIVEN
        val workTreePath = Paths.get(workTreeDirectory).toAbsolutePath().normalize()
        val existing = setOf(workTreePath, watchedClonePath)
        val mappings = listOf(mapping(localFolderPath = watchedClonePath.toString()))

        // WHEN
        val result = PullRequestWorktreeResolver.resolve(
            mappings = mappings,
            workTreeDirectory = workTreeDirectory,
            projectsDirectory = projectsDirectory,
            workspace = workspace,
            repoSlug = repoSlug,
            prNumber = prNumber,
            isDirectory = { path -> path in existing }
        )

        // THEN
        val resolved = result.shouldBeInstanceOf<PullRequestWorktreeResolution.Resolved>()
        assertSoftly {
            resolved.sourceCheckoutPath shouldBe watchedClonePath
            resolved.worktreePath shouldBe PullRequestWorktreeLayout.worktreePath(
                workTreeDirectory = workTreeDirectory,
                repoSlug = repoSlug,
                prNumber = prNumber
            )
        }
    }

    @Test
    fun `resolve - watched mapping missing but conventional clone exists gives Resolved using the conventional path`() {
        // GIVEN: the watched mapping has a blank folder, but $projectsDirectory/$repoSlug exists
        val workTreePath = Paths.get(workTreeDirectory).toAbsolutePath().normalize()
        val projectsRoot = Paths.get(projectsDirectory).toAbsolutePath().normalize()
        val existing = setOf<Path>(workTreePath, projectsRoot, conventionalClonePath)
        val mappings = listOf(mapping(localFolderPath = ""))

        // WHEN
        val result = PullRequestWorktreeResolver.resolve(
            mappings = mappings,
            workTreeDirectory = workTreeDirectory,
            projectsDirectory = projectsDirectory,
            workspace = workspace,
            repoSlug = repoSlug,
            prNumber = prNumber,
            isDirectory = { path -> path in existing }
        )

        // THEN
        val resolved = result.shouldBeInstanceOf<PullRequestWorktreeResolution.Resolved>()
        resolved.sourceCheckoutPath shouldBe conventionalClonePath
    }

    @Test
    fun `resolve - no mapping and conventional clone missing gives SourceCheckoutMissing`() {
        // GIVEN: work tree and projects root exist, but $projectsDirectory/$repoSlug does not
        val workTreePath = Paths.get(workTreeDirectory).toAbsolutePath().normalize()
        val projectsRoot = Paths.get(projectsDirectory).toAbsolutePath().normalize()
        val existing = setOf(workTreePath, projectsRoot)

        // WHEN
        val result = PullRequestWorktreeResolver.resolve(
            mappings = emptyList(),
            workTreeDirectory = workTreeDirectory,
            projectsDirectory = projectsDirectory,
            workspace = workspace,
            repoSlug = repoSlug,
            prNumber = prNumber,
            isDirectory = { path -> path in existing }
        )

        // THEN
        result shouldBe PullRequestWorktreeResolution.SourceCheckoutMissing
    }
}