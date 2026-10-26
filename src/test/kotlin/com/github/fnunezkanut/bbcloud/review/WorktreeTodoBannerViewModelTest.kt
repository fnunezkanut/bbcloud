@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.github.fnunezkanut.bbcloud.review

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.ui.main.HeaderLinesChanged
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class WorktreeTodoBannerViewModelTest {
    @Test
    fun `resolveState - should resolve slug and pull request id from the work tree folder name`() {
        // GIVEN
        val inputs = inputs(
            projectBasePath = "/home/dev/.worktrees/examplerepo--pr-42",
            pullRequestKnownOpen = true
        )

        // WHEN
        val state = WorktreeTodoBannerViewModel.resolveState(inputs = inputs, previous = WorktreeTodoBannerUiState())

        // THEN
        assertSoftly(state) {
            isWorktree shouldBe true
            repoSlug shouldBe "examplerepo"
            pullRequestId shouldBe 42L
        }
    }

    @Test
    fun `resolveState - should mark a non-worktree folder as not a work tree`() {
        // GIVEN
        val inputs = inputs(projectBasePath = "/home/dev/projects/examplerepo")

        // WHEN
        val state = WorktreeTodoBannerViewModel.resolveState(inputs = inputs, previous = WorktreeTodoBannerUiState())

        // THEN
        state.isWorktree shouldBe false
    }

    @Test
    fun `resolveState - should prefer the binding branches over the summary branches`() {
        // GIVEN
        val inputs = inputs(
            projectBasePath = "/home/dev/.worktrees/examplerepo--pr-42",
            binding = binding(sourceBranch = "feature/from-binding", destinationBranch = "main"),
            summary = summary(sourceBranch = "feature/from-summary", destinationBranch = "develop")
        )

        // WHEN
        val state = WorktreeTodoBannerViewModel.resolveState(inputs = inputs, previous = WorktreeTodoBannerUiState())

        // THEN
        assertSoftly(state) {
            sourceBranch shouldBe "feature/from-binding"
            destinationBranch shouldBe "main"
        }
    }

    @Test
    fun `resolveState - should fall back to the summary branches when there is no binding`() {
        // GIVEN
        val inputs = inputs(
            projectBasePath = "/home/dev/.worktrees/examplerepo--pr-42",
            binding = null,
            summary = summary(sourceBranch = "feature/from-summary", destinationBranch = "develop")
        )

        // WHEN
        val state = WorktreeTodoBannerViewModel.resolveState(inputs = inputs, previous = WorktreeTodoBannerUiState())

        // THEN
        assertSoftly(state) {
            sourceBranch shouldBe "feature/from-summary"
            destinationBranch shouldBe "develop"
        }
    }

    @Test
    fun `resolveState - should leave branches null while nothing has resolved`() {
        // GIVEN
        val inputs = inputs(
            projectBasePath = "/home/dev/.worktrees/examplerepo--pr-42",
            binding = null,
            summary = null
        )

        // WHEN
        val state = WorktreeTodoBannerViewModel.resolveState(inputs = inputs, previous = WorktreeTodoBannerUiState())

        // THEN
        assertSoftly(state) {
            sourceBranch shouldBe null
            destinationBranch shouldBe null
        }
    }

    @Test
    fun `resolveState - should prefer the summary web url over a built one`() {
        // GIVEN
        val inputs = inputs(
            projectBasePath = "/home/dev/.worktrees/examplerepo--pr-42",
            organizationUrl = "https://bitbucket.org/my-workspace/",
            summary = summary(webUrl = "https://bitbucket.org/my-workspace/examplerepo/pull-requests/42")
        )

        // WHEN
        val state = WorktreeTodoBannerViewModel.resolveState(inputs = inputs, previous = WorktreeTodoBannerUiState())

        // THEN
        state.pullRequestWebUrl shouldBe "https://bitbucket.org/my-workspace/examplerepo/pull-requests/42"
    }

    @Test
    fun `resolveState - should build the web url from the organization url when no summary url is known`() {
        // GIVEN
        val inputs = inputs(
            projectBasePath = "/home/dev/.worktrees/examplerepo--pr-42",
            organizationUrl = "https://bitbucket.org/my-workspace/",
            summary = null,
            pullRequestKnownOpen = true
        )

        // WHEN
        val state = WorktreeTodoBannerViewModel.resolveState(inputs = inputs, previous = WorktreeTodoBannerUiState())

        // THEN
        state.pullRequestWebUrl shouldBe "https://bitbucket.org/my-workspace/examplerepo/pull-requests/42"
    }

    @Test
    fun `resolveState - should preserve the refresh enabled flag from the previous state`() {
        // GIVEN
        val inputs = inputs(projectBasePath = "/home/dev/.worktrees/examplerepo--pr-42")
        val previous = WorktreeTodoBannerUiState(refreshEnabled = false)

        // WHEN
        val state = WorktreeTodoBannerViewModel.resolveState(inputs = inputs, previous = previous)

        // THEN
        state.refreshEnabled shouldBe false
    }

    @Test
    fun `resolveState - should preserve the lines-changed indicator when other fields update`() {
        // GIVEN
        val linesChanged = HeaderLinesChanged(emoji = "\uD83D\uDE42", addedText = "+12", removedText = "-3", tooltip = "tooltip")
        val previous = WorktreeTodoBannerUiState(
            isWorktree = true,
            repoSlug = "examplerepo",
            pullRequestId = 42L,
            sourceBranch = "feature/old",
            destinationBranch = "main",
            linesChanged = linesChanged
        )
        val inputs = inputs(
            projectBasePath = "/home/dev/.worktrees/examplerepo--pr-42",
            binding = binding(sourceBranch = "feature/new", destinationBranch = "main"),
            pullRequestKnownOpen = true
        )

        // WHEN
        val state = WorktreeTodoBannerViewModel.resolveState(inputs = inputs, previous = previous)

        // THEN
        assertSoftly(state) {
            sourceBranch shouldBe "feature/new"
            linesChanged shouldBe linesChanged
        }
    }

    @Test
    fun `resolveState - should hide the bar for a merged pull request summary`() {
        // GIVEN
        val inputs = inputs(
            projectBasePath = "/home/dev/.worktrees/examplerepo--pr-42",
            summary = summary(state = PullRequestState.Merged)
        )

        // WHEN
        val state = WorktreeTodoBannerViewModel.resolveState(inputs = inputs, previous = WorktreeTodoBannerUiState())

        // THEN
        state.isWorktree shouldBe false
    }

    @Test
    fun `resolveState - should hide the bar for a declined pull request summary`() {
        // GIVEN
        val inputs = inputs(
            projectBasePath = "/home/dev/.worktrees/examplerepo--pr-42",
            summary = summary(state = PullRequestState.Declined)
        )

        // WHEN
        val state = WorktreeTodoBannerViewModel.resolveState(inputs = inputs, previous = WorktreeTodoBannerUiState())

        // THEN
        state.isWorktree shouldBe false
    }

    @Test
    fun `resolveState - should show the bar for a draft open pull request summary`() {
        // GIVEN
        val inputs = inputs(
            projectBasePath = "/home/dev/.worktrees/examplerepo--pr-42",
            summary = summary(state = PullRequestState.Open, isDraft = true)
        )

        // WHEN
        val state = WorktreeTodoBannerViewModel.resolveState(inputs = inputs, previous = WorktreeTodoBannerUiState())

        // THEN
        state.isWorktree shouldBe true
    }

    @Test
    fun `resolveState - should keep the known-closed flag inert when there is no summary`() {
        // GIVEN
        val inputs = inputs(
            projectBasePath = "/home/dev/.worktrees/examplerepo--pr-42",
            summary = null,
            pullRequestKnownClosed = true
        )

        // WHEN
        val state = WorktreeTodoBannerViewModel.resolveState(inputs = inputs, previous = WorktreeTodoBannerUiState())

        // THEN
        state.isWorktree shouldBe false
    }

    @Test
    fun `resolveState - should hide the bar when a parsed work tree has no summary and neither flag is set`() {
        // GIVEN
        val inputs = inputs(
            projectBasePath = "/home/dev/.worktrees/examplerepo--pr-42",
            summary = null,
            pullRequestKnownOpen = false,
            pullRequestKnownClosed = false
        )

        // WHEN
        val state = WorktreeTodoBannerViewModel.resolveState(inputs = inputs, previous = WorktreeTodoBannerUiState())

        // THEN
        state.isWorktree shouldBe false
    }

    @Test
    fun `resolveState - should show the bar when the pull request is known open even without a summary or binding`() {
        // GIVEN
        val inputs = inputs(
            projectBasePath = "/home/dev/.worktrees/examplerepo--pr-42",
            binding = null,
            summary = null,
            pullRequestKnownOpen = true
        )

        // WHEN
        val state = WorktreeTodoBannerViewModel.resolveState(inputs = inputs, previous = WorktreeTodoBannerUiState())

        // THEN
        assertSoftly(state) {
            isWorktree shouldBe true
            repoSlug shouldBe "examplerepo"
            pullRequestId shouldBe 42L
        }
    }

    @Test
    fun `resolveState - should clear the stale lines-changed indicator when the pull request is proven closed`() {
        // GIVEN
        val linesChanged = HeaderLinesChanged(emoji = "\uD83D\uDE42", addedText = "+12", removedText = "-3", tooltip = "tooltip")
        val previous = WorktreeTodoBannerUiState(
            isWorktree = true,
            repoSlug = "examplerepo",
            pullRequestId = 42L,
            linesChanged = linesChanged
        )
        val inputs = inputs(
            projectBasePath = "/home/dev/.worktrees/examplerepo--pr-42",
            summary = summary(state = PullRequestState.Merged)
        )

        // WHEN
        val state = WorktreeTodoBannerViewModel.resolveState(inputs = inputs, previous = previous)

        // THEN
        assertSoftly(state) {
            isWorktree shouldBe false
            this.linesChanged shouldBe null
        }
    }

    private fun inputs(
        projectBasePath: String?,
        organizationUrl: String? = "https://bitbucket.org/my-workspace/",
        binding: WorktreePullRequestBinding? = null,
        summary: PullRequestSummary? = null,
        repositoryAvatarUrl: String? = null,
        pullRequestKnownClosed: Boolean = false,
        pullRequestKnownOpen: Boolean = false
    ): WorktreeTodoBannerInputs {
        return WorktreeTodoBannerInputs(
            projectBasePath = projectBasePath,
            organizationUrl = organizationUrl,
            binding = binding,
            summary = summary,
            repositoryAvatarUrl = repositoryAvatarUrl,
            pullRequestKnownClosed = pullRequestKnownClosed,
            pullRequestKnownOpen = pullRequestKnownOpen
        )
    }

    private fun binding(sourceBranch: String, destinationBranch: String): WorktreePullRequestBinding {
        return WorktreePullRequestBinding(
            pullRequestId = 42L,
            sourceBranch = sourceBranch,
            destinationBranch = destinationBranch,
            headRevision = "abc123",
            changedFilePaths = emptySet(),
            fileLineCounts = emptyMap()
        )
    }

    private fun summary(
        sourceBranch: String = "feature",
        destinationBranch: String = "main",
        webUrl: String = "",
        state: PullRequestState = PullRequestState.Open,
        isDraft: Boolean = false
    ): PullRequestSummary {
        return PullRequestSummary(
            id = 42L,
            title = "PR 42",
            authorDisplayName = "Author",
            authorAvatarUrl = null,
            createdOn = Instant.parse("2026-01-01T00:00:00Z"),
            updatedOn = Instant.parse("2026-01-02T00:00:00Z"),
            sourceBranch = sourceBranch,
            destinationBranch = destinationBranch,
            commentCount = 0,
            webUrl = webUrl,
            state = state,
            isDraft = isDraft
        )
    }
}