package com.github.fnunezkanut.bbcloud.ui.pullrequests

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState

/** Merges the loaded sources of a tab into one ordered, de-duplicated list. Pure: no Compose, no IntelliJ APIs. */
object PullRequestListMerger {
    /**
     * Merges the loaded sources and drops duplicates (first source in PullRequestListingSource order wins).
     * Order: newest updatedOn first, ties broken by repoSlug then id descending for a stable order.
     * With [openFirst] the rows whose state is Open come first, then the others; each group is ordered as above.
     */
    fun merge(entriesBySource: Map<PullRequestListingSource, List<PullRequestListEntry>>, openFirst: Boolean): List<PullRequestListEntry> {
        val deduplicated = deduplicate(entriesBySource = entriesBySource)
        val ordered = deduplicated.sortedWith(NEWEST_FIRST_COMPARATOR)
        if (!openFirst) {
            return ordered
        }
        val (open, others) = ordered.partition { entry -> entry.pullRequest.summary.state == PullRequestState.Open }
        return open + others
    }

    /**
     * Walks the sources in [PullRequestListingSource] declaration order so the first source to carry a key wins, and
     * keeps only the first entry seen for each [PullRequestListKey].
     */
    private fun deduplicate(entriesBySource: Map<PullRequestListingSource, List<PullRequestListEntry>>): List<PullRequestListEntry> {
        val seen = mutableSetOf<PullRequestListKey>()
        val result = mutableListOf<PullRequestListEntry>()
        for (source in PullRequestListingSource.entries) {
            val entries = entriesBySource[source] ?: continue
            for (entry in entries) {
                if (seen.add(entry.key)) {
                    result.add(entry)
                }
            }
        }
        return result
    }

    private val NEWEST_FIRST_COMPARATOR: Comparator<PullRequestListEntry> =
        compareByDescending<PullRequestListEntry> { entry -> entry.pullRequest.summary.updatedOn }
            .thenByDescending { entry -> entry.pullRequest.repoSlug }
            .thenByDescending { entry -> entry.pullRequest.summary.id }
}