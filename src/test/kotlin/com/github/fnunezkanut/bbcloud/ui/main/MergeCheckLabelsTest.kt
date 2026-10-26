package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.MergeCheckStatus
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestMergeCheck
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class MergeCheckLabelsTest {
    @Nested
    inner class LabelForTests {
        @Test
        fun `labelFor - should use the configured minimum approvals`() {
            // GIVEN
            val check = check(kind = "minimum_approvals", requiredValue = 2)

            // WHEN
            val label = MergeCheckLabels.labelFor(check)

            // THEN
            label shouldBe MergeCheckLabel.Message(key = "main.branch.checks.kind.minimumApprovals", count = 2)
        }

        @Test
        fun `labelFor - should default the minimum successful builds to one`() {
            // GIVEN
            val check = check(kind = "minimum_successful_builds", requiredValue = null)

            // WHEN
            val label = MergeCheckLabels.labelFor(check)

            // THEN
            label shouldBe MergeCheckLabel.Message(key = "main.branch.checks.kind.minimumSuccessfulBuilds", count = 1)
        }

        @Test
        fun `labelFor - should describe zero maximum commits behind as no commits behind`() {
            // GIVEN
            val check = check(kind = "maximum_commits_behind", requiredValue = 0)

            // WHEN
            val label = MergeCheckLabels.labelFor(check)

            // THEN
            label shouldBe MergeCheckLabel.Message(key = "main.branch.checks.kind.noCommitsBehind")
        }

        @Test
        fun `labelFor - should include a positive maximum commits behind`() {
            // GIVEN
            val check = check(kind = "maximum_commits_behind", requiredValue = 5)

            // WHEN
            val label = MergeCheckLabels.labelFor(check)

            // THEN
            label shouldBe MergeCheckLabel.Message(key = "main.branch.checks.kind.maximumCommitsBehind", count = 5)
        }

        @Test
        fun `labelFor - should use the name of a custom check`() {
            // GIVEN
            val check = check(kind = PullRequestMergeCheck.CUSTOM_KIND, name = "Security scan")

            // WHEN
            val label = MergeCheckLabels.labelFor(check)

            // THEN
            label shouldBe MergeCheckLabel.Text("Security scan")
        }

        @Test
        fun `labelFor - should fall back to a generic message for an unnamed custom check`() {
            // GIVEN
            val check = check(kind = PullRequestMergeCheck.CUSTOM_KIND, name = null)

            // WHEN
            val label = MergeCheckLabels.labelFor(check)

            // THEN
            label shouldBe MergeCheckLabel.Message(key = "main.branch.checks.kind.custom")
        }

        @Test
        fun `labelFor - should humanize an unknown kind`() {
            // GIVEN
            val check = check(kind = "require_all_comments_resolved")

            // WHEN
            val label = MergeCheckLabels.labelFor(check)

            // THEN
            label shouldBe MergeCheckLabel.Text("Require all comments resolved")
        }
    }

    @Test
    fun `humanize - should capitalize the first word and separate words with spaces`() {
        // WHEN
        val text = MergeCheckLabels.humanize("NO_failed_builds")

        // THEN
        text shouldBe "No failed builds"
    }

    @Test
    fun `MergeCheckSummary of - should count passed checks and detect blocking ones`() {
        // GIVEN
        val checks = listOf(
            check(kind = "minimum_approvals", status = MergeCheckStatus.Failed),
            check(kind = "resolved_tasks", status = MergeCheckStatus.Passed),
            check(kind = "no_failed_builds", status = MergeCheckStatus.Failed, isBlocking = true),
            check(kind = "no_changes_requested", status = MergeCheckStatus.Passed)
        )

        // WHEN
        val summary = MergeCheckSummary.of(checks)

        // THEN
        summary shouldBe MergeCheckSummary(passed = 2, total = 4, isBlocking = true)
    }

    private fun check(
        kind: String,
        name: String? = null,
        status: MergeCheckStatus = MergeCheckStatus.Passed,
        isBlocking: Boolean = false,
        requiredValue: Int? = null
    ): PullRequestMergeCheck {
        return PullRequestMergeCheck(
            kind = kind,
            name = name,
            status = status,
            isBlocking = isBlocking,
            requiredValue = requiredValue,
            observedValue = null
        )
    }
}