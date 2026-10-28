package com.github.fnunezkanut.bbcloud.project

/**
 * The result of pruning the PR work trees of one watched project.
 *
 * [unverifiableCount] counts the work trees that were kept because Bitbucket could not confirm their pull request state,
 * or because the project could not be pruned at all.
 */
data class ProjectPruneOutcome(val repoSlug: String, val removedCount: Int = 0, val failedRemovalCount: Int = 0, val unverifiableCount: Int = 0)

/**
 * The aggregated result of pruning every watched project, shown to the user as one notification.
 */
data class WorktreePruneSummary(val removed: Int, val failedRemovals: Int, val unverifiable: Int, val projectsWithRemovals: Int) {
    companion object {
        /** Sums the per-project [outcomes]; [projectsWithRemovals] counts only projects that removed at least one work tree. */
        fun from(outcomes: List<ProjectPruneOutcome>): WorktreePruneSummary {
            return WorktreePruneSummary(
                removed = outcomes.sumOf { outcome -> outcome.removedCount },
                failedRemovals = outcomes.sumOf { outcome -> outcome.failedRemovalCount },
                unverifiable = outcomes.sumOf { outcome -> outcome.unverifiableCount },
                projectsWithRemovals = outcomes.count { outcome -> outcome.removedCount > 0 }
            )
        }
    }
}