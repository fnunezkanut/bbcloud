package com.github.fnunezkanut.bbcloud.git

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class LocalPrBranchBehindRemoteCheckTest {
    private val sourceBranch = "feature/BB-123-local-behind-remote"
    private val localRevision = "aaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbb"

    @Nested
    inner class Count {
        @Test
        fun `evaluate - should hide when the local branch is up to date`() {
            // GIVEN
            val commitsBehindRemote = 0

            // WHEN
            val decision = LocalPrBranchBehindRemoteCheck.evaluate(
                currentBranchName = sourceBranch,
                sourceBranch = sourceBranch,
                localRevision = localRevision,
                commitsBehindRemote = commitsBehindRemote
            )

            // THEN
            decision shouldBe LocalBehindRemoteDecision.Hidden
        }

        @Test
        fun `evaluate - should show the count when the local branch is behind`() {
            // GIVEN
            val commitsBehindRemote = 3

            // WHEN
            val decision = LocalPrBranchBehindRemoteCheck.evaluate(
                currentBranchName = sourceBranch,
                sourceBranch = sourceBranch,
                localRevision = localRevision,
                commitsBehindRemote = commitsBehindRemote
            )

            // THEN
            val behind = decision.shouldBeInstanceOf<LocalBehindRemoteDecision.Behind>()
            behind.commitsBehind shouldBe 3
        }

        @Test
        fun `evaluate - should hide when the count could not be computed`() {
            // GIVEN
            val commitsBehindRemote: Int? = null

            // WHEN
            val decision = LocalPrBranchBehindRemoteCheck.evaluate(
                currentBranchName = sourceBranch,
                sourceBranch = sourceBranch,
                localRevision = localRevision,
                commitsBehindRemote = commitsBehindRemote
            )

            // THEN
            decision shouldBe LocalBehindRemoteDecision.Hidden
        }

        @Test
        fun `evaluate - should hide a negative count defensively`() {
            // GIVEN
            val commitsBehindRemote = -1

            // WHEN
            val decision = LocalPrBranchBehindRemoteCheck.evaluate(
                currentBranchName = sourceBranch,
                sourceBranch = sourceBranch,
                localRevision = localRevision,
                commitsBehindRemote = commitsBehindRemote
            )

            // THEN
            decision shouldBe LocalBehindRemoteDecision.Hidden
        }
    }

    @Nested
    inner class BranchGate {
        @Test
        fun `evaluate - should hide when checked out on a different branch regardless of the count`() {
            // GIVEN
            val commitsBehindRemote = 5

            // WHEN
            val decision = LocalPrBranchBehindRemoteCheck.evaluate(
                currentBranchName = "feature/other",
                sourceBranch = sourceBranch,
                localRevision = localRevision,
                commitsBehindRemote = commitsBehindRemote
            )

            // THEN
            decision shouldBe LocalBehindRemoteDecision.Hidden
        }

        @Test
        fun `evaluate - should hide on a detached head regardless of the count`() {
            // GIVEN
            val commitsBehindRemote = 5

            // WHEN
            val decision = LocalPrBranchBehindRemoteCheck.evaluate(
                currentBranchName = null,
                sourceBranch = sourceBranch,
                localRevision = localRevision,
                commitsBehindRemote = commitsBehindRemote
            )

            // THEN
            decision shouldBe LocalBehindRemoteDecision.Hidden
        }

        @Test
        fun `evaluate - should match a branch written with the refs heads prefix`() {
            // GIVEN
            val commitsBehindRemote = 2

            // WHEN
            val decision = LocalPrBranchBehindRemoteCheck.evaluate(
                currentBranchName = "refs/heads/$sourceBranch",
                sourceBranch = sourceBranch,
                localRevision = localRevision,
                commitsBehindRemote = commitsBehindRemote
            )

            // THEN
            val behind = decision.shouldBeInstanceOf<LocalBehindRemoteDecision.Behind>()
            behind.commitsBehind shouldBe 2
        }
    }

    @Nested
    inner class Revision {
        @Test
        fun `evaluate - should hide when the local revision is blank`() {
            // GIVEN
            val blankRevision = ""

            // WHEN
            val decision = LocalPrBranchBehindRemoteCheck.evaluate(
                currentBranchName = sourceBranch,
                sourceBranch = sourceBranch,
                localRevision = blankRevision,
                commitsBehindRemote = 4
            )

            // THEN
            decision shouldBe LocalBehindRemoteDecision.Hidden
        }

        @Test
        fun `evaluate - should hide when the local revision is null`() {
            // GIVEN
            val nullRevision: String? = null

            // WHEN
            val decision = LocalPrBranchBehindRemoteCheck.evaluate(
                currentBranchName = sourceBranch,
                sourceBranch = sourceBranch,
                localRevision = nullRevision,
                commitsBehindRemote = 4
            )

            // THEN
            decision shouldBe LocalBehindRemoteDecision.Hidden
        }
    }
}