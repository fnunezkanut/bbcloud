package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BitbucketRepositoryUrlsTest {
    @Test
    fun `workspaceFromOrganizationUrl - should parse workspace from normalized url`() {
        BitbucketRepositoryUrls.workspaceFromOrganizationUrl("https://bitbucket.org/my-workspace/") shouldBe "my-workspace"
    }

    @Test
    fun `repositoryWebUrl - should append slug without duplicate slash`() {
        BitbucketRepositoryUrls.repositoryWebUrl(
            organizationUrl = "https://bitbucket.org/my-workspace/",
            repoSlug = "examplerepo"
        ) shouldBe "https://bitbucket.org/my-workspace/examplerepo"
    }

    @Test
    fun `repositoryWebUrl - should work when org url has no trailing slash`() {
        BitbucketRepositoryUrls.repositoryWebUrl(
            organizationUrl = "https://bitbucket.org/my-workspace",
            repoSlug = "my-repo"
        ) shouldBe "https://bitbucket.org/my-workspace/my-repo"
    }

    @Test
    fun `repositoryWebUrlForWorkspace - should build the url from the workspace and slug`() {
        BitbucketRepositoryUrls.repositoryWebUrlForWorkspace(
            workspace = "my-workspace",
            repoSlug = "examplerepo"
        ) shouldBe "https://bitbucket.org/my-workspace/examplerepo"
    }

    @Test
    fun `repositoryWebUrlForWorkspace - should follow a workspace that differs from the configured one`() {
        BitbucketRepositoryUrls.repositoryWebUrlForWorkspace(
            workspace = "other-team",
            repoSlug = "payments"
        ) shouldBe "https://bitbucket.org/other-team/payments"
    }

    @Test
    fun `repositoryWebUrlForWorkspace - should be null for a blank workspace or slug`() {
        assertSoftly {
            BitbucketRepositoryUrls.repositoryWebUrlForWorkspace(workspace = "  ", repoSlug = "examplerepo") shouldBe null
            BitbucketRepositoryUrls.repositoryWebUrlForWorkspace(workspace = "my-workspace", repoSlug = "  ") shouldBe null
        }
    }

    @Test
    fun `repositoryWebUrl - should equal the workspace-based overload for a configured org url`() {
        val organizationUrl = "https://bitbucket.org/my-workspace/"
        val workspace = BitbucketRepositoryUrls.workspaceFromOrganizationUrl(organizationUrl)

        workspace.shouldNotBeNull()
        BitbucketRepositoryUrls.repositoryWebUrl(
            organizationUrl = organizationUrl,
            repoSlug = "examplerepo"
        ) shouldBe BitbucketRepositoryUrls.repositoryWebUrlForWorkspace(
            workspace = workspace,
            repoSlug = "examplerepo"
        )
    }

    @Test
    fun `pullRequestDiffUrl - should append the diff path without duplicate slash`() {
        BitbucketRepositoryUrls.pullRequestDiffUrl(
            organizationUrl = "https://bitbucket.org/my-workspace/",
            repoSlug = "examplerepo",
            pullRequestId = 42
        ) shouldBe "https://bitbucket.org/my-workspace/examplerepo/pull-requests/42/diff"
    }

    @Test
    fun `pullRequestDiffUrl - should work when org url has no trailing slash`() {
        BitbucketRepositoryUrls.pullRequestDiffUrl(
            organizationUrl = "https://bitbucket.org/my-workspace",
            repoSlug = "my-repo",
            pullRequestId = 7
        ) shouldBe "https://bitbucket.org/my-workspace/my-repo/pull-requests/7/diff"
    }

    @Test
    fun `pullRequestDiffUrl - should be null without a repository slug`() {
        BitbucketRepositoryUrls.pullRequestDiffUrl(
            organizationUrl = "https://bitbucket.org/my-workspace/",
            repoSlug = "  ",
            pullRequestId = 42
        ) shouldBe null
    }

    @Test
    fun `pullRequestDiffUrl - should append a README file fragment`() {
        BitbucketRepositoryUrls.pullRequestDiffUrl(
            organizationUrl = "https://bitbucket.org/my-workspace/",
            repoSlug = "examplerepo",
            pullRequestId = 42,
            fileRelativePath = "README.md"
        ) shouldBe "https://bitbucket.org/my-workspace/examplerepo/pull-requests/42/diff#chg-README.md"
    }

    @Test
    fun `pullRequestDiffUrl - should append a nested file fragment without encoding`() {
        BitbucketRepositoryUrls.pullRequestDiffUrl(
            organizationUrl = "https://bitbucket.org/my-workspace/",
            repoSlug = "examplerepo",
            pullRequestId = 42,
            fileRelativePath = "src/main/Foo.kt"
        ) shouldBe "https://bitbucket.org/my-workspace/examplerepo/pull-requests/42/diff#chg-src/main/Foo.kt"
    }

    @Test
    fun `pullRequestDiffUrl - should strip a leading slash from the file fragment`() {
        BitbucketRepositoryUrls.pullRequestDiffUrl(
            organizationUrl = "https://bitbucket.org/my-workspace/",
            repoSlug = "examplerepo",
            pullRequestId = 42,
            fileRelativePath = "/README.md"
        ) shouldBe "https://bitbucket.org/my-workspace/examplerepo/pull-requests/42/diff#chg-README.md"
    }

    @Test
    fun `pullRequestDiffUrl - should return the plain diff url for a missing or blank file path`() {
        val plainDiffUrl = "https://bitbucket.org/my-workspace/examplerepo/pull-requests/42/diff"

        assertSoftly {
            BitbucketRepositoryUrls.pullRequestDiffUrl(
                organizationUrl = "https://bitbucket.org/my-workspace/",
                repoSlug = "examplerepo",
                pullRequestId = 42,
                fileRelativePath = null
            ) shouldBe plainDiffUrl
            BitbucketRepositoryUrls.pullRequestDiffUrl(
                organizationUrl = "https://bitbucket.org/my-workspace/",
                repoSlug = "examplerepo",
                pullRequestId = 42,
                fileRelativePath = "  "
            ) shouldBe plainDiffUrl
        }
    }

    @Test
    fun `pullRequestDiffUrl - should remain null for a blank slug with a file path`() {
        BitbucketRepositoryUrls.pullRequestDiffUrl(
            organizationUrl = "https://bitbucket.org/my-workspace/",
            repoSlug = "  ",
            pullRequestId = 42,
            fileRelativePath = "README.md"
        ) shouldBe null
    }

    @Test
    fun `pullRequestDiffUrl - should be null for a non-Bitbucket organization url`() {
        BitbucketRepositoryUrls.pullRequestDiffUrl(
            organizationUrl = "https://example.com/my-workspace/",
            repoSlug = "examplerepo",
            pullRequestId = 42
        ) shouldBe null
    }

    @Test
    fun `pullRequestWebUrl - should append the pull request path without the diff suffix`() {
        BitbucketRepositoryUrls.pullRequestWebUrl(
            organizationUrl = "https://bitbucket.org/my-workspace/",
            repoSlug = "examplerepo",
            pullRequestId = 42
        ) shouldBe "https://bitbucket.org/my-workspace/examplerepo/pull-requests/42"
    }

    @Test
    fun `pullRequestWebUrl - should work when org url has no trailing slash`() {
        BitbucketRepositoryUrls.pullRequestWebUrl(
            organizationUrl = "https://bitbucket.org/my-workspace",
            repoSlug = "my-repo",
            pullRequestId = 7
        ) shouldBe "https://bitbucket.org/my-workspace/my-repo/pull-requests/7"
    }

    @Test
    fun `pullRequestWebUrl - should be null without a repository slug`() {
        BitbucketRepositoryUrls.pullRequestWebUrl(
            organizationUrl = "https://bitbucket.org/my-workspace/",
            repoSlug = "  ",
            pullRequestId = 42
        ) shouldBe null
    }

    @Test
    fun `pullRequestWebUrl - should be null for a non-Bitbucket organization url`() {
        BitbucketRepositoryUrls.pullRequestWebUrl(
            organizationUrl = "https://example.com/my-workspace/",
            repoSlug = "examplerepo",
            pullRequestId = 42
        ) shouldBe null
    }

    @Test
    fun `workspaceOverviewUrl - should append the overview path to a trailing-slash url`() {
        BitbucketRepositoryUrls.workspaceOverviewUrl("https://bitbucket.org/my-workspace/") shouldBe
            "https://bitbucket.org/my-workspace/workspace/overview"
    }

    @Test
    fun `workspaceOverviewUrl - should work when org url has no trailing slash`() {
        BitbucketRepositoryUrls.workspaceOverviewUrl("https://bitbucket.org/my-workspace") shouldBe
            "https://bitbucket.org/my-workspace/workspace/overview"
    }

    @Test
    fun `workspaceOverviewUrl - should be null for a non-Bitbucket organization url`() {
        BitbucketRepositoryUrls.workspaceOverviewUrl("https://example.com/my-workspace/") shouldBe null
    }

    @Test
    fun `workspaceOverviewUrl - should be null for a blank url`() {
        BitbucketRepositoryUrls.workspaceOverviewUrl("") shouldBe null
    }

    @Nested
    inner class RepoSlugFromPullRequestLink {
        @Test
        fun `should read the slug out of an api link`() {
            BitbucketRepositoryUrls.repoSlugFromPullRequestLink(
                "https://api.bitbucket.org/2.0/repositories/my-workspace/examplerepo/pullrequests/42"
            ) shouldBe "examplerepo"
        }

        @Test
        fun `should read the slug out of a browser link`() {
            BitbucketRepositoryUrls.repoSlugFromPullRequestLink(
                "https://bitbucket.org/my-workspace/examplerepo/pull-requests/42"
            ) shouldBe "examplerepo"
        }

        @Test
        fun `should read the slug of a link with a trailing slash`() {
            BitbucketRepositoryUrls.repoSlugFromPullRequestLink(
                "https://bitbucket.org/my-workspace/examplerepo/pull-requests/42/"
            ) shouldBe "examplerepo"
        }

        @Test
        fun `should be null for a repository link without a pull request`() {
            BitbucketRepositoryUrls.repoSlugFromPullRequestLink(
                "https://api.bitbucket.org/2.0/repositories/my-workspace/examplerepo"
            ) shouldBe null
        }

        @Test
        fun `should be null for a link without a repository in the path`() {
            BitbucketRepositoryUrls.repoSlugFromPullRequestLink("https://bitbucket.org/pull-requests/42") shouldBe null
        }

        @Test
        fun `should be null for a link that is not a url`() {
            BitbucketRepositoryUrls.repoSlugFromPullRequestLink("pull-requests/42") shouldBe null
        }

        @Test
        fun `should be null for a blank link`() {
            BitbucketRepositoryUrls.repoSlugFromPullRequestLink("   ") shouldBe null
        }
    }
}