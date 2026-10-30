package com.github.fnunezkanut.bbcloud.ui.otherprojects

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewingPullRequest
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@Tag("unit")
class WorkspacePullRequestsUiStateTest {
    // Anchor the created/updated instants to "now" so they stay inside PullRequestRelativeTime's 7-day relative window.
    // The absolute fallback (DateFormatUtil.formatPrettyDateTime) needs a running IntelliJ Application, which a plain
    // unit test does not have, so fixed calendar dates would start NPE-ing once they aged past the relative cutoff.
    private val now = Clock.System.now()

    @Test
    fun `buildWorkspacePullRequestRow - should map summary fields to card row`() {
        // GIVEN
        val summary = PullRequestSummary(
            id = 42,
            title = "Improve pull request cards",
            authorDisplayName = "Jane Smith",
            authorAvatarUrl = "https://bitbucket.org/avatar.png",
            createdOn = now - 2.days,
            updatedOn = now - 3.hours,
            sourceBranch = "feature/cards",
            destinationBranch = "main",
            commentCount = 7,
            webUrl = "https://bitbucket.org/acme/repo/pull-requests/42",
            state = PullRequestState.Merged,
            isDraft = false
        )

        // WHEN
        val row = buildWorkspacePullRequestRow(
            pullRequest = ReviewingPullRequest(summary = summary, workspace = "acme", repoSlug = "repo"),
            organizationUrl = "https://bitbucket.org",
            authorAvatarUrl = summary.authorAvatarUrl,
            repoAvatarUrl = "https://bitbucket.org/avatar.png"
        )

        // THEN
        assertSoftly(row) {
            title shouldBe summary.title
            repoSlug shouldBe "repo"
            repoAvatarUrl shouldBe "https://bitbucket.org/avatar.png"
            repositoryWebUrl shouldBe "https://bitbucket.org/acme/repo"
            prId shouldBe "#42"
            branches shouldBe "feature/cards → main"
            isDraft shouldBe false
            state shouldBe PullRequestState.Merged
            commentCount shouldBe 7
            updatedRelative.shouldNotBeEmpty()
            createdRelative.shouldNotBeEmpty()
            pullRequest.summary shouldBe summary
            pullRequest.summary.webUrl shouldBe "https://bitbucket.org/acme/repo/pull-requests/42"
        }
    }

    @Test
    fun `buildWorkspacePullRequestRow - should preserve placeholder data for unresolved repository`() {
        // GIVEN
        val summary = PullRequestSummary(
            id = 7,
            title = "Unresolved repository",
            authorDisplayName = "Jane Smith",
            authorAvatarUrl = null,
            createdOn = now - 2.days,
            updatedOn = now - 3.hours,
            sourceBranch = "feature",
            destinationBranch = "main",
            commentCount = 0,
            webUrl = "https://bitbucket.org/acme/repo/pull-requests/7",
            state = PullRequestState.Open,
            isDraft = false
        )

        // WHEN
        val row = buildWorkspacePullRequestRow(
            pullRequest = ReviewingPullRequest(summary = summary, workspace = "", repoSlug = ""),
            organizationUrl = "https://bitbucket.org",
            authorAvatarUrl = null,
            repoAvatarUrl = null
        )

        // THEN
        assertSoftly(row) {
            repoAvatarUrl shouldBe null
            repoSlug shouldBe ""
            repositoryWebUrl shouldBe null
        }
    }
}