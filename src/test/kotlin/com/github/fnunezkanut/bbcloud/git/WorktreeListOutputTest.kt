package com.github.fnunezkanut.bbcloud.git

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Paths

@Tag("unit")
class WorktreeListOutputTest {
    private val porcelain =
        """
        worktree /tmp/projects/my-repo
        HEAD abcdef1234567890
        branch refs/heads/main

        worktree /tmp/worktrees/my-repo/42
        HEAD 1234567890abcdef
        branch refs/heads/pr/42
        """.trimIndent()

    @Test
    fun `containsWorktree - returns true for a registered worktree path`() {
        // GIVEN
        val target = Paths.get("/tmp/worktrees/my-repo/42")

        // WHEN
        val contains = WorktreeListOutput.containsWorktree(porcelainOutput = porcelain, worktreePath = target)

        // THEN
        contains shouldBe true
    }

    @Test
    fun `containsWorktree - normalizes the candidate path before comparing`() {
        // GIVEN: a non-normalized path pointing at the same registered worktree
        val target = Paths.get("/tmp/worktrees/my-repo/../my-repo/42")

        // WHEN
        val contains = WorktreeListOutput.containsWorktree(porcelainOutput = porcelain, worktreePath = target)

        // THEN
        contains shouldBe true
    }

    @Test
    fun `containsWorktree - returns false for an unregistered path`() {
        // GIVEN
        val target = Paths.get("/tmp/worktrees/my-repo/99")

        // WHEN
        val contains = WorktreeListOutput.containsWorktree(porcelainOutput = porcelain, worktreePath = target)

        // THEN
        contains shouldBe false
    }

    @Test
    fun `containsWorktree - returns false for empty output`() {
        // GIVEN
        val target = Paths.get("/tmp/worktrees/my-repo/42")

        // WHEN
        val contains = WorktreeListOutput.containsWorktree(porcelainOutput = "", worktreePath = target)

        // THEN
        contains shouldBe false
    }
}