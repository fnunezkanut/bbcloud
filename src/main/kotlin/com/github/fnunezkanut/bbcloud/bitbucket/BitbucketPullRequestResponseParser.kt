package com.github.fnunezkanut.bbcloud.bitbucket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

object BitbucketPullRequestResponseParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parsePullRequests(responseBody: String): List<PullRequestSummary> {
        return parsePullRequestsPage(responseBody).pullRequests
    }

    fun parsePullRequestsPage(responseBody: String): PullRequestPage {
        val page = json.decodeFromString(PaginatedPullRequests.serializer(), responseBody)
        return PullRequestPage(
            pullRequests = page.values.mapNotNull { entry -> entry.toSummary() },
            nextUrl = page.next?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Reads one pull request of a paginated listing from the value it occupies in the page.
     *
     * A listing that has to keep its summaries paired with something beside them, such as the repository a
     * workspace-wide pull request belongs to, walks the values itself and needs each summary to stay with its
     * own value, which pairing the summaries against separately parsed entries cannot guarantee once an entry is
     * dropped as unreadable.
     */
    fun parsePullRequestSummary(value: JsonElement): PullRequestSummary? {
        return json.decodeFromJsonElement(PullRequestEntry.serializer(), value).toSummary()
    }

    fun parseErrorMessage(responseBody: String): String? {
        val error = runCatching {
            json.decodeFromString(ErrorResponse.serializer(), responseBody).error
        }.getOrNull() ?: return null
        val message = error.message?.takeIf { it.isNotBlank() }
        val detail = error.detail?.takeIf { it.isNotBlank() }
        return when {
            message != null && detail != null -> "$message ($detail)"
            else -> message ?: detail
        }
    }

    fun buildListPullRequestsUrl(workspace: String, repoSlug: String): String {
        return "$BITBUCKET_API_BASE/repositories/$workspace/$repoSlug/pullrequests" +
            "?state=OPEN&state=MERGED&state=DECLINED&sort=-updated_on&pagelen=$PAGE_LEN"
    }

    /** Builds the all-pages OPEN-only listing used as the safe source of truth for worktree pruning. */
    fun buildListOpenPullRequestsUrl(workspace: String, repoSlug: String): String {
        return "$BITBUCKET_API_BASE/repositories/$workspace/$repoSlug/pullrequests" +
            "?state=OPEN&sort=-updated_on&pagelen=$PAGE_LEN"
    }

    fun buildDiffstatUrl(workspace: String, repoSlug: String, pullRequestId: Long): String {
        return "$BITBUCKET_API_BASE/repositories/$workspace/$repoSlug/pullrequests/$pullRequestId/diffstat" +
            "?pagelen=$COUNT_PAGE_LEN&fields=size,next,values.status"
    }

    fun buildCommitsUrl(workspace: String, repoSlug: String, pullRequestId: Long): String {
        return "$BITBUCKET_API_BASE/repositories/$workspace/$repoSlug/pullrequests/$pullRequestId/commits" +
            "?pagelen=$COUNT_PAGE_LEN&fields=next,values.hash"
    }

    fun buildCommitsBehindUrl(workspace: String, repoSlug: String, sourceBranch: String, destinationBranch: String): String {
        return "$BITBUCKET_API_BASE/repositories/$workspace/$repoSlug/commits".toHttpUrl()
            .newBuilder()
            .addQueryParameter(name = "include", value = destinationBranch)
            .addQueryParameter(name = "exclude", value = sourceBranch)
            .addQueryParameter(name = "pagelen", value = COUNT_PAGE_LEN.toString())
            .addQueryParameter(name = "fields", value = "next,values.hash")
            .build()
            .toString()
    }

    fun buildStatusesUrl(workspace: String, repoSlug: String, pullRequestId: Long): String {
        return "$BITBUCKET_API_BASE/repositories/$workspace/$repoSlug/pullrequests/$pullRequestId/statuses" +
            "?pagelen=$COUNT_PAGE_LEN&fields=next,values.key,values.name,values.state,values.url,values.description,values.updated_on," +
            "values.commit.hash,values.links.commit.href"
    }

    fun buildCurrentUserUrl(): String {
        return "$BITBUCKET_API_BASE/user?fields=uuid,links.avatar.href"
    }

    /**
     * Builds the single-account lookup. [uuid] keeps its braces, because Bitbucket writes account keys that way and the
     * path segment encoder escapes them; the Atlassian account id form is passed through unchanged.
     */
    fun buildUserUrl(uuid: String): String {
        val url = "$BITBUCKET_API_BASE/users".toHttpUrl().newBuilder()
            .addPathSegment(uuid)
            .addQueryParameter(name = "fields", value = ACCOUNT_SUBFIELDS)
            .build()
        return url.toString()
    }

    /** Reads a single account; null when the account has no usable display name or the body is not an account. */
    fun parseUser(responseBody: String): ActivityUser? {
        return json.decodeFromString(AccountEntry.serializer(), responseBody).toActivityUser()
    }

    fun buildPullRequestUrl(workspace: String, repoSlug: String, pullRequestId: Long): String {
        // If a fields filter is added here, it must include rendered.description.raw, rendered.description.html, and description.
        return "$BITBUCKET_API_BASE/repositories/$workspace/$repoSlug/pullrequests/$pullRequestId"
    }

    fun buildMergeUrl(workspace: String, repoSlug: String, pullRequestId: Long): String {
        return buildPullRequestUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequestId) + "/merge"
    }

    fun buildApproveUrl(workspace: String, repoSlug: String, pullRequestId: Long): String {
        return buildPullRequestUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequestId) + "/approve"
    }

    fun buildRequestChangesUrl(workspace: String, repoSlug: String, pullRequestId: Long): String {
        return buildPullRequestUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequestId) + "/request-changes"
    }

    fun buildDeclineUrl(workspace: String, repoSlug: String, pullRequestId: Long): String {
        return buildPullRequestUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequestId) + "/decline"
    }

    fun buildMergeabilityChecksUrl(workspace: String, repoSlug: String, pullRequestId: Long): String {
        return buildPullRequestUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequestId).toHttpUrl()
            .newBuilder()
            .addPathSegments("mergeability/checks")
            .addQueryParameter(name = "q", value = "type!=\"git_mergeability_check\"")
            .build()
            .toString()
    }

    fun buildWorkspaceMembersUrl(workspace: String): String {
        return "$BITBUCKET_API_BASE/workspaces/$workspace/members".toHttpUrl()
            .newBuilder()
            .addQueryParameter(name = "pagelen", value = COUNT_PAGE_LEN.toString())
            .addQueryParameter(
                name = "fields",
                value = "next,values.user.uuid,values.user.display_name,values.user.nickname,values.user.links.avatar.href"
            )
            .build()
            .toString()
    }

    fun buildEffectiveDefaultReviewersUrl(workspace: String, repoSlug: String): String {
        return "$BITBUCKET_API_BASE/repositories/$workspace/$repoSlug/effective-default-reviewers" +
            "?pagelen=$COUNT_PAGE_LEN&fields=next,values.user.uuid"
    }

    fun buildTasksUrl(workspace: String, repoSlug: String, pullRequestId: Long): String {
        return buildPullRequestUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequestId).toHttpUrl()
            .newBuilder()
            .addPathSegment("tasks")
            .addQueryParameter(name = "pagelen", value = PAGE_LEN.toString())
            .addQueryParameter(name = "fields", value = TASK_FIELDS)
            .build()
            .toString()
    }

    fun buildActivityUrl(workspace: String, repoSlug: String, pullRequestId: Long): String {
        return buildPullRequestUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequestId).toHttpUrl()
            .newBuilder()
            .addPathSegment("activity")
            .addQueryParameter(name = "fields", value = "-values.comment,-values.pull_request")
            .build()
            .toString()
    }

    fun buildCommentsUrl(workspace: String, repoSlug: String, pullRequestId: Long): String {
        return buildPullRequestUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequestId).toHttpUrl()
            .newBuilder()
            .addPathSegment("comments")
            .addQueryParameter(name = "pagelen", value = COUNT_PAGE_LEN.toString())
            .addQueryParameter(name = "fields", value = COMMENT_FIELDS)
            .build()
            .toString()
    }

    fun buildCommentCreateUrl(workspace: String, repoSlug: String, pullRequestId: Long): String {
        return buildPullRequestUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequestId) + "/comments"
    }

    fun buildCommentUrl(workspace: String, repoSlug: String, pullRequestId: Long, commentId: Long): String {
        return buildPullRequestUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequestId) +
            "/comments/$commentId"
    }

    fun buildCommentResolveUrl(workspace: String, repoSlug: String, pullRequestId: Long, commentId: Long): String {
        return buildCommentUrl(
            workspace = workspace,
            repoSlug = repoSlug,
            pullRequestId = pullRequestId,
            commentId = commentId
        ) + "/resolve"
    }

    /**
     * Bitbucket renders [content] as Markdown.
     *
     * A non-blank [inlinePath] anchors the comment to a file. When [inlineLine] is also set, the comment is anchored to
     * that one-based line: [InlineCommentSide.AFTER] writes `inline.to` (the source/after side) and
     * [InlineCommentSide.BEFORE] writes `inline.from` (the pre-image side). A null [inlineLine] keeps the file-scoped
     * path-only anchor.
     */
    fun buildCommentCreateBody(
        content: String,
        parentCommentId: Long? = null,
        inlinePath: String? = null,
        inlineLine: Int? = null,
        inlineSide: InlineCommentSide = InlineCommentSide.AFTER
    ): String {
        val body = buildJsonObject {
            put(key = "content", element = buildJsonObject { put(key = "raw", value = content) })
            if (parentCommentId != null) {
                put(key = "parent", element = buildJsonObject { put(key = "id", value = parentCommentId) })
            }
            inlinePath?.takeIf { path -> path.isNotBlank() }?.let { path ->
                put(
                    key = "inline",
                    element = buildJsonObject {
                        put(key = "path", value = path)
                        if (inlineLine != null && inlineLine > 0) {
                            when (inlineSide) {
                                InlineCommentSide.AFTER -> put(key = "to", value = inlineLine)
                                InlineCommentSide.BEFORE -> put(key = "from", value = inlineLine)
                            }
                        }
                    }
                )
            }
        }
        return body.toString()
    }

    fun buildCommentUpdateBody(content: String): String {
        val body = buildJsonObject {
            put(key = "content", element = buildJsonObject { put(key = "raw", value = content) })
        }
        return body.toString()
    }

    fun buildPullRequestCommitsUrl(workspace: String, repoSlug: String, pullRequestId: Long): String {
        return buildPullRequestUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequestId).toHttpUrl()
            .newBuilder()
            .addPathSegment("commits")
            .addQueryParameter(name = "pagelen", value = COUNT_PAGE_LEN.toString())
            .addQueryParameter(name = "fields", value = COMMIT_FIELDS)
            .build()
            .toString()
    }

    /** Keeps the documented `update` and `approval` entries and skips every other entry kind. */
    fun parseActivityPage(responseBody: String): ActivityPage {
        val root = json.parseToJsonElement(responseBody).jsonObject
        val values = root["values"] as? JsonArray
        return ActivityPage(
            items = values.orEmpty().mapNotNull { value -> (value as? JsonObject)?.let { entry -> toActivityItem(entry) } },
            nextUrl = root.stringValue("next")?.takeIf { it.isNotBlank() }
        )
    }

    fun parseCommentsPage(responseBody: String): CommentPage {
        val page = json.decodeFromString(PaginatedComments.serializer(), responseBody)
        return CommentPage(
            comments = page.values.mapNotNull { entry -> entry.toComment() },
            nextUrl = page.next?.takeIf { it.isNotBlank() }
        )
    }

    fun parseCommitsPage(responseBody: String): CommitPage {
        val page = json.decodeFromString(PaginatedCommits.serializer(), responseBody)
        return CommitPage(
            commits = page.values.mapNotNull { entry -> entry.toCommit() },
            nextUrl = page.next?.takeIf { it.isNotBlank() }
        )
    }

    fun buildTaskUrl(workspace: String, repoSlug: String, pullRequestId: Long, taskId: Long): String {
        return buildPullRequestUrl(workspace = workspace, repoSlug = repoSlug, pullRequestId = pullRequestId) + "/tasks/$taskId"
    }

    fun parseTasksPage(responseBody: String): TaskPage {
        val page = json.decodeFromString(PaginatedTasks.serializer(), responseBody)
        return TaskPage(
            tasks = page.values.mapNotNull { entry -> entry.toTask() },
            nextUrl = page.next?.takeIf { it.isNotBlank() }
        )
    }

    fun buildTaskCreateBody(content: String): String {
        return buildTaskUpdateBody(content = content, resolved = null)
    }

    /** Leaves out the fields that are null so that a state change does not rewrite the task text and vice versa. */
    fun buildTaskUpdateBody(content: String?, resolved: Boolean?): String {
        val body = buildJsonObject {
            if (content != null) {
                put(key = "content", element = buildJsonObject { put(key = "raw", value = content) })
            }
            if (resolved != null) {
                put(key = "state", value = if (resolved) RESOLVED_TASK_STATE else UNRESOLVED_TASK_STATE)
            }
        }
        return body.toString()
    }

    fun parseCurrentUser(responseBody: String): CurrentUser? {
        val entry = json.decodeFromString(CurrentUserEntry.serializer(), responseBody)
        val uuid = entry.uuid?.takeIf { it.isNotBlank() } ?: return null
        return CurrentUser(uuid = uuid, avatarUrl = entry.links?.avatar?.href?.takeIf { it.isNotBlank() })
    }

    fun parsePullRequestDetails(responseBody: String, currentUserUuid: String?): PullRequestDetails? {
        val entry = json.decodeFromString(PullRequestEntry.serializer(), responseBody)
        val summary = entry.toSummary() ?: return null
        val isApproved = currentUserUuid != null &&
            entry.participants.any { participant -> participant.approved == true && participant.user?.uuid == currentUserUuid }
        val isChangesRequested = currentUserUuid != null &&
            entry.participants.any { participant -> participant.state == CHANGES_REQUESTED_STATE && participant.user?.uuid == currentUserUuid }
        val authorUuid = entry.author?.uuid?.takeIf { it.isNotBlank() }
        return PullRequestDetails(
            summary = summary,
            isApprovedByCurrentUser = isApproved,
            reviewers = entry.reviewers.mapNotNull { reviewer -> reviewer.toReviewer(participants = entry.participants) },
            authorUuid = authorUuid,
            description = entry.descriptionMarkdown(),
            isChangesRequestedByCurrentUser = isChangesRequested,
            isAuthoredByCurrentUser = currentUserUuid != null && authorUuid == currentUserUuid
        )
    }

    fun buildDraftUpdateBody(title: String, isDraft: Boolean): String {
        return json.encodeToString(DraftUpdate.serializer(), DraftUpdate(title = title, draft = isDraft))
    }

    fun buildReviewersUpdateBody(title: String, reviewerUuids: List<String>): String {
        val update = ReviewersUpdate(title = title, reviewers = reviewerUuids.map { uuid -> ReviewerReference(uuid = uuid) })
        return json.encodeToString(ReviewersUpdate.serializer(), update)
    }

    /**
     * Bitbucket Cloud's pull request `description` is a plain Markdown string on the write side; it is NOT nested
     * under a rendered `content.raw` object the way comments and tasks are. Sending `{"description":{"raw":...}}`
     * makes Bitbucket store the object literal and echo back `{'raw': '...'}` on every subsequent read. The
     * `buildJsonObject` DSL escapes newlines, quotes, and backslashes correctly, so multi-line Markdown round-trips.
     */
    fun buildDescriptionUpdateBody(title: String, description: String): String {
        val body = buildJsonObject {
            put(key = "title", value = title)
            put(key = "description", value = description)
        }
        return body.toString()
    }

    fun buildBranchesUrl(workspace: String, repoSlug: String): String {
        return "$BITBUCKET_API_BASE/repositories/$workspace/$repoSlug/refs/branches".toHttpUrl()
            .newBuilder()
            .addQueryParameter(name = "pagelen", value = COUNT_PAGE_LEN.toString())
            .addQueryParameter(name = "sort", value = "-target.date")
            .addQueryParameter(name = "fields", value = "next,values.name")
            .build()
            .toString()
    }

    fun buildRepositoryMainBranchUrl(workspace: String, repoSlug: String): String {
        return "$BITBUCKET_API_BASE/repositories/$workspace/$repoSlug?fields=mainbranch.name"
    }

    fun buildCreatePullRequestUrl(workspace: String, repoSlug: String): String {
        return "$BITBUCKET_API_BASE/repositories/$workspace/$repoSlug/pullrequests"
    }

    fun buildOpenPullRequestForBranchesUrl(workspace: String, repoSlug: String, sourceBranch: String, destinationBranch: String): String {
        // No fields filter is added, so the parsed entries keep every field toSummary() requires.
        val query = "source.branch.name=\"${escapeQueryValue(sourceBranch)}\"" +
            " AND destination.branch.name=\"${escapeQueryValue(destinationBranch)}\"" +
            " AND state=\"OPEN\""
        return "$BITBUCKET_API_BASE/repositories/$workspace/$repoSlug/pullrequests".toHttpUrl()
            .newBuilder()
            .addQueryParameter(name = "q", value = query)
            .addQueryParameter(name = "pagelen", value = "1")
            .build()
            .toString()
    }

    /** Escapes a value for Bitbucket's filter-query string syntax: a backslash becomes `\\` and a double quote `\"`. */
    internal fun escapeQueryValue(value: String): String {
        return value.replace(oldValue = "\\", newValue = "\\\\").replace(oldValue = "\"", newValue = "\\\"")
    }

    /**
     * Builds the create-pull-request body.
     *
     * Bitbucket Cloud's pull request `description` is a plain Markdown string on the write side; it is NOT nested under
     * a rendered `content.raw` object the way comments and tasks are (see [buildDescriptionUpdateBody]). A blank
     * description is omitted so Bitbucket keeps its own default.
     */
    fun buildCreatePullRequestBody(request: NewPullRequest): String {
        val body = buildJsonObject {
            put(key = "title", value = request.title)
            if (request.description.isNotBlank()) {
                put(key = "description", value = request.description)
            }
            putJsonObject("source") {
                putJsonObject("branch") {
                    put(key = "name", value = request.sourceBranch)
                }
            }
            putJsonObject("destination") {
                putJsonObject("branch") {
                    put(key = "name", value = request.destinationBranch)
                }
            }
            putJsonArray("reviewers") {
                request.reviewerUuids.forEach { uuid ->
                    addJsonObject {
                        put(key = "uuid", value = uuid)
                    }
                }
            }
            put(key = "close_source_branch", value = request.closeSourceBranch)
            put(key = "draft", value = request.isDraft)
        }
        return body.toString()
    }

    fun parseBranchNamesPage(responseBody: String): BranchNamePage {
        val page = json.decodeFromString(PaginatedBranches.serializer(), responseBody)
        return BranchNamePage(
            names = page.values.mapNotNull { entry -> entry.name?.takeIf { it.isNotBlank() } },
            nextUrl = page.next?.takeIf { it.isNotBlank() }
        )
    }

    fun parseMainBranchName(responseBody: String): String? {
        val repository = json.decodeFromString(RepositoryMainBranch.serializer(), responseBody)
        return repository.mainbranch?.name?.takeIf { it.isNotBlank() }
    }

    fun parseCreatedPullRequest(responseBody: String): PullRequestSummary? {
        return json.decodeFromString(PullRequestEntry.serializer(), responseBody).toSummary()
    }

    /** Keeps standard and custom merge checks; state, permission, Git, and merge queue checks are not shown as merge checks. */
    fun parseMergeabilityChecks(responseBody: String): List<PullRequestMergeCheck> {
        val values = json.parseToJsonElement(responseBody).jsonObject["values"] as? JsonArray ?: return emptyList()
        return values.mapNotNull { value -> (value as? JsonObject)?.let { entry -> toMergeCheck(entry) } }
    }

    fun parseWorkspaceMembersPage(responseBody: String): WorkspaceMemberPage {
        val page = json.decodeFromString(PaginatedMemberships.serializer(), responseBody)
        return WorkspaceMemberPage(
            members = page.values.mapNotNull { membership -> membership.user?.toCandidate() },
            nextUrl = page.next?.takeIf { it.isNotBlank() }
        )
    }

    fun parseDefaultReviewerUuids(responseBody: String): Set<String> {
        val page = json.decodeFromString(PaginatedMemberships.serializer(), responseBody)
        return page.values.mapNotNull { entry -> entry.user?.uuid?.takeIf { it.isNotBlank() } }.toSet()
    }

    fun parseBuildStatusesPage(responseBody: String): BuildStatusPage {
        val page = json.decodeFromString(PaginatedStatuses.serializer(), responseBody)
        return BuildStatusPage(
            statuses = page.values.mapNotNull { entry -> entry.toBuildStatus() },
            nextUrl = page.next?.takeIf { it.isNotBlank() }
        )
    }

    fun parseCountPage(responseBody: String): CountPage {
        val page = json.decodeFromString(PaginatedCountEntries.serializer(), responseBody)
        return CountPage(
            size = page.size,
            valueCount = page.values.size,
            nextUrl = page.next?.takeIf { it.isNotBlank() }
        )
    }

    private fun parseState(value: String?): PullRequestState? {
        return when (value?.uppercase()) {
            "OPEN" -> PullRequestState.Open
            "MERGED" -> PullRequestState.Merged
            "DECLINED" -> PullRequestState.Declined
            else -> null
        }
    }

    private fun parseBuildState(value: String?): BuildState? {
        return when (value?.uppercase()) {
            "SUCCESSFUL" -> BuildState.Successful
            "FAILED" -> BuildState.Failed
            "INPROGRESS" -> BuildState.InProgress
            "STOPPED" -> BuildState.Stopped
            else -> null
        }
    }

    private fun parseMergeCheckStatus(value: String?): MergeCheckStatus {
        return when (value?.uppercase()) {
            "PASSED" -> MergeCheckStatus.Passed
            "FAILED" -> MergeCheckStatus.Failed
            "PENDING" -> MergeCheckStatus.Pending
            "SKIPPED" -> MergeCheckStatus.Skipped
            else -> MergeCheckStatus.Unknown
        }
    }

    private fun toMergeCheck(entry: JsonObject): PullRequestMergeCheck? {
        val check = entry["check"] as? JsonObject
        val kind = when (entry.stringValue("type")) {
            STANDARD_MERGE_CHECK_TYPE -> check?.stringValue("kind")?.takeIf { it.isNotBlank() } ?: return null
            CUSTOM_PRE_MERGE_CHECK_TYPE, CUSTOM_ON_MERGE_CHECK_TYPE -> PullRequestMergeCheck.CUSTOM_KIND
            else -> return null
        }
        return PullRequestMergeCheck(
            kind = kind,
            name = check?.stringValue("name")?.takeIf { it.isNotBlank() },
            status = parseMergeCheckStatus(entry.stringValue("status")),
            isBlocking = (entry["blocking"] as? JsonPrimitive)?.booleanOrNull ?: false,
            requiredValue = firstIntValue(entry["requirement"]),
            observedValue = firstIntValue(entry["observed"])
        )
    }

    private fun JsonObject.stringValue(key: String): String? {
        return (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    private fun toActivityItem(entry: JsonObject): PullRequestActivityItem? {
        val update = entry["update"] as? JsonObject
        if (update != null) {
            val date = parseInstant(update.stringValue("date")) ?: return null
            return PullRequestActivityItem.Update(
                date = date,
                user = (update["author"] as? JsonObject)?.let { author -> toActivityUser(author) },
                state = parseState(update.stringValue("state"))
            )
        }
        val approval = entry["approval"] as? JsonObject
        if (approval != null) {
            val date = parseInstant(approval.stringValue("date")) ?: return null
            return PullRequestActivityItem.Approval(
                date = date,
                user = (approval["user"] as? JsonObject)?.let { user -> toActivityUser(user) }
            )
        }
        return null
    }

    private fun toActivityUser(account: JsonObject): ActivityUser? {
        val name = account.stringValue("display_name")?.takeIf { it.isNotBlank() }
            ?: account.stringValue("nickname")?.takeIf { it.isNotBlank() }
            ?: return null
        val avatarUrl = ((account["links"] as? JsonObject)?.get("avatar") as? JsonObject)?.stringValue("href")
        return ActivityUser(
            uuid = account.stringValue("uuid")?.takeIf { it.isNotBlank() },
            displayName = name,
            avatarUrl = avatarUrl?.takeIf { it.isNotBlank() }
        )
    }

    private fun parseInstant(value: String?): Instant? {
        return value?.let { runCatching { Instant.parse(it) }.getOrNull() }
    }

    private fun firstIntValue(element: JsonElement?): Int? {
        val values = (element as? JsonObject)?.values ?: return null
        return values.firstNotNullOfOrNull { value -> (value as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull }
    }

    const val PAGE_LEN = 50

    const val COUNT_PAGE_LEN = 100

    private const val BITBUCKET_API_BASE = "https://api.bitbucket.org/2.0"

    private const val STANDARD_MERGE_CHECK_TYPE = "standard_merge_check"
    private const val CUSTOM_PRE_MERGE_CHECK_TYPE = "custom_pre_merge_check"
    private const val CUSTOM_ON_MERGE_CHECK_TYPE = "custom_on_merge_check"
    private const val CHANGES_REQUESTED_STATE = "changes_requested"
    private const val APPROVED_STATE = "approved"
    private const val RESOLVED_TASK_STATE = "RESOLVED"
    private const val UNRESOLVED_TASK_STATE = "UNRESOLVED"

    private const val ACCOUNT_SUBFIELDS = "uuid,account_id,display_name,links.avatar.href"

    private val TASK_FIELDS = listOf(
        "next",
        "values.id",
        "values.state",
        "values.content.raw",
        "values.created_on",
        "values.resolved_on"
    ).plus(accountFields("values.creator")).plus(accountFields("values.resolved_by")).joinToString(separator = ",")

    private val COMMENT_FIELDS = listOf(
        "next",
        "values.id",
        "values.parent.id",
        "values.created_on",
        "values.deleted",
        "values.pending",
        "values.content.html",
        "values.content.raw",
        "values.inline.path",
        "values.inline.to",
        "values.inline.from",
        "values.inline.start_to",
        "values.inline.start_from",
        "values.resolution.type",
        "values.resolution.created_on"
    ).plus(accountFields("values.user")).plus(accountFields("values.resolution.user")).joinToString(separator = ",")

    private val COMMIT_FIELDS = listOf(
        "next",
        "values.hash",
        "values.date",
        "values.message",
        "values.links.html.href",
        "values.author.raw"
    ).plus(accountFields("values.author.user")).joinToString(separator = ",")

    private fun accountFields(prefix: String): List<String> {
        return ACCOUNT_SUBFIELDS.split(',').map { field -> "$prefix.$field" }
    }

    @Serializable
    private data class PaginatedTasks(val values: List<TaskEntry> = emptyList(), val next: String? = null)

    @Serializable
    private data class TaskEntry(
        val id: Long? = null,
        val state: String? = null,
        val content: TaskContent? = null,
        @SerialName("created_on")
        val createdOn: String? = null,
        val creator: AccountEntry? = null,
        @SerialName("resolved_on")
        val resolvedOn: String? = null,
        @SerialName("resolved_by")
        val resolvedBy: AccountEntry? = null
    ) {
        fun toTask(): PullRequestTask? {
            val taskId = id ?: return null
            val isResolved = state?.uppercase() == RESOLVED_TASK_STATE
            return PullRequestTask(
                id = taskId,
                content = content?.raw.orEmpty(),
                isResolved = isResolved,
                createdOn = parseInstant(createdOn),
                creator = creator?.toActivityUser(),
                resolvedOn = if (isResolved) parseInstant(resolvedOn) else null,
                resolvedBy = if (isResolved) resolvedBy?.toActivityUser() else null
            )
        }
    }

    @Serializable
    private data class TaskContent(val raw: String? = null)

    @Serializable
    private data class PaginatedComments(val values: List<CommentEntry> = emptyList(), val next: String? = null)

    @Serializable
    private data class CommentEntry(
        val id: Long? = null,
        val parent: CommentReference? = null,
        @SerialName("created_on")
        val createdOn: String? = null,
        val deleted: Boolean? = null,
        val pending: Boolean? = null,
        val content: CommentContent? = null,
        val inline: InlineEntry? = null,
        val resolution: ResolutionEntry? = null,
        val user: AccountEntry? = null
    ) {
        fun toComment(): PullRequestComment? {
            val commentId = id ?: return null
            val created = parseInstant(createdOn) ?: return null
            val isResolved = resolution?.type != null
            val resolver = resolution?.user?.toActivityUser()
            val resolvedAt = parseInstant(resolution?.createdOn)
            val inlineSide = when {
                inline?.to != null -> InlineCommentSide.AFTER
                inline?.from != null -> InlineCommentSide.BEFORE
                else -> null
            }
            val inlineLine = when (inlineSide) {
                InlineCommentSide.AFTER -> inline?.to
                InlineCommentSide.BEFORE -> inline?.from
                null -> null
            }
            val inlineLineEnd = when (inlineSide) {
                InlineCommentSide.AFTER -> inline?.startTo?.let { inline.to }
                InlineCommentSide.BEFORE -> inline?.startFrom?.let { inline.from }
                null -> null
            }
            val inlineLineStart = when (inlineSide) {
                InlineCommentSide.AFTER -> inline?.startTo ?: inlineLine
                InlineCommentSide.BEFORE -> inline?.startFrom ?: inlineLine
                null -> null
            }
            return PullRequestComment(
                id = commentId,
                parentId = parent?.id,
                createdOn = created,
                author = user?.toActivityUser(),
                html = content?.html.orEmpty(),
                inlinePath = inline?.path?.takeIf { it.isNotBlank() },
                inlineLine = inlineLineStart,
                inlineLineEnd = inlineLineEnd,
                inlineSide = inlineSide,
                isResolved = isResolved,
                isDeleted = deleted ?: false,
                isPending = pending ?: false,
                resolvedBy = if (isResolved) resolver else null,
                resolvedOn = if (isResolved) resolvedAt else null,
                raw = content?.raw
            )
        }
    }

    @Serializable
    private data class CommentReference(val id: Long? = null)

    @Serializable
    private data class CommentContent(val html: String? = null, val raw: String? = null)

    @Serializable
    private data class InlineEntry(
        val path: String? = null,
        val to: Int? = null,
        val from: Int? = null,
        @SerialName("start_to")
        val startTo: Int? = null,
        @SerialName("start_from")
        val startFrom: Int? = null
    )

    @Serializable
    private data class ResolutionEntry(
        val type: String? = null,
        val user: AccountEntry? = null,
        @SerialName("created_on")
        val createdOn: String? = null
    )

    @Serializable
    private data class PaginatedCommits(val values: List<CommitEntry> = emptyList(), val next: String? = null)

    @Serializable
    private data class CommitEntry(
        val hash: String? = null,
        val date: String? = null,
        val message: String? = null,
        val author: CommitAuthorEntry? = null,
        val links: HtmlLinks? = null
    ) {
        fun toCommit(): PullRequestCommit? {
            val commitHash = hash?.takeIf { it.isNotBlank() } ?: return null
            val commitDate = parseInstant(date) ?: return null
            return PullRequestCommit(
                hash = commitHash,
                message = message.orEmpty(),
                date = commitDate,
                author = author?.toActivityUser(),
                url = links?.html?.href?.takeIf { it.isNotBlank() }
            )
        }
    }

    /** [raw] has the `Name <email>` form; only the name is shown when the commit author is not linked to a Bitbucket account. */
    @Serializable
    private data class CommitAuthorEntry(val raw: String? = null, val user: AccountEntry? = null) {
        fun toActivityUser(): ActivityUser? {
            user?.toActivityUser()?.let { account -> return account }
            val name = raw?.substringBefore('<')?.trim()?.takeIf { it.isNotBlank() } ?: return null
            return ActivityUser(uuid = null, displayName = name, avatarUrl = null)
        }
    }

    @Serializable
    private data class PaginatedPullRequests(val values: List<PullRequestEntry> = emptyList(), val next: String? = null)

    @Serializable
    private data class PaginatedCountEntries(val size: Int? = null, val values: List<JsonElement> = emptyList(), val next: String? = null)

    @Serializable
    private data class PaginatedStatuses(val values: List<StatusEntry> = emptyList(), val next: String? = null)

    @Serializable
    private data class StatusEntry(
        val key: String? = null,
        val name: String? = null,
        val state: String? = null,
        val url: String? = null,
        val description: String? = null,
        @SerialName("updated_on")
        val updatedOn: String? = null,
        val commit: CommitReference? = null,
        val links: StatusLinks? = null
    ) {
        fun toBuildStatus(): PullRequestBuildStatus? {
            val statusName = name?.takeIf { it.isNotBlank() } ?: return null
            val statusState = parseBuildState(state) ?: return null
            val statusUrl = url?.takeIf { it.isNotBlank() } ?: return null
            return PullRequestBuildStatus(
                key = key?.takeIf { it.isNotBlank() } ?: statusName,
                name = statusName,
                state = statusState,
                url = statusUrl,
                description = description?.takeIf { it.isNotBlank() },
                updatedOn = updatedOn?.let { runCatching { Instant.parse(it) }.getOrNull() },
                commitHash = commitHash()
            )
        }

        private fun commitHash(): String? {
            commit?.hash?.takeIf { it.isNotBlank() }?.let { hash -> return hash }
            return links?.commit?.href
                ?.trimEnd('/')
                ?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() }
        }
    }

    @Serializable
    private data class CommitReference(val hash: String? = null)

    @Serializable
    private data class StatusLinks(val commit: HrefLink? = null)

    @Serializable
    private data class ErrorResponse(val error: ErrorDetail? = null)

    @Serializable
    private data class ErrorDetail(val message: String? = null, val detail: String? = null)

    @Serializable
    private data class PullRequestEntry(
        val id: Long? = null,
        val title: String? = null,
        @SerialName("comment_count")
        val commentCount: Int? = null,
        @SerialName("created_on")
        val createdOn: String? = null,
        @SerialName("updated_on")
        val updatedOn: String? = null,
        val author: AuthorEntry? = null,
        val source: BranchContainer? = null,
        val destination: BranchContainer? = null,
        val links: HtmlLinks? = null,
        val state: String? = null,
        val draft: Boolean? = null,
        val participants: List<ParticipantEntry> = emptyList(),
        val reviewers: List<AccountEntry> = emptyList(),
        @SerialName("rendered")
        val rendered: RenderedBlock? = null,
        val description: String? = null
    ) {
        fun descriptionMarkdown(): String? {
            return rendered?.description?.raw?.takeIf { it.isNotBlank() }
                ?: rendered?.description?.html?.takeIf { it.isNotBlank() }
                ?: description?.takeIf { it.isNotBlank() }
        }

        fun toSummary(): PullRequestSummary? {
            val prId = id ?: return null
            val prState = parseState(state) ?: return null
            val prTitle = title?.takeIf { it.isNotBlank() } ?: return null
            val updated = updatedOn?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
            val created = createdOn?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: updated
            val webUrl = links?.html?.href?.takeIf { it.isNotBlank() } ?: return null
            val sourceBranch = source?.branch?.name?.takeIf { it.isNotBlank() } ?: return null
            val destinationBranch = destination?.branch?.name?.takeIf { it.isNotBlank() } ?: return null
            return PullRequestSummary(
                id = prId,
                title = prTitle,
                authorDisplayName = author?.displayName?.takeIf { it.isNotBlank() } ?: "Unknown",
                authorAvatarUrl = AvatarUrlUpgrader.upgrade(author?.links?.avatar?.href),
                createdOn = created,
                updatedOn = updated,
                sourceBranch = sourceBranch,
                destinationBranch = destinationBranch,
                commentCount = commentCount ?: 0,
                webUrl = webUrl,
                state = prState,
                isDraft = draft ?: false
            )
        }
    }

    @Serializable
    private data class RenderedBlock(val description: RenderedContent? = null)

    @Serializable
    private data class RenderedContent(val raw: String? = null, val html: String? = null)

    @Serializable
    private data class AuthorEntry(
        val uuid: String? = null,
        @SerialName("display_name")
        val displayName: String? = null,
        val links: AvatarLinks? = null
    )

    @Serializable
    private data class AccountEntry(
        val uuid: String? = null,
        @SerialName("account_id")
        val accountId: String? = null,
        @SerialName("display_name")
        val displayName: String? = null,
        val nickname: String? = null,
        val links: AvatarLinks? = null
    ) {
        fun toReviewer(participants: List<ParticipantEntry>): PullRequestReviewer? {
            val reviewerUuid = uuid?.takeIf { it.isNotBlank() } ?: return null
            val participant = participants.firstOrNull { entry -> entry.user?.uuid == reviewerUuid }
            val status = when {
                participant?.state == CHANGES_REQUESTED_STATE -> ReviewerStatus.ChangesRequested
                participant?.approved == true || participant?.state == APPROVED_STATE -> ReviewerStatus.Approved
                else -> ReviewerStatus.None
            }
            return PullRequestReviewer(
                uuid = reviewerUuid,
                displayName = displayName?.takeIf { it.isNotBlank() } ?: nickname?.takeIf { it.isNotBlank() } ?: "Unknown",
                avatarUrl = links?.avatar?.href?.takeIf { it.isNotBlank() },
                status = status
            )
        }

        fun toActivityUser(): ActivityUser? {
            val name = displayName?.takeIf { it.isNotBlank() } ?: nickname?.takeIf { it.isNotBlank() } ?: return null
            return ActivityUser(
                uuid = uuid?.takeIf { it.isNotBlank() },
                displayName = name,
                avatarUrl = links?.avatar?.href?.takeIf { it.isNotBlank() }
            )
        }

        fun toCandidate(): ReviewerCandidate? {
            val candidateUuid = uuid?.takeIf { it.isNotBlank() } ?: return null
            val name = displayName?.takeIf { it.isNotBlank() } ?: nickname?.takeIf { it.isNotBlank() } ?: return null
            return ReviewerCandidate(
                uuid = candidateUuid,
                displayName = name,
                nickname = nickname?.takeIf { it.isNotBlank() },
                avatarUrl = links?.avatar?.href?.takeIf { it.isNotBlank() }
            )
        }
    }

    @Serializable
    private data class ParticipantEntry(val user: ParticipantUser? = null, val approved: Boolean? = null, val state: String? = null)

    @Serializable
    private data class ParticipantUser(val uuid: String? = null)

    @Serializable
    private data class CurrentUserEntry(val uuid: String? = null, val links: AvatarLinks? = null)

    @Serializable
    private data class DraftUpdate(val title: String, val draft: Boolean)

    @Serializable
    private data class ReviewersUpdate(val title: String, val reviewers: List<ReviewerReference>)

    @Serializable
    private data class ReviewerReference(val uuid: String)

    /** Used for both workspace memberships and default reviewers, which share the `values[].user` shape. */
    @Serializable
    private data class PaginatedMemberships(val values: List<MembershipEntry> = emptyList(), val next: String? = null)

    @Serializable
    private data class MembershipEntry(val user: AccountEntry? = null)

    @Serializable
    private data class AvatarLinks(val avatar: HrefLink? = null)

    @Serializable
    private data class BranchContainer(val branch: BranchName? = null)

    @Serializable
    private data class BranchName(val name: String? = null)

    @Serializable
    private data class PaginatedBranches(val values: List<BranchName> = emptyList(), val next: String? = null)

    @Serializable
    private data class RepositoryMainBranch(val mainbranch: BranchName? = null)

    @Serializable
    private data class HtmlLinks(val html: HrefLink? = null)

    @Serializable
    private data class HrefLink(val href: String? = null)
}