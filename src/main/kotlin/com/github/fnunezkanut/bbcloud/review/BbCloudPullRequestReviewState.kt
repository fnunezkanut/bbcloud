package com.github.fnunezkanut.bbcloud.review

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestInlineCommentIndex
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestPathMatcher
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/** Holds the active pull request and inline comment index for diff decoration. */
@Service(Service.Level.PROJECT)
class BbCloudPullRequestReviewState {
    @Volatile
    var activePullRequestId: Long? = null
        private set

    @Volatile
    var inlineCommentIndex: PullRequestInlineCommentIndex? = null
        private set

    /**
     * The project-root-relative paths of the files the active pull request changes.
     *
     * The paths are normalized with [PullRequestPathMatcher.normalize], so the membership check of
     * [isFileChangedInActivePullRequest] is a plain set lookup.
     */
    @Volatile
    var changedFilePaths: Set<String> = emptySet()
        private set

    fun bindPullRequest(pullRequestId: Long) {
        if (activePullRequestId == pullRequestId) {
            return
        }
        activePullRequestId = pullRequestId
        inlineCommentIndex = null
        changedFilePaths = emptySet()
    }

    fun updateInlineComments(pullRequestId: Long, index: PullRequestInlineCommentIndex?): Boolean {
        if (activePullRequestId != pullRequestId) {
            return false
        }
        inlineCommentIndex = index
        return true
    }

    /**
     * Records the files the pull request changes.
     *
     * The update is refused when the pull request is no longer the active one, so a slow diff load of an old pull
     * request never overwrites the files of the pull request the user is looking at.
     */
    fun updateChangedFilePaths(pullRequestId: Long, paths: Set<String>): Boolean {
        if (activePullRequestId != pullRequestId) {
            return false
        }
        changedFilePaths = paths
        return true
    }

    /** Tells whether the file is one of the files the active pull request changes. */
    fun isFileChangedInActivePullRequest(relativePath: String): Boolean {
        val normalized = PullRequestPathMatcher.normalize(relativePath)
        return changedFilePaths.contains(normalized)
    }

    fun clear() {
        activePullRequestId = null
        inlineCommentIndex = null
        changedFilePaths = emptySet()
    }

    companion object {
        fun getInstance(project: Project): BbCloudPullRequestReviewState {
            return project.getService(BbCloudPullRequestReviewState::class.java)
        }
    }
}