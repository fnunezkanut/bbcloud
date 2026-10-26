package com.github.fnunezkanut.bbcloud.review

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestViewedFileKeyTest {
    @Test
    fun `of - should normalize relative paths`() {
        // GIVEN
        val normalizedPathKey = PullRequestViewedFileKey.of(pullRequestId = 42, relativePath = "src/a.kt")

        // WHEN
        val dottedPathKey = PullRequestViewedFileKey.of(pullRequestId = 42, relativePath = "./src/a.kt")

        // THEN
        dottedPathKey shouldBe normalizedPathKey
    }

    @Test
    fun `of - should distinguish pull requests`() {
        // GIVEN
        val firstKey = PullRequestViewedFileKey.of(pullRequestId = 42, relativePath = "src/a.kt")

        // WHEN
        val secondKey = PullRequestViewedFileKey.of(pullRequestId = 43, relativePath = "src/a.kt")

        // THEN
        firstKey shouldBe "42:src/a.kt"
        secondKey shouldBe "43:src/a.kt"
        firstKey shouldNotBe secondKey
    }

    @Test
    fun `pullRequestIdOf - should round trip and reject malformed keys`() {
        // GIVEN
        val key = PullRequestViewedFileKey.of(pullRequestId = 42, relativePath = "src/a.kt")

        // WHEN
        val parsed = PullRequestViewedFileKey.pullRequestIdOf(key)

        // THEN
        assertSoftly {
            parsed shouldBe 42L
            PullRequestViewedFileKey.pullRequestIdOf("abc:src/a.kt") shouldBe null
            PullRequestViewedFileKey.pullRequestIdOf("no-colon") shouldBe null
        }
    }

    @Test
    fun `keysToPrune - should remove only stale keys of the requested pull request`() {
        // GIVEN
        val staleKey = PullRequestViewedFileKey.of(pullRequestId = 42, relativePath = "src/stale.kt")
        val keptKey = PullRequestViewedFileKey.of(pullRequestId = 42, relativePath = "src/current.kt")
        val otherPullRequestKey = PullRequestViewedFileKey.of(pullRequestId = 43, relativePath = "src/stale.kt")
        val malformedKey = "unknown-key"

        // WHEN
        val result = PullRequestViewedFileKey.keysToPrune(
            keys = setOf(staleKey, keptKey, otherPullRequestKey, malformedKey),
            pullRequestId = 42,
            keptPaths = setOf("./src/current.kt")
        )

        // THEN
        assertSoftly {
            result shouldBe setOf(staleKey)
            (otherPullRequestKey in result) shouldBe false
            (malformedKey in result) shouldBe false
        }
    }

    @Test
    fun `pullRequestIdOf - should parse paths containing a colon`() {
        // GIVEN
        val key = PullRequestViewedFileKey.of(pullRequestId = 42, relativePath = "folder:name/src/a.kt")

        // WHEN
        val result = PullRequestViewedFileKey.pullRequestIdOf(key)

        // THEN
        result shouldBe 42L
    }

    @Test
    fun `entryOf and decodeEntry - should use the last at-sign`() {
        // GIVEN
        val key = PullRequestViewedFileKey.of(pullRequestId = 42, relativePath = "folder:name/file@name.kt")
        val revision = "0123456789abcdef"

        // WHEN
        val result = PullRequestViewedFileKey.decodeEntry(PullRequestViewedFileKey.entryOf(key = key, revision = revision))

        // THEN
        result shouldBe ViewedFileEntry(key = key, revision = revision)
    }

    @Test
    fun `decodeEntry - should preserve deleted revisions and reject entries without a separator`() {
        // GIVEN
        val deletedEntry = "1:src/a.kt@"

        // WHEN
        val result = PullRequestViewedFileKey.decodeEntry(deletedEntry)

        // THEN
        assertSoftly {
            result shouldBe ViewedFileEntry(key = "1:src/a.kt", revision = PullRequestViewedFileKey.DELETED_REVISION)
            PullRequestViewedFileKey.decodeEntry("1:src/a.kt") shouldBe null
        }
    }
}