package com.github.fnunezkanut.bbcloud.bitbucket

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Builds the per-repository reviewer query: the repositories are the checkout folders, and the query filters
 * on `q=state="OPEN" AND reviewers.uuid="{uuid}" AND author.uuid!="{uuid}"`, sorted by `-updated_on`, with
 * `pagelen` set.
 */
object BitbucketReviewQueueResponseParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * One repository's queue: the newest-updated [pageLen] open pull requests [reviewerUuid] reviews. The query
     * filters on `state="OPEN"`, `reviewers.uuid` and `author.uuid!=` so the answer needs no paging.
     */
    fun buildReviewingPullRequestsUrl(workspace: String, repoSlug: String, reviewerUuid: String, pageLen: Int): String {
        return "$BITBUCKET_API_BASE/repositories/$workspace/$repoSlug/pullrequests".toHttpUrl()
            .newBuilder()
            .addQueryParameter(name = "q", value = "state=\"OPEN\" AND reviewers.uuid=\"$reviewerUuid\" AND author.uuid!=\"$reviewerUuid\"")
            .addQueryParameter(name = "sort", value = NEWEST_FIRST_ORDER)
            .addQueryParameter(name = "pagelen", value = pageLen.toString())
            // addQueryParameter encodes the leading `+` of the expansion as %2B, so the Approved-by-me filter can read approvals.
            .addQueryParameter(name = "fields", value = BitbucketPullRequestResponseParser.LISTING_PARTICIPANTS_FIELDS)
            .build()
            .toString()
    }

    private const val BITBUCKET_API_BASE = "https://api.bitbucket.org/2.0"

    private const val NEWEST_FIRST_ORDER = "-updated_on"
}