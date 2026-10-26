package com.github.fnunezkanut.bbcloud.review

import com.github.fnunezkanut.bbcloud.bitbucket.ActivityUser
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class PullRequestThreadFingerprintTest {
    @Test
    fun `of - should be stable for identical content`() {
        // GIVEN
        val first = thread(replyRaw = null)
        val second = thread(replyRaw = null)

        // WHEN
        val firstFingerprint = PullRequestThreadFingerprint.of(thread = first)
        val secondFingerprint = PullRequestThreadFingerprint.of(thread = second)

        // THEN
        firstFingerprint shouldBe secondFingerprint
    }

    @Test
    fun `of - should change when a reply is added`() {
        // GIVEN
        val withoutReply = thread(replyRaw = null)
        val withReply = thread(replyRaw = "reply")

        // WHEN
        val withoutReplyFingerprint = PullRequestThreadFingerprint.of(thread = withoutReply)
        val withReplyFingerprint = PullRequestThreadFingerprint.of(thread = withReply)

        // THEN
        withoutReplyFingerprint shouldNotBe withReplyFingerprint
    }

    @Test
    fun `of - should change when a comment body changes`() {
        // GIVEN
        val original = thread(rootRaw = "original")
        val edited = thread(rootRaw = "edited")

        // WHEN
        val originalFingerprint = PullRequestThreadFingerprint.of(thread = original)
        val editedFingerprint = PullRequestThreadFingerprint.of(thread = edited)

        // THEN
        originalFingerprint shouldNotBe editedFingerprint
    }

    @Test
    fun `of - should change when a comment is deleted`() {
        // GIVEN
        val visible = thread(rootDeleted = false)
        val deleted = thread(rootDeleted = true)

        // WHEN
        val visibleFingerprint = PullRequestThreadFingerprint.of(thread = visible)
        val deletedFingerprint = PullRequestThreadFingerprint.of(thread = deleted)

        // THEN
        visibleFingerprint shouldNotBe deletedFingerprint
    }

    private fun thread(rootRaw: String = "root", rootDeleted: Boolean = false, replyRaw: String? = null): TimelineEntry.CommentThread {
        val root = comment(id = 1, parentId = null, raw = rootRaw, isDeleted = rootDeleted)
        val replies = replyRaw?.let { raw -> listOf(comment(id = 2, parentId = 1, raw = raw)) }.orEmpty()
        return TimelineEntry.CommentThread(root = root, replies = replies)
    }

    private fun comment(id: Long, parentId: Long?, raw: String, isDeleted: Boolean = false): PullRequestComment {
        return PullRequestComment(
            id = id,
            parentId = parentId,
            createdOn = Instant.parse("2026-01-01T00:00:00Z"),
            author = ActivityUser(uuid = "user", displayName = "User", avatarUrl = null),
            html = raw,
            inlinePath = null,
            inlineLine = null,
            inlineSide = null,
            isResolved = false,
            isDeleted = isDeleted,
            isPending = false,
            raw = raw
        )
    }
}