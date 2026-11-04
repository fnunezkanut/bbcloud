package com.github.fnunezkanut.bbcloud.ui.pullrequests

import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketAccountUuid
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.settings.PullRequestListFilter
import com.github.fnunezkanut.bbcloud.settings.PullRequestUserScope

/** Applies the Exclude checkboxes and the user scope dropdown to a merged list of pull request entries. Pure: no Compose, no IntelliJ APIs. */
object PullRequestListFiltering {
    /**
     * Keeps the entries that pass every enabled exclusion and the user scope. With [PullRequestUserScope.Me], only the
     * current user's authored entries pass; see [isMine]. With [PullRequestUserScope.AssignedToMe], only entries where
     * the current user is a reviewer pass; see [isAssignedTo]. A null [currentUserUuid] also disables the
     * Approved-by-me exclusion.
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
        if (!passesUserScope(entry = entry, userScope = filter.userScope, currentUserUuid = currentUserUuid)) {
            return false
        }
        if (filter.excludeApprovedByMe && isApprovedByMe(entry = entry, currentUserUuid = currentUserUuid)) {
            return false
        }
        return true
    }

    /** Whether [entry] passes [userScope]. [PullRequestUserScope.AnyUser] passes every entry. */
    private fun passesUserScope(entry: PullRequestListEntry, userScope: PullRequestUserScope, currentUserUuid: String?): Boolean {
        return when (userScope) {
            PullRequestUserScope.AnyUser -> true
            PullRequestUserScope.Me -> isMine(entry = entry, currentUserUuid = currentUserUuid)
            PullRequestUserScope.AssignedToMe -> isAssignedTo(entry = entry, currentUserUuid = currentUserUuid)
        }
    }

    /**
     * Whether [entry] was authored by the current user. A WorkspaceAuthored row is always mine, because that endpoint
     * is keyed by the current user's uuid; this holds even when [currentUserUuid] is null. Otherwise it is mine only
     * when the author uuid is known and matches. A row with a null author uuid is not mine, so the Created-by-me
     * scope hides it.
     */
    private fun isMine(entry: PullRequestListEntry, currentUserUuid: String?): Boolean {
        if (entry.source == PullRequestListingSource.WorkspaceAuthored) {
            return true
        }
        return currentUserUuid != null &&
            BitbucketAccountUuid.matches(first = entry.pullRequest.summary.authorUuid, second = currentUserUuid)
    }

    /**
     * Whether the current user is a reviewer of [entry]. Reviewers come from the listing's participants and exclude
     * the author, so a pull request the user only authored does not match. A null [currentUserUuid] matches nothing.
     */
    private fun isAssignedTo(entry: PullRequestListEntry, currentUserUuid: String?): Boolean {
        return isReviewer(summary = entry.pullRequest.summary, userUuid = currentUserUuid)
    }

    /** Whether the user with [userUuid] is a reviewer of [summary]; always false when [userUuid] is null or blank. */
    internal fun isReviewer(summary: PullRequestSummary, userUuid: String?): Boolean {
        if (userUuid.isNullOrBlank()) {
            return false
        }
        return summary.reviewers.any { reviewer ->
            BitbucketAccountUuid.matches(first = reviewer.uuid, second = userUuid)
        }
    }

    /** Whether the current user approves [entry]; always false when [currentUserUuid] is null. */
    private fun isApprovedByMe(entry: PullRequestListEntry, currentUserUuid: String?): Boolean {
        return isApprovedBy(summary = entry.pullRequest.summary, userUuid = currentUserUuid)
    }

    /** Whether the user with [userUuid] approves [summary]; always false when [userUuid] is null or blank. */
    internal fun isApprovedBy(summary: PullRequestSummary, userUuid: String?): Boolean {
        if (userUuid == null) {
            return false
        }
        return summary.approverUuids.any { approver ->
            BitbucketAccountUuid.matches(first = approver, second = userUuid)
        }
    }
}