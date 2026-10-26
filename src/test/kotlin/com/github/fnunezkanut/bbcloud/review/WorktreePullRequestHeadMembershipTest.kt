package com.github.fnunezkanut.bbcloud.review

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class WorktreePullRequestHeadMembershipTest {
    private val sourceBranch = "feature/BB-123-review-bar"

    @Nested
    inner class Branch {
        @Test
        fun `membership - should accept a matching branch whose head is a pull request commit`() {
            // GIVEN
            val headRevision = "aaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbb"

            // WHEN
            val result = WorktreePullRequestHeadMembership.isHeadPartOfPullRequest(
                currentBranchName = sourceBranch,
                headRevision = headRevision,
                sourceBranch = sourceBranch,
                pullRequestCommitHashes = listOf(headRevision),
                isCommitListTruncated = false,
                commitsKnown = true,
                headIsDescendantOfPullRequestTip = false
            )

            // THEN
            result.shouldBeTrue()
        }

        @Test
        fun `membership - should accept a matching branch written with the refs heads prefix`() {
            // GIVEN
            val headRevision = "aaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbb"

            // WHEN
            val result = WorktreePullRequestHeadMembership.isHeadPartOfPullRequest(
                currentBranchName = "refs/heads/$sourceBranch",
                headRevision = headRevision,
                sourceBranch = "refs/heads/$sourceBranch",
                pullRequestCommitHashes = listOf(headRevision),
                isCommitListTruncated = false,
                commitsKnown = true,
                headIsDescendantOfPullRequestTip = false
            )

            // THEN
            result.shouldBeTrue()
        }

        @Test
        fun `membership - should reject a branch that does not match the pull request`() {
            // GIVEN
            val headRevision = "aaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbb"

            // WHEN
            val result = WorktreePullRequestHeadMembership.isHeadPartOfPullRequest(
                currentBranchName = "feature/other",
                headRevision = headRevision,
                sourceBranch = sourceBranch,
                pullRequestCommitHashes = listOf("cccccccccccccccccccccccccccccccccccccccc"),
                isCommitListTruncated = false,
                commitsKnown = true,
                headIsDescendantOfPullRequestTip = false
            )

            // THEN
            result.shouldBeFalse()
        }

        @Test
        fun `membership - should reject a missing head revision`() {
            // GIVEN
            val headRevision = ""

            // WHEN
            val result = WorktreePullRequestHeadMembership.isHeadPartOfPullRequest(
                currentBranchName = sourceBranch,
                headRevision = headRevision,
                sourceBranch = sourceBranch,
                pullRequestCommitHashes = listOf("aaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbb"),
                isCommitListTruncated = false,
                commitsKnown = true,
                headIsDescendantOfPullRequestTip = false
            )

            // THEN
            result.shouldBeFalse()
        }
    }

    @Nested
    inner class Detached {
        @Test
        fun `membership - should accept a detached head sitting on a pull request commit`() {
            // GIVEN
            val headRevision = "aaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbb"

            // WHEN
            val result = WorktreePullRequestHeadMembership.isHeadPartOfPullRequest(
                currentBranchName = null,
                headRevision = headRevision,
                sourceBranch = sourceBranch,
                pullRequestCommitHashes = listOf(headRevision),
                isCommitListTruncated = false,
                commitsKnown = true,
                headIsDescendantOfPullRequestTip = false
            )

            // THEN
            result.shouldBeTrue()
        }

        @Test
        fun `membership - should reject a detached head that is not a pull request commit`() {
            // GIVEN
            val headRevision = "aaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbb"

            // WHEN
            val result = WorktreePullRequestHeadMembership.isHeadPartOfPullRequest(
                currentBranchName = null,
                headRevision = headRevision,
                sourceBranch = sourceBranch,
                pullRequestCommitHashes = listOf("cccccccccccccccccccccccccccccccccccccccc"),
                isCommitListTruncated = false,
                commitsKnown = true,
                headIsDescendantOfPullRequestTip = false
            )

            // THEN
            result.shouldBeFalse()
        }
    }

    @Nested
    inner class Descendant {
        @Test
        fun `membership - should accept a matching branch whose head is a descendant of the pull request tip`() {
            // GIVEN
            val headRevision = "dddddddddddddddddddddddddddddddddddddddd"

            // WHEN
            val result = WorktreePullRequestHeadMembership.isHeadPartOfPullRequest(
                currentBranchName = sourceBranch,
                headRevision = headRevision,
                sourceBranch = sourceBranch,
                pullRequestCommitHashes = listOf("aaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbb"),
                isCommitListTruncated = false,
                commitsKnown = true,
                headIsDescendantOfPullRequestTip = true
            )

            // THEN
            result.shouldBeTrue()
        }

        @Test
        fun `membership - should reject a matching branch whose head is neither a commit nor a descendant`() {
            // GIVEN
            val headRevision = "dddddddddddddddddddddddddddddddddddddddd"

            // WHEN
            val result = WorktreePullRequestHeadMembership.isHeadPartOfPullRequest(
                currentBranchName = sourceBranch,
                headRevision = headRevision,
                sourceBranch = sourceBranch,
                pullRequestCommitHashes = listOf("aaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbb"),
                isCommitListTruncated = false,
                commitsKnown = true,
                headIsDescendantOfPullRequestTip = false
            )

            // THEN
            result.shouldBeFalse()
        }

        @Test
        fun `membership - should accept a matching branch when the commit list is truncated`() {
            // GIVEN
            val headRevision = "dddddddddddddddddddddddddddddddddddddddd"

            // WHEN
            val result = WorktreePullRequestHeadMembership.isHeadPartOfPullRequest(
                currentBranchName = sourceBranch,
                headRevision = headRevision,
                sourceBranch = sourceBranch,
                pullRequestCommitHashes = listOf("aaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbb"),
                isCommitListTruncated = true,
                commitsKnown = true,
                headIsDescendantOfPullRequestTip = false
            )

            // THEN
            result.shouldBeTrue()
        }
    }

    @Nested
    inner class CommitsUnknown {
        @Test
        fun `membership - should fall back to the branch when the commit list is unknown`() {
            // GIVEN
            val headRevision = "dddddddddddddddddddddddddddddddddddddddd"

            // WHEN
            val result = WorktreePullRequestHeadMembership.isHeadPartOfPullRequest(
                currentBranchName = sourceBranch,
                headRevision = headRevision,
                sourceBranch = sourceBranch,
                pullRequestCommitHashes = emptyList(),
                isCommitListTruncated = false,
                commitsKnown = false,
                headIsDescendantOfPullRequestTip = false
            )

            // THEN
            result.shouldBeTrue()
        }

        @Test
        fun `membership - should reject a branch that does not match when the commit list is unknown`() {
            // GIVEN
            val headRevision = "dddddddddddddddddddddddddddddddddddddddd"

            // WHEN
            val result = WorktreePullRequestHeadMembership.isHeadPartOfPullRequest(
                currentBranchName = "feature/other",
                headRevision = headRevision,
                sourceBranch = sourceBranch,
                pullRequestCommitHashes = emptyList(),
                isCommitListTruncated = false,
                commitsKnown = false,
                headIsDescendantOfPullRequestTip = false
            )

            // THEN
            result.shouldBeFalse()
        }
    }

    @Nested
    inner class Hashes {
        @Test
        fun `membership - should accept a short hash that prefixes the head revision`() {
            // GIVEN
            val headRevision = "abcdef1234567890abcdef1234567890abcdef12"

            // WHEN
            val result = WorktreePullRequestHeadMembership.isHeadPartOfPullRequest(
                currentBranchName = sourceBranch,
                headRevision = headRevision,
                sourceBranch = sourceBranch,
                pullRequestCommitHashes = listOf("abcdef1"),
                isCommitListTruncated = false,
                commitsKnown = true,
                headIsDescendantOfPullRequestTip = false
            )

            // THEN
            result.shouldBeTrue()
        }

        @Test
        fun `membership - should accept a full hash that extends the head revision`() {
            // GIVEN
            val headRevision = "abcdef1"

            // WHEN
            val result = WorktreePullRequestHeadMembership.isHeadPartOfPullRequest(
                currentBranchName = sourceBranch,
                headRevision = headRevision,
                sourceBranch = sourceBranch,
                pullRequestCommitHashes = listOf("abcdef1234567890abcdef1234567890abcdef12"),
                isCommitListTruncated = false,
                commitsKnown = true,
                headIsDescendantOfPullRequestTip = false
            )

            // THEN
            result.shouldBeTrue()
        }

        @Test
        fun `membership - should accept a hash written in upper case`() {
            // GIVEN
            val headRevision = "ABCDEF1234567890ABCDEF1234567890ABCDEF12"

            // WHEN
            val result = WorktreePullRequestHeadMembership.isHeadPartOfPullRequest(
                currentBranchName = sourceBranch,
                headRevision = headRevision,
                sourceBranch = sourceBranch,
                pullRequestCommitHashes = listOf("abcdef1234567890abcdef1234567890abcdef12"),
                isCommitListTruncated = false,
                commitsKnown = true,
                headIsDescendantOfPullRequestTip = false
            )

            // THEN
            result.shouldBeTrue()
        }

        @Test
        fun `membership - should reject a hash that is shorter than the minimum length`() {
            // GIVEN
            val headRevision = "abcdef1234567890abcdef1234567890abcdef12"

            // WHEN
            val result = WorktreePullRequestHeadMembership.isHeadPartOfPullRequest(
                currentBranchName = sourceBranch,
                headRevision = headRevision,
                sourceBranch = sourceBranch,
                pullRequestCommitHashes = listOf("abcdef"),
                isCommitListTruncated = false,
                commitsKnown = true,
                headIsDescendantOfPullRequestTip = false
            )

            // THEN
            result.shouldBeFalse()
        }
    }
}