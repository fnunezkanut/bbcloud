package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class MentionCandidateTest {
    private val uuid = "{11111111-1111-1111-1111-111111111111}"
    private val accountId = "712020:aaaa-bbbb"

    @Nested
    inner class MentionKey {
        @Test
        fun `mentionKey - should prefer the account id`() {
            // GIVEN
            val candidate = candidate(uuid = uuid, accountId = accountId)

            // WHEN
            val key = candidate.mentionKey()

            // THEN
            key shouldBe accountId
        }

        @Test
        fun `mentionKey - should fall back to the brace-less uuid`() {
            // GIVEN
            val candidate = candidate(uuid = uuid, accountId = null)

            // WHEN
            val key = candidate.mentionKey()

            // THEN
            key shouldBe "11111111-1111-1111-1111-111111111111"
        }

        @Test
        fun `mentionKey - should be null when both are missing`() {
            // GIVEN
            val candidate = candidate(uuid = null, accountId = null)

            // WHEN
            val key = candidate.mentionKey()

            // THEN
            key.shouldBeNull()
        }
    }

    @Nested
    inner class IdentityKey {
        @Test
        fun `identityKey - should normalize the uuid case and braces`() {
            // GIVEN
            val candidate = candidate(uuid = "{AAAAAAAA-1111-1111-1111-111111111111}", accountId = null)

            // WHEN
            val key = candidate.identityKey()

            // THEN
            key shouldBe "aaaaaaaa-1111-1111-1111-111111111111"
        }

        @Test
        fun `identityKey - should prefix the account id when the uuid is missing`() {
            // GIVEN
            val candidate = candidate(uuid = null, accountId = "712020:AAAA-BBBB")

            // WHEN
            val key = candidate.identityKey()

            // THEN
            key shouldBe "account:712020:aaaa-bbbb"
        }
    }

    @Nested
    inner class Factories {
        @Test
        fun `toMentionCandidate - should map an activity user`() {
            // GIVEN
            val user = ActivityUser(uuid = uuid, displayName = "Anna", avatarUrl = "https://avatar/a", accountId = accountId)

            // WHEN
            val candidate = user.toMentionCandidate(tier = MentionCandidateTier.PullRequest)

            // THEN
            candidate.shouldNotBeNull()
            assertSoftly(candidate) {
                displayName shouldBe "Anna"
                this.accountId shouldBe accountId
                tier shouldBe MentionCandidateTier.PullRequest
            }
        }

        @Test
        fun `toMentionCandidate - should map a pull request reviewer`() {
            // GIVEN
            val reviewer = PullRequestReviewer(
                uuid = uuid,
                displayName = "Anna",
                avatarUrl = null,
                status = ReviewerStatus.None,
                accountId = accountId
            )

            // WHEN
            val candidate = reviewer.toMentionCandidate(tier = MentionCandidateTier.Workspace)

            // THEN
            candidate?.mentionKey() shouldBe accountId
        }

        @Test
        fun `toMentionCandidate - should map a reviewer candidate with its nickname`() {
            // GIVEN
            val reviewerCandidate = ReviewerCandidate(uuid = uuid, displayName = "Anna", nickname = "anna", avatarUrl = null, accountId = accountId)

            // WHEN
            val candidate = reviewerCandidate.toMentionCandidate(tier = MentionCandidateTier.Workspace)

            // THEN
            candidate?.nickname shouldBe "anna"
        }

        @Test
        fun `authorMentionCandidate - should map the author`() {
            // GIVEN
            val summary = summary(authorDisplayName = "Anna")

            // WHEN
            val candidate = summary.authorMentionCandidate(tier = MentionCandidateTier.PullRequest)

            // THEN
            candidate?.displayName shouldBe "Anna"
        }

        @Test
        fun `authorMentionCandidate - should skip an Unknown author`() {
            // GIVEN
            val summary = summary(authorDisplayName = "Unknown")

            // WHEN
            val candidate = summary.authorMentionCandidate(tier = MentionCandidateTier.PullRequest)

            // THEN
            candidate.shouldBeNull()
        }
    }

    private fun candidate(uuid: String?, accountId: String?): MentionCandidate {
        return MentionCandidate(
            uuid = uuid,
            accountId = accountId,
            displayName = "Anna",
            nickname = null,
            avatarUrl = null,
            tier = MentionCandidateTier.PullRequest
        )
    }

    private fun summary(authorDisplayName: String): PullRequestSummary {
        return PullRequestSummary(
            id = 1,
            title = "PR 1",
            authorDisplayName = authorDisplayName,
            authorAvatarUrl = null,
            createdOn = kotlin.time.Instant.parse("2026-09-28T10:00:00Z"),
            updatedOn = kotlin.time.Instant.parse("2026-09-28T10:00:00Z"),
            sourceBranch = "feature",
            destinationBranch = "main",
            commentCount = 0,
            webUrl = "https://bitbucket.org/workspace/repo/pull-requests/1",
            state = PullRequestState.Open,
            isDraft = false,
            authorUuid = uuid,
            authorAccountId = accountId
        )
    }
}