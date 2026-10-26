package com.github.fnunezkanut.bbcloud.bitbucket

import com.github.fnunezkanut.bbcloud.credentials.BbCloudCredentialStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

@Tag("unit")
class BbCloudProjectRepositoryLinkServiceTest {
    private val organizationUrl = "https://bitbucket.org/my-workspace/"

    /** A checkout with no readable git remote, so the resolver falls back to the folder name + configured workspace. */
    private val noRemote: (Path) -> String? = { null }

    @Test
    fun `resolveAndVerify - should not call api when base path missing`(): Unit =
        runBlocking {
            val client = mockk<BitbucketRepositoryClient>()
            val store = mockk<BbCloudCredentialStore>()
            val service = BbCloudProjectRepositoryLinkService(
                credentialStore = store,
                repositoryClient = client,
                readRemoteUrl = noRemote
            )

            val result = service.resolveAndVerify(projectBasePath = null, organizationUrl = organizationUrl)

            result shouldBe ProjectRepositoryStatus.NoLocalProjectPath
        }

    @Test
    fun `resolveAndVerify - should return Found when repository exists`(): Unit =
        runBlocking {
            val client = mockk<BitbucketRepositoryClient>()
            val avatarUrl = "https://bitbucket.org/account/avatar/32"
            coEvery {
                client.checkRepositoryExists(
                    workspace = "my-workspace",
                    repoSlug = "examplerepo",
                    apiToken = "token"
                )
            } returns RepositoryCheckResult.Exists(avatarUrl = avatarUrl)
            val store = mockk<BbCloudCredentialStore>()
            coEvery { store.getToken() } returns "token"
            val service = BbCloudProjectRepositoryLinkService(
                credentialStore = store,
                repositoryClient = client,
                readRemoteUrl = noRemote
            )

            val result = service.resolveAndVerify(
                projectBasePath = "/Users/me/Projects/examplerepo",
                organizationUrl = organizationUrl
            )

            result.shouldBeInstanceOf<ProjectRepositoryStatus.Found>()
            result.repositoryWebUrl shouldBe "https://bitbucket.org/my-workspace/examplerepo"
            result.avatarUrl shouldBe AvatarUrlUpgrader.upgrade(avatarUrl)
        }

    @Test
    fun `resolveAndVerify - should return NotFound when api returns 404`(): Unit =
        runBlocking {
            val client = mockk<BitbucketRepositoryClient>()
            coEvery {
                client.checkRepositoryExists(
                    workspace = "my-workspace",
                    repoSlug = "examplerepo",
                    apiToken = "token"
                )
            } returns RepositoryCheckResult.NotFound
            val store = mockk<BbCloudCredentialStore>()
            coEvery { store.getToken() } returns "token"
            val service = BbCloudProjectRepositoryLinkService(
                credentialStore = store,
                repositoryClient = client,
                readRemoteUrl = noRemote
            )

            val result = service.resolveAndVerify(
                projectBasePath = "/Users/me/Projects/examplerepo",
                organizationUrl = organizationUrl
            )

            result.shouldBeInstanceOf<ProjectRepositoryStatus.NotFound>()
            result.repositoryWebUrl shouldBe "https://bitbucket.org/my-workspace/examplerepo"
        }

    @Test
    fun `resolveAndVerify - should resolve the real slug from the remote for a worktree path`(): Unit =
        runBlocking {
            // GIVEN a worktree review window whose folder name encodes the pull request number
            val client = mockk<BitbucketRepositoryClient>()
            coEvery {
                client.checkRepositoryExists(
                    workspace = "my-workspace",
                    repoSlug = "examplerepo",
                    apiToken = "token"
                )
            } returns RepositoryCheckResult.Exists()
            val store = mockk<BbCloudCredentialStore>()
            coEvery { store.getToken() } returns "token"
            val worktreeRemote: (Path) -> String? = { "https://bitbucket.org/my-workspace/examplerepo.git" }
            val service = BbCloudProjectRepositoryLinkService(
                credentialStore = store,
                repositoryClient = client,
                readRemoteUrl = worktreeRemote
            )

            // WHEN the worktree project is verified
            val result = service.resolveAndVerify(
                projectBasePath = "/p/.worktrees/examplerepo--pr-7",
                organizationUrl = organizationUrl
            )

            // THEN the API is queried with the real slug and the web URL has no `--pr-7` suffix
            result.shouldBeInstanceOf<ProjectRepositoryStatus.Found>()
            result.repositoryWebUrl shouldBe "https://bitbucket.org/my-workspace/examplerepo"
        }

    @Test
    fun `resolveAndVerify - should follow the remote workspace when it differs from the configured one`(): Unit =
        runBlocking {
            // GIVEN a checkout whose origin remote names a different Bitbucket workspace than the configured one
            val client = mockk<BitbucketRepositoryClient>()
            coEvery {
                client.checkRepositoryExists(
                    workspace = "other-team",
                    repoSlug = "payments",
                    apiToken = "token"
                )
            } returns RepositoryCheckResult.Exists()
            val store = mockk<BbCloudCredentialStore>()
            coEvery { store.getToken() } returns "token"
            val crossWorkspaceRemote: (Path) -> String? = { "git@bitbucket.org:other-team/payments.git" }
            val service = BbCloudProjectRepositoryLinkService(
                credentialStore = store,
                repositoryClient = client,
                readRemoteUrl = crossWorkspaceRemote
            )

            // WHEN verifying with a configured org URL for a different workspace
            val result = service.resolveAndVerify(
                projectBasePath = "/p/payments",
                organizationUrl = organizationUrl
            )

            // THEN both the query and the web URL follow the resolved remote workspace, not the configured one
            result.shouldBeInstanceOf<ProjectRepositoryStatus.Found>()
            result.repositoryWebUrl shouldBe "https://bitbucket.org/other-team/payments"
        }
}