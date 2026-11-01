package com.github.fnunezkanut.bbcloud.git

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

@Tag("unit")
class WorktreeCollisionSwitchDecisionTest {
    private val cleanStatus = WorkingTreeStatus(currentBranch = "feature/275", hasUncommittedChanges = false)
    private val dirtyStatus = WorkingTreeStatus(currentBranch = "feature/275", hasUncommittedChanges = true)

    @Test
    fun `decide - should switch when the tree is clean, the checkout matches, and a main branch is known`() {
        // GIVEN
        val root = Path.of("/projects/examplerepo")

        // WHEN
        val plan = WorktreeCollisionSwitchDecision.decide(
            currentCheckoutRoot = root,
            sourceCheckoutPath = root,
            prBranch = "feature/275",
            mainBranchCandidate = "main",
            status = cleanStatus
        )

        // THEN
        plan shouldBe WorktreeCollisionSwitchPlan.SwitchTo(branchName = "main")
    }

    @Test
    fun `decide - should block on uncommitted changes`() {
        // GIVEN
        val root = Path.of("/projects/examplerepo")

        // WHEN
        val plan = WorktreeCollisionSwitchDecision.decide(
            currentCheckoutRoot = root,
            sourceCheckoutPath = root,
            prBranch = "feature/275",
            mainBranchCandidate = "main",
            status = dirtyStatus
        )

        // THEN
        plan shouldBe WorktreeCollisionSwitchPlan.BlockedByUncommittedChanges(mainBranchName = "main")
    }

    @Test
    fun `decide - should report NotTheCurrentCheckout for a different root even when the tree is dirty`() {
        // GIVEN the dirty status would otherwise win, but the checkout-root check runs first
        val plan = WorktreeCollisionSwitchDecision.decide(
            currentCheckoutRoot = Path.of("/projects/examplerepo"),
            sourceCheckoutPath = Path.of("/projects/other-clone"),
            prBranch = "feature/275",
            mainBranchCandidate = "main",
            status = dirtyStatus
        )

        // THEN
        plan shouldBe WorktreeCollisionSwitchPlan.NotTheCurrentCheckout
    }

    @Test
    fun `decide - should report NotTheCurrentCheckout when the checkout root is null`() {
        // WHEN
        val plan = WorktreeCollisionSwitchDecision.decide(
            currentCheckoutRoot = null,
            sourceCheckoutPath = Path.of("/projects/examplerepo"),
            prBranch = "feature/275",
            mainBranchCandidate = "main",
            status = cleanStatus
        )

        // THEN
        plan shouldBe WorktreeCollisionSwitchPlan.NotTheCurrentCheckout
    }

    @Test
    fun `decide - should report NoMainBranch when the candidate is null, blank, or the PR branch`() {
        // GIVEN
        val root = Path.of("/projects/examplerepo")

        // WHEN / THEN
        WorktreeCollisionSwitchDecision.decide(
            currentCheckoutRoot = root,
            sourceCheckoutPath = root,
            prBranch = "feature/275",
            mainBranchCandidate = null,
            status = cleanStatus
        ) shouldBe WorktreeCollisionSwitchPlan.NoMainBranch

        WorktreeCollisionSwitchDecision.decide(
            currentCheckoutRoot = root,
            sourceCheckoutPath = root,
            prBranch = "feature/275",
            mainBranchCandidate = "   ",
            status = cleanStatus
        ) shouldBe WorktreeCollisionSwitchPlan.NoMainBranch

        WorktreeCollisionSwitchDecision.decide(
            currentCheckoutRoot = root,
            sourceCheckoutPath = root,
            prBranch = "feature/275",
            mainBranchCandidate = " feature/275 ",
            status = cleanStatus
        ) shouldBe WorktreeCollisionSwitchPlan.NoMainBranch
    }

    @Test
    fun `decide - should report StatusUnavailable when the status is null`() {
        // GIVEN
        val root = Path.of("/projects/examplerepo")

        // WHEN
        val plan = WorktreeCollisionSwitchDecision.decide(
            currentCheckoutRoot = root,
            sourceCheckoutPath = root,
            prBranch = "feature/275",
            mainBranchCandidate = "main",
            status = null
        )

        // THEN
        plan.shouldBeInstanceOf<WorktreeCollisionSwitchPlan.StatusUnavailable>()
    }

    @Test
    fun `decide - should treat paths equal after normalization as the same checkout`() {
        // GIVEN a path with a redundant segment and a trailing separator against the plain path
        val plan = WorktreeCollisionSwitchDecision.decide(
            currentCheckoutRoot = Path.of("/a/b/../b/"),
            sourceCheckoutPath = Path.of("/a/b"),
            prBranch = "feature/275",
            mainBranchCandidate = "main",
            status = cleanStatus
        )

        // THEN
        plan shouldBe WorktreeCollisionSwitchPlan.SwitchTo(branchName = "main")
    }
}