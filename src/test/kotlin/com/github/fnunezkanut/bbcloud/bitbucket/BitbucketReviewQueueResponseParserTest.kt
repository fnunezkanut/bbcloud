package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BitbucketReviewQueueResponseParserTest {
    @Test
    fun `buildReviewingPullRequestsUrl - should filter on the reviewer and keep the open ones newest first`() {
        // WHEN
        val url = BitbucketReviewQueueResponseParser.buildReviewingPullRequestsUrl(
            workspace = "my-workspace",
            repoSlug = "other-repo",
            reviewerUuid = "{5b10ac8d}",
            pageLen = 50
        )

        // THEN
        assertSoftly(url) {
            shouldContain("/2.0/repositories/my-workspace/other-repo/pullrequests")
            shouldContain("state%3D%22OPEN%22")
            shouldContain("sort=-updated_on")
            shouldContain("pagelen=50")
            // The participants expansion must keep its leading `+` encoded as %2B, not a raw `+`.
            shouldContain("fields=%2Bvalues.participants")
        }
    }

    @Test
    fun `buildReviewingPullRequestsUrl - should not leak an unescaped filter into the path`() {
        // WHEN
        val url = BitbucketReviewQueueResponseParser.buildReviewingPullRequestsUrl(
            workspace = "my-workspace",
            repoSlug = "other-repo",
            reviewerUuid = "{5b10ac8d}",
            pageLen = 50
        )

        // THEN the quotes and braces of the filter stay percent-encoded in the query string
        url shouldContain ("%22%7B5b10ac8d%7D%22")
    }
}