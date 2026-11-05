package com.github.fnunezkanut.bbcloud.project

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

@Tag("unit")
class ResolvedWorktreeCleanupSelectionTest {
    private val root: Path = Path.of("/tmp/worktrees")

    private fun directory(prNumber: Long, slug: String = "api"): WatchedProjectWorktree {
        return WatchedProjectWorktree(repoSlug = slug, prNumber = prNumber, worktreePath = root.resolve("$slug--pr-$prNumber"))
    }

    private fun select(
        directories: List<WatchedProjectWorktree> = emptyList(),
        registered: List<Path> = emptyList(),
        resolved: Set<Long> = emptySet(),
        open: Set<Long>? = emptySet()
    ): List<ResolvedWorktreeCandidate> {
        return ResolvedWorktreeCleanupSelection.candidates(
            repoSlug = "api",
            workTreeDirectory = root,
            discoveredDirectories = directories,
            registeredPaths = registered,
            resolvedPrNumbers = resolved,
            completeOpenPrNumbers = open
        )
    }

    @Test
    fun `candidates - selects a merged PR with a directory`() {
        // GIVEN / WHEN
        val result = select(directories = listOf(directory(prNumber = 5)), resolved = setOf(5L), open = setOf(9L))

        // THEN
        result shouldBe listOf(ResolvedWorktreeCandidate(prNumber = 5, worktreePath = root.resolve("api--pr-5"), directoryExists = true))
    }

    @Test
    fun `candidates - selects a declined PR that only has an orphan registration`() {
        // GIVEN / WHEN
        val result = select(registered = listOf(root.resolve("api--pr-6")), resolved = setOf(6L), open = setOf(9L))

        // THEN
        result shouldBe listOf(ResolvedWorktreeCandidate(prNumber = 6, worktreePath = root.resolve("api--pr-6"), directoryExists = false))
    }

    @Test
    fun `candidates - never selects an open PR`() {
        // GIVEN / WHEN
        val result = select(directories = listOf(directory(prNumber = 5)), resolved = setOf(5L), open = setOf(5L))

        // THEN
        result.shouldBeEmpty()
    }

    @Test
    fun `candidates - selects a resolved PR when the open listing is unknown`() {
        // GIVEN / WHEN
        val result = select(directories = listOf(directory(prNumber = 5), directory(prNumber = 7)), resolved = setOf(5L), open = null)

        // THEN
        result.map { it.prNumber } shouldBe listOf(5L)
    }

    @Test
    fun `candidates - selects a PR absent from the complete open set`() {
        // GIVEN / WHEN
        val result = select(directories = listOf(directory(prNumber = 5), directory(prNumber = 7)), open = setOf(7L))

        // THEN
        result.map { it.prNumber } shouldBe listOf(5L)
    }

    @Test
    fun `candidates - ignores registrations of another repo slug`() {
        // GIVEN / WHEN
        val result = select(registered = listOf(root.resolve("other--pr-5")), resolved = setOf(5L))

        // THEN
        result.shouldBeEmpty()
    }

    @Test
    fun `candidates - ignores registrations outside the work tree root`() {
        // GIVEN / WHEN
        val result = select(registered = listOf(Path.of("/elsewhere/api--pr-5")), resolved = setOf(5L))

        // THEN
        result.shouldBeEmpty()
    }

    @Test
    fun `candidates - ignores malformed folder names`() {
        // GIVEN / WHEN
        val result = select(registered = listOf(root.resolve("api"), root.resolve("api--pr-x")), resolved = setOf(5L))

        // THEN
        result.shouldBeEmpty()
    }

    @Test
    fun `candidates - collapses a directory and its registration into one candidate`() {
        // GIVEN / WHEN
        val result = select(
            directories = listOf(directory(prNumber = 5)),
            registered = listOf(root.resolve("api--pr-5")),
            resolved = setOf(5L)
        )

        // THEN
        assertSoftly {
            result.size shouldBe 1
            result.single().directoryExists shouldBe true
        }
    }

    @Test
    fun `candidates - returns nothing for empty inputs`() {
        // GIVEN / WHEN / THEN
        select().shouldBeEmpty()
    }
}