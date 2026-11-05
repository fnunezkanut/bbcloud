package com.github.fnunezkanut.bbcloud.git

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
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

    @Nested
    inner class WorktreeForBranch {
        private val mixed =
            """
            $ git worktree list --porcelain
            worktree /tmp/projects/my-repo
            HEAD abcdef1234567890
            branch refs/heads/main

            worktree /tmp/worktrees/my-repo/42
            HEAD 1234567890abcdef
            branch refs/heads/feature/foo

            worktree /tmp/worktrees/detached
            HEAD 1111111111111111
            detached

            worktree /tmp/projects/bare.git
            bare
            """.trimIndent()

        @Test
        fun `worktreeForBranch - should find the main work tree`() {
            val path = WorktreeListOutput.worktreeForBranch(porcelainOutput = mixed, branchName = "main")

            path shouldBe Paths.get("/tmp/projects/my-repo")
        }

        @Test
        fun `worktreeForBranch - should find a linked work tree for a branch with a slash`() {
            val path = WorktreeListOutput.worktreeForBranch(porcelainOutput = mixed, branchName = "feature/foo")

            path shouldBe Paths.get("/tmp/worktrees/my-repo/42")
        }

        @Test
        fun `worktreeForBranch - should ignore detached and bare entries`() {
            WorktreeListOutput.worktreeForBranch(porcelainOutput = mixed, branchName = "detached").shouldBeNull()
        }

        @Test
        fun `worktreeForBranch - should return null when no work tree has the branch`() {
            WorktreeListOutput.worktreeForBranch(porcelainOutput = mixed, branchName = "develop").shouldBeNull()
        }

        @Test
        fun `worktreeForBranch - should not match a branch that only shares a prefix`() {
            WorktreeListOutput.worktreeForBranch(porcelainOutput = mixed, branchName = "feature").shouldBeNull()
        }

        @Test
        fun `worktreeForBranch - should normalize the returned path`() {
            val output = "worktree /tmp/a/../b\nHEAD 1\nbranch refs/heads/main"

            val path = WorktreeListOutput.worktreeForBranch(porcelainOutput = output, branchName = "main")

            path shouldBe Paths.get("/tmp/b")
        }
    }
}