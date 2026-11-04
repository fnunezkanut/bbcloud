package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.ActivityUser
import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidateTier
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestDetails
import com.github.fnunezkanut.bbcloud.bitbucket.authorMentionCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.toMentionCandidate

/**
 * Collects the people on one pull request as [MentionCandidateTier.PullRequest] mention candidates: the author,
 * reviewers, and participants from the details, followed by the comment authors and resolvers. Plain class with
 * `@Volatile` fields, updated from the session as details and comments arrive.
 */
class PullRequestMentionPeople {
    @Volatile
    private var detailsPeople: List<MentionCandidate> = emptyList()

    @Volatile
    private var commentPeople: List<MentionCandidate> = emptyList()

    /** Stores the author, reviewers, then participants of [details] as mention candidates. */
    fun updateDetails(details: PullRequestDetails) {
        val people = buildList {
            details.summary.authorMentionCandidate(tier = MentionCandidateTier.PullRequest)?.let { add(it) }
            details.reviewers.forEach { reviewer ->
                reviewer.toMentionCandidate(tier = MentionCandidateTier.PullRequest)?.let { add(it) }
            }
            details.participants.forEach { participant ->
                participant.toMentionCandidate(tier = MentionCandidateTier.PullRequest)?.let { add(it) }
            }
        }
        detailsPeople = people
    }

    /** Stores the authors and resolvers of [comments] as mention candidates. */
    fun updateComments(comments: List<PullRequestComment>) {
        val people = buildList {
            comments.forEach { comment ->
                addCandidate(user = comment.author)
                addCandidate(user = comment.resolvedBy)
            }
        }
        commentPeople = people
    }

    /** The details people followed by the comment people, all in the pull-request tier. */
    fun candidates(): List<MentionCandidate> {
        return detailsPeople + commentPeople
    }

    /** Forgets the stored people, used when the session binds a different pull request. */
    fun clear() {
        detailsPeople = emptyList()
        commentPeople = emptyList()
    }

    private fun MutableList<MentionCandidate>.addCandidate(user: ActivityUser?) {
        user?.toMentionCandidate(tier = MentionCandidateTier.PullRequest)?.let { add(it) }
    }
}