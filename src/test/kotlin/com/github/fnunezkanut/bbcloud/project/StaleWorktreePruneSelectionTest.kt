package com.github.fnunezkanut.bbcloud.project

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

@Tag("unit")
class StaleWorktreePruneSelectionTest {
    @Test
    fun `worktreesToPrune - empty open set should select every discovered worktree`() {
        // GIVEN
        val discovered = listOf(worktree(1), worktree(2))

        // WHEN
        val stale = StaleWorktreePruneSelection.worktreesToPrune(discovered = discovered, openPrNumbers = emptySet())

        // THEN
        stale shouldBe discovered
    }

    @Test
    fun `worktreesToPrune - all open worktrees should select none`() {
        // GIVEN
        val discovered = listOf(worktree(1), worktree(2))

        // WHEN
        val stale = StaleWorktreePruneSelection.worktreesToPrune(discovered = discovered, openPrNumbers = setOf(1, 2))

        // THEN
        stale.shouldBeEmpty()
    }

    @Test
    fun `worktreesToPrune - should select only worktrees missing from open set`() {
        // GIVEN
        val discovered = listOf(worktree(1), worktree(2), worktree(3))

        // WHEN
        val stale = StaleWorktreePruneSelection.worktreesToPrune(discovered = discovered, openPrNumbers = setOf(1, 3))

        // THEN
        stale shouldHaveSize 1
        stale.single().prNumber shouldBe 2L
    }

    @Test
    fun `worktreesToPrune - duplicate discovered ids should preserve each missing entry`() {
        // GIVEN
        val discovered = listOf(worktree(7, "first"), worktree(7, "second"), worktree(8))

        // WHEN
        val stale = StaleWorktreePruneSelection.worktreesToPrune(discovered = discovered, openPrNumbers = setOf(8))

        // THEN
        stale shouldBe listOf(worktree(7, "first"), worktree(7, "second"))
    }

    private fun worktree(prNumber: Long, name: String = prNumber.toString()): WatchedProjectWorktree {
        return WatchedProjectWorktree(
            repoSlug = "repo",
            prNumber = prNumber,
            worktreePath = Path.of("/tmp/$name")
        )
    }
}