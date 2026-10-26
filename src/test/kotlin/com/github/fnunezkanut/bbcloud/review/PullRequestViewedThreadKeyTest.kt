package com.github.fnunezkanut.bbcloud.review

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestViewedThreadKeyTest {
    @Test
    fun `key and entry - should round trip workspace thread identity and last at-sign`() {
        // GIVEN
        val key = PullRequestViewedThreadKey.of(
            workspaceSlug = "team",
            pullRequestId = 42,
            relativePath = "folder:name/file@name.kt",
            rootCommentId = 7
        )
        val fingerprint = "fingerprint"

        // WHEN
        val decoded = PullRequestViewedThreadKey.decodeEntry(
            PullRequestViewedThreadKey.entryOf(key = key, fingerprint = fingerprint)
        )

        // THEN
        assertSoftly {
            key shouldBe "team/42:folder:name/file@name.kt:7"
            decoded shouldBe ViewedThreadEntry(key = key, fingerprint = fingerprint)
        }
    }

    @Test
    fun `key segments - should parse workspace pull request and root comment`() {
        // GIVEN
        val key = PullRequestViewedThreadKey.of(
            workspaceSlug = "team",
            pullRequestId = 42,
            relativePath = "folder:name/src/a.kt",
            rootCommentId = 7
        )

        // WHEN
        val workspaceSlug = PullRequestViewedThreadKey.workspaceSlugOf(key)
        val pullRequestId = PullRequestViewedThreadKey.pullRequestIdOf(key)
        val rootCommentId = PullRequestViewedThreadKey.rootCommentIdOf(key)

        // THEN
        assertSoftly {
            workspaceSlug shouldBe "team"
            pullRequestId shouldBe 42L
            rootCommentId shouldBe 7L
        }
    }

    @Test
    fun `keysToPrune - should remove only stale keys in the requested workspace and pull request`() {
        // GIVEN
        val staleKey = PullRequestViewedThreadKey.of(
            workspaceSlug = "team",
            pullRequestId = 42,
            relativePath = "src/stale.kt",
            rootCommentId = 1
        )
        val keptKey = PullRequestViewedThreadKey.of(
            workspaceSlug = "team",
            pullRequestId = 42,
            relativePath = "src/current.kt",
            rootCommentId = 2
        )
        val otherPullRequestKey = PullRequestViewedThreadKey.of(
            workspaceSlug = "team",
            pullRequestId = 43,
            relativePath = "src/stale.kt",
            rootCommentId = 3
        )
        val otherWorkspaceKey = PullRequestViewedThreadKey.of(
            workspaceSlug = "other",
            pullRequestId = 42,
            relativePath = "src/stale.kt",
            rootCommentId = 4
        )

        // WHEN
        val result = PullRequestViewedThreadKey.keysToPrune(
            keys = setOf(staleKey, keptKey, otherPullRequestKey, otherWorkspaceKey),
            workspaceSlug = "team",
            pullRequestId = 42,
            keptRootCommentIds = setOf(2L)
        )

        // THEN
        result shouldBe setOf(staleKey)
    }

    @Test
    fun `decodeEntry - should reject entries without a separator`() {
        PullRequestViewedThreadKey.decodeEntry("malformed") shouldBe null
    }
}