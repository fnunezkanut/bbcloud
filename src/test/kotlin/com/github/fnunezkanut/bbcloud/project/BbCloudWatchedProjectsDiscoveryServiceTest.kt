package com.github.fnunezkanut.bbcloud.project

import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryClient
import com.github.fnunezkanut.bbcloud.bitbucket.RepositoryCheckResult
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@Tag("unit")
class BbCloudWatchedProjectsDiscoveryServiceTest {
    @TempDir
    lateinit var projectsDir: Path

    @Test
    fun `discover - should skip dot-folders and keep only the valid repository`(): Unit =
        runBlocking {
            // GIVEN dot-folders alongside one valid Bitbucket Cloud checkout for the configured workspace
            createDotFolder(name = ".worktrees")
            createDotFolder(name = ".git")
            createDotFolder(name = ".idea")
            createRepo(folderName = "repo-a", workspace = "my-workspace", repoSlug = "repo-a")
            val client = fakeClient(RepositoryCheckResult.Exists())
            val service = BbCloudWatchedProjectsDiscoveryService(repositoryClient = client)

            // WHEN
            val result = service.discover(projectsDir.toString(), "my-workspace", "token")

            // THEN only the valid repository survives
            result.shouldBeInstanceOf<WatchedProjectsDiscoveryResult.Success>()
            result.mappings.map { it.remoteRepoSlug } shouldBe listOf("repo-a")
        }

    @Test
    fun `discover - should ignore a plain file in the projects directory`(): Unit =
        runBlocking {
            // GIVEN a plain file plus one valid repository
            Files.writeString(projectsDir.resolve("notes.txt"), "not a repo")
            createRepo(folderName = "repo-a", workspace = "my-workspace", repoSlug = "repo-a")
            val service = BbCloudWatchedProjectsDiscoveryService(repositoryClient = fakeClient(RepositoryCheckResult.Exists()))

            // WHEN
            val result = service.discover(projectsDir.toString(), "my-workspace", "token")

            // THEN the plain file is not a candidate
            result.shouldBeInstanceOf<WatchedProjectsDiscoveryResult.Success>()
            result.mappings.map { it.remoteRepoSlug } shouldBe listOf("repo-a")
        }

    @Test
    fun `discover - should reject a child without a git entry`(): Unit =
        runBlocking {
            // GIVEN a child directory with no .git entry
            Files.createDirectory(projectsDir.resolve("repo-a"))
            val service = BbCloudWatchedProjectsDiscoveryService(repositoryClient = fakeClient(RepositoryCheckResult.Exists()))

            // WHEN
            val result = service.discover(projectsDir.toString(), "my-workspace", "token")

            // THEN it is not a candidate, so the scan succeeds with nothing verified
            result.shouldBeInstanceOf<WatchedProjectsDiscoveryResult.Success>()
            result.mappings shouldBe emptyList()
        }

    @Test
    fun `discover - should reject a child whose folder name differs from the remote repo slug`(): Unit =
        runBlocking {
            // GIVEN a checkout whose folder name does not equal the parsed remote repo slug
            createRepo(folderName = "local-name", workspace = "my-workspace", repoSlug = "remote-slug")
            val service = BbCloudWatchedProjectsDiscoveryService(repositoryClient = fakeClient(RepositoryCheckResult.Exists()))

            // WHEN
            val result = service.discover(projectsDir.toString(), "my-workspace", "token")

            // THEN the mismatch drops the candidate
            result.shouldBeInstanceOf<WatchedProjectsDiscoveryResult.Success>()
            result.mappings shouldBe emptyList()
        }

    @Test
    fun `discover - should reject a child whose remote workspace differs from the configured workspace`(): Unit =
        runBlocking {
            // GIVEN a checkout whose remote workspace is a different workspace
            createRepo(folderName = "repo-a", workspace = "other-workspace", repoSlug = "repo-a")
            val service = BbCloudWatchedProjectsDiscoveryService(repositoryClient = fakeClient(RepositoryCheckResult.Exists()))

            // WHEN
            val result = service.discover(projectsDir.toString(), "my-workspace", "token")

            // THEN the workspace mismatch drops the candidate
            result.shouldBeInstanceOf<WatchedProjectsDiscoveryResult.Success>()
            result.mappings shouldBe emptyList()
        }

    @Test
    fun `discover - should return ProjectsDirectoryUnreadable for a non-existent path`(): Unit =
        runBlocking {
            // GIVEN a path that is not a directory on disk
            val missing = projectsDir.resolve("does-not-exist").toString()
            val service = BbCloudWatchedProjectsDiscoveryService(repositoryClient = fakeClient(RepositoryCheckResult.Exists()))

            // WHEN / THEN
            service.discover(missing, "my-workspace", "token")
                .shouldBeInstanceOf<WatchedProjectsDiscoveryResult.ProjectsDirectoryUnreadable>()
        }

