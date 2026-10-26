package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerCandidates

object ReviewerCandidateFilter {
    const val MAX_RESULTS = 20

    private const val RANK_NAME_PREFIX = 0
    private const val RANK_WORD_PREFIX = 1
    private const val RANK_SUBSTRING = 2

    /**
     * Returns the candidates matching [query], excluding [excludedUuids] (the author and current reviewers).
     *
     * A blank query lists default reviewers first; otherwise full-name prefix matches rank before word prefix matches,
     * which rank before substring matches. Ties list default reviewers first, then sort by display name.
     */
    fun filter(candidates: ReviewerCandidates, query: String, excludedUuids: Set<String>, limit: Int = MAX_RESULTS): List<ReviewerCandidate> {
        val available = candidates.members
            .distinctBy { candidate -> candidate.uuid }
            .filterNot { candidate -> candidate.uuid in excludedUuids }
        val normalizedQuery = query.trim().lowercase()
        val byDefaultReviewerThenName = compareBy<ReviewerCandidate> { candidate -> candidate.uuid !in candidates.defaultReviewerUuids }
            .thenBy { candidate -> candidate.displayName.lowercase() }
        if (normalizedQuery.isEmpty()) {
            return available.sortedWith(byDefaultReviewerThenName).take(limit)
        }
        return available
            .mapNotNull { candidate -> rank(candidate = candidate, query = normalizedQuery)?.let { rank -> candidate to rank } }
            .sortedWith(compareBy<Pair<ReviewerCandidate, Int>> { (_, rank) -> rank }.thenBy(byDefaultReviewerThenName) { (candidate, _) -> candidate })
            .take(limit)
            .map { (candidate, _) -> candidate }
    }

    private fun rank(candidate: ReviewerCandidate, query: String): Int? {
        val names = listOfNotNull(candidate.displayName, candidate.nickname).map { name -> name.lowercase() }
        return when {
            names.any { name -> name.startsWith(query) } -> RANK_NAME_PREFIX
            names.any { name -> name.split(' ', '.', '-', '_').any { word -> word.startsWith(query) } } -> RANK_WORD_PREFIX
            names.any { name -> name.contains(query) } -> RANK_SUBSTRING
            else -> null
        }
    }
}