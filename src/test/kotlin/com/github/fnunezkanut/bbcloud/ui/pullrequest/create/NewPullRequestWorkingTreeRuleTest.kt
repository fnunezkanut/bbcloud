package com.github.fnunezkanut.bbcloud.ui.pullrequest.create

import com.github.fnunezkanut.bbcloud.git.WorkingTreeStatus
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class NewPullRequestWorkingTreeRuleTest {
    private val mainNames = setOf("develop", "main", "master")

    @Test
    fun `evaluate - should be Unknown for a null status`() {
        NewPullRequestWorkingTreeRule.evaluate(status = null, mainBranchNames = mainNames) shouldBe WorkingTreeCheck.Unknown
    }

    @Test
    fun `evaluate - should be Clean for a clean feature branch`() {
        val status = WorkingTreeStatus(currentBranch = "feature/x", hasUncommittedChanges = false)
        NewPullRequestWorkingTreeRule.evaluate(status = status, mainBranchNames = mainNames) shouldBe WorkingTreeCheck.Clean
    }

    @Test
    fun `evaluate - should be Clean for uncommitted changes on main master or a custom main branch`() {
        assertSoftly {
            NewPullRequestWorkingTreeRule.evaluate(
                status = WorkingTreeStatus(currentBranch = "main", hasUncommittedChanges = true),
                mainBranchNames = mainNames
            ) shouldBe WorkingTreeCheck.Clean
            NewPullRequestWorkingTreeRule.evaluate(
                status = WorkingTreeStatus(currentBranch = "master", hasUncommittedChanges = true),
                mainBranchNames = mainNames
            ) shouldBe WorkingTreeCheck.Clean
            NewPullRequestWorkingTreeRule.evaluate(
                status = WorkingTreeStatus(currentBranch = "develop", hasUncommittedChanges = true),
                mainBranchNames = mainNames
            ) shouldBe WorkingTreeCheck.Clean
        }
    }

    @Test
    fun `evaluate - should block uncommitted changes on a feature branch`() {
        val status = WorkingTreeStatus(currentBranch = "feature/x", hasUncommittedChanges = true)

        val result = NewPullRequestWorkingTreeRule.evaluate(status = status, mainBranchNames = mainNames)

        result shouldBe WorkingTreeCheck.BlockedUncommittedChanges(currentBranch = "feature/x")
    }

    @Test
    fun `evaluate - should block uncommitted changes on a detached HEAD`() {
        val status = WorkingTreeStatus(currentBranch = null, hasUncommittedChanges = true)

        val result = NewPullRequestWorkingTreeRule.evaluate(status = status, mainBranchNames = mainNames)

        result.shouldBeInstanceOf<WorkingTreeCheck.BlockedUncommittedChanges>()
        result.currentBranch shouldBe null
    }

    @Test
    fun `evaluate - should be case sensitive so Main is not main`() {
        val status = WorkingTreeStatus(currentBranch = "Main", hasUncommittedChanges = true)

        val result = NewPullRequestWorkingTreeRule.evaluate(status = status, mainBranchNames = setOf("main"))

        result shouldBe WorkingTreeCheck.BlockedUncommittedChanges(currentBranch = "Main")
    }
}