    @Test
    fun `discover - should keep Exists and drop NotFound candidates`(): Unit =
        runBlocking {
            // GIVEN two valid checkouts, one that exists remotely and one that does not
            createRepo(folderName = "repo-a", workspace = "my-workspace", repoSlug = "repo-a")
            createRepo(folderName = "repo-b", workspace = "my-workspace", repoSlug = "repo-b")
            val client = mockk<BitbucketRepositoryClient>()
            coEvery { client.checkRepositoryExists("my-workspace", "repo-a", "token") } returns RepositoryCheckResult.Exists()
            coEvery { client.checkRepositoryExists("my-workspace", "repo-b", "token") } returns RepositoryCheckResult.NotFound
            val service = BbCloudWatchedProjectsDiscoveryService(repositoryClient = client)

            // WHEN
            val result = service.discover(projectsDir.toString(), "my-workspace", "token")

            // THEN only the existing repository is kept
            result.shouldBeInstanceOf<WatchedProjectsDiscoveryResult.Success>()
            result.mappings.map { it.remoteRepoSlug } shouldBe listOf("repo-a")
        }

    @Test
    fun `discover - should return VerifyFailed when every API check fails`(): Unit =
        runBlocking {
            // GIVEN a valid checkout whose API check fails with a non-404 error
            createRepo(folderName = "repo-a", workspace = "my-workspace", repoSlug = "repo-a")
            val service = BbCloudWatchedProjectsDiscoveryService(repositoryClient = fakeClient(RepositoryCheckResult.Failed()))

            // WHEN / THEN all checks failing keeps the previous mappings
            service.discover(projectsDir.toString(), "my-workspace", "token")
                .shouldBeInstanceOf<WatchedProjectsDiscoveryResult.VerifyFailed>()
        }

    @Test
    fun `discover - should return empty Success when there are no candidates`(): Unit =
        runBlocking {
            // GIVEN an empty projects directory
            val service = BbCloudWatchedProjectsDiscoveryService(repositoryClient = fakeClient(RepositoryCheckResult.Exists()))

            // WHEN
            val result = service.discover(projectsDir.toString(), "my-workspace", "token")

            // THEN
            assertSoftly {
                result.shouldBeInstanceOf<WatchedProjectsDiscoveryResult.Success>()
                result.mappings shouldBe emptyList()
            }
        }

    @Test
    fun `discover - should carry the avatar hrefs of existing repositories, omitting those without one`(): Unit =
        runBlocking {
            // GIVEN two existing repositories, one with an avatar href and one without
            createRepo(folderName = "repo-a", workspace = "my-workspace", repoSlug = "repo-a")
            createRepo(folderName = "repo-b", workspace = "my-workspace", repoSlug = "repo-b")
            val client = mockk<BitbucketRepositoryClient>()
            coEvery {
                client.checkRepositoryExists("my-workspace", "repo-a", "token")
            } returns RepositoryCheckResult.Exists(avatarUrl = "https://cdn.example.com/repo-a.png")
            coEvery {
                client.checkRepositoryExists("my-workspace", "repo-b", "token")
            } returns RepositoryCheckResult.Exists(avatarUrl = null)
            val service = BbCloudWatchedProjectsDiscoveryService(repositoryClient = client)

            // WHEN
            val result = service.discover(projectsDir.toString(), "my-workspace", "token")

            // THEN both repositories are kept, but only repo-a contributes an avatar URL
            result.shouldBeInstanceOf<WatchedProjectsDiscoveryResult.Success>()
            assertSoftly(result.shouldBeInstanceOf<WatchedProjectsDiscoveryResult.Success>()) {
                mappings.map { it.remoteRepoSlug }.toSet() shouldBe setOf("repo-a", "repo-b")
                avatarUrlBySlug shouldBe mapOf("repo-a" to "https://cdn.example.com/repo-a.png")
            }
        }

    /** A repository client that returns [result] for every check, so filesystem-filter tests stay deterministic. */
    private fun fakeClient(result: RepositoryCheckResult): BitbucketRepositoryClient {
        val client = mockk<BitbucketRepositoryClient>()
        coEvery { client.checkRepositoryExists(any(), any(), any()) } returns result
        return client
    }

    private fun createDotFolder(name: String) {
        Files.createDirectory(projectsDir.resolve(name))
    }

    /**
     * Creates a real checkout under the projects directory with a real `.git/config` holding a Bitbucket Cloud origin
     * remote, so the production [com.github.fnunezkanut.bbcloud.git.BitbucketGitRemoteReader] and
     * [com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryRemoteUrl] run against actual files.
     */
    private fun createRepo(folderName: String, workspace: String, repoSlug: String) {
        val repoDir = Files.createDirectory(projectsDir.resolve(folderName))
        val gitDir = Files.createDirectory(repoDir.resolve(".git"))
        val config =
            """
            [core]
                repositoryformatversion = 0
            [remote "origin"]
                url = https://bitbucket.org/$workspace/$repoSlug.git
                fetch = +refs/heads/*:refs/remotes/origin/*
            """.trimIndent()
        Files.writeString(gitDir.resolve("config"), config)
    }
}