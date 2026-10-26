package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BbCloudPullRequestReviewBarRefreshTest {
    @Test
    fun `work tree - should recognize a pull request work tree`() {
        assertSoftly {
            BbCloudPullRequestReviewBarRefresh.isPullRequestWorkTree(projectBasePath = "/tmp/repo--pr-42").shouldBeTrue()
            BbCloudPullRequestReviewBarRefresh.isPullRequestWorkTree(projectBasePath = "/tmp/repo").shouldBeFalse()
            BbCloudPullRequestReviewBarRefresh.isPullRequestWorkTree(projectBasePath = "/tmp/repo--pr-").shouldBeFalse()
            BbCloudPullRequestReviewBarRefresh.isPullRequestWorkTree(projectBasePath = null).shouldBeFalse()
        }
    }

    @Test
    fun `refresh - should be skipped without an active pull request`() {
        assertSoftly {
            BbCloudPullRequestReviewBarRefresh.shouldRefresh(
                projectBasePath = "/tmp/repo--pr-42",
                activePullRequestId = null,
                changedFilePaths = setOf("src/main.kt"),
                filePath = "/tmp/repo--pr-42/src/main.kt"
            ).shouldBeFalse()
        }
    }

    @Test
    fun `refresh - should be skipped outside a pull request work tree`() {
        assertSoftly {
            BbCloudPullRequestReviewBarRefresh.shouldRefresh(
                projectBasePath = "/tmp/repo",
                activePullRequestId = 7,
                changedFilePaths = setOf("src/main.kt"),
                filePath = "/tmp/repo/src/main.kt"
            ).shouldBeFalse()
        }
    }

    @Test
    fun `refresh - should happen for a file the pull request changes`() {
        assertSoftly {
            BbCloudPullRequestReviewBarRefresh.shouldRefresh(
                projectBasePath = "/tmp/repo--pr-42",
                activePullRequestId = 7,
                changedFilePaths = setOf("src/main.kt"),
                filePath = "/tmp/repo--pr-42/src/main.kt"
            ).shouldBeTrue()
        }
    }

    @Test
    fun `refresh - should happen for a file the pull request changes outside the project folder`() {
        assertSoftly {
            BbCloudPullRequestReviewBarRefresh.shouldRefresh(
                projectBasePath = "/tmp/repo--pr-42",
                activePullRequestId = 7,
                changedFilePaths = setOf("src/main.kt"),
                filePath = "src/main.kt"
            ).shouldBeTrue()
        }
    }

    @Test
    fun `refresh - should be skipped for a file the pull request does not change`() {
        assertSoftly {
            BbCloudPullRequestReviewBarRefresh.shouldRefresh(
                projectBasePath = "/tmp/repo--pr-42",
                activePullRequestId = 7,
                changedFilePaths = setOf("src/main.kt"),
                filePath = "/tmp/repo--pr-42/src/other.kt"
            ).shouldBeFalse()
        }
    }

    @Test
    fun `refresh - should be skipped while no changed file is known`() {
        assertSoftly {
            BbCloudPullRequestReviewBarRefresh.shouldRefresh(
                projectBasePath = "/tmp/repo--pr-42",
                activePullRequestId = 7,
                changedFilePaths = emptySet(),
                filePath = "/tmp/repo--pr-42/src/main.kt"
            ).shouldBeFalse()
        }
    }
}