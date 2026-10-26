package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class ReviewingPullRequestAssemblerTest {
    @Nested
    inner class RepositoryTargets {
        @Test
        fun `repositoryTargets - should drop the current project repository`() {
            // WHEN
            val targets = ReviewingPullRequestAssembler.repositoryTargets(
                repoSlugs = listOf("examplerepo", "another-repo", "third-repo"),
                excludedRepoSlug = "another-repo",
                maxRepositories = 50
            )

            // THEN
            targets shouldContainExactly listOf("examplerepo", "third-repo")
        }

        @Test
        fun `repositoryTargets - should match the excluded repository without case`() {
            // GIVEN the local directory name is not lowercase the way Bitbucket slugs are
            val repoSlugs = listOf("MyRepo", "other-repo")

            // WHEN
            val targets = ReviewingPullRequestAssembler.repositoryTargets(
                repoSlugs = repoSlugs,
                excludedRepoSlug = "MyRepo",
                maxRepositories = 50
            )

            // THEN
            targets shouldContainExactly listOf("other-repo")
        }

        @Test
        fun `repositoryTargets - should keep every repository when nothing is excluded`() {
            // WHEN
            val targets = ReviewingPullRequestAssembler.repositoryTargets(
                repoSlugs = listOf("examplerepo", "other-repo"),
                excludedRepoSlug = null,
                maxRepositories = 50
            )

            // THEN
            targets shouldContainExactly listOf("examplerepo", "other-repo")
        }

        @Test
        fun `repositoryTargets - should not let a blank exclusion drop everything`() {
            // WHEN
            val targets = ReviewingPullRequestAssembler.repositoryTargets(
                repoSlugs = listOf("examplerepo", "other-repo"),
                excludedRepoSlug = "   ",
                maxRepositories = 50
            )

            // THEN
            targets shouldContainExactly listOf("examplerepo", "other-repo")
        }

        @Test
        fun `repositoryTargets - should collapse duplicates and blank slugs`() {
            // WHEN
            val targets = ReviewingPullRequestAssembler.repositoryTargets(
                repoSlugs = listOf("examplerepo", " ", "examplerepo", "other-repo"),
                excludedRepoSlug = null,
                maxRepositories = 50
            )

            // THEN
            targets shouldContainExactly listOf("examplerepo", "other-repo")
        }

        @Test
        fun `repositoryTargets - should cap the number of repositories`() {
            // GIVEN more repositories than one load may ask
            val repoSlugs = (1..10).map { index -> "repo-$index" }

            // WHEN
            val targets = ReviewingPullRequestAssembler.repositoryTargets(
                repoSlugs = repoSlugs,
                excludedRepoSlug = null,
                maxRepositories = 3
            )

            // THEN
            targets shouldContainExactly listOf("repo-1", "repo-2", "repo-3")
        }
    }

    @Nested
    inner class Assemble {
        @Test
        fun `assemble - should order every repository newest updated first`() {
            // GIVEN
            val queues = listOf(
                queue(repoSlug = "examplerepo", summaries = listOf(pullRequest(id = 1, updatedOn = "2026-09-01T12:00:00Z"))),
                queue(repoSlug = "other-repo", summaries = listOf(pullRequest(id = 2, updatedOn = "2026-10-01T12:00:00Z")))
            )

            // WHEN
            val result = ReviewingPullRequestAssembler.assemble(
                workspace = "my-workspace",
                queues = queues,
                isRepositoryListTruncated = false,
                maxPullRequests = 50
            )

            // THEN
            val success = result.shouldBeInstanceOf<ReviewingPullRequestListResult.Success>()
            success.pullRequests shouldContainExactly listOf(
                reviewing(summary = pullRequest(id = 2, updatedOn = "2026-10-01T12:00:00Z"), repoSlug = "other-repo"),
                reviewing(summary = pullRequest(id = 1, updatedOn = "2026-09-01T12:00:00Z"), repoSlug = "examplerepo")
            )
            success.isTruncated shouldBe false
        }

        @Test
        fun `assemble - should carry the repository of each pull request`() {
            // GIVEN
            val queues = listOf(queue(repoSlug = "other-repo", summaries = listOf(pullRequest(id = 1, updatedOn = "2026-09-01T12:00:00Z"))))

            // WHEN
            val result = ReviewingPullRequestAssembler.assemble(
                workspace = "my-workspace",
                queues = queues,
                isRepositoryListTruncated = false,
                maxPullRequests = 50
            )

            // THEN
            val success = result.shouldBeInstanceOf<ReviewingPullRequestListResult.Success>()
            success.pullRequests.single().repositoryName() shouldBe "other-repo"
        }

        @Test
        fun `assemble - should succeed with nothing when no repository is left to ask`() {
            // WHEN the current project is the only repository, so it is the one excluded
            val result = ReviewingPullRequestAssembler.assemble(
                workspace = "my-workspace",
                queues = emptyList(),
                isRepositoryListTruncated = false,
                maxPullRequests = 50
            )

            // THEN
            val success = result.shouldBeInstanceOf<ReviewingPullRequestListResult.Success>()
            success.pullRequests.shouldHaveSize(0)
            success.isTruncated shouldBe false
        }

        @Test
        fun `assemble - should keep the repositories that answered when one fails`() {
            // GIVEN one repository could not be read
            val queues = listOf(
                queue(repoSlug = "examplerepo", summaries = null),
                queue(repoSlug = "other-repo", summaries = listOf(pullRequest(id = 2, updatedOn = "2026-09-01T12:00:00Z")))
            )

            // WHEN
            val result = ReviewingPullRequestAssembler.assemble(
                workspace = "my-workspace",
                queues = queues,
                isRepositoryListTruncated = false,
                maxPullRequests = 50
            )

            // THEN
            val success = result.shouldBeInstanceOf<ReviewingPullRequestListResult.Success>()
            success.pullRequests shouldContainExactly listOf(
                reviewing(summary = pullRequest(id = 2, updatedOn = "2026-09-01T12:00:00Z"), repoSlug = "other-repo")
            )
            success.isTruncated shouldBe true
        }

        @Test
        fun `assemble - should fail when every repository failed`() {
            // GIVEN
            val queues = listOf(queue(repoSlug = "examplerepo", summaries = null), queue(repoSlug = "other-repo", summaries = null))

            // WHEN
            val result = ReviewingPullRequestAssembler.assemble(
                workspace = "my-workspace",
                queues = queues,
                isRepositoryListTruncated = false,
                maxPullRequests = 50
            )

            // THEN
            result.shouldBeInstanceOf<ReviewingPullRequestListResult.Failed>()
        }

        @Test
        fun `assemble - should keep the newest rows and report the cap`() {
            // GIVEN more pull requests than the table shows
            val summaries = listOf(
                pullRequest(id = 1, updatedOn = "2026-09-01T12:00:00Z"),
                pullRequest(id = 2, updatedOn = "2026-09-02T12:00:00Z"),
                pullRequest(id = 3, updatedOn = "2026-09-03T12:00:00Z")
            )

            // WHEN
            val result = ReviewingPullRequestAssembler.assemble(
                workspace = "my-workspace",
                queues = listOf(queue(repoSlug = "examplerepo", summaries = summaries)),
                isRepositoryListTruncated = false,
                maxPullRequests = 2
            )

            // THEN
            val success = result.shouldBeInstanceOf<ReviewingPullRequestListResult.Success>()
            assertSoftly {
                success.pullRequests.map { pullRequest -> pullRequest.summary.id } shouldContainExactly listOf(3, 2)
                success.isTruncated shouldBe true
            }
        }

        @Test
        fun `assemble - should report a capped repository walk`() {
            // WHEN
            val result = ReviewingPullRequestAssembler.assemble(
                workspace = "my-workspace",
                queues = listOf(queue(repoSlug = "examplerepo", summaries = listOf(pullRequest(id = 1, updatedOn = "2026-09-01T12:00:00Z")))),
                isRepositoryListTruncated = true,
                maxPullRequests = 50
            )

            // THEN
            result.shouldBeInstanceOf<ReviewingPullRequestListResult.Success>().isTruncated shouldBe true
        }
    }

    private fun queue(repoSlug: String, summaries: List<PullRequestSummary>?): RepositoryReviewQueue {
        return RepositoryReviewQueue(repoSlug = repoSlug, pullRequests = summaries)
    }

    private fun reviewing(summary: PullRequestSummary, repoSlug: String): ReviewingPullRequest {
        return ReviewingPullRequest(summary = summary, workspace = "my-workspace", repoSlug = repoSlug)
    }

    private fun pullRequest(id: Long, updatedOn: String): PullRequestSummary {
        return PullRequestSummary(
            id = id,
            title = "PR $id",
            authorDisplayName = "John Smith",
            authorAvatarUrl = null,
            createdOn = Instant.parse(updatedOn),
            updatedOn = Instant.parse(updatedOn),
            sourceBranch = "feature/$id",
            destinationBranch = "main",
            commentCount = 0,
            webUrl = "https://bitbucket.org/my-workspace/repo/pull-requests/$id",
            state = PullRequestState.Open,
            isDraft = false
        )
    }
}