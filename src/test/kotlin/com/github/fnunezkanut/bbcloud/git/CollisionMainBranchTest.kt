package com.github.fnunezkanut.bbcloud.git

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class CollisionMainBranchTest {
    @Test
    fun `collisionMainBranch - should prefer the Bitbucket main branch`() {
        // WHEN
        val result = collisionMainBranch(
            bitbucketMainBranch = "trunk",
            destinationBranch = "develop",
            prBranch = "feature/275"
        )

        // THEN
        result shouldBe "trunk"
    }

    @Test
    fun `collisionMainBranch - should fall back to the destination branch when the Bitbucket value is null or blank`() {
        // WHEN / THEN
        collisionMainBranch(
            bitbucketMainBranch = null,
            destinationBranch = "develop",
            prBranch = "feature/275"
        ) shouldBe "develop"

        collisionMainBranch(
            bitbucketMainBranch = "   ",
            destinationBranch = "develop",
            prBranch = "feature/275"
        ) shouldBe "develop"
    }

    @Test
    fun `collisionMainBranch - should skip candidates equal to the PR branch`() {
        // GIVEN the Bitbucket value equals the PR branch, so the destination is used instead
        val result = collisionMainBranch(
            bitbucketMainBranch = "feature/275",
            destinationBranch = "develop",
            prBranch = "feature/275"
        )

        // THEN
        result shouldBe "develop"
    }

    @Test
    fun `collisionMainBranch - should fall back to main as a last resort`() {
        // WHEN
        val result = collisionMainBranch(
            bitbucketMainBranch = null,
            destinationBranch = null,
            prBranch = "feature/275"
        )

        // THEN
        result shouldBe "main"
    }

    @Test
    fun `collisionMainBranch - should return null when every candidate equals the PR branch`() {
        // GIVEN a PR opened from the main branch
        val result = collisionMainBranch(
            bitbucketMainBranch = "main",
            destinationBranch = "main",
            prBranch = "main"
        )

        // THEN
        result.shouldBeNull()
    }
}