package com.github.fnunezkanut.bbcloud.ui.otherprojects

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class WatchedProjectsViewModelTest {
    @Test
    fun `initial state - prune is enabled`() {
        // GIVEN
        val viewModel = viewModel()

        // WHEN
        val pruneEnabled = viewModel.state.value.pruneEnabled

        // THEN
        pruneEnabled shouldBe true
    }

    @Test
    fun `setPruneInProgress - disables prune while running and enables it when finished`() {
        // GIVEN
        val viewModel = viewModel()

        // WHEN
        viewModel.setPruneInProgress(inProgress = true)
        val whileRunning = viewModel.state.value.pruneEnabled
        viewModel.setPruneInProgress(inProgress = false)
        val afterFinish = viewModel.state.value.pruneEnabled

        // THEN
        listOf(whileRunning, afterFinish) shouldBe listOf(false, true)
    }

    @Test
    fun `onPruneWorktreesClicked - invokes the prune callback once`() {
        // GIVEN
        var pruneCalls = 0
        val viewModel = viewModel(onPruneWorktrees = { pruneCalls++ })

        // WHEN
        viewModel.onPruneWorktreesClicked()

        // THEN
        pruneCalls shouldBe 1
    }

    private fun viewModel(onPruneWorktrees: () -> Unit = {}): WatchedProjectsViewModel {
        return WatchedProjectsViewModel(
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            onWatchedChangedCallback = { _, _ -> },
            onAllWatchedChangedCallback = { _ -> },
            onSyncRequestedCallback = {},
            onOpenLocalProjectCallback = { _ -> },
            onOpenWorktreeCallback = { _, _ -> },
            onPruneWorktreesCallback = onPruneWorktrees
        )
    }
}