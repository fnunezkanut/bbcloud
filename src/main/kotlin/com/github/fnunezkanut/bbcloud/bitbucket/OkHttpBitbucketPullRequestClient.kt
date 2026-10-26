package com.github.fnunezkanut.bbcloud.bitbucket

import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OkHttpBitbucketPullRequestClient(private val httpClient: OkHttpClient = sharedHttpClient) : BitbucketPullRequestClient {
    override suspend fun listRecentPullRequests(workspace: String, repoSlug: String, apiToken: String): PullRequestListResult {
        return withContext(Dispatchers.IO) {
            val url = BitbucketPullRequestResponseParser.buildListPullRequestsUrl(
                workspace = workspace,
                repoSlug = repoSlug
            )
            when (val pageResult = fetchPage(url = url, apiToken = apiToken)) {
                is PageFetchResult.Success -> {
                    val pullRequests = pageResult.page.pullRequests
                    LOG.debug("Parsed ${pullRequests.size} pull requests for $workspace/$repoSlug")
                    PullRequestListResult.Success(pullRequests)
                }
                is PageFetchResult.Failed -> {
                    pageResult.result
                }
            }
        }
    }

    override suspend fun listAllOpenPullRequests(workspace: String, repoSlug: String, apiToken: String): PullRequestListResult {
        return withContext(Dispatchers.IO) {
            val pullRequests = mutableListOf<PullRequestSummary>()
            var nextUrl: String? = BitbucketPullRequestResponseParser.buildListOpenPullRequestsUrl(
                workspace = workspace,
                repoSlug = repoSlug
            )
            var pagesFetched = 0
            while (nextUrl != null) {
                if (pagesFetched >= MAX_OPEN_PR_PAGES) {
                    LOG.warn("Open pull-request listing for $workspace/$repoSlug was truncated after $pagesFetched pages")
                    return@withContext PullRequestListResult.Failed()
                }
                when (val pageResult = fetchPage(url = nextUrl, apiToken = apiToken)) {
                    is PageFetchResult.Success -> {
                        pullRequests += pageResult.page.pullRequests
                        nextUrl = pageResult.page.nextUrl
                        pagesFetched++
                    }
                    is PageFetchResult.Failed -> {
                        return@withContext pageResult.result
                    }
                }
            }
            LOG.debug("Parsed ${pullRequests.size} open pull requests across $pagesFetched pages for $workspace/$repoSlug")
            PullRequestListResult.Success(pullRequests)
        }
    }

    override suspend fun countChangedFiles(workspace: String, repoSlug: String, pullRequestId: Long, apiToken: String): ItemCount? {
        return withContext(Dispatchers.IO) {
            val url = BitbucketPullRequestResponseParser.buildDiffstatUrl(
                workspace = workspace,
                repoSlug = repoSlug,
                pullRequestId = pullRequestId
            )
            countPaginated(firstUrl = url, apiToken = apiToken, maxPages = MAX_COUNT_PAGES)
        }
    }

    override suspend fun countCommits(workspace: String, repoSlug: String, pullRequestId: Long, apiToken: String): ItemCount? {
        return withContext(Dispatchers.IO) {
            val url = BitbucketPullRequestResponseParser.buildCommitsUrl(
                workspace = workspace,
                repoSlug = repoSlug,
                pullRequestId = pullRequestId
            )
            countPaginated(firstUrl = url, apiToken = apiToken, maxPages = MAX_COUNT_PAGES)
        }
    }

    override suspend fun countCommitsBehind(
        workspace: String,
        repoSlug: String,
        sourceBranch: String,
        destinationBranch: String,
        apiToken: String
    ): ItemCount? {
        return withContext(Dispatchers.IO) {
            val url = BitbucketPullRequestResponseParser.buildCommitsBehindUrl(
                workspace = workspace,
                repoSlug = repoSlug,
                sourceBranch = sourceBranch,
                destinationBranch = destinationBranch
            )
            countPaginated(firstUrl = url, apiToken = apiToken, maxPages = MAX_COUNT_PAGES)
        }
    }

    override suspend fun listBuildStatuses(workspace: String, repoSlug: String, pullRequestId: Long, apiToken: String): List<PullRequestBuildStatus>? {
        return withContext(Dispatchers.IO) {
            val firstUrl = BitbucketPullRequestResponseParser.buildStatusesUrl(
                workspace = workspace,
                repoSlug = repoSlug,
                pullRequestId = pullRequestId
            )
            val statuses = mutableListOf<PullRequestBuildStatus>()
            var nextUrl: String? = firstUrl
            var pagesFetched = 0
            while (nextUrl != null && pagesFetched < MAX_COUNT_PAGES) {
                val page = fetchBuildStatusPage(url = nextUrl, apiToken = apiToken) ?: return@withContext null
                pagesFetched++
                statuses += page.statuses
                nextUrl = page.nextUrl
            }
            LOG.debug("Parsed ${statuses.size} build statuses across $pagesFetched pages for pull request #$pullRequestId")
            statuses
        }
    }

    override suspend fun getCurrentUser(apiToken: String): CurrentUser? {
        return withContext(Dispatchers.IO) {
            val url = BitbucketPullRequestResponseParser.buildCurrentUserUrl()
            fetchBody(url = url, apiToken = apiToken)?.let { body ->
                BitbucketPullRequestResponseParser.parseCurrentUser(body)
            }
        }
    }

    override suspend fun listOpenPullRequestsReviewedBy(
        workspace: String,
        repoSlug: String,
        reviewerUuid: String,
        pageLen: Int,
        apiToken: String
    ): ReviewerQueueQueryResult {
        return withContext(Dispatchers.IO) {
            val url = BitbucketReviewQueueResponseParser.buildReviewingPullRequestsUrl(
                workspace = workspace,
                repoSlug = repoSlug,
                reviewerUuid = reviewerUuid,
                pageLen = pageLen
            )
            fetchReviewerQueue(url = url, apiToken = apiToken)
        }
    }

    override suspend fun listOpenAuthoredPullRequestsInWorkspace(workspace: String, selectedUserUuid: String, apiToken: String): AuthoredPullRequestPage? {
        return withContext(Dispatchers.IO) {
            val url = BitbucketWorkspacePullRequestResponseParser.buildListAuthoredPullRequestsUrl(
                workspace = workspace,
                selectedUserUuid = selectedUserUuid,
                pageLen = MAX_LISTED_PULL_REQUESTS
            )
            val body = fetchBody(url = url, apiToken = apiToken) ?: return@withContext null
            val page = runCatching { BitbucketWorkspacePullRequestResponseParser.parseAuthoredPullRequestPage(body) }
                .onFailure { exception -> LOG.debug("Could not parse the pull requests opened in $workspace", exception) }
                .getOrNull() ?: return@withContext null
            LOG.debug("Parsed ${page.entries.size} pull requests opened in $workspace (truncated=${page.isTruncated})")
            page
        }
    }

    override suspend fun getPullRequestDetails(
        workspace: String,
        repoSlug: String,
        pullRequestId: Long,
        currentUserUuid: String?,
        apiToken: String
    ): PullRequestDetails? {
        return withContext(Dispatchers.IO) {
            val url = BitbucketPullRequestResponseParser.buildPullRequestUrl(
                workspace = workspace,
                repoSlug = repoSlug,
                pullRequestId = pullRequestId
            )
            fetchBody(url = url, apiToken = apiToken)?.let { body ->
                BitbucketPullRequestResponseParser.parsePullRequestDetails(responseBody = body, currentUserUuid = currentUserUuid)
            }
        }
    }

    override suspend fun performAction(
        workspace: String,
        repoSlug: String,
        pullRequest: PullRequestSummary,
        action: PullRequestAction,
        apiToken: String
    ): PullRequestActionResult {
        return withContext(Dispatchers.IO) {
            val request = buildActionRequest(
                workspace = workspace,
                repoSlug = repoSlug,
                pullRequest = pullRequest,
                action = action,
                apiToken = apiToken
            )
            executeAction(request = request)
        }
    }

    override suspend fun listMergeChecks(workspace: String, repoSlug: String, pullRequestId: Long, apiToken: String): List<PullRequestMergeCheck>? {
        return withContext(Dispatchers.IO) {
            val url = BitbucketPullRequestResponseParser.buildMergeabilityChecksUrl(
                workspace = workspace,
                repoSlug = repoSlug,
                pullRequestId = pullRequestId
            )
            val body = fetchBody(url = url, apiToken = apiToken) ?: return@withContext null
            parseOrNull(url = url) { BitbucketPullRequestResponseParser.parseMergeabilityChecks(body) }?.also { checks ->
                LOG.debug("Parsed ${checks.size} merge checks for pull request #$pullRequestId: kinds=${checks.map { check -> check.kind }}")
            }
        }
    }

    override suspend fun listWorkspaceMembers(workspace: String, apiToken: String): WorkspaceMembers? {
        return withContext(Dispatchers.IO) {
            val members = mutableListOf<ReviewerCandidate>()
            var nextUrl: String? = BitbucketPullRequestResponseParser.buildWorkspaceMembersUrl(workspace)
            var pagesFetched = 0
            while (nextUrl != null && pagesFetched < MAX_MEMBER_PAGES) {
                val url: String = nextUrl
                val body = fetchBody(url = url, apiToken = apiToken) ?: return@withContext null
                val page = parseOrNull(url = url) { BitbucketPullRequestResponseParser.parseWorkspaceMembersPage(body) } ?: return@withContext null
                pagesFetched++
                members += page.members
                nextUrl = page.nextUrl
            }
            val isTruncated = nextUrl != null
            LOG.debug("Parsed ${members.size} workspace members across $pagesFetched pages for $workspace (truncated=$isTruncated)")
            WorkspaceMembers(members = members, isTruncated = isTruncated)
        }
    }

    override suspend fun listEffectiveDefaultReviewerUuids(workspace: String, repoSlug: String, apiToken: String): Set<String>? {
        return withContext(Dispatchers.IO) {
            val url = BitbucketPullRequestResponseParser.buildEffectiveDefaultReviewersUrl(workspace = workspace, repoSlug = repoSlug)
            val body = fetchBody(url = url, apiToken = apiToken) ?: return@withContext null
            parseOrNull(url = url) { BitbucketPullRequestResponseParser.parseDefaultReviewerUuids(body) }?.also { uuids ->
                LOG.debug("Parsed ${uuids.size} effective default reviewers for $workspace/$repoSlug")
            }
        }
    }

    override suspend fun updateReviewers(
        workspace: String,
        repoSlug: String,
        pullRequestId: Long,
        title: String,
        reviewerUuids: List<String>,
        apiToken: String
    ): PullRequestActionResult {
        return withContext(Dispatchers.IO) {
            val body = BitbucketPullRequestResponseParser.buildReviewersUpdateBody(title = title, reviewerUuids = reviewerUuids)
            val request = Request.Builder()
                .header("Authorization", "Bearer $apiToken")
                .url(BitbucketPullRequestResponseParser.buildPullRequestUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequestId))
                .put(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeAction(request = request)
        }
    }

    override suspend fun updatePullRequestDescription(
        workspace: String,
        repoSlug: String,
        pullRequestId: Long,
        title: String,
        description: String,
        apiToken: String
    ): PullRequestActionResult {
        return withContext(Dispatchers.IO) {
            val body = BitbucketPullRequestResponseParser.buildDescriptionUpdateBody(title = title, description = description)
            val request = Request.Builder()
                .header("Authorization", "Bearer $apiToken")
                .url(BitbucketPullRequestResponseParser.buildPullRequestUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequestId))
                .put(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeAction(request = request)
        }
    }

    override suspend fun listTasks(workspace: String, repoSlug: String, pullRequestId: Long, apiToken: String): List<PullRequestTask>? {
        return withContext(Dispatchers.IO) {
            val firstUrl = BitbucketPullRequestResponseParser.buildTasksUrl(
                workspace = workspace,
                repoSlug = repoSlug,
                pullRequestId = pullRequestId
            )
            val tasks = fetchAllPages(firstUrl = firstUrl, apiToken = apiToken) { body ->
                val page = BitbucketPullRequestResponseParser.parseTasksPage(body)
                page.tasks to page.nextUrl
            } ?: return@withContext null
            LOG.debug("Parsed ${tasks.items.size} tasks for pull request #$pullRequestId (truncated=${tasks.isTruncated})")
            tasks.items.sortedBy { task -> task.id }
        }
    }

    override suspend fun listActivity(workspace: String, repoSlug: String, pullRequestId: Long, apiToken: String): PagedItems<PullRequestActivityItem>? {
        return withContext(Dispatchers.IO) {
            val firstUrl = BitbucketPullRequestResponseParser.buildActivityUrl(
                workspace = workspace,
                repoSlug = repoSlug,
                pullRequestId = pullRequestId
            )
            fetchAllPages(firstUrl = firstUrl, apiToken = apiToken) { body ->
                val page = BitbucketPullRequestResponseParser.parseActivityPage(body)
                page.items to page.nextUrl
            }?.also { activity ->
                LOG.debug("Parsed ${activity.items.size} activity entries for pull request #$pullRequestId (truncated=${activity.isTruncated})")
            }
        }
    }

    override suspend fun listComments(workspace: String, repoSlug: String, pullRequestId: Long, apiToken: String): PagedItems<PullRequestComment>? {
        return withContext(Dispatchers.IO) {
            val firstUrl = BitbucketPullRequestResponseParser.buildCommentsUrl(
                workspace = workspace,
                repoSlug = repoSlug,
                pullRequestId = pullRequestId
            )
            fetchAllPages(firstUrl = firstUrl, apiToken = apiToken) { body ->
                val page = BitbucketPullRequestResponseParser.parseCommentsPage(body)
                page.comments to page.nextUrl
            }?.also { comments ->
                LOG.debug("Parsed ${comments.items.size} comments for pull request #$pullRequestId (truncated=${comments.isTruncated})")
            }
        }
    }

    override suspend fun listPullRequestCommits(workspace: String, repoSlug: String, pullRequestId: Long, apiToken: String): PagedItems<PullRequestCommit>? {
        return withContext(Dispatchers.IO) {
            val firstUrl = BitbucketPullRequestResponseParser.buildPullRequestCommitsUrl(
                workspace = workspace,
                repoSlug = repoSlug,
                pullRequestId = pullRequestId
            )
            fetchAllPages(firstUrl = firstUrl, apiToken = apiToken) { body ->
                val page = BitbucketPullRequestResponseParser.parseCommitsPage(body)
                page.commits to page.nextUrl
            }?.also { commits ->
                LOG.debug("Parsed ${commits.items.size} commits for pull request #$pullRequestId (truncated=${commits.isTruncated})")
            }
        }
    }

    override suspend fun listBranches(workspace: String, repoSlug: String, apiToken: String): RepositoryBranches? {
        return withContext(Dispatchers.IO) {
            val firstUrl = BitbucketPullRequestResponseParser.buildBranchesUrl(workspace = workspace, repoSlug = repoSlug)
            val names = fetchAllPages(firstUrl = firstUrl, apiToken = apiToken) { body ->
                val page = BitbucketPullRequestResponseParser.parseBranchNamesPage(body)
                page.names to page.nextUrl
            } ?: return@withContext null
            LOG.debug("Parsed ${names.items.size} branches for $workspace/$repoSlug (truncated=${names.isTruncated})")
            RepositoryBranches(names = names.items.distinct(), isTruncated = names.isTruncated)
        }
    }

    override suspend fun getMainBranchName(workspace: String, repoSlug: String, apiToken: String): String? {
        return withContext(Dispatchers.IO) {
            val url = BitbucketPullRequestResponseParser.buildRepositoryMainBranchUrl(workspace = workspace, repoSlug = repoSlug)
            val body = fetchBody(url = url, apiToken = apiToken) ?: return@withContext null
            parseOrNull(url = url) { BitbucketPullRequestResponseParser.parseMainBranchName(body) }
        }
    }

    override suspend fun findOpenPullRequest(
        workspace: String,
        repoSlug: String,
        sourceBranch: String,
        destinationBranch: String,
        apiToken: String
    ): ExistingPullRequestLookupResult {
        return withContext(Dispatchers.IO) {
            val url = BitbucketPullRequestResponseParser.buildOpenPullRequestForBranchesUrl(
                workspace = workspace,
                repoSlug = repoSlug,
                sourceBranch = sourceBranch,
                destinationBranch = destinationBranch
            )
            val body = fetchBody(url = url, apiToken = apiToken) ?: return@withContext ExistingPullRequestLookupResult.Failed()
            // A failed parse is distinct from an empty page, so parse directly instead of through parseOrNull, which
            // would collapse both to null.
            val page = try {
                BitbucketPullRequestResponseParser.parsePullRequestsPage(body)
            } catch (exception: Exception) {
                LOG.debug("Could not parse the existing-pull-request lookup response of GET $url", exception)
                return@withContext ExistingPullRequestLookupResult.Failed(cause = exception)
            }
            val summary = page.pullRequests.firstOrNull()
            if (summary == null) {
                LOG.debug("No open pull request found for the requested branches in $workspace/$repoSlug")
                ExistingPullRequestLookupResult.None
            } else {
                LOG.debug("Found open pull request #${summary.id} for the requested branches in $workspace/$repoSlug")
                ExistingPullRequestLookupResult.Found(summary = summary)
            }
        }
    }

    override suspend fun createPullRequest(workspace: String, repoSlug: String, request: NewPullRequest, apiToken: String): CreatePullRequestResult {
        return withContext(Dispatchers.IO) {
            val body = BitbucketPullRequestResponseParser.buildCreatePullRequestBody(request = request)
            val httpRequest = Request.Builder()
                .header("Authorization", "Bearer $apiToken")
                .url(BitbucketPullRequestResponseParser.buildCreatePullRequestUrl(workspace = workspace, repoSlug = repoSlug))
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeCreate(request = httpRequest)
        }
    }

    private fun executeCreate(request: Request): CreatePullRequestResult {
        val description = "${request.method} ${request.url}"
        LOG.debug(description)
        val startNanos = System.nanoTime()
        return try {
            httpClient.newCall(request).execute().use { response ->
                LOG.debug("$description -> ${response.code} in ${elapsedMillis(startNanos = startNanos)} ms")
                val responseBody = response.body.string()
                if (response.isSuccessful) {
                    val summary = runCatching {
                        BitbucketPullRequestResponseParser.parseCreatedPullRequest(responseBody)
                    }.getOrNull()
                    if (summary == null) {
                        LOG.debug("Created pull request but could not parse the response")
                        CreatePullRequestResult.Failed(message = null)
                    } else {
                        CreatePullRequestResult.Created(summary = summary)
                    }
                } else {
                    LOG.warn("Unexpected HTTP status ${response.code} for $description${BitbucketHttpErrors.describe(response)}")
                    val message = runCatching {
                        BitbucketPullRequestResponseParser.parseErrorMessage(responseBody)
                    }.getOrNull()
                    CreatePullRequestResult.Failed(message = message)
                }
            }
        } catch (exception: Exception) {
            LOG.debug("$description failed after ${elapsedMillis(startNanos = startNanos)} ms", exception)
            CreatePullRequestResult.Failed(cause = exception)
        }
    }

    /** [parsePage] returns the items of one page and the next page URL; returns null when any page fails to load or parse. */
    private fun <T> fetchAllPages(firstUrl: String, apiToken: String, parsePage: (String) -> Pair<List<T>, String?>): PagedItems<T>? {
        val items = mutableListOf<T>()
        var nextUrl: String? = firstUrl
        var pagesFetched = 0
        while (nextUrl != null && pagesFetched < MAX_COUNT_PAGES) {
            val url: String = nextUrl
            val body = fetchBody(url = url, apiToken = apiToken) ?: return null
            val (pageItems, pageNextUrl) = parseOrNull(url = url) { parsePage(body) } ?: return null
            pagesFetched++
            items += pageItems
            nextUrl = pageNextUrl
        }
        return PagedItems(items = items, isTruncated = nextUrl != null)
    }

    override suspend fun createTask(workspace: String, repoSlug: String, pullRequestId: Long, content: String, apiToken: String): PullRequestActionResult {
        return withContext(Dispatchers.IO) {
            val body = BitbucketPullRequestResponseParser.buildTaskCreateBody(content)
            val request = Request.Builder()
                .header("Authorization", "Bearer $apiToken")
                .url(BitbucketPullRequestResponseParser.buildTasksUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequestId))
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeAction(request = request)
        }
    }

    override suspend fun createComment(
        workspace: String,
        repoSlug: String,
        pullRequestId: Long,
        content: String,
        apiToken: String,
        parentCommentId: Long?,
        inlinePath: String?,
        inlineLine: Int?,
        inlineSide: InlineCommentSide
    ): PullRequestActionResult {
        return withContext(Dispatchers.IO) {
            val body = BitbucketPullRequestResponseParser.buildCommentCreateBody(
                content = content,
                parentCommentId = parentCommentId,
                inlinePath = inlinePath,
                inlineLine = inlineLine,
                inlineSide = inlineSide
            )
            val request = Request.Builder()
                .header("Authorization", "Bearer $apiToken")
                .url(BitbucketPullRequestResponseParser.buildCommentCreateUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequestId))
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeAction(request = request)
        }
    }

    override suspend fun updateComment(
        workspace: String,
        repoSlug: String,
        pullRequestId: Long,
        commentId: Long,
        content: String,
        apiToken: String
    ): PullRequestActionResult {
        return withContext(Dispatchers.IO) {
            val body = BitbucketPullRequestResponseParser.buildCommentUpdateBody(content = content)
            val request = Request.Builder()
                .header("Authorization", "Bearer $apiToken")
                .url(
                    BitbucketPullRequestResponseParser.buildCommentUrl(
                        workspace = workspace,
                        repoSlug = repoSlug,
                        pullRequestId = pullRequestId,
                        commentId = commentId
                    )
                )
                .put(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeAction(request = request)
        }
    }

    override suspend fun resolveCommentThread(
        workspace: String,
        repoSlug: String,
        pullRequestId: Long,
        commentId: Long,
        apiToken: String
    ): PullRequestActionResult {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .header("Authorization", "Bearer $apiToken")
                .url(
                    BitbucketPullRequestResponseParser.buildCommentResolveUrl(
                        workspace = workspace,
                        repoSlug = repoSlug,
                        pullRequestId = pullRequestId,
                        commentId = commentId
                    )
                )
                .post(EMPTY_JSON_BODY.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeAction(request = request)
        }
    }

    override suspend fun reopenCommentThread(
        workspace: String,
        repoSlug: String,
        pullRequestId: Long,
        commentId: Long,
        apiToken: String
    ): PullRequestActionResult {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .header("Authorization", "Bearer $apiToken")
                .url(
                    BitbucketPullRequestResponseParser.buildCommentResolveUrl(
                        workspace = workspace,
                        repoSlug = repoSlug,
                        pullRequestId = pullRequestId,
                        commentId = commentId
                    )
                )
                .delete()
                .build()
            executeAction(request = request)
        }
    }

    override suspend fun deleteComment(workspace: String, repoSlug: String, pullRequestId: Long, commentId: Long, apiToken: String): PullRequestActionResult {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .header("Authorization", "Bearer $apiToken")
                .url(
                    BitbucketPullRequestResponseParser.buildCommentUrl(
                        workspace = workspace,
                        repoSlug = repoSlug,
                        pullRequestId = pullRequestId,
                        commentId = commentId
                    )
                )
                .delete()
                .build()
            executeAction(request = request)
        }
    }

    override suspend fun updateTask(
        workspace: String,
        repoSlug: String,
        pullRequestId: Long,
        taskId: Long,
        content: String?,
        resolved: Boolean?,
        apiToken: String
    ): PullRequestActionResult {
        return withContext(Dispatchers.IO) {
            val body = BitbucketPullRequestResponseParser.buildTaskUpdateBody(content = content, resolved = resolved)
            val request = Request.Builder()
                .header("Authorization", "Bearer $apiToken")
                .url(
                    BitbucketPullRequestResponseParser.buildTaskUrl(
                        workspace = workspace,
                        repoSlug = repoSlug,
                        pullRequestId = pullRequestId,
                        taskId = taskId
                    )
                )
                .put(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeAction(request = request)
        }
    }

    override suspend fun deleteTask(workspace: String, repoSlug: String, pullRequestId: Long, taskId: Long, apiToken: String): PullRequestActionResult {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .header("Authorization", "Bearer $apiToken")
                .url(
                    BitbucketPullRequestResponseParser.buildTaskUrl(
                        workspace = workspace,
                        repoSlug = repoSlug,
                        pullRequestId = pullRequestId,
                        taskId = taskId
                    )
                )
                .delete()
                .build()
            executeAction(request = request)
        }
    }

    private fun <T> parseOrNull(url: String, parse: () -> T): T? {
        return try {
            parse()
        } catch (exception: Exception) {
            LOG.debug("Could not parse the response of GET $url", exception)
            null
        }
    }

    private fun buildActionRequest(workspace: String, repoSlug: String, pullRequest: PullRequestSummary, action: PullRequestAction, apiToken: String): Request {
        val builder = Request.Builder().header("Authorization", "Bearer $apiToken")
        return when (action) {
            PullRequestAction.Merge -> {
                builder
                    .url(BitbucketPullRequestResponseParser.buildMergeUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequest.id))
                    .post(EMPTY_JSON_BODY.toRequestBody(JSON_MEDIA_TYPE))
            }
            PullRequestAction.Approve -> {
                builder
                    .url(BitbucketPullRequestResponseParser.buildApproveUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequest.id))
                    .post(EMPTY_JSON_BODY.toRequestBody(JSON_MEDIA_TYPE))
            }
            PullRequestAction.Unapprove -> {
                builder
                    .url(BitbucketPullRequestResponseParser.buildApproveUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequest.id))
                    .delete()
            }
            PullRequestAction.MarkAsDraft, PullRequestAction.MarkAsReady -> {
                val body = BitbucketPullRequestResponseParser.buildDraftUpdateBody(
                    title = pullRequest.title,
                    isDraft = action == PullRequestAction.MarkAsDraft
                )
                builder
                    .url(BitbucketPullRequestResponseParser.buildPullRequestUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequest.id))
                    .put(body.toRequestBody(JSON_MEDIA_TYPE))
            }
            PullRequestAction.Decline -> {
                builder
                    .url(BitbucketPullRequestResponseParser.buildDeclineUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequest.id))
                    .post(EMPTY_JSON_BODY.toRequestBody(JSON_MEDIA_TYPE))
            }
        }.build()
    }

    private fun executeAction(request: Request): PullRequestActionResult {
        val description = "${request.method} ${request.url}"
        LOG.debug(description)
        val startNanos = System.nanoTime()
        return try {
            httpClient.newCall(request).execute().use { response ->
                LOG.debug("$description -> ${response.code} in ${elapsedMillis(startNanos = startNanos)} ms")
                if (response.isSuccessful) {
                    PullRequestActionResult.Success
                } else {
                    LOG.warn("Unexpected HTTP status ${response.code} for $description${BitbucketHttpErrors.describe(response)}")
                    val message = runCatching {
                        BitbucketPullRequestResponseParser.parseErrorMessage(response.body.string())
                    }.getOrNull()
                    PullRequestActionResult.Failed(message = message)
                }
            }
        } catch (exception: Exception) {
            LOG.debug("$description failed after ${elapsedMillis(startNanos = startNanos)} ms", exception)
            PullRequestActionResult.Failed(cause = exception)
        }
    }

    private fun fetchBody(url: String, apiToken: String): String? {
        val request = buildGetRequest(url = url, apiToken = apiToken)
        LOG.debug("GET $url")
        val startNanos = System.nanoTime()
        return try {
            httpClient.newCall(request).execute().use { response ->
                LOG.debug("GET $url -> ${response.code} in ${elapsedMillis(startNanos = startNanos)} ms")
                if (!response.isSuccessful) {
                    LOG.warn("Unexpected HTTP status ${response.code} for GET $url${BitbucketHttpErrors.describe(response)}")
                    return null
                }
                response.body.string().takeIf { it.isNotBlank() }
            }
        } catch (exception: Exception) {
            LOG.debug("GET $url failed after ${elapsedMillis(startNanos = startNanos)} ms", exception)
            null
        }
    }

    private fun fetchReviewerQueue(url: String, apiToken: String): ReviewerQueueQueryResult {
        val request = buildGetRequest(url = url, apiToken = apiToken)
        LOG.debug("GET $url")
        val startNanos = System.nanoTime()
        return try {
            httpClient.newCall(request).execute().use { response ->
                LOG.debug("GET $url -> ${response.code} in ${elapsedMillis(startNanos = startNanos)} ms")
                when (response.code) {
                    in 200..299 -> {
                        val body = response.body.string()
                        if (body.isBlank()) {
                            LOG.debug("GET $url returned an empty body")
                            return ReviewerQueueQueryResult.Listed(emptyList())
                        }
                        val pullRequests = runCatching { BitbucketPullRequestResponseParser.parsePullRequests(body) }
                            .onFailure { exception -> LOG.debug("Could not parse the review queue of $url", exception) }
                            .getOrNull()
                        if (pullRequests == null) {
                            return ReviewerQueueQueryResult.Failed()
                        }
                        LOG.debug("Parsed ${pullRequests.size} reviewing pull requests for $url")
                        ReviewerQueueQueryResult.Listed(pullRequests)
                    }
                    401 -> {
                        LOG.warn("Unexpected HTTP status 401 for GET $url${BitbucketHttpErrors.describe(response)}")
                        ReviewerQueueQueryResult.Unauthorized
                    }
                    in 400..499 -> {
                        LOG.debug("GET $url -> ${response.code} (skipped)")
                        ReviewerQueueQueryResult.Skipped
                    }
                    else -> {
                        LOG.warn("Unexpected HTTP status ${response.code} for GET $url${BitbucketHttpErrors.describe(response)}")
                        ReviewerQueueQueryResult.Failed()
                    }
                }
            }
        } catch (exception: Exception) {
            LOG.debug("GET $url failed after ${elapsedMillis(startNanos = startNanos)} ms", exception)
            ReviewerQueueQueryResult.Failed(exception)
        }
    }

    private fun fetchBuildStatusPage(url: String, apiToken: String): BuildStatusPage? {
        val request = buildGetRequest(url = url, apiToken = apiToken)
        LOG.debug("GET $url")
        val startNanos = System.nanoTime()
        return try {
            httpClient.newCall(request).execute().use { response ->
                LOG.debug("GET $url -> ${response.code} in ${elapsedMillis(startNanos = startNanos)} ms")
                if (!response.isSuccessful) {
                    LOG.warn("Unexpected HTTP status ${response.code} for GET $url${BitbucketHttpErrors.describe(response)}")
                    return null
                }
                val body = response.body.string()
                if (body.isBlank()) {
                    LOG.debug("GET $url returned an empty body")
                    return BuildStatusPage(statuses = emptyList(), nextUrl = null)
                }
                BitbucketPullRequestResponseParser.parseBuildStatusesPage(body)
            }
        } catch (exception: Exception) {
            LOG.debug("GET $url failed after ${elapsedMillis(startNanos = startNanos)} ms", exception)
            null
        }
    }

    private fun countPaginated(firstUrl: String, apiToken: String, maxPages: Int): ItemCount? {
        var nextUrl: String? = firstUrl
        var total = 0
        var pagesFetched = 0
        while (nextUrl != null) {
            if (pagesFetched >= maxPages) {
                LOG.debug("Stopped counting after $pagesFetched pages starting at $firstUrl")
                return ItemCount(value = total, isTruncated = true)
            }
            val page = fetchCountPage(url = nextUrl, apiToken = apiToken) ?: return null
            pagesFetched++
            if (page.size != null) {
                LOG.debug("Counted ${page.size} items via size field for $firstUrl")
                return ItemCount(value = page.size, isTruncated = false)
            }
            total += page.valueCount
            nextUrl = page.nextUrl
        }
        LOG.debug("Counted $total items across $pagesFetched pages for $firstUrl")
        return ItemCount(value = total, isTruncated = false)
    }

    private fun fetchCountPage(url: String, apiToken: String): CountPage? {
        val request = buildGetRequest(url = url, apiToken = apiToken)
        LOG.debug("GET $url")
        val startNanos = System.nanoTime()
        return try {
            httpClient.newCall(request).execute().use { response ->
                LOG.debug("GET $url -> ${response.code} in ${elapsedMillis(startNanos = startNanos)} ms")
                if (!response.isSuccessful) {
                    LOG.warn("Unexpected HTTP status ${response.code} for GET $url${BitbucketHttpErrors.describe(response)}")
                    return null
                }
                val body = response.body.string()
                if (body.isBlank()) {
                    LOG.debug("GET $url returned an empty body")
                    return CountPage(size = null, valueCount = 0, nextUrl = null)
                }
                BitbucketPullRequestResponseParser.parseCountPage(body)
            }
        } catch (exception: Exception) {
            LOG.debug("GET $url failed after ${elapsedMillis(startNanos = startNanos)} ms", exception)
            null
        }
    }

    private fun buildGetRequest(url: String, apiToken: String): Request {
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiToken")
            .get()
            .build()
    }

    private fun fetchPage(url: String, apiToken: String): PageFetchResult {
        val request = buildGetRequest(url = url, apiToken = apiToken)
        LOG.debug("GET $url")
        val startNanos = System.nanoTime()
        return try {
            httpClient.newCall(request).execute().use { response ->
                LOG.debug("GET $url -> ${response.code} in ${elapsedMillis(startNanos = startNanos)} ms")
                if (!response.isSuccessful) {
                    LOG.warn("Unexpected HTTP status ${response.code} for GET $url${BitbucketHttpErrors.describe(response)}")
                    return PageFetchResult.Failed(PullRequestListResult.Failed())
                }
                val body = response.body.string()
                if (body.isBlank()) {
                    LOG.debug("GET $url returned an empty body")
                    return PageFetchResult.Success(PullRequestPage(pullRequests = emptyList(), nextUrl = null))
                }
                PageFetchResult.Success(BitbucketPullRequestResponseParser.parsePullRequestsPage(body))
            }
        } catch (exception: IOException) {
            LOG.debug("GET $url failed after ${elapsedMillis(startNanos = startNanos)} ms", exception)
            PageFetchResult.Failed(PullRequestListResult.Failed(exception))
        } catch (exception: Exception) {
            LOG.debug("GET $url failed after ${elapsedMillis(startNanos = startNanos)} ms", exception)
            PageFetchResult.Failed(PullRequestListResult.Failed(exception))
        }
    }

    private sealed interface PageFetchResult {
        data class Success(val page: PullRequestPage) : PageFetchResult

        data class Failed(val result: PullRequestListResult.Failed) : PageFetchResult
    }

    companion object {
        private val LOG = logger<OkHttpBitbucketPullRequestClient>()

        private const val MAX_COUNT_PAGES = 10

        /** 40 pages at 50 pull requests per page bounds the complete pruning set at 2,000 open PRs. */
        private const val MAX_OPEN_PR_PAGES = 40

        private const val MAX_MEMBER_PAGES = 20

        /** How many rows one authored listing asks for, matching the cap the Other Projects table shows. */
        private const val MAX_LISTED_PULL_REQUESTS = 50

        private const val EMPTY_JSON_BODY = "{}"

        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        private fun elapsedMillis(startNanos: Long): Long {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        }
    }
}