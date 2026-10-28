package com.github.fnunezkanut.bbcloud.ui.pullrequest.decline

import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.git.BbCloudWorktreeService
import com.github.fnunezkanut.bbcloud.git.WorktreeRemovalResult
import com.github.fnunezkanut.bbcloud.project.WatchedProjectMapping
import com.intellij.openapi.project.Project
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Tag("unit")
class ClosedPullRequestWorktreeCleanupOrchestratorTest {
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
            val configurationService = configurationService(
                worktreeRoot = worktreeRoot,
                sourceCheckout = sourceCheckout
            )
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val orchestrator = orchestrator(projectCloser = projectCloser, worktreeService = worktreeService, scope = scope)
            val completion = CompletableDeferred<ClosedPullRequestWorktreeCleanupCompletion>()

            // WHEN
            orchestrator.launch(
                request = request(worktreePath = worktreePath, configurationService = configurationService),
                onCompleted = { result -> completion.complete(result) }
            )
            val result = completion.await()
            withTimeout(5.seconds) {
                while (events.size < 2) {
                    delay(10)
                }
            }
            scope.cancel()

            // THEN
            assertSoftly {
                result shouldBe ClosedPullRequestWorktreeCleanupCompletion.Success
                events shouldBe listOf("close", "remove")
            }
        }

    @Test
    fun `launch - skips when the project base path is not the expected worktree`(): Unit =
        runBlocking {
            // GIVEN
            val worktreeRoot = Files.createDirectories(temporaryDirectory.resolve(".worktrees"))
            val sourceCheckout = Files.createDirectories(temporaryDirectory.resolve("repo"))
            val otherProjectPath = Files.createDirectories(temporaryDirectory.resolve("some-other-project"))
            var closeCalls = 0
            val projectCloser = ProjectCloser {
                closeCalls += 1
                true
            }
            val worktreeService = mockk<BbCloudWorktreeService>()
            val configurationService = configurationService(
                worktreeRoot = worktreeRoot,
                sourceCheckout = sourceCheckout
            )
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val orchestrator = orchestrator(projectCloser = projectCloser, worktreeService = worktreeService, scope = scope)
            val completion = CompletableDeferred<ClosedPullRequestWorktreeCleanupCompletion>()

            // WHEN
            orchestrator.launch(
                request = request(worktreePath = otherProjectPath, configurationService = configurationService),
                onCompleted = { result -> completion.complete(result) }
            )
            val result = completion.await()
            scope.cancel()

            // THEN
            assertSoftly {
                result shouldBe ClosedPullRequestWorktreeCleanupCompletion.SkippedNotWorktree
                closeCalls shouldBe 0
            }
        }

    @Test
    fun `launch - reports failed and leaves the project open when the source checkout is unavailable`(): Unit =
        runBlocking {
            // GIVEN
            val worktreeRoot = Files.createDirectories(temporaryDirectory.resolve(".worktrees"))
            val worktreePath = worktreeRoot.resolve("repo--pr-42")
            Files.createDirectories(worktreePath)
            var closeCalls = 0
            val projectCloser = ProjectCloser {
                closeCalls += 1
                true
            }
            val worktreeService = mockk<BbCloudWorktreeService>()
            // No watched mapping and no conventional clone on disk, so the resolver cannot find a source checkout.
            val configurationService = mockk<BbCloudConfigurationService>()
            every { configurationService.workTreeDirectoryOrNull() } returns worktreeRoot.toString()
            every { configurationService.projectsDirectoryOrNull() } returns temporaryDirectory.toString()
            every { configurationService.watchedProjectsForCurrentWorkspaceWithLocalPaths() } returns emptyList()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val orchestrator = orchestrator(projectCloser = projectCloser, worktreeService = worktreeService, scope = scope)
            val completion = CompletableDeferred<ClosedPullRequestWorktreeCleanupCompletion>()

            // WHEN
            orchestrator.launch(
                request = request(worktreePath = worktreePath, configurationService = configurationService),
                onCompleted = { result -> completion.complete(result) }
            )
            val result = completion.await()
            scope.cancel()

            // THEN
            assertSoftly {
                (result is ClosedPullRequestWorktreeCleanupCompletion.Failed) shouldBe true
                closeCalls shouldBe 0
            }
        }

    @Test
    fun `cleanupWorktreeProject - ignores a duplicate cleanup while the first holds the path`(): Unit =
        runBlocking {
            // GIVEN
            val worktreeRoot = Files.createDirectories(temporaryDirectory.resolve(".worktrees"))
            val sourceCheckout = Files.createDirectories(temporaryDirectory.resolve("repo"))
            val worktreePath = worktreeRoot.resolve("repo--pr-42")
            Files.createDirectories(worktreePath)
            val firstCloseStarted = CompletableDeferred<Unit>()
            val releaseFirstClose = CompletableDeferred<Unit>()
            val projectCloser = ProjectCloser {
                firstCloseStarted.complete(Unit)
                releaseFirstClose.await()
                true
            }
            val worktreeService = mockk<BbCloudWorktreeService>()
            coEvery {
                worktreeService.removeWorktree(
                    sourceCheckoutRoot = sourceCheckout,
                    worktreePath = worktreePath,
                    workTreeDirectory = worktreeRoot
                )
            } returns WorktreeRemovalResult.Success
            val cleanupOrchestrator = WorktreeProjectCleanupOrchestrator(
                projectCloser = projectCloser,
                worktreeService = worktreeService
            )
            val configurationService = configurationService(
                worktreeRoot = worktreeRoot,
                sourceCheckout = sourceCheckout
            )
            val resolution = cleanupOrchestrator.resolveCleanup(
                projectBasePath = worktreePath.toString(),
                organizationUrl = ORGANIZATION_URL,
                repositorySlug = "repo",
                pullRequestId = 42,
                configurationService = configurationService
            ) as WorktreeCleanupResolution.Resolved
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val project = mockk<Project>()

            // WHEN
            val first = scope.async {
                cleanupOrchestrator.cleanupWorktreeProject(project = project, pullRequestId = 42, resolution = resolution)
            }
            firstCloseStarted.await()
            val second = cleanupOrchestrator.cleanupWorktreeProject(
                project = project,
                pullRequestId = 42,
                resolution = resolution
            )
            releaseFirstClose.complete(Unit)
            val firstOutcome = withTimeout(5.seconds) { first.await() }
            scope.cancel()

            // THEN
            assertSoftly {
                (firstOutcome is WorktreeProjectCleanupOrchestrator.CleanupOutcome.Removed) shouldBe true
                second shouldBe WorktreeProjectCleanupOrchestrator.CleanupOutcome.Skipped
            }
        }

    @Test
    fun `launch - auto closes the modal while a hung cleanup keeps running`(): Unit =
        runBlocking {
            // GIVEN
            val fixture = hungCleanupFixture()
            val modalReturned = CompletableDeferred<Unit>()
            val orchestrator = orchestrator(
                projectCloser = fixture.projectCloser,
                worktreeService = fixture.worktreeService,
                scope = fixture.scope,
                cleanupModal = CleanupModal { _, block ->
                    block()
                    modalReturned.complete(Unit)
                },
                modalAutoCloseTimeout = 50.milliseconds
            )
            var completions = 0

            // WHEN
            orchestrator.launch(request = fixture.request, onCompleted = { completions += 1 })
            withTimeout(5.seconds) { modalReturned.await() }
            val completionsWhileHung = completions
            fixture.releaseClose.complete(Unit)
            withTimeout(5.seconds) {
                while (completions < 1) {
                    delay(10)
                }
            }
            delay(100)
            fixture.scope.cancel()

            // THEN
            assertSoftly {
                completionsWhileHung shouldBe 0
                completions shouldBe 1
            }
        }

    @Test
    fun `launch - returns from the modal early when cleanup finishes before the timeout`(): Unit =
        runBlocking {
            // GIVEN
            val fixture = hungCleanupFixture()
            val modalReturned = CompletableDeferred<Unit>()
            val orchestrator = orchestrator(
                projectCloser = fixture.projectCloser,
                worktreeService = fixture.worktreeService,
                scope = fixture.scope,
                cleanupModal = CleanupModal { _, block ->
                    block()
                    modalReturned.complete(Unit)
                },
                modalAutoCloseTimeout = 30.seconds
            )
            val completion = CompletableDeferred<ClosedPullRequestWorktreeCleanupCompletion>()

            // WHEN
            orchestrator.launch(request = fixture.request, onCompleted = { result -> completion.complete(result) })
            fixture.releaseClose.complete(Unit)
            val result = withTimeout(5.seconds) { completion.await() }
            withTimeout(5.seconds) { modalReturned.await() }
            fixture.scope.cancel()

            // THEN
            result shouldBe ClosedPullRequestWorktreeCleanupCompletion.Success
        }

    private class HungCleanupFixture(
        val scope: CoroutineScope,
        val projectCloser: ProjectCloser,
        val worktreeService: BbCloudWorktreeService,
        val releaseClose: CompletableDeferred<Unit>,
        val request: ClosedPullRequestWorktreeCleanupRequest
    )

    private fun hungCleanupFixture(): HungCleanupFixture {
        val worktreeRoot = Files.createDirectories(temporaryDirectory.resolve(".worktrees"))
        val sourceCheckout = Files.createDirectories(temporaryDirectory.resolve("repo"))
        val worktreePath = worktreeRoot.resolve("repo--pr-42")
        Files.createDirectories(worktreePath)
        val releaseClose = CompletableDeferred<Unit>()
        val worktreeService = mockk<BbCloudWorktreeService>()
        coEvery {
            worktreeService.removeWorktree(
                sourceCheckoutRoot = sourceCheckout,
                worktreePath = worktreePath,
                workTreeDirectory = worktreeRoot
            )
        } returns WorktreeRemovalResult.Success
        return HungCleanupFixture(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            projectCloser = ProjectCloser {
                releaseClose.await()
                true
            },
            worktreeService = worktreeService,
            releaseClose = releaseClose,
            request = request(
                worktreePath = worktreePath,
                configurationService = configurationService(worktreeRoot = worktreeRoot, sourceCheckout = sourceCheckout)
            )
        )
    }

    private fun orchestrator(
        projectCloser: ProjectCloser,
        worktreeService: BbCloudWorktreeService,
        scope: CoroutineScope,
        cleanupModal: CleanupModal = CleanupModal { _, block -> block() },
        modalAutoCloseTimeout: Duration = 5.seconds
    ): ClosedPullRequestWorktreeCleanupOrchestrator {
        return ClosedPullRequestWorktreeCleanupOrchestrator(
            scope = scope,
            cleanupOrchestrator = WorktreeProjectCleanupOrchestrator(
                projectCloser = projectCloser,
                worktreeService = worktreeService
            ),
            cleanupModal = cleanupModal,
            modalAutoCloseTimeout = modalAutoCloseTimeout
        )
    }

    private fun configurationService(worktreeRoot: Path, sourceCheckout: Path): BbCloudConfigurationService {
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
        return configurationService
    }

    private fun request(worktreePath: Path, configurationService: BbCloudConfigurationService): ClosedPullRequestWorktreeCleanupRequest {
        return ClosedPullRequestWorktreeCleanupRequest(
            project = mockk<Project>(),
            projectBasePath = worktreePath.toString(),
            organizationUrl = ORGANIZATION_URL,
            repositorySlug = "repo",
            pullRequestId = 42,
            configurationService = configurationService
        )
    }

    companion object {
        private const val ORGANIZATION_URL = "https://bitbucket.org/workspace/"
    }
}