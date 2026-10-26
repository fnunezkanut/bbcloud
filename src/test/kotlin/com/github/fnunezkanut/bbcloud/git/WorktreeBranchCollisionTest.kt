package com.github.fnunezkanut.bbcloud.git

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class WorktreeBranchCollisionTest {
    @Test
    fun `isCollision - should detect a collision when the current branch equals the PR branch and it exists`() {
        // GIVEN
        val currentBranch = "feature-x"
        val prBranch = "feature-x"

        // WHEN
        val result = WorktreeBranchCollision.isCollision(currentBranch = currentBranch, prBranch = prBranch, branchExists = true)

        // THEN
        result shouldBe true
    }

    @Test
    fun `isCollision - should not detect a collision when the current branch differs from the PR branch`() {
        // GIVEN
        val currentBranch = "main"
        val prBranch = "feature-x"

        // WHEN
        val result = WorktreeBranchCollision.isCollision(currentBranch = currentBranch, prBranch = prBranch, branchExists = true)

        // THEN
        result shouldBe false
    }

    @Test
    fun `isCollision - should not detect a collision when the source clone has no current branch`() {
        // GIVEN
        val currentBranch: String? = null
        val prBranch = "feature-x"

        // WHEN
        val result = WorktreeBranchCollision.isCollision(currentBranch = currentBranch, prBranch = prBranch, branchExists = true)

        // THEN
        result shouldBe false
    }

    @Test
    fun `isCollision - should not detect a collision when the branch does not yet exist`() {
        // GIVEN
        val currentBranch = "feature-x"
        val prBranch = "feature-x"

        // WHEN
        val result = WorktreeBranchCollision.isCollision(currentBranch = currentBranch, prBranch = prBranch, branchExists = false)

        // THEN
        result shouldBe false
    }

    @Test
    fun `isCollision - should compare trimmed so surrounding whitespace still detects a collision`() {
        // GIVEN
        val currentBranch = "  feature-x\n"
        val prBranch = "feature-x "

        // WHEN
        val result = WorktreeBranchCollision.isCollision(currentBranch = currentBranch, prBranch = prBranch, branchExists = true)

        // THEN
        result shouldBe true
    }

    @Test
    fun `isCollision - should be case-sensitive so a case difference is not a collision`() {
        // GIVEN
        val currentBranch = "Feature-x"
        val prBranch = "feature-x"

        // WHEN
        val result = WorktreeBranchCollision.isCollision(currentBranch = currentBranch, prBranch = prBranch, branchExists = true)

        // THEN
        result shouldBe false
    }

    @Test
    fun `isCollision - should classify the combined edge cases consistently`() {
        // GIVEN
        val prBranch = "release-1"

        // WHEN / THEN
        assertSoftly {
            WorktreeBranchCollision.isCollision(currentBranch = "release-1", prBranch = prBranch, branchExists = true) shouldBe true
            WorktreeBranchCollision.isCollision(currentBranch = "develop", prBranch = prBranch, branchExists = true) shouldBe false
            WorktreeBranchCollision.isCollision(currentBranch = null, prBranch = prBranch, branchExists = true) shouldBe false
            WorktreeBranchCollision.isCollision(currentBranch = "release-1", prBranch = prBranch, branchExists = false) shouldBe false
        }
    }
}