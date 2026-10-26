package com.github.fnunezkanut.bbcloud.review

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BbCloudPullRequestReviewStateTest {
    @Test
    fun `inline comments - should reject results for an inactive pull request`() {
        val state = BbCloudPullRequestReviewState()
        state.bindPullRequest(pullRequestId = 12)

        val updated = state.updateInlineComments(pullRequestId = 34, index = null)

        updated.shouldBeFalse()
        state.activePullRequestId shouldBe 12
        state.inlineCommentIndex.shouldBeNull()
    }

    @Test
    fun `inline comments - should accept results for the active pull request`() {
        val state = BbCloudPullRequestReviewState()
        state.bindPullRequest(pullRequestId = 12)

        val updated = state.updateInlineComments(pullRequestId = 12, index = null)

        updated.shouldBeTrue()
        state.inlineCommentIndex.shouldBeNull()
    }

    @Test
    fun `changed files - should reject results for an inactive pull request`() {
        val state = BbCloudPullRequestReviewState()
        state.bindPullRequest(pullRequestId = 12)

        val updated = state.updateChangedFilePaths(pullRequestId = 34, paths = setOf("src/main.kt"))

        updated.shouldBeFalse()
        state.changedFilePaths.shouldBe(emptySet())
        state.isFileChangedInActivePullRequest(relativePath = "src/main.kt").shouldBeFalse()
    }

    @Test
    fun `changed files - should accept results for the active pull request`() {
        val state = BbCloudPullRequestReviewState()
        state.bindPullRequest(pullRequestId = 12)

        val updated = state.updateChangedFilePaths(pullRequestId = 12, paths = setOf("src/main.kt"))

        updated.shouldBeTrue()
        state.isFileChangedInActivePullRequest(relativePath = "src/main.kt").shouldBeTrue()
        state.isFileChangedInActivePullRequest(relativePath = "src/other.kt").shouldBeFalse()
    }

    @Test
    fun `changed files - should match the normalized path`() {
        val state = BbCloudPullRequestReviewState()
        state.bindPullRequest(pullRequestId = 12)
        state.updateChangedFilePaths(pullRequestId = 12, paths = setOf("src/main.kt"))

        state.isFileChangedInActivePullRequest(relativePath = "./src/main.kt").shouldBeTrue()
        state.isFileChangedInActivePullRequest(relativePath = "/src/main.kt").shouldBeTrue()
        state.isFileChangedInActivePullRequest(relativePath = "src\\main.kt").shouldBeTrue()
    }

    @Test
    fun `changed files - should be forgotten when another pull request is bound`() {
        val state = BbCloudPullRequestReviewState()
        state.bindPullRequest(pullRequestId = 12)
        state.updateChangedFilePaths(pullRequestId = 12, paths = setOf("src/main.kt"))

        state.bindPullRequest(pullRequestId = 34)

        state.changedFilePaths.shouldBe(emptySet())
        state.isFileChangedInActivePullRequest(relativePath = "src/main.kt").shouldBeFalse()
    }

    @Test
    fun `changed files - should be forgotten when the pull request is cleared`() {
        val state = BbCloudPullRequestReviewState()
        state.bindPullRequest(pullRequestId = 12)
        state.updateChangedFilePaths(pullRequestId = 12, paths = setOf("src/main.kt"))

        state.clear()

        state.changedFilePaths.shouldBe(emptySet())
        state.isFileChangedInActivePullRequest(relativePath = "src/main.kt").shouldBeFalse()
    }
}