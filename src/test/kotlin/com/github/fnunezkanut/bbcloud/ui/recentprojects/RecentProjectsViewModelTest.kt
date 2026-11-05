package com.github.fnunezkanut.bbcloud.ui.recentprojects

import com.github.fnunezkanut.bbcloud.project.RecentProjectGroupSnapshot
import com.github.fnunezkanut.bbcloud.project.RecentProjectsSnapshot
import com.github.fnunezkanut.bbcloud.project.RecentProjectsSource
import com.intellij.openapi.Disposable
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

@Tag("unit")
class RecentProjectsViewModelTest {
    private class FakeSource(var snapshot: RecentProjectsSnapshot, var failure: Exception? = null) : RecentProjectsSource {
        val expandedCalls = mutableListOf<Pair<String, Boolean>>()
        val removed = mutableListOf<String>()

        override fun snapshot(): RecentProjectsSnapshot {
            failure?.let { throw it }
            return snapshot
        }

        override fun displayName(path: String): String {
            return path.substringAfterLast('/')
        }

        override fun setGroupExpanded(groupName: String, expanded: Boolean) {
            expandedCalls += groupName to expanded
        }

        override fun removePath(path: String) {
            removed += path
        }

        override fun subscribe(parentDisposable: Disposable, onChange: () -> Unit) = Unit
    }

    private val snapshot = RecentProjectsSnapshot(
        recentPaths = listOf("/home/me/a", "/home/me/b", "/home/me/gone"),
        groups = listOf(RecentProjectGroupSnapshot(name = "G", projectPaths = listOf("/home/me/b"), isExpanded = false))
    )

    private fun viewModel(
        source: FakeSource,
        readBranch: suspend (Path) -> String? = { path -> "br-${path.fileName}" },
        opened: MutableList<RecentProjectRow> = mutableListOf(),
        failures: MutableList<Throwable> = mutableListOf()
    ): RecentProjectsViewModel {
        return RecentProjectsViewModel(
            source = source,
            readBranch = readBranch,
            pathExists = { path -> !path.endsWith("gone") },
            userHome = "/home/me",
            coroutineScope = CoroutineScope(Job() + Dispatchers.Unconfined),
            openProject = { row -> opened += row },
            onLoadFailed = { throwable -> failures += throwable },
            runOnEdt = { block -> block() },
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun `reload - should populate groups and ungrouped rows`() {
        // GIVEN
        val viewModel = viewModel(source = FakeSource(snapshot = snapshot))

        // WHEN
        viewModel.reload()

        // THEN
        val state = viewModel.state.value
        assertSoftly {
            state.isLoading shouldBe false
            state.groups.single().projects.single().name shouldBe "b"
            state.groups.single().projects.single().displayPath shouldBe "~/b"
            state.groups.single().projects.single().branch shouldBe "br-b"
            state.ungrouped.map { it.name } shouldBe listOf("a", "gone")
            state.ungrouped.first().exists shouldBe true
        }
    }

    @Test
    fun `reload - should not read the branch of missing paths`() {
        // GIVEN
        val viewModel = viewModel(source = FakeSource(snapshot = snapshot))

        // WHEN
        viewModel.reload()

        // THEN
        val gone = viewModel.state.value.ungrouped.single { it.name == "gone" }
        assertSoftly {
            gone.exists shouldBe false
            gone.branch shouldBe null
        }
    }

    @Test
    fun `reload - a stale load should not overwrite a newer one`() {
        // GIVEN
        val gate = CompletableDeferred<Unit>()
        var firstCall = true
        val source = FakeSource(snapshot = snapshot)
        val viewModel = viewModel(
            source = source,
            readBranch = { _ ->
                if (firstCall) {
                    firstCall = false
                    gate.await()
                    "stale"
                } else {
                    "fresh"
                }
            }
        )

        // WHEN
        viewModel.reload()
        viewModel.reload()
        gate.complete(Unit)

        // THEN
        viewModel.state.value.ungrouped.first().branch shouldBe "fresh"
    }

    @Test
    fun `reload - a snapshot failure should set loadFailed`() {
        // GIVEN
        val failures = mutableListOf<Throwable>()
        val viewModel = viewModel(
            source = FakeSource(snapshot = snapshot, failure = IllegalStateException("boom")),
            failures = failures
        )

        // WHEN
        viewModel.reload()

        // THEN
        assertSoftly {
            viewModel.state.value.loadFailed shouldBe true
            viewModel.state.value.isLoading shouldBe false
            failures.size shouldBe 1
        }
    }

    @Test
    fun `onGroupToggled - should flip the state and tell the source`() {
        // GIVEN
        val source = FakeSource(snapshot = snapshot)
        val viewModel = viewModel(source = source)
        viewModel.reload()

        // WHEN
        viewModel.onGroupToggled(groupName = "G")

        // THEN
        assertSoftly {
            viewModel.state.value.groups.single().isExpanded shouldBe true
            source.expandedCalls shouldBe listOf("G" to true)
        }
    }

    @Test
    fun `onProjectClicked - should forward the row`() {
        // GIVEN
        val opened = mutableListOf<RecentProjectRow>()
        val viewModel = viewModel(source = FakeSource(snapshot = snapshot), opened = opened)
        viewModel.reload()
        val row = viewModel.state.value.ungrouped.first()

        // WHEN
        viewModel.onProjectClicked(row = row)

        // THEN
        opened shouldBe listOf(row)
    }

    @Test
    fun `onSearchQueryChanged - should update the query`() {
        // GIVEN
        val viewModel = viewModel(source = FakeSource(snapshot = snapshot))

        // WHEN
        viewModel.onSearchQueryChanged(query = "abc")

        // THEN
        viewModel.state.value.searchQuery shouldBe "abc"
    }

    @Test
    fun `onRemoveRequested - should remove the path and reload`() {
        // GIVEN
        val source = FakeSource(snapshot = snapshot)
        val viewModel = viewModel(source = source)
        viewModel.reload()
        val row = viewModel.state.value.ungrouped.first { it.name == "gone" }
        source.snapshot = RecentProjectsSnapshot(recentPaths = listOf("/home/me/a"), groups = emptyList())

        // WHEN
        viewModel.onRemoveRequested(row = row)

        // THEN
        assertSoftly {
            source.removed shouldBe listOf("/home/me/gone")
            viewModel.state.value.ungrouped.map { it.name } shouldBe listOf("a")
        }
    }
}