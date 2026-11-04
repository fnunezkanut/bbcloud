package com.github.fnunezkanut.bbcloud.ui.pullrequests

import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidateTier
import com.github.fnunezkanut.bbcloud.bitbucket.authorMentionCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.toMentionCandidate

/**
 * Collects the people visible in a pull request listing (each entry's author and reviewers) as recently-seen mention
 * candidates, grouped by the entry's workspace and de-duplicated by identity within each workspace.
 */
internal fun mentionPeopleByWorkspace(entries: List<PullRequestListEntry>): Map<String, List<MentionCandidate>> {
    val byWorkspace = linkedMapOf<String, LinkedHashMap<String, MentionCandidate>>()
    for (entry in entries) {
        val workspace = entry.pullRequest.workspace.takeIf { it.isNotBlank() } ?: continue
        val summary = entry.pullRequest.summary
        val people = buildList {
            summary.authorMentionCandidate(tier = MentionCandidateTier.RecentlySeen)?.let { add(it) }
            summary.reviewers.forEach { reviewer ->
                reviewer.toMentionCandidate(tier = MentionCandidateTier.RecentlySeen)?.let { add(it) }
            }
        }
        val bucket = byWorkspace.getOrPut(workspace) { linkedMapOf() }
        for (candidate in people) {
            val key = candidate.identityKey() ?: continue
            bucket.putIfAbsent(key, candidate)
        }
    }
    return byWorkspace.mapValues { (_, bucket) -> bucket.values.toList() }
}