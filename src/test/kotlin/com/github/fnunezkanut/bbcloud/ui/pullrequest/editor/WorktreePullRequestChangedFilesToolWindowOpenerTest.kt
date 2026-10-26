package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.git.BranchDiffResult
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.throwable.shouldHaveMessage
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class WorktreePullRequestChangedFilesToolWindowOpenerTest {
    @Test
    fun `branchDiffChangesOrThrow - should return the changes on success`() {
        // GIVEN
        val change = mockk<Change>()
        val result = BranchDiffResult.Success(
            changes = listOf(change),
            destinationRef = "origin/main",
            sourceRef = "origin/feature"
        )

        // WHEN
        val changes = WorktreePullRequestChangedFilesToolWindowOpener.branchDiffChangesOrThrow(result = result)

        // THEN
        changes.shouldContainExactly(change)
    }

    @Test
    fun `branchDiffChangesOrThrow - should throw for a missing repository`() {
        // GIVEN
        val result = BranchDiffResult.NoRepository

        // WHEN
        val exception = shouldThrow<VcsException> {
            WorktreePullRequestChangedFilesToolWindowOpener.branchDiffChangesOrThrow(result = result)
        }

        // THEN
        exception shouldHaveMessage "No Git repository found for this project."
    }

    @Test
    fun `branchDiffChangesOrThrow - should name the missing branch`() {
        // GIVEN
        val result = BranchDiffResult.BranchNotFound(branchName = "feature/widget")

        // WHEN
        val exception = shouldThrow<VcsException> {
            WorktreePullRequestChangedFilesToolWindowOpener.branchDiffChangesOrThrow(result = result)
        }

        // THEN
        exception.message shouldContain "feature/widget"
    }

    @Test
    fun `branchDiffChangesOrThrow - should use the plain failure message when no detail is present`() {
        // GIVEN
        val result = BranchDiffResult.Failed(message = "")

        // WHEN
        val exception = shouldThrow<VcsException> {
            WorktreePullRequestChangedFilesToolWindowOpener.branchDiffChangesOrThrow(result = result)
        }

        // THEN
        exception shouldHaveMessage "Could not load changed files from Git."
    }

    @Test
    fun `branchDiffChangesOrThrow - should include the detail in the failure message`() {
        // GIVEN
        val result = BranchDiffResult.Failed(message = "bad object abc123")

        // WHEN
        val exception = shouldThrow<VcsException> {
            WorktreePullRequestChangedFilesToolWindowOpener.branchDiffChangesOrThrow(result = result)
        }

        // THEN
        exception.message shouldContain "bad object abc123"
    }
}