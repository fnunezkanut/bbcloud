package com.github.fnunezkanut.bbcloud.project

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestWorktreeMatchTest {
    @Test
    fun `expectedWorktreePath - returns normalized path for the exact repository and pull request`() {
        val result = PullRequestWorktreeMatch.expectedWorktreePath(
            projectBasePath = "/tmp/worktrees/../worktrees/repo--pr-42",
            workTreeDirectory = "/tmp/worktrees/../worktrees",
            currentRepositorySlug = "repo",
            currentPullRequestId = 42,
            declinedRepositorySlug = "repo",
            declinedPullRequestId = 42
        )

        result.toString() shouldBe "/tmp/worktrees/repo--pr-42"
    }

    @Test
    fun `expectedWorktreePath - rejects repository and pull request mismatches`() {
        val repositoryMismatch = expected(currentRepositorySlug = "other")
        val pullRequestMismatch = expected(currentPullRequestId = 43)

        repositoryMismatch.shouldBeNull()
        pullRequestMismatch.shouldBeNull()
    }

    @Test
    fun `expectedWorktreePath - rejects a normal checkout, wrong root, and missing configuration`() {
        val normalCheckout = PullRequestWorktreeMatch.expectedWorktreePath(
            projectBasePath = "/tmp/projects/repo",
            workTreeDirectory = "/tmp/worktrees",
            currentRepositorySlug = "repo",
            currentPullRequestId = 42,
            declinedRepositorySlug = "repo",
            declinedPullRequestId = 42
        )
        val wrongRoot = PullRequestWorktreeMatch.expectedWorktreePath(
            projectBasePath = "/tmp/other/repo--pr-42",
            workTreeDirectory = "/tmp/worktrees",
            currentRepositorySlug = "repo",
            currentPullRequestId = 42,
            declinedRepositorySlug = "repo",
            declinedPullRequestId = 42
        )
        val missingRoot = PullRequestWorktreeMatch.expectedWorktreePath(
            projectBasePath = "/tmp/worktrees/repo--pr-42",
            workTreeDirectory = null,
            currentRepositorySlug = "repo",
            currentPullRequestId = 42,
            declinedRepositorySlug = "repo",
            declinedPullRequestId = 42
        )

        normalCheckout.shouldBeNull()
        wrongRoot.shouldBeNull()
        missingRoot.shouldBeNull()
    }

    private fun expected(currentRepositorySlug: String = "repo", currentPullRequestId: Long = 42): java.nio.file.Path? {
        return PullRequestWorktreeMatch.expectedWorktreePath(
            projectBasePath = "/tmp/worktrees/repo--pr-42",
            workTreeDirectory = "/tmp/worktrees",
            currentRepositorySlug = currentRepositorySlug,
            currentPullRequestId = currentPullRequestId,
            declinedRepositorySlug = "repo",
            declinedPullRequestId = 42
        )
    }
}