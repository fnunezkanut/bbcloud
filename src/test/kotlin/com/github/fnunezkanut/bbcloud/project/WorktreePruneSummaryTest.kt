package com.github.fnunezkanut.bbcloud.project

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class WorktreePruneSummaryTest {
    @Test
    fun `from - returns zero counts without outcomes`() {
        // GIVEN
        val outcomes = emptyList<ProjectPruneOutcome>()

        // WHEN
        val summary = WorktreePruneSummary.from(outcomes = outcomes)

        // THEN
        summary shouldBe WorktreePruneSummary(
            removed = 0,
            failedRemovals = 0,
            unverifiable = 0,
            projectsWithRemovals = 0
        )
    }

    @Test
    fun `from - sums counts and counts only projects with removals`() {
        // GIVEN
        val outcomes = listOf(
            ProjectPruneOutcome(repoSlug = "first", removedCount = 2, failedRemovalCount = 1),
            ProjectPruneOutcome(repoSlug = "second", unverifiableCount = 3),
            ProjectPruneOutcome(repoSlug = "third", removedCount = 1)
        )

        // WHEN
        val summary = WorktreePruneSummary.from(outcomes = outcomes)

        // THEN
        summary shouldBe WorktreePruneSummary(
            removed = 3,
            failedRemovals = 1,
            unverifiable = 3,
            projectsWithRemovals = 2
        )
    }
}