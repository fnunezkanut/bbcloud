package com.github.fnunezkanut.bbcloud.ui.pullrequest.session

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestFileCommentCounts

/** Coordinates raw comment loads and keeps their results scoped to the displayed pull request. */
internal class PullRequestFileCommentCountsLoadState {
    sealed interface Request {
        data object Started : Request

        data object AlreadyInProgress : Request

        data object AlreadyLoaded : Request
    }

    data class Completion(val counts: PullRequestFileCommentCounts?, val shouldReload: Boolean, val isForDisplayedPullRequest: Boolean)

    private var displayedPullRequestId: Long? = null
    private val loadedComments = mutableMapOf<Long, List<PullRequestComment>?>()
    private val inFlightPullRequestIds = mutableSetOf<Long>()
    private val reloadRequestedPullRequestIds = mutableSetOf<Long>()

    fun bind(pullRequestId: Long) {
        displayedPullRequestId = pullRequestId
    }

    fun request(pullRequestId: Long, force: Boolean): Request {
        if (!inFlightPullRequestIds.add(pullRequestId)) {
            if (force) {
                reloadRequestedPullRequestIds.add(pullRequestId)
            }
            return Request.AlreadyInProgress
        }
        if (!force && loadedComments.containsKey(pullRequestId)) {
            inFlightPullRequestIds.remove(pullRequestId)
            return Request.AlreadyLoaded
        }
        return Request.Started
    }

    fun countsForDisplayedPullRequest(): PullRequestFileCommentCounts? {
        val pullRequestId = displayedPullRequestId ?: return null
        val comments = loadedComments[pullRequestId] ?: return null
        return PullRequestFileCommentCounts.from(comments = comments)
    }

    fun filesChangedCompleted(pullRequestId: Long): PullRequestFileCommentCounts? {
        if (pullRequestId != displayedPullRequestId) {
            return null
        }
        return countsForDisplayedPullRequest()
    }

    fun complete(pullRequestId: Long, comments: List<PullRequestComment>?): Completion {
        inFlightPullRequestIds.remove(pullRequestId)
        val shouldReload = reloadRequestedPullRequestIds.remove(pullRequestId) &&
            pullRequestId == displayedPullRequestId
        val isForDisplayedPullRequest = pullRequestId == displayedPullRequestId
        if (isForDisplayedPullRequest) {
            loadedComments[pullRequestId] = comments
        }
        val counts = if (isForDisplayedPullRequest) {
            comments?.let { items -> PullRequestFileCommentCounts.from(comments = items) }
        } else {
            null
        }
        return Completion(
            counts = counts,
            shouldReload = shouldReload,
            isForDisplayedPullRequest = isForDisplayedPullRequest
        )
    }
}