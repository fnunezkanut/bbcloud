package com.github.fnunezkanut.bbcloud.ui.worktree

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.git.WorktreeSetupResult
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Instant

@Tag("unit")
class WorktreeOpenMappingTest {
    @Test
    fun `setup outcome - should map a created work tree to Prepared with the pull request summary`() {
        // GIVEN
        val summary = summary()
        val worktreePath = Path.of("/work/repo--pr-42")
        val setup = WorktreeSetupResult.Success(worktreePath = worktreePath)

        // WHEN
        val outcome = WorktreeOpenMapping.setupOutcome(summary = summary, setup = setup)

        // THEN
        outcome shouldBe WorktreeOpenOutcome.Prepared(summary = summary, worktreePath = worktreePath)
    }

    @Test
    fun `setup outcome - should map a git failure to Failed with the git output`() {
        // GIVEN
        val setup = WorktreeSetupResult.Failed(output = "fatal: a branch named 'feature/42' already exists")

        // WHEN
        val outcome = WorktreeOpenMapping.setupOutcome(summary = summary(), setup = setup)

        // THEN
        outcome shouldBe WorktreeOpenOutcome.Failed(content = "fatal: a branch named 'feature/42' already exists")
    }

    @Test
    fun `setup outcome - should map a branch checked out in the source to BranchCollision`() {
        // GIVEN
        val sourcePath = Path.of("/projects/repo")
        val setup = WorktreeSetupResult.BranchCheckedOutInSource(branchName = "feature/42", sourceCheckoutPath = sourcePath)

        // WHEN
        val outcome = WorktreeOpenMapping.setupOutcome(summary = summary(), setup = setup)

        // THEN
        outcome shouldBe WorktreeOpenOutcome.BranchCollision(branchName = "feature/42", sourceCheckoutPath = sourcePath)
    }

    private fun summary(): PullRequestSummary {
        val timestamp = Instant.parse("2026-09-01T12:00:00Z")
        return PullRequestSummary(
            id = 42L,
            title = "PR 42",
            authorDisplayName = "John Smith",
            authorAvatarUrl = null,
            createdOn = timestamp,
            updatedOn = timestamp,
            sourceBranch = "feature/42",
            destinationBranch = "main",
            commentCount = 0,
            webUrl = "https://bitbucket.org/acme/my-repo/pull-requests/42",
            state = PullRequestState.Open,
            isDraft = false
        )
    }
}