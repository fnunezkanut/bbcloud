package com.github.fnunezkanut.bbcloud.bitbucket

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Builds and reads the workspace-wide query behind the "PRs opened by me" section of the Other Projects tab: the
 * open pull requests [selectedUserUuid] authored anywhere in the workspace.
 *
 * This is the documented `GET /2.0/workspaces/{workspace}/pullrequests/{selected_user}` route, which needs the
 * user uuid rather than a nickname. See [BitbucketReviewQueueResponseParser] for why the review queue, which
 * this route does not cover, has to be collected per repository instead.
 *
 * Unlike the per-repository route, this one returns no repository object per row, so the repository behind each
 * pull request is recovered from its own links; see [BitbucketRepositoryUrls.repoSlugFromPullRequestLink].
 */
object BitbucketWorkspacePullRequestResponseParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    /** The newest-updated [pageLen] open pull requests [selectedUserUuid] authored across [workspace]. */
    fun buildListAuthoredPullRequestsUrl(workspace: String, selectedUserUuid: String, pageLen: Int): String {
        return "$BITBUCKET_API_BASE/workspaces/$workspace/pullrequests/$selectedUserUuid".toHttpUrl()
            .newBuilder()
            .addQueryParameter(name = "state", value = OPEN_STATE)
            .addQueryParameter(name = "sort", value = NEWEST_FIRST_ORDER)
            .addQueryParameter(name = "pagelen", value = pageLen.toString())
            .build()
            .toString()
    }

    /**
     * Pairs every readable pull request of the page with the repository behind it. A row whose links name no
     * repository is kept with a null slug, because dropping it here would hide a load failure from the caller.
     */
    fun parseAuthoredPullRequestPage(responseBody: String): AuthoredPullRequestPage {
        val root = json.parseToJsonElement(responseBody).jsonObject
        val values = root["values"] as? JsonArray
        val entries = values.orEmpty().mapNotNull { value ->
            val entry = value as? JsonObject ?: return@mapNotNull null
            val summary = BitbucketPullRequestResponseParser.parsePullRequestSummary(value = entry) ?: return@mapNotNull null
            AuthoredPullRequestEntry(summary = summary, repoSlug = repoSlugOf(entry = entry))
        }
        return AuthoredPullRequestPage(entries = entries, isTruncated = root.stringValue("next") != null)
    }

    /** The API link names the repository directly; the browser link is the fallback when it is missing. */
    private fun repoSlugOf(entry: JsonObject): String? {
        val links = entry["links"] as? JsonObject ?: return null
        val selfHref = (links["self"] as? JsonObject)?.stringValue("href")
        val htmlHref = (links["html"] as? JsonObject)?.stringValue("href")
        return BitbucketRepositoryUrls.repoSlugFromPullRequestLink(href = selfHref ?: htmlHref ?: return null)
    }

    private fun JsonObject.stringValue(key: String): String? {
        return (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    }

    private const val BITBUCKET_API_BASE = "https://api.bitbucket.org/2.0"

    private const val OPEN_STATE = "OPEN"

    private const val NEWEST_FIRST_ORDER = "-updated_on"
}