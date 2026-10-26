package com.github.fnunezkanut.bbcloud.project

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@Tag("unit")
class PullRequestWorktreeProjectTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `isPluginManagedWorktree - should be true for a gitfile worktree under the work tree root`() {
        // GIVEN
        val workTreeRoot = Files.createDirectories(tempDir.resolve(".worktrees"))
        val worktreePath = Files.createDirectories(workTreeRoot.resolve("kapidsl--pr-8"))
        Files.writeString(worktreePath.resolve(".git"), "gitdir: /path/to/main.git/worktrees/kapidsl--pr-8\n")

        // WHEN
        val result = PullRequestWorktreeProject.isPluginManagedWorktree(
            projectBasePath = worktreePath.toString(),
            workTreeDirectory = workTreeRoot.toString()
        )

        // THEN
        result.shouldBeTrue()
    }

    @Test
    fun `isPluginManagedWorktree - should be false when dot git is a directory`() {
        // GIVEN
        val workTreeRoot = Files.createDirectories(tempDir.resolve(".worktrees"))
        val checkoutPath = Files.createDirectories(workTreeRoot.resolve("kapidsl--pr-8"))
        Files.createDirectories(checkoutPath.resolve(".git"))

        // WHEN
        val result = PullRequestWorktreeProject.isPluginManagedWorktree(
            projectBasePath = checkoutPath.toString(),
            workTreeDirectory = workTreeRoot.toString()
        )

        // THEN
        result.shouldBeFalse()
    }

    @Test
    fun `isPluginManagedWorktree - should be false when the parent is not the work tree root`() {
        // GIVEN
        val workTreeRoot = tempDir.resolve(".worktrees")
        val worktreePath = Files.createDirectories(tempDir.resolve("kapidsl--pr-8"))
        Files.writeString(worktreePath.resolve(".git"), "gitdir: /elsewhere\n")

        // WHEN
        val result = PullRequestWorktreeProject.isPluginManagedWorktree(
            projectBasePath = worktreePath.toString(),
            workTreeDirectory = workTreeRoot.toString()
        )

        // THEN
        result.shouldBeFalse()
    }

    @Test
    fun `isPluginManagedWorktree - should be false for a malformed worktree folder name`() {
        // GIVEN
        val workTreeRoot = Files.createDirectories(tempDir.resolve(".worktrees"))
        val worktreePath = Files.createDirectories(workTreeRoot.resolve("kapidsl--pr-"))
        Files.writeString(worktreePath.resolve(".git"), "gitdir: /path\n")

        // WHEN
        val result = PullRequestWorktreeProject.isPluginManagedWorktree(
            projectBasePath = worktreePath.toString(),
            workTreeDirectory = workTreeRoot.toString()
        )

        // THEN
        result.shouldBeFalse()
    }

    @Test
    fun `isPluginManagedWorktree - should be false for blank paths`() {
        PullRequestWorktreeProject.isPluginManagedWorktree(
            projectBasePath = null,
            workTreeDirectory = "/p/.worktrees"
        ).shouldBeFalse()
        PullRequestWorktreeProject.isPluginManagedWorktree(
            projectBasePath = "/p/.worktrees/kapidsl--pr-8",
            workTreeDirectory = null
        ).shouldBeFalse()
    }
}