package com.github.fnunezkanut.bbcloud.project

import com.github.fnunezkanut.bbcloud.git.OpenProjectLocator
import com.github.fnunezkanut.bbcloud.git.WorktreeRemovalResult
import com.github.fnunezkanut.bbcloud.ui.pullrequest.decline.ProjectCloser
import com.intellij.openapi.project.Project
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException

@Tag("unit")
class ResolvedWorktreeCleanerTest {
    private val sourceRoot: Path = Path.of("/tmp/clone")
    private val workTreeDirectory: Path = Path.of("/tmp/worktrees")

    private fun candidate(prNumber: Long, directoryExists: Boolean = true): ResolvedWorktreeCandidate {
        return ResolvedWorktreeCandidate(
            prNumber = prNumber,
            worktreePath = workTreeDirectory.resolve("api--pr-$prNumber"),
            directoryExists = directoryExists
        )
    }

    private fun cleaner(
        removed: MutableList<Path> = mutableListOf(),
        inProgress: Set<Path> = emptySet(),
        openProjects: Set<Path> = emptySet(),
        closeSucceeds: Boolean = true,
        removal: (Path) -> WorktreeRemovalResult = { WorktreeRemovalResult.Success }
    ): ResolvedWorktreeCleaner {
        val project = mockk<Project>(relaxed = true)
        return ResolvedWorktreeCleaner(
            isOpenInProgress = { path -> path in inProgress },
            remover = WatchedWorktreeRemover(
                openProjectLocator = OpenProjectLocator { path -> if (path in openProjects) project else null },
                projectCloser = ProjectCloser { closeSucceeds },
                removeWorktree = { _, worktreePath, _ ->
                    removed.add(worktreePath)
                    removal(worktreePath)
                }
            )
        )
    }

    @Test
    fun `clean - removes a candidate`() {
        // GIVEN
        val removed = mutableListOf<Path>()

        // WHEN
        val summary = runBlocking { cleaner(removed = removed).clean(sourceRoot, workTreeDirectory, listOf(candidate(5))) }

        // THEN
        assertSoftly {
            summary shouldBe ResolvedWorktreeCleanupSummary(removed = 1, skippedInUse = 0, closeFailed = 0, failed = 0)
            removed shouldBe listOf(candidate(5).worktreePath)
        }
    }

    @Test
    fun `clean - passes an orphan registration without a directory to the remover`() {
        // GIVEN
        val removed = mutableListOf<Path>()

        // WHEN
        val summary = runBlocking { cleaner(removed = removed).clean(sourceRoot, workTreeDirectory, listOf(candidate(6, directoryExists = false))) }

        // THEN
        assertSoftly {
            summary.removed shouldBe 1
            removed shouldBe listOf(candidate(6).worktreePath)
        }
    }

    @Test
    fun `clean - skips a work tree that is being opened`() {
        // GIVEN
        val removed = mutableListOf<Path>()
        val cleaner = cleaner(removed = removed, inProgress = setOf(candidate(5).worktreePath))

        // WHEN
        val summary = runBlocking { cleaner.clean(sourceRoot, workTreeDirectory, listOf(candidate(5))) }

        // THEN
        assertSoftly {
            summary shouldBe ResolvedWorktreeCleanupSummary(removed = 0, skippedInUse = 1, closeFailed = 0, failed = 0)
            removed shouldBe emptyList()
        }
    }

    @Test
    fun `clean - counts CloseFailed and still processes later candidates`() {
        // GIVEN
        val removed = mutableListOf<Path>()
        val cleaner = cleaner(removed = removed, openProjects = setOf(candidate(5).worktreePath), closeSucceeds = false)

        // WHEN
        val summary = runBlocking { cleaner.clean(sourceRoot, workTreeDirectory, listOf(candidate(5), candidate(6))) }

        // THEN
        assertSoftly {
            summary shouldBe ResolvedWorktreeCleanupSummary(removed = 1, skippedInUse = 0, closeFailed = 1, failed = 0)
            removed shouldBe listOf(candidate(6).worktreePath)
        }
    }

    @Test
    fun `clean - counts RemovalFailed`() {
        // GIVEN
        val cleaner = cleaner(removal = { WorktreeRemovalResult.Failed(output = "is not a working tree") })

        // WHEN
        val summary = runBlocking { cleaner.clean(sourceRoot, workTreeDirectory, listOf(candidate(5))) }

        // THEN
        summary shouldBe ResolvedWorktreeCleanupSummary(removed = 0, skippedInUse = 0, closeFailed = 0, failed = 1)
    }

    @Test
    fun `clean - a thrown exception does not stop later candidates`() {
        // GIVEN
        val cleaner = cleaner(removal = { path ->
            if (path == candidate(5).worktreePath) error("boom") else WorktreeRemovalResult.Success
        })

        // WHEN
        val summary = runBlocking { cleaner.clean(sourceRoot, workTreeDirectory, listOf(candidate(5), candidate(6))) }

        // THEN
        summary shouldBe ResolvedWorktreeCleanupSummary(removed = 1, skippedInUse = 0, closeFailed = 0, failed = 1)
    }

    @Test
    fun `clean - propagates cancellation`() {
        // GIVEN
        val cleaner = cleaner(removal = { throw CancellationException("cancelled") })

        // WHEN / THEN
        shouldThrow<CancellationException> {
            runBlocking { cleaner.clean(sourceRoot, workTreeDirectory, listOf(candidate(5))) }
        }
    }

    @Test
    fun `clean - does nothing for an empty list`() {
        // GIVEN
        val removed = mutableListOf<Path>()

        // WHEN
        val summary = runBlocking { cleaner(removed = removed).clean(sourceRoot, workTreeDirectory, emptyList()) }

        // THEN
        assertSoftly {
            summary shouldBe ResolvedWorktreeCleanupSummary(removed = 0, skippedInUse = 0, closeFailed = 0, failed = 0)
            removed shouldBe emptyList()
        }
    }
}