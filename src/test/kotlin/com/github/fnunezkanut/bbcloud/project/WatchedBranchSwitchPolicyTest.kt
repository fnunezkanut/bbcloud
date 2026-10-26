package com.github.fnunezkanut.bbcloud.project

import com.github.fnunezkanut.bbcloud.git.WorkingTreeStatus
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class WatchedBranchSwitchPolicyTest {
    @Test
    fun `a null status gives StateUnknown`() {
        // GIVEN
        val status: WorkingTreeStatus? = null

        // WHEN
        val decision = WatchedBranchSwitchPolicy.decide(status = status, targetBranch = "feature/x")

        // THEN
        decision shouldBe WatchedBranchSwitchDecision.StateUnknown
    }

    @Test
    fun `clean working tree on develop targeting feature - should give Switch`() {
        // GIVEN
        val status = WorkingTreeStatus(currentBranch = "develop", hasUncommittedChanges = false)

        // WHEN
        val decision = WatchedBranchSwitchPolicy.decide(status = status, targetBranch = "feature/x")

        // THEN
        decision shouldBe WatchedBranchSwitchDecision.Switch
    }

    @Test
    fun `clean working tree on feature - x targeting feature - x should give AlreadyOnBranch`() {
        // GIVEN
        val status = WorkingTreeStatus(currentBranch = "feature/x", hasUncommittedChanges = false)

        // WHEN
        val decision = WatchedBranchSwitchPolicy.decide(status = status, targetBranch = "feature/x")

        // THEN
        decision shouldBe WatchedBranchSwitchDecision.AlreadyOnBranch
    }

    @Test
    fun `dirty working tree on develop targeting feature - x should give SkipUncommittedChanges`() {
        // GIVEN
        val status = WorkingTreeStatus(currentBranch = "develop", hasUncommittedChanges = true)

        // WHEN
        val decision = WatchedBranchSwitchPolicy.decide(status = status, targetBranch = "feature/x")

        // THEN
        val skip = decision as WatchedBranchSwitchDecision.SkipUncommittedChanges
        assertSoftly {
            decision::class shouldBe WatchedBranchSwitchDecision.SkipUncommittedChanges::class
            skip.currentBranch shouldBe "develop"
        }
    }

    @Test
    fun `dirty working tree on feature - x targeting feature - x should give AlreadyOnBranch`() {
        // GIVEN
        val status = WorkingTreeStatus(currentBranch = "feature/x", hasUncommittedChanges = true)

        // WHEN
        val decision = WatchedBranchSwitchPolicy.decide(status = status, targetBranch = "feature/x")

        // THEN
        decision shouldBe WatchedBranchSwitchDecision.AlreadyOnBranch
    }

    @Test
    fun `clean detached HEAD targeting feature - x should give Switch`() {
        // GIVEN
        val status = WorkingTreeStatus(currentBranch = null, hasUncommittedChanges = false)

        // WHEN
        val decision = WatchedBranchSwitchPolicy.decide(status = status, targetBranch = "feature/x")

        // THEN
        decision shouldBe WatchedBranchSwitchDecision.Switch
    }

    @Test
    fun `dirty detached HEAD targeting feature - x should give SkipUncommittedChanges with null currentBranch`() {
        // GIVEN
        val status = WorkingTreeStatus(currentBranch = null, hasUncommittedChanges = true)

        // WHEN
        val decision = WatchedBranchSwitchPolicy.decide(status = status, targetBranch = "feature/x")

        // THEN
        val skip = decision as WatchedBranchSwitchDecision.SkipUncommittedChanges
        assertSoftly {
            decision::class shouldBe WatchedBranchSwitchDecision.SkipUncommittedChanges::class
            skip.currentBranch shouldBe null
        }
    }
}