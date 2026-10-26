package com.github.fnunezkanut.bbcloud.ui.pullrequest.decline

import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudOpenPullRequestService
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestDeclineResult
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.git.BbCloudWorktreeService
import com.github.fnunezkanut.bbcloud.git.WorktreeRemovalResult
import com.github.fnunezkanut.bbcloud.project.WatchedProjectMapping
import com.intellij.openapi.project.Project
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Tag("unit")
class PullRequestDeclineOrchestratorTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `launch - closes the exact worktree project before removing the worktree`(): Unit =
        runBlocking {
            // GIVEN
            val worktreeRoot = Files.createDirectories(temporaryDirectory.resolve(".worktrees"))
            val sourceCheckout = Files.createDirectories(temporaryDirectory.resolve("repo"))
            val worktreePath = worktreeRoot.resolve("repo--pr-42")
            Files.createDirectories(worktreePath)
            val events = mutableListOf<String>()
            val projectCloser = ProjectCloser {
                events += "close"
                true
            }
            val worktreeService = mockk<BbCloudWorktreeService>()
            coEvery {
                worktreeService.removeWorktree(
                    sourceCheckoutRoot = sourceCheckout,
                    worktreePath = worktreePath,
                    workTreeDirectory = worktreeRoot
                )
            } answers {
                events += "remove"
                WorktreeRemovalResult.Success
            }
            val openPullRequestService = mockk<BbCloudOpenPullRequestService>()
            coEvery {
                openPullRequestService.declinePullRequest(
                    projectBasePath = worktreePath.toString(),
                    organizationUrl = "https://bitbucket.org/workspace/",
                    pullRequest = summary(),
                    reason = "reason"
                )
            } returns PullRequestDeclineResult.Success()
            val configurationService = mockk<BbCloudConfigurationService>()
            every { configurationService.workTreeDirectoryOrNull() } returns worktreeRoot.toString()
            every { configurationService.projectsDirectoryOrNull() } returns temporaryDirectory.toString()
            every { configurationService.watchedProjectsForCurrentWorkspaceWithLocalPaths() } returns listOf(
                WatchedProjectMapping(
                    localFolderPath = sourceCheckout.toString(),
                    remoteRepoSlug = "repo",
                    workspace = "workspace",
                    watched = true
                )
            )
            val request = PullRequestDeclineRequest(
                project = mockk<Project>(),
                projectBasePath = worktreePath.toString(),
                organizationUrl = "https://bitbucket.org/workspace/",
                repositorySlug = "repo",
                pullRequest = summary(),
                reason = "reason",
                configurationService = configurationService,
                openPullRequestService = openPullRequestService
            )
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val orchestrator = PullRequestDeclineOrchestrator(
                projectCloser = projectCloser,
                worktreeService = worktreeService,
                scope = scope
            )
            val completion = CompletableDeferred<PullRequestDeclineCompletion>()

            // WHEN
            orchestrator.launch(request = request, onCompleted = { result -> completion.complete(result) })
            val result = completion.await()
            withTimeout(5.seconds) {
                while (events.size < 2) {
                    delay(10)
                }
            }
            scope.cancel()

            // THEN
            result shouldBe PullRequestDeclineCompletion.Success(projectWillClose = true)
            events shouldBe listOf("close", "remove")
        }

    private fun summary(): PullRequestSummary {
        val instant = Instant.parse("2026-09-30T12:00:00Z")
        return PullRequestSummary(
            id = 42,
            title = "PR 42",
            authorDisplayName = "Author",
            authorAvatarUrl = null,
            createdOn = instant,
            updatedOn = instant,
            sourceBranch = "feature/42",
            destinationBranch = "main",
            commentCount = 0,
            webUrl = "https://bitbucket.org/workspace/repo/pull-requests/42",
            state = PullRequestState.Open,
            isDraft = false
        )
    }
}