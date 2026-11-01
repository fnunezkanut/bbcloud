package com.github.fnunezkanut.bbcloud.ui.pullrequests

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestReviewer
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerStatus
import kotlin.time.Instant

/** The reviewers a listing card shows as avatars ([visible]) and the ones folded into the "+N" circle ([hidden]). */
data class ReviewerStack(val visible: List<PullRequestReviewer>, val hidden: List<PullRequestReviewer>) {
    val hiddenCount: Int get() {
        return hidden.size
    }

    val isEmpty: Boolean get() {
        return visible.isEmpty()
    }

    companion object {
        val EMPTY = ReviewerStack(visible = emptyList(), hidden = emptyList())
    }
}

const val MAX_VISIBLE_REVIEWER_AVATARS = 2

/** Caps the hidden count for the "+N" label, so a very long reviewer list never overflows the circle. */
private const val MAX_OVERFLOW_COUNT = 99

/**
 * Orders [reviewers] for display and splits them into the [ReviewerStack.visible] slots (at most [maxVisible]) and the
 * [ReviewerStack.hidden] overflow. The sort is stable: within a group the original order is kept. Approvals come first,
 * most recent [PullRequestReviewer.participatedOn] first (null last), then changes-requested, then not-yet-reviewed.
 */
internal fun buildReviewerStack(reviewers: List<PullRequestReviewer>, maxVisible: Int = MAX_VISIBLE_REVIEWER_AVATARS): ReviewerStack {
    if (reviewers.isEmpty()) {
        return ReviewerStack.EMPTY
    }
    val limit = maxVisible.coerceAtLeast(1)
    val comparator = compareBy<PullRequestReviewer> { reviewer -> statusRank(reviewer.status) }
        .thenComparing(Comparator { left, right -> compareApprovalRecency(left = left, right = right) })
    val sorted = reviewers.sortedWith(comparator)
    return ReviewerStack(visible = sorted.take(limit), hidden = sorted.drop(limit))
}

/** The number shown by the "+N" circle, capped at [MAX_OVERFLOW_COUNT]. */
internal fun cappedOverflowCount(hiddenCount: Int): Int {
    return hiddenCount.coerceAtMost(MAX_OVERFLOW_COUNT)
}

/** The "+N" label text for the overflow circle, using the capped hidden count. */
internal fun reviewerOverflowLabel(hiddenCount: Int): String {
    return BbCloudBundle.message("main.pullRequests.reviewers.more", cappedOverflowCount(hiddenCount = hiddenCount))
}

private fun statusRank(status: ReviewerStatus): Int {
    return when (status) {
        ReviewerStatus.Approved -> 0
        ReviewerStatus.ChangesRequested -> 1
        ReviewerStatus.None -> 2
    }
}

/**
 * Orders two reviewers within the same status group by approval recency: a dated approval comes before an approval
 * with no date, and newer approvals come before older ones. Non-approval statuses compare equal here, so a stable sort
 * keeps their input order.
 */
private fun compareApprovalRecency(left: PullRequestReviewer, right: PullRequestReviewer): Int {
    val leftKey = approvalRecencyKey(reviewer = left)
    val rightKey = approvalRecencyKey(reviewer = right)
    return when {
        leftKey == null && rightKey == null -> 0
        leftKey == null -> 1
        rightKey == null -> -1
        else -> rightKey.compareTo(leftKey)
    }
}

/** Only approvals participate in the recency tiebreak; every other status returns null so its group keeps input order. */
private fun approvalRecencyKey(reviewer: PullRequestReviewer): Instant? {
    return if (reviewer.status == ReviewerStatus.Approved) reviewer.participatedOn else null
}