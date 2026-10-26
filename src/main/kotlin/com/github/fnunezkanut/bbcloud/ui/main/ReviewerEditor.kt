package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestReviewer
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerCandidates

/** Reviewer operations of the displayed pull request; implementations call back on the event dispatch thread. */
interface ReviewerEditor {
    fun addReviewer(candidate: ReviewerCandidate)

    fun removeReviewer(reviewer: PullRequestReviewer)

    /** Calls [onLoaded] with null when the candidates could not be loaded. */
    fun loadCandidates(onLoaded: (ReviewerCandidates?) -> Unit)
}