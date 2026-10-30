package com.github.fnunezkanut.bbcloud.ui.pullrequests

import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketAccountUuid
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.settings.PullRequestListFilter

/** Applies the Exclude: checkboxes to a merged list of pull request entries. Pure: no Compose, no IntelliJ APIs. */
object PullRequestListFiltering {
    /**
     * Keeps the entries that pass every enabled exclusion. A null [currentUserUuid] disables the Approved-by-me
     * exclusion and limits the Mine exclusion to WorkspaceAuthored rows; see edge case 5.
     */
    fun apply(entries: List<PullRequestListEntry>, filter: PullRequestListFilter, currentUserUuid: String?): List<PullRequestListEntry> {
        return entries.filter { entry -> keeps(entry = entry, filter = filter, currentUserUuid = currentUserUuid) }
    }

    private fun keeps(entry: PullRequestListEntry, filter: PullRequestListFilter, currentUserUuid: String?): Boolean {
        val summary = entry.pullRequest.summary
        if (filter.excludeResolved && summary.state != PullRequestState.Open) {
            return false
        }
        if (filter.excludeDrafts && summary.isDraft) {
            return false
        }
        if (filter.excludeMine && isMine(entry = entry, currentUserUuid = currentUserUuid)) {
            return false
        }
        if (filter.excludeApprovedByMe && isApprovedByMe(entry = entry, currentUserUuid = currentUserUuid)) {
            return false
        }
        return true
    }

    /**
     * Whether [entry] was authored by the current user. A WorkspaceAuthored row is always mine, because that endpoint
     * is keyed by the current user's uuid; this holds even when [currentUserUuid] is null. Otherwise it is mine only
     * when the author uuid is known and matches, so a row with a null author uuid counts as not mine and stays visible.
     */
    private fun isMine(entry: PullRequestListEntry, currentUserUuid: String?): Boolean {
        if (entry.source == PullRequestListingSource.WorkspaceAuthored) {
            return true
        }
        return currentUserUuid != null &&
            BitbucketAccountUuid.matches(first = entry.pullRequest.summary.authorUuid, second = currentUserUuid)
    }

    /** Whether the current user approves [entry]; always false when [currentUserUuid] is null. */
    private fun isApprovedByMe(entry: PullRequestListEntry, currentUserUuid: String?): Boolean {
        if (currentUserUuid == null) {
            return false
        }
        return entry.pullRequest.summary.approverUuids.any { approver ->
            BitbucketAccountUuid.matches(first = approver, second = currentUserUuid)
        }
    }
}