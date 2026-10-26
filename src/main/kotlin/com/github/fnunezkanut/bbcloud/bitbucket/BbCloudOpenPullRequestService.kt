package com.github.fnunezkanut.bbcloud.bitbucket

import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.credentials.BbCloudCredentialStore
import com.github.fnunezkanut.bbcloud.git.BitbucketGitRemoteReader
import com.github.fnunezkanut.bbcloud.project.ProjectRepositoryResolver
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.nio.file.Path

class BbCloudOpenPullRequestService(
    private val credentialStore: BbCloudCredentialStore,
    private val pullRequestClient: BitbucketPullRequestClient,
    private val readRemoteUrl: (Path) -> String? = BitbucketGitRemoteReader::readRemoteUrl,
    private val configurationService: BbCloudConfigurationService? = null
) {
    @Volatile
    private var cachedCurrentUser: CachedCurrentUser? = null

    @Volatile
    private var cachedMembers: CachedMembers? = null

    suspend fun listRecentPullRequests(projectBasePath: String?, organizationUrl: String): PullRequestListResult {
        val target = resolveTarget(
            operation = "list pull requests",
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl
        ) ?: return PullRequestListResult.Failed()

        LOG.debug("Listing pull requests for ${target.workspace}/${target.repoSlug}")
        // Bitbucket already answers newest-updated first; grouping and ordering for display belong to the panel.
        return pullRequestClient.listRecentPullRequests(
            workspace = target.workspace,
            repoSlug = target.repoSlug,
            apiToken = target.apiToken
        )
    }

    /** Lists the complete open pull-request set for safe worktree pruning, distinct from the capped display listing. */
    suspend fun listAllOpenPullRequests(projectBasePath: String?, organizationUrl: String): PullRequestListResult {
        val target = resolveTarget(
            operation = "list all open pull requests",
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl
        ) ?: return PullRequestListResult.Failed()

        LOG.debug("Listing all open pull requests for ${target.workspace}/${target.repoSlug}")
        return pullRequestClient.listAllOpenPullRequests(
            workspace = target.workspace,
            repoSlug = target.repoSlug,
            apiToken = target.apiToken
        )
    }

    suspend fun pullRequestCounts(projectBasePath: String?, organizationUrl: String, pullRequestId: Long): PullRequestCounts {
        val target = resolveTarget(
            operation = "count pull request files and commits",
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl
        ) ?: return PullRequestCounts(filesChanged = null, commits = null)

        LOG.debug("Counting files and commits for pull request #$pullRequestId in ${target.workspace}/${target.repoSlug}")
        return coroutineScope {
            val filesChanged = async {
                pullRequestClient.countChangedFiles(
                    workspace = target.workspace,
                    repoSlug = target.repoSlug,
                    pullRequestId = pullRequestId,
                    apiToken = target.apiToken
                )
            }
            val commits = async {
                pullRequestClient.countCommits(
                    workspace = target.workspace,
                    repoSlug = target.repoSlug,
                    pullRequestId = pullRequestId,
                    apiToken = target.apiToken
                )
            }
            PullRequestCounts(filesChanged = filesChanged.await(), commits = commits.await())
        }
    }

    suspend fun pullRequestOverview(projectBasePath: String?, organizationUrl: String, summary: PullRequestSummary): PullRequestOverview {
        val target = resolveTarget(
            operation = "load pull request overview",
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl
        ) ?: return PullRequestOverview(commitsBehind = null, builds = null)

        LOG.debug("Loading overview for pull request #${summary.id} in ${target.workspace}/${target.repoSlug}")
        return coroutineScope {
            val commitsBehind = async {
                pullRequestClient.countCommitsBehind(
                    workspace = target.workspace,
                    repoSlug = target.repoSlug,
                    sourceBranch = summary.sourceBranch,
                    destinationBranch = summary.destinationBranch,
                    apiToken = target.apiToken
                )
            }
            val builds = async {
                pullRequestClient.listBuildStatuses(
                    workspace = target.workspace,
                    repoSlug = target.repoSlug,
                    pullRequestId = summary.id,
                    apiToken = target.apiToken
                )
            }
            val mergeChecks = async {
                pullRequestClient.listMergeChecks(
                    workspace = target.workspace,
                    repoSlug = target.repoSlug,
                    pullRequestId = summary.id,
                    apiToken = target.apiToken
                )
            }
            val tasks = async {
                pullRequestClient.listTasks(
                    workspace = target.workspace,
                    repoSlug = target.repoSlug,
                    pullRequestId = summary.id,
                    apiToken = target.apiToken
                )
            }
            val timelineSources = async { timelineSources(target = target, pullRequestId = summary.id) }
            val loadedBuilds = builds.await()
            val loadedTasks = tasks.await()
            val sources = timelineSources.await()
            val conversation = sources?.comments?.let { comments ->
                PullRequestTimelineBuilder.buildConversation(
                    comments = comments,
                    currentUserUuid = sources.currentUser?.uuid,
                    currentUserAvatarUrl = sources.currentUser?.avatarUrl
                )
            }
            val activityFeed = PullRequestTimelineBuilder.buildActivityFeed(
                summary = summary,
                activity = sources?.activity,
                tasks = loadedTasks,
                builds = loadedBuilds,
                commits = sources?.commits
            )
            PullRequestOverview(
                commitsBehind = commitsBehind.await(),
                builds = loadedBuilds,
                mergeChecks = mergeChecks.await(),
                tasks = loadedTasks,
                conversation = conversation,
                activityFeed = activityFeed
            )
        }
    }

    /** Returns null when the comments could not be loaded; missing activity or commits are reported via partial failure flags. */
    private suspend fun timelineSources(target: RepositoryTarget, pullRequestId: Long): TimelineSources? {
        return coroutineScope {
            val activity = async {
                pullRequestClient.listActivity(
                    workspace = target.workspace,
                    repoSlug = target.repoSlug,
                    pullRequestId = pullRequestId,
                    apiToken = target.apiToken
                )
            }
            val comments = async {
                pullRequestClient.listComments(
                    workspace = target.workspace,
                    repoSlug = target.repoSlug,
                    pullRequestId = pullRequestId,
                    apiToken = target.apiToken
                )
            }
            val commits = async {
                pullRequestClient.listPullRequestCommits(
                    workspace = target.workspace,
                    repoSlug = target.repoSlug,
                    pullRequestId = pullRequestId,
                    apiToken = target.apiToken
                )
            }
            val currentUser = async { currentUser(apiToken = target.apiToken) }
            val loadedActivity = activity.await()
            val loadedComments = comments.await()
            val loadedCommits = commits.await()
            val loadedCurrentUser = currentUser.await()
            if (loadedComments == null) {
                LOG.debug(
                    "Comment source unavailable for pull request #$pullRequestId: comments=null (will set conversation to null)"
                )
            }
            if (loadedActivity == null) {
                LOG.debug(
                    "Activity source unavailable for pull request #$pullRequestId: activity=null (will set hasPartialFailure on activityFeed)"
                )
            }
            TimelineSources(
                activity = loadedActivity,
                comments = loadedComments,
                commits = loadedCommits,
                currentUser = loadedCurrentUser
            )
        }
    }

    private data class TimelineSources(
        val activity: PagedItems<PullRequestActivityItem>?,
        val comments: PagedItems<PullRequestComment>?,
        val commits: PagedItems<PullRequestCommit>?,
        val currentUser: CurrentUser?
    )

    /** Runs the requests one after another and stops at the first failure, which is returned. */
    suspend fun applyTaskChange(projectBasePath: String?, organizationUrl: String, pullRequestId: Long, change: TaskChange): PullRequestActionResult {
        val target = resolveTarget(
            operation = "change tasks of pull request",
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl
        ) ?: return PullRequestActionResult.Failed()

        LOG.debug("Applying ${change::class.simpleName} task change to pull request #$pullRequestId in ${target.workspace}/${target.repoSlug}")
        return when (change) {
            is TaskChange.Create -> {
                pullRequestClient.createTask(
                    workspace = target.workspace,
                    repoSlug = target.repoSlug,
                    pullRequestId = pullRequestId,
                    content = change.content,
                    apiToken = target.apiToken
                )
            }
            is TaskChange.SetResolved -> {
                runUntilFailure(items = change.taskIds) { taskId ->
                    pullRequestClient.updateTask(
                        workspace = target.workspace,
                        repoSlug = target.repoSlug,
                        pullRequestId = pullRequestId,
                        taskId = taskId,
                        content = null,
                        resolved = change.resolved,
                        apiToken = target.apiToken
                    )
                }
            }
            is TaskChange.Edit -> {
                applyTaskEdits(target = target, pullRequestId = pullRequestId, edits = change.edits)
            }
        }
    }

    private suspend fun applyTaskEdits(target: RepositoryTarget, pullRequestId: Long, edits: TaskEdits): PullRequestActionResult {
        val updateResult = runUntilFailure(items = edits.contentUpdates.entries) { (taskId, content) ->
            pullRequestClient.updateTask(
                workspace = target.workspace,
                repoSlug = target.repoSlug,
                pullRequestId = pullRequestId,
                taskId = taskId,
                content = content,
                resolved = null,
                apiToken = target.apiToken
            )
        }
        if (updateResult is PullRequestActionResult.Failed) {
            return updateResult
        }
        return runUntilFailure(items = edits.deletedIds) { taskId ->
            pullRequestClient.deleteTask(
                workspace = target.workspace,
                repoSlug = target.repoSlug,
                pullRequestId = pullRequestId,
                taskId = taskId,
                apiToken = target.apiToken
            )
        }
    }

    private suspend fun <T> runUntilFailure(items: Iterable<T>, request: suspend (T) -> PullRequestActionResult): PullRequestActionResult {
        for (item in items) {
            val result = request(item)
            if (result is PullRequestActionResult.Failed) {
                return result
            }
        }
        return PullRequestActionResult.Success
    }

    /** Returns null when the workspace members could not be loaded; missing default reviewers only remove the suggestions. */
    suspend fun reviewerCandidates(projectBasePath: String?, organizationUrl: String): ReviewerCandidates? {
        val target = resolveTarget(
            operation = "load reviewer candidates",
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl
        ) ?: return null

        LOG.debug("Loading reviewer candidates for ${target.workspace}/${target.repoSlug}")
        return coroutineScope {
            val members = async { workspaceMembers(workspace = target.workspace, apiToken = target.apiToken) }
            val defaultReviewerUuids = async {
                pullRequestClient.listEffectiveDefaultReviewerUuids(
                    workspace = target.workspace,
                    repoSlug = target.repoSlug,
                    apiToken = target.apiToken
                )
            }
            val loadedMembers = members.await() ?: return@coroutineScope null
            ReviewerCandidates(
                members = loadedMembers.members,
                defaultReviewerUuids = defaultReviewerUuids.await().orEmpty(),
                isTruncated = loadedMembers.isTruncated
            )
        }
    }

    /** Loads the repository branches and its Bitbucket `mainbranch` for the new pull request form; null when branches fail. */
    suspend fun repositoryBranches(projectBasePath: String?, organizationUrl: String): NewPullRequestBranchData? {
        val target = resolveTarget(
            operation = "load repository branches",
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl
        ) ?: return null

        LOG.debug("Loading repository branches for ${target.workspace}/${target.repoSlug}")
        return coroutineScope {
            val branches = async {
                pullRequestClient.listBranches(
                    workspace = target.workspace,
                    repoSlug = target.repoSlug,
                    apiToken = target.apiToken
                )
            }
            val mainBranchName = async {
                pullRequestClient.getMainBranchName(
                    workspace = target.workspace,
                    repoSlug = target.repoSlug,
                    apiToken = target.apiToken
                )
            }
            val loadedBranches = branches.await() ?: return@coroutineScope null
            NewPullRequestBranchData(branches = loadedBranches, mainBranchName = mainBranchName.await())
        }
    }

    /** Looks up an OPEN pull request for the source and destination branch pair; a null target fails the lookup. */
    suspend fun findOpenPullRequest(
        projectBasePath: String?,
        organizationUrl: String,
        sourceBranch: String,
        destinationBranch: String
    ): ExistingPullRequestLookupResult {
        val target = resolveTarget(
            operation = "find an existing pull request",
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl
        ) ?: return ExistingPullRequestLookupResult.Failed()

        LOG.debug("Finding an existing open pull request for ${target.workspace}/${target.repoSlug}")
        return pullRequestClient.findOpenPullRequest(
            workspace = target.workspace,
            repoSlug = target.repoSlug,
            sourceBranch = sourceBranch,
            destinationBranch = destinationBranch,
            apiToken = target.apiToken
        )
    }

    /** Creates a pull request; a null target fails the create. */
    suspend fun createPullRequest(projectBasePath: String?, organizationUrl: String, request: NewPullRequest): CreatePullRequestResult {
        val target = resolveTarget(
            operation = "create a pull request",
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl
        ) ?: return CreatePullRequestResult.Failed()

        LOG.debug("Creating a pull request for ${target.workspace}/${target.repoSlug} (draft=${request.isDraft})")
        return pullRequestClient.createPullRequest(
            workspace = target.workspace,
            repoSlug = target.repoSlug,
            request = request,
            apiToken = target.apiToken
        )
    }

    /**
     * Bitbucket replaces the whole reviewer list on update, so the current reviewers are read again right before writing
     * to avoid dropping reviewers that someone else added since the view was loaded.
     */
    suspend fun updateReviewer(projectBasePath: String?, organizationUrl: String, pullRequestId: Long, change: ReviewerChange): PullRequestActionResult {
        val target = resolveTarget(
            operation = "update reviewers of pull request",
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl
        ) ?: return PullRequestActionResult.Failed()

        LOG.debug("Applying ${change::class.simpleName} reviewer change to pull request #$pullRequestId in ${target.workspace}/${target.repoSlug}")
        val details = pullRequestClient.getPullRequestDetails(
            workspace = target.workspace,
            repoSlug = target.repoSlug,
            pullRequestId = pullRequestId,
            currentUserUuid = null,
            apiToken = target.apiToken
        )
        if (details == null) {
            LOG.debug("Could not load the current reviewers of pull request #$pullRequestId before updating them")
            return PullRequestActionResult.Failed()
        }
        val currentUuids = details.reviewers.map { reviewer -> reviewer.uuid }
        val updatedUuids = change.applyTo(currentUuids)
        if (updatedUuids == currentUuids) {
            LOG.debug("Reviewers of pull request #$pullRequestId already reflect the requested change")
            return PullRequestActionResult.Success
        }
        return pullRequestClient.updateReviewers(
            workspace = target.workspace,
            repoSlug = target.repoSlug,
            pullRequestId = pullRequestId,
            title = details.summary.title,
            reviewerUuids = updatedUuids,
            apiToken = target.apiToken
        )
    }

    suspend fun updateDescription(
        projectBasePath: String?,
        organizationUrl: String,
        pullRequestId: Long,
        title: String,
        description: String
    ): PullRequestActionResult {
        val target = resolveTarget(
            operation = "update description",
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl
        ) ?: return PullRequestActionResult.Failed()

        LOG.debug("Applying description update to pull request #$pullRequestId in ${target.workspace}/${target.repoSlug}")
        return pullRequestClient.updatePullRequestDescription(
            workspace = target.workspace,
            repoSlug = target.repoSlug,
            pullRequestId = pullRequestId,
            title = title,
            description = description,
            apiToken = target.apiToken
        )
    }

    private suspend fun workspaceMembers(workspace: String, apiToken: String): WorkspaceMembers? {
        val tokenHash = apiToken.hashCode()
        cachedMembers?.takeIf { cached -> cached.workspace == workspace && cached.tokenHash == tokenHash }?.let { cached ->
            LOG.debug("Using ${cached.members.members.size} cached workspace members for $workspace")
            return cached.members
        }
        val members = pullRequestClient.listWorkspaceMembers(workspace = workspace, apiToken = apiToken) ?: return null
        cachedMembers = CachedMembers(workspace = workspace, tokenHash = tokenHash, members = members)
        return members
    }

    suspend fun pullRequestDetails(projectBasePath: String?, organizationUrl: String, pullRequestId: Long): PullRequestDetails? {
        val target = resolveTarget(
            operation = "load pull request details",
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl
        ) ?: return null

        LOG.debug("Loading details for pull request #$pullRequestId in ${target.workspace}/${target.repoSlug}")
        val currentUserUuid = currentUser(apiToken = target.apiToken)?.uuid
        if (currentUserUuid == null) {
            LOG.debug("Current user uuid is unavailable; approval state of pull request #$pullRequestId is treated as not approved")
        }
        return pullRequestClient.getPullRequestDetails(
            workspace = target.workspace,
            repoSlug = target.repoSlug,
            pullRequestId = pullRequestId,
            currentUserUuid = currentUserUuid,
            apiToken = target.apiToken
        )
    }

    /** Returns null when the commits could not be loaded; the pull request id is reported in the logs. */
    suspend fun pullRequestCommits(projectBasePath: String?, organizationUrl: String, pullRequestId: Long): PagedItems<PullRequestCommit>? {
        val target = resolveTarget(
            operation = "load pull request commits",
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl
        ) ?: return null

        LOG.debug("Loading commits for pull request #$pullRequestId in ${target.workspace}/${target.repoSlug}")
        val commits = pullRequestClient.listPullRequestCommits(
            workspace = target.workspace,
            repoSlug = target.repoSlug,
            pullRequestId = pullRequestId,
            apiToken = target.apiToken
        )
        LOG.debug("Loaded ${commits?.items?.size ?: 0} commit(s) for pull request #$pullRequestId in ${target.workspace}/${target.repoSlug}")
        return commits
    }

    /**
     * Loads the details of a single pull request addressed directly by [workspace] and [repoSlug], without deriving
     * them from an open project's base path. The Workspace tabs already carry the workspace and repository slug on
     * each row, so this avoids depending on an open project when re-checking whether a pull request is still open.
     * Returns null when the API token is unavailable or when Bitbucket returns no details.
     */
    suspend fun pullRequestDetailsFor(workspace: String, repoSlug: String, organizationUrl: String, pullRequestId: Long): PullRequestDetails? {
        val apiToken = credentialStore.getToken()
        if (apiToken == null) {
            LOG.info("Cannot load pull request details for $workspace/$repoSlug #$pullRequestId: no stored API token")
            return null
        }

        LOG.debug("Loading details for pull request #$pullRequestId in $workspace/$repoSlug (direct lookup, token present=${apiToken.isNotEmpty()})")
        val currentUserUuid = currentUser(apiToken = apiToken)?.uuid
        if (currentUserUuid == null) {
            LOG.debug("Current user uuid is unavailable; approval state of pull request #$pullRequestId is treated as not approved")
        }
        return pullRequestClient.getPullRequestDetails(
            workspace = workspace,
            repoSlug = repoSlug,
            pullRequestId = pullRequestId,
            currentUserUuid = currentUserUuid,
            apiToken = apiToken
        )
    }

    suspend fun performAction(
        projectBasePath: String?,
        organizationUrl: String,
        pullRequest: PullRequestSummary,
        action: PullRequestAction
    ): PullRequestActionResult {
        val target = resolveTarget(
            operation = "perform $action on pull request",
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl
        ) ?: return PullRequestActionResult.Failed()

        LOG.debug("Performing $action on pull request #${pullRequest.id} in ${target.workspace}/${target.repoSlug}")
        return pullRequestClient.performAction(
            workspace = target.workspace,
            repoSlug = target.repoSlug,
            pullRequest = pullRequest,
            action = action,
            apiToken = target.apiToken
        )
    }

    /**
     * Declines a pull request and, when requested, records the trimmed reason in a top-level comment.
     *
     * Bitbucket's decline endpoint does not accept a reason field, so the comment is deliberately a secondary action:
     * a comment failure never changes an already-successful decline into a failed action.
     */
    suspend fun declinePullRequest(
        projectBasePath: String?,
        organizationUrl: String,
        pullRequest: PullRequestSummary,
        reason: String
    ): PullRequestDeclineResult {
        val target = resolveTarget(
            operation = "decline pull request",
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl
        ) ?: return PullRequestDeclineResult.Failed(failure = PullRequestActionResult.Failed())

        val normalizedReason = reason.trim()
        LOG.info(
            "Declining pull request #${pullRequest.id} in ${target.workspace}/${target.repoSlug} " +
                "(reasonPresent=${normalizedReason.isNotEmpty()})"
        )
        val declineResult = pullRequestClient.performAction(
            workspace = target.workspace,
            repoSlug = target.repoSlug,
            pullRequest = pullRequest,
            action = PullRequestAction.Decline,
            apiToken = target.apiToken
        )
        if (declineResult is PullRequestActionResult.Failed) {
            return PullRequestDeclineResult.Failed(failure = declineResult)
        }

        if (normalizedReason.isEmpty()) {
            return PullRequestDeclineResult.Success()
        }

        val commentResult = pullRequestClient.createComment(
            workspace = target.workspace,
            repoSlug = target.repoSlug,
            pullRequestId = pullRequest.id,
            content = declinedCommentBody(reason = normalizedReason),
            apiToken = target.apiToken,
            parentCommentId = null
        )
        return if (commentResult is PullRequestActionResult.Failed) {
            LOG.warn(
                "Declined pull request #${pullRequest.id}, but could not post the decline comment " +
                    "in ${target.workspace}/${target.repoSlug}",
                commentResult.cause
            )
            PullRequestDeclineResult.Success(commentFailure = commentResult)
        } else {
            PullRequestDeclineResult.Success()
        }
    }

    suspend fun currentUserUuid(projectBasePath: String?, organizationUrl: String): String? {
        val target = resolveTarget(
            operation = "load the current user uuid",
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl
        ) ?: return null
        return currentUser(apiToken = target.apiToken)?.uuid
    }

    suspend fun currentUserAvatarUrl(projectBasePath: String?, organizationUrl: String): String? {
        val target = resolveTarget(
            operation = "load the current user avatar",
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl
        ) ?: return null
        return currentUser(apiToken = target.apiToken)?.avatarUrl
    }

    suspend fun listPullRequestComments(projectBasePath: String?, organizationUrl: String, pullRequestId: Long): PagedItems<PullRequestComment>? {
        val target = resolveTarget(
            operation = "list comments of pull request",
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl
        ) ?: return null

        LOG.debug("Listing comments for pull request #$pullRequestId in ${target.workspace}/${target.repoSlug}")
        return pullRequestClient.listComments(
            workspace = target.workspace,
            repoSlug = target.repoSlug,
            pullRequestId = pullRequestId,
            apiToken = target.apiToken
        )
    }

    suspend fun addComment(projectBasePath: String?, organizationUrl: String, pullRequestId: Long, content: String): PullRequestActionResult {
        return applyCommentChange(
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl,
            pullRequestId = pullRequestId,
            change = CommentChange.Create(content = content)
        )
    }

    suspend fun applyCommentChange(projectBasePath: String?, organizationUrl: String, pullRequestId: Long, change: CommentChange): PullRequestActionResult {
        val target = resolveTarget(
            operation = commentChangeOperation(change),
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl
        ) ?: return PullRequestActionResult.Failed()
        val inlinePathToCheck = when (change) {
            is CommentChange.CreateFileScoped -> change.path
            is CommentChange.CreateInlineLine -> change.path
            else -> null
        }
        if (inlinePathToCheck != null && inlinePathToCheck.isBlank()) {
            LOG.info("Cannot comment on pull request #$pullRequestId: the file path is empty")
            return PullRequestActionResult.Failed(message = "File path is empty")
        }

        LOG.debug(
            "Applying ${change::class.simpleName} ${commentChangeTarget(change)} of pull request #$pullRequestId in ${target.workspace}/${target.repoSlug}"
        )
        return when (change) {
            is CommentChange.Create -> createComment(target = target, pullRequestId = pullRequestId, content = change.content, parentCommentId = null)

            is CommentChange.CreateFileScoped -> createComment(
                target = target,
                pullRequestId = pullRequestId,
                content = change.content,
                parentCommentId = null,
                inlinePath = change.path
            )

            is CommentChange.CreateInlineLine -> createComment(
                target = target,
                pullRequestId = pullRequestId,
                content = change.content,
                parentCommentId = null,
                inlinePath = change.path,
                inlineLine = change.line,
                inlineSide = change.side
            )

            is CommentChange.Reply -> createComment(
                target = target,
                pullRequestId = pullRequestId,
                content = change.content,
                parentCommentId = change.commentId
            )

            is CommentChange.Edit -> pullRequestClient.updateComment(
                workspace = target.workspace,
                repoSlug = target.repoSlug,
                pullRequestId = pullRequestId,
                commentId = change.commentId,
                content = change.content,
                apiToken = target.apiToken
            )

            is CommentChange.Resolve -> pullRequestClient.resolveCommentThread(
                workspace = target.workspace,
                repoSlug = target.repoSlug,
                pullRequestId = pullRequestId,
                commentId = change.commentId,
                apiToken = target.apiToken
            )

            is CommentChange.Unresolve -> pullRequestClient.reopenCommentThread(
                workspace = target.workspace,
                repoSlug = target.repoSlug,
                pullRequestId = pullRequestId,
                commentId = change.commentId,
                apiToken = target.apiToken
            )

            is CommentChange.Delete -> pullRequestClient.deleteComment(
                workspace = target.workspace,
                repoSlug = target.repoSlug,
                pullRequestId = pullRequestId,
                commentId = change.commentId,
                apiToken = target.apiToken
            )
        }
    }

    private suspend fun createComment(
        target: RepositoryTarget,
        pullRequestId: Long,
        content: String,
        parentCommentId: Long?,
        inlinePath: String? = null,
        inlineLine: Int? = null,
        inlineSide: InlineCommentSide = InlineCommentSide.AFTER
    ): PullRequestActionResult {
        return pullRequestClient.createComment(
            workspace = target.workspace,
            repoSlug = target.repoSlug,
            pullRequestId = pullRequestId,
            content = content,
            apiToken = target.apiToken,
            parentCommentId = parentCommentId,
            inlinePath = inlinePath,
            inlineLine = inlineLine,
            inlineSide = inlineSide
        )
    }

    /** Names the failure so the early-return logs read as the action the user attempted. */
    private fun commentChangeOperation(change: CommentChange): String {
        return when (change) {
            is CommentChange.Create -> "comment on pull request"
            is CommentChange.CreateFileScoped -> "comment on a pull request file"
            is CommentChange.CreateInlineLine -> "comment on a pull request line"
            is CommentChange.Reply -> "reply to a pull request comment"
            is CommentChange.Edit -> "edit a pull request comment"
            is CommentChange.Resolve -> "resolve a pull request comment"
            is CommentChange.Unresolve -> "unresolve a pull request comment"
            is CommentChange.Delete -> "delete a pull request comment"
        }
    }

    private fun commentChangeTarget(change: CommentChange): String {
        val commentId = change.commentId ?: return "a new comment"
        return "to comment $commentId"
    }

    private suspend fun currentUser(apiToken: String): CurrentUser? {
        val tokenHash = apiToken.hashCode()
        cachedCurrentUser?.takeIf { cached -> cached.tokenHash == tokenHash }?.let { cached -> return cached.user }
        val user = pullRequestClient.getCurrentUser(apiToken = apiToken) ?: return null
        cachedCurrentUser = CachedCurrentUser(tokenHash = tokenHash, user = user)
        return user
    }

    /** Keyed by a hash of the token so that a token change fetches the user again. */
    private data class CachedCurrentUser(val tokenHash: Int, val user: CurrentUser)

    /** Keyed by workspace and a hash of the token so that a configuration change fetches the members again. */
    private data class CachedMembers(val workspace: String, val tokenHash: Int, val members: WorkspaceMembers)

    private suspend fun resolveTarget(operation: String, projectBasePath: String?, organizationUrl: String): RepositoryTarget? {
        val configuredWorkspace = BitbucketRepositoryUrls.workspaceFromOrganizationUrl(organizationUrl)
        // A confirmed worktree gets its slug from its known folder layout; other checkouts use the git remote first.
        val resolved = withContext(Dispatchers.IO) {
            ProjectRepositoryResolver.resolve(
                projectBasePath = projectBasePath,
                configuredWorkspace = configuredWorkspace,
                readRemoteUrl = readRemoteUrl,
                workTreeDirectory = configurationService?.workTreeDirectoryOrNull()
            )
        }
        if (resolved == null) {
            LOG.info("Cannot $operation: no repository resolved for project base path $projectBasePath")
            return null
        }

        val apiToken = credentialStore.getToken()
        if (apiToken == null) {
            LOG.info("Cannot $operation: no stored API token")
            return null
        }

        return RepositoryTarget(workspace = resolved.workspace, repoSlug = resolved.repoSlug, apiToken = apiToken)
    }

    private data class RepositoryTarget(val workspace: String, val repoSlug: String, val apiToken: String) {
        override fun toString(): String {
            return "RepositoryTarget(workspace=$workspace, repoSlug=$repoSlug)"
        }
    }

    companion object {
        private val LOG = logger<BbCloudOpenPullRequestService>()
    }
}