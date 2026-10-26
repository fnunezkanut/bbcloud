package com.github.fnunezkanut.bbcloud.bitbucket

enum class ReviewerStatus {
    Approved,
    ChangesRequested,
    None
}

data class PullRequestReviewer(val uuid: String, val displayName: String, val avatarUrl: String?, val status: ReviewerStatus)

data class ReviewerCandidate(val uuid: String, val displayName: String, val nickname: String?, val avatarUrl: String?)

/**
 * Users that can be added as reviewers; [isTruncated] is true when the workspace member listing stopped at the page limit.
 */
data class ReviewerCandidates(val members: List<ReviewerCandidate>, val defaultReviewerUuids: Set<String>, val isTruncated: Boolean)

data class WorkspaceMemberPage(val members: List<ReviewerCandidate>, val nextUrl: String?)

/** [isTruncated] is true when the listing stopped at the page limit before reaching the last page. */
data class WorkspaceMembers(val members: List<ReviewerCandidate>, val isTruncated: Boolean)

sealed interface ReviewerChange {
    val uuid: String

    fun applyTo(currentUuids: List<String>): List<String>

    data class Add(override val uuid: String) : ReviewerChange {
        override fun applyTo(currentUuids: List<String>): List<String> {
            return if (uuid in currentUuids) currentUuids else currentUuids + uuid
        }
    }

    data class Remove(override val uuid: String) : ReviewerChange {
        override fun applyTo(currentUuids: List<String>): List<String> {
            return currentUuids.filterNot { current -> current == uuid }
        }
    }
}