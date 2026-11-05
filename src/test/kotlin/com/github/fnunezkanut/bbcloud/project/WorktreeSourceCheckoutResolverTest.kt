package com.github.fnunezkanut.bbcloud.project

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@Tag("unit")
class WorktreeSourceCheckoutResolverTest {
    @TempDir
    lateinit var temp: Path

    private fun directory(name: String): Path {
        return Files.createDirectories(temp.resolve(name))
    }

    @Test
    fun `sourceRootFromGitDirPointer - should return the checkout root of a worktree gitdir`() {
        // GIVEN
        val gitDir = Path.of("/src/repo/.git/worktrees/repo--pr-1")

        // WHEN
        val root = sourceRootFromGitDirPointer(gitDir = gitDir)

        // THEN
        root shouldBe Path.of("/src/repo")
    }

    @Test
    fun `sourceRootFromGitDirPointer - should return null for other layouts`() {
        // GIVEN / WHEN / THEN
        assertSoftly {
            sourceRootFromGitDirPointer(gitDir = Path.of("/src/repo/.git/modules/sub")) shouldBe null
            sourceRootFromGitDirPointer(gitDir = Path.of("/src/repo.git/worktrees/x")) shouldBe null
            sourceRootFromGitDirPointer(gitDir = Path.of("/")) shouldBe null
        }
    }

    @Test
    fun `resolve - should follow an absolute gitdir pointer`() {
        // GIVEN
        val source = directory("source")
        val worktree = directory("wt")
        Files.writeString(worktree.resolve(".git"), "gitdir: ${source.resolve(".git/worktrees/wt")}\n")

        // WHEN
        val result = WorktreeSourceCheckoutResolver.resolve(worktreePath = worktree, repoSlug = null, mappingsBySlug = emptyMap(), projectsDirectory = null)

        // THEN
        result shouldBe source
    }

    @Test
    fun `resolve - should follow a relative gitdir pointer`() {
        // GIVEN
        val source = directory("source")
        val worktree = directory("wt")
        Files.writeString(worktree.resolve(".git"), "gitdir: ../source/.git/worktrees/wt\n")

        // WHEN
        val result = WorktreeSourceCheckoutResolver.resolve(worktreePath = worktree, repoSlug = null, mappingsBySlug = emptyMap(), projectsDirectory = null)

        // THEN
        result shouldBe source
    }

    @Test
    fun `resolve - should fall back to the watched mapping when there is no git file`() {
        // GIVEN
        val clone = directory("clone")
        val worktree = directory("wt")

        // WHEN
        val result = WorktreeSourceCheckoutResolver.resolve(
            worktreePath = worktree,
            repoSlug = "repo",
            mappingsBySlug = mapOf("repo" to clone.toString()),
            projectsDirectory = null
        )

        // THEN
        result shouldBe clone
    }

    @Test
    fun `resolve - should fall back to the projects directory when the mapping folder is gone`() {
        // GIVEN
        val projects = directory("projects")
        val conventional = Files.createDirectories(projects.resolve("repo"))
        val worktree = directory("wt")

        // WHEN
        val result = WorktreeSourceCheckoutResolver.resolve(
            worktreePath = worktree,
            repoSlug = "repo",
            mappingsBySlug = mapOf("repo" to temp.resolve("deleted").toString()),
            projectsDirectory = projects.toString()
        )

        // THEN
        result shouldBe conventional
    }

    @Test
    fun `resolve - should return null without throwing when nothing resolves`() {
        // GIVEN
        val worktree = directory("wt")
        Files.writeString(worktree.resolve(".git"), "gitdir: \u0000bad")

        // WHEN
        val result = WorktreeSourceCheckoutResolver.resolve(
            worktreePath = worktree,
            repoSlug = "repo",
            mappingsBySlug = emptyMap(),
            projectsDirectory = temp.resolve("none").toString()
        )

        // THEN
        result shouldBe null
    }
}