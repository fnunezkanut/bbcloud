package com.github.fnunezkanut.bbcloud.project

import com.github.fnunezkanut.bbcloud.git.OpenProjectLocator
import com.github.fnunezkanut.bbcloud.git.WorktreeRemovalResult
import com.github.fnunezkanut.bbcloud.ui.pullrequest.decline.ProjectCloser
import com.intellij.openapi.project.Project
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

@Tag("unit")
class WatchedWorktreeRemoverTest {
    private val sourceRoot: Path = Path.of("/tmp/clone")
    private val workTreeDirectory: Path = Path.of("/tmp/worktrees")
    private val worktreePath: Path = Path.of("/tmp/worktrees/alpha-api--pr-7")

    @Test
    fun `remove - removes the work tree without closing anything when no window is open`() {
        // GIVEN
        val calls = mutableListOf<String>()
        val remover = remover(openProject = null, calls = calls)

        // WHEN
        val outcome = runBlocking { remover.remove(sourceRoot, worktreePath, workTreeDirectory) }

        // THEN
        assertSoftly {
            outcome shouldBe WatchedWorktreeRemovalOutcome.Removed
            calls shouldBe listOf("remove")
        }
    }

    @Test
    fun `remove - closes the open window before the work tree is removed`() {
        // GIVEN
        val calls = mutableListOf<String>()
        val remover = remover(openProject = mockk<Project>(relaxed = true), calls = calls)

        // WHEN
        val outcome = runBlocking { remover.remove(sourceRoot, worktreePath, workTreeDirectory) }

        // THEN
        assertSoftly {
            outcome shouldBe WatchedWorktreeRemovalOutcome.Removed
            calls shouldBe listOf("close", "remove")
        }
    }

    @Test
    fun `remove - returns CloseFailed and leaves the work tree in place when the window cannot be closed`() {
        // GIVEN
        val calls = mutableListOf<String>()
        val remover = remover(
            openProject = mockk<Project>(relaxed = true),
            closeSucceeds = false,
            calls = calls
        )

        // WHEN
        val outcome = runBlocking { remover.remove(sourceRoot, worktreePath, workTreeDirectory) }

        // THEN
        assertSoftly {
            outcome shouldBe WatchedWorktreeRemovalOutcome.CloseFailed
            calls shouldBe listOf("close")
        }
    }

    @Test
    fun `remove - returns RemovalFailed with git output and cause when git cannot remove the work tree`() {
        // GIVEN
        val calls = mutableListOf<String>()
        val cause = IllegalStateException("git failed")
        val remover = remover(
            openProject = null,
            removal = WorktreeRemovalResult.Failed(output = "fatal: locked worktree", cause = cause),
            calls = calls
        )

        // WHEN
        val outcome = runBlocking { remover.remove(sourceRoot, worktreePath, workTreeDirectory) }

        // THEN
        assertSoftly {
            outcome shouldBe WatchedWorktreeRemovalOutcome.RemovalFailed(output = "fatal: locked worktree", cause = cause)
            calls shouldBe listOf("remove")
        }
    }

    private fun remover(
        openProject: Project?,
        closeSucceeds: Boolean = true,
        removal: WorktreeRemovalResult = WorktreeRemovalResult.Success,
        calls: MutableList<String>
    ): WatchedWorktreeRemover {
        return WatchedWorktreeRemover(
            openProjectLocator = OpenProjectLocator { _ -> openProject },
            projectCloser = ProjectCloser { _ ->
                calls.add("close")
                closeSucceeds
            },
            removeWorktree = { _, _, _ ->
                calls.add("remove")
                removal
            }
        )
    }
}