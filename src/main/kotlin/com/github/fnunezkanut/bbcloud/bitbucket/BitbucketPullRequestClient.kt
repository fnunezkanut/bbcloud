package com.github.fnunezkanut.bbcloud.bitbucket

interface BitbucketPullRequestClient {
    suspend fun listRecentPullRequests(workspace: String, repoSlug: String, apiToken: String): PullRequestListResult

    /** Pages through every open pull request (follows `next` until exhausted or the page cap); returns [PullRequestListResult.Failed] if any page request fails. */
    suspend fun listAllOpenPullRequests(workspace: String, repoSlug: String, apiToken: String): PullRequestListResult

    suspend fun countChangedFiles(workspace: String, repoSlug: String, pullRequestId: Long, apiToken: String): ItemCount?

    suspend fun countCommits(workspace: String, repoSlug: String, pullRequestId: Long, apiToken: String): ItemCount?

    suspend fun countCommitsBehind(workspace: String, repoSlug: String, sourceBranch: String, destinationBranch: String, apiToken: String): ItemCount?

    suspend fun listBuildStatuses(workspace: String, repoSlug: String, pullRequestId: Long, apiToken: String): List<PullRequestBuildStatus>?

    suspend fun getCurrentUser(apiToken: String): CurrentUser?

    /** Lists the repository's remote branch names, newest-updated first; null when any page fails. */
    suspend fun listBranches(workspace: String, repoSlug: String, apiToken: String): RepositoryBranches?

    /** Reads the repository's Bitbucket `mainbranch` name; null when it is unavailable. */
    suspend fun getMainBranchName(workspace: String, repoSlug: String, apiToken: String): String?

    /** Looks up the single OPEN pull request for the source and destination branch pair, if any. */
    suspend fun findOpenPullRequest(
        workspace: String,
        repoSlug: String,
        sourceBranch: String,
        destinationBranch: String,
        apiToken: String
    ): ExistingPullRequestLookupResult

    /** Creates a pull request and returns the created summary or the failure. */
    suspend fun createPullRequest(workspace: String, repoSlug: String, request: NewPullRequest, apiToken: String): CreatePullRequestResult

    /**
     * Only the first page is read. The result says whether the repository was listed, skipped, unauthorized or failed.
     */
    suspend fun listOpenPullRequestsReviewedBy(
        workspace: String,
        repoSlug: String,
        reviewerUuid: String,
        pageLen: Int,
        apiToken: String
    ): ReviewerQueueQueryResult

    /**
     * The newest-updated open pull requests [selectedUserUuid] authored anywhere in [workspace]; null when the
     * request fails. Only the first page is read, and [AuthoredPullRequestPage.isTruncated] says when more pages
     * were on offer than were read.
     */
    suspend fun listOpenAuthoredPullRequestsInWorkspace(workspace: String, selectedUserUuid: String, apiToken: String): AuthoredPullRequestPage?

    suspend fun getPullRequestDetails(workspace: String, repoSlug: String, pullRequestId: Long, currentUserUuid: String?, apiToken: String): PullRequestDetails?

    suspend fun performAction(
        workspace: String,
        repoSlug: String,
        pullRequest: PullRequestSummary,
        action: PullRequestAction,
        apiToken: String
    ): PullRequestActionResult

    suspend fun listMergeChecks(workspace: String, repoSlug: String, pullRequestId: Long, apiToken: String): List<PullRequestMergeCheck>?

    /** Returns null when any page fails; stops at the page limit and reports that through [WorkspaceMembers.isTruncated]. */
    suspend fun listWorkspaceMembers(workspace: String, apiToken: String): WorkspaceMembers?

    suspend fun listEffectiveDefaultReviewerUuids(workspace: String, repoSlug: String, apiToken: String): Set<String>?

    suspend fun updateReviewers(
        workspace: String,
        repoSlug: String,
        pullRequestId: Long,
        title: String,
        reviewerUuids: List<String>,
        apiToken: String
    ): PullRequestActionResult

    /** Updates the raw Markdown description; [title] is required by Bitbucket's full-resource pull-request PUT. */
    suspend fun updatePullRequestDescription(
        workspace: String,
        repoSlug: String,
        pullRequestId: Long,
        title: String,
        description: String,
        apiToken: String
    ): PullRequestActionResult

    /** Returns null when any page fails; tasks are sorted by id. */
    suspend fun listTasks(workspace: String, repoSlug: String, pullRequestId: Long, apiToken: String): List<PullRequestTask>?

    suspend fun createTask(workspace: String, repoSlug: String, pullRequestId: Long, content: String, apiToken: String): PullRequestActionResult

    /** A null [content] or [resolved] leaves that field unchanged. */
    suspend fun updateTask(
        workspace: String,
        repoSlug: String,
        pullRequestId: Long,
        taskId: Long,
        content: String?,
        resolved: Boolean?,
        apiToken: String
    ): PullRequestActionResult

    suspend fun deleteTask(workspace: String, repoSlug: String, pullRequestId: Long, taskId: Long, apiToken: String): PullRequestActionResult

    /** Returns null when any page fails; stops at the page limit and reports that through [PagedItems.isTruncated]. */
    suspend fun listActivity(workspace: String, repoSlug: String, pullRequestId: Long, apiToken: String): PagedItems<PullRequestActivityItem>?

    /**
     * Adds a comment; a non-null [parentCommentId] replies to that comment instead of starting a new thread.
     *
     * A non-null [inlineLine] anchors the comment to that one-based line of [inlinePath]; [inlineSide] selects the
     * Bitbucket inline anchor (`inline.to` for [InlineCommentSide.AFTER], `inline.from` for [InlineCommentSide.BEFORE]).
     */
    suspend fun createComment(
        workspace: String,
        repoSlug: String,
        pullRequestId: Long,
        content: String,
        apiToken: String,
        parentCommentId: Long? = null,
        inlinePath: String? = null,
        inlineLine: Int? = null,
        inlineSide: InlineCommentSide = InlineCommentSide.AFTER
    ): PullRequestActionResult

    suspend fun updateComment(
        workspace: String,
        repoSlug: String,
        pullRequestId: Long,
        commentId: Long,
        content: String,
        apiToken: String
    ): PullRequestActionResult

    suspend fun resolveCommentThread(workspace: String, repoSlug: String, pullRequestId: Long, commentId: Long, apiToken: String): PullRequestActionResult

    suspend fun reopenCommentThread(workspace: String, repoSlug: String, pullRequestId: Long, commentId: Long, apiToken: String): PullRequestActionResult

    suspend fun deleteComment(workspace: String, repoSlug: String, pullRequestId: Long, commentId: Long, apiToken: String): PullRequestActionResult

    /** Returns null when any page fails; stops at the page limit and reports that through [PagedItems.isTruncated]. */
    suspend fun listComments(workspace: String, repoSlug: String, pullRequestId: Long, apiToken: String): PagedItems<PullRequestComment>?

    /** Returns null when any page fails; stops at the page limit and reports that through [PagedItems.isTruncated]. */
    suspend fun listPullRequestCommits(workspace: String, repoSlug: String, pullRequestId: Long, apiToken: String): PagedItems<PullRequestCommit>?
}