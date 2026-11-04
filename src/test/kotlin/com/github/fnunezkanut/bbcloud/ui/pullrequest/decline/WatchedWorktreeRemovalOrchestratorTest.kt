package com.github.fnunezkanut.bbcloud.ui.pullrequest.decline

import com.github.fnunezkanut.bbcloud.git.OpenProjectLocator
import com.github.fnunezkanut.bbcloud.git.WorktreeRemovalResult
import com.github.fnunezkanut.bbcloud.project.WatchedWorktreeRemovalOutcome
import com.github.fnunezkanut.bbcloud.project.WatchedWorktreeRemover
import com.intellij.openapi.project.Project
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Tag("unit")
class WatchedWorktreeRemovalOrchestratorTest {
    private val project: Project = mockk<Project>(relaxed = true)
    private val sourceRoot: Path = Path.of("/tmp/clone")
    private val workTreeDirectory: Path = Path.of("/tmp/worktrees")
    private val worktreePath: Path = Path.of("/tmp/worktrees/alpha-api--pr-7")

    @Test
    fun `launch - closes the modal after the timeout while a hung close keeps running`(): Unit =
        runBlocking {
            // GIVEN
            val releaseClose = CompletableDeferred<Unit>()
            val modalReturned = CompletableDeferred<Unit>()
            val completion = CompletableDeferred<WatchedWorktreeRemovalOutcome>()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val orchestrator = WatchedWorktreeRemovalOrchestrator(
                scope = scope,
                remover = remover(closeGate = releaseClose),
                modal = CleanupModal { _, block ->
                    block()
                    modalReturned.complete(Unit)
                },
                modalAutoCloseTimeout = 50.milliseconds
            )

            // WHEN
            orchestrator.launch(
                project = project,
                sourceCheckoutRoot = sourceRoot,
                worktreePath = worktreePath,
                workTreeDirectory = workTreeDirectory,
                onCompleted = { outcome -> completion.complete(outcome) }
            )
            withTimeout(5.seconds) { modalReturned.await() }
            val completedWhileHung = completion.isCompleted
            releaseClose.complete(Unit)
            val outcome = withTimeout(5.seconds) { completion.await() }
            scope.cancel()

            // THEN
            assertSoftly {
                completedWhileHung shouldBe false
                outcome shouldBe WatchedWorktreeRemovalOutcome.Removed
            }
        }

    @Test
    fun `launch - returns from the modal early when the removal finishes before the timeout`(): Unit =
        runBlocking {
            // GIVEN
            val modalReturned = CompletableDeferred<Unit>()
            val completion = CompletableDeferred<WatchedWorktreeRemovalOutcome>()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val orchestrator = WatchedWorktreeRemovalOrchestrator(
                scope = scope,
                remover = remover(),
                modal = CleanupModal { _, block ->
                    block()
                    modalReturned.complete(Unit)
                },
                modalAutoCloseTimeout = 30.seconds
            )

            // WHEN
            orchestrator.launch(
                project = project,
                sourceCheckoutRoot = sourceRoot,
                worktreePath = worktreePath,
                workTreeDirectory = workTreeDirectory,
                onCompleted = { outcome -> completion.complete(outcome) }
            )
            val outcome = withTimeout(5.seconds) { completion.await() }
            withTimeout(5.seconds) { modalReturned.await() }
            scope.cancel()

            // THEN
            assertSoftly {
                outcome shouldBe WatchedWorktreeRemovalOutcome.Removed
                modalReturned.isCompleted shouldBe true
            }
        }

    @Test
    fun `launch - reports RemovalFailed when the removal throws unexpectedly`(): Unit =
        runBlocking {
            // GIVEN
            val failure = IllegalStateException("git exploded")
            val modalReturned = CompletableDeferred<Unit>()
            val completion = CompletableDeferred<WatchedWorktreeRemovalOutcome>()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val orchestrator = WatchedWorktreeRemovalOrchestrator(
                scope = scope,
                remover = remover(openProject = null, removeWorktree = { throw failure }),
                modal = CleanupModal { _, block ->
                    block()
                    modalReturned.complete(Unit)
                },
                modalAutoCloseTimeout = 5.seconds
            )

            // WHEN
            orchestrator.launch(
                project = project,
                sourceCheckoutRoot = sourceRoot,
                worktreePath = worktreePath,
                workTreeDirectory = workTreeDirectory,
                onCompleted = { outcome -> completion.complete(outcome) }
            )
            val outcome = withTimeout(5.seconds) { completion.await() }
            withTimeout(5.seconds) { modalReturned.await() }
            scope.cancel()

            // THEN
            assertSoftly {
                outcome shouldBe WatchedWorktreeRemovalOutcome.RemovalFailed(output = "git exploded", cause = failure)
                modalReturned.isCompleted shouldBe true
            }
        }

    @Test
    fun `launch - survives the modal being cancelled`(): Unit =
        runBlocking {
            // GIVEN
            val completion = CompletableDeferred<WatchedWorktreeRemovalOutcome>()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val orchestrator = WatchedWorktreeRemovalOrchestrator(
                scope = scope,
                remover = remover(openProject = null),
                modal = CleanupModal { _, _ -> throw CancellationException("modal dismissed") },
                modalAutoCloseTimeout = 5.seconds
            )

            // WHEN
            orchestrator.launch(
                project = project,
                sourceCheckoutRoot = sourceRoot,
                worktreePath = worktreePath,
                workTreeDirectory = workTreeDirectory,
                onCompleted = { outcome -> completion.complete(outcome) }
            )
            val outcome = withTimeout(5.seconds) { completion.await() }
            scope.cancel()

            // THEN
            outcome shouldBe WatchedWorktreeRemovalOutcome.Removed
        }

    @Test
    fun `MODAL_AUTO_CLOSE_TIMEOUT - is five seconds`() {
        // GIVEN
        val expected = 5.seconds

        // WHEN
        val actual = WatchedWorktreeRemovalOrchestrator.MODAL_AUTO_CLOSE_TIMEOUT

        // THEN
        actual shouldBe expected
    }

    private fun remover(
        closeGate: CompletableDeferred<Unit>? = null,
        openProject: Project? = project,
        removeWorktree: suspend () -> WorktreeRemovalResult = { WorktreeRemovalResult.Success }
    ): WatchedWorktreeRemover {
        return WatchedWorktreeRemover(
            openProjectLocator = OpenProjectLocator { _ -> openProject },
            projectCloser = ProjectCloser { _ ->
                closeGate?.await()
                true
            },
            removeWorktree = { _, _, _ -> removeWorktree() }
        )
    }
}