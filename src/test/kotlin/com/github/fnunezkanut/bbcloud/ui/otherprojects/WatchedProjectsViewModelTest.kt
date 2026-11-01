package com.github.fnunezkanut.bbcloud.ui.otherprojects

import androidx.compose.ui.state.ToggleableState
import com.github.fnunezkanut.bbcloud.project.WatchedProjectMapping
import com.github.fnunezkanut.bbcloud.project.WatchedProjectsDiscoveryResult
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

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

    @Test
    fun `onWorktreesHelpClicked - invokes the work trees help callback once`() {
        // GIVEN
        var helpCalls = 0
        val viewModel = viewModel(onOpenWorktreesHelp = { helpCalls++ })

        // WHEN
        viewModel.onWorktreesHelpClicked()

        // THEN
        helpCalls shouldBe 1
    }

    @Test
    fun `bindWatchedProjects - routes to not-configured and hides Sync when no projects directory is set`() {
        // GIVEN
        val dataSource = FakeWatchedProjectsDataSource(projectsDirectorySet = false)
        val host = HostCalls()
        val viewModel = viewModel(dataSource = dataSource, host = host)

        // WHEN
        viewModel.bindWatchedProjects()

        // THEN
        assertSoftly {
            host.notConfigured shouldBe 1
            host.content shouldBe 0
            viewModel.state.value.syncVisible shouldBe false
        }
    }

    @Test
    fun `bindWatchedProjects - shows the stored mappings sorted and switches to content`() {
        // GIVEN
        val dataSource = FakeWatchedProjectsDataSource(mappings = listOf(mapping("zeta"), mapping("alpha")))
        val host = HostCalls()
        val viewModel = viewModel(dataSource = dataSource, host = host)

        // WHEN
        viewModel.bindWatchedProjects()

        // THEN
        assertSoftly {
            host.content shouldBe 1
            viewModel.state.value.card shouldBe WatchedProjectsCard.Listing
            viewModel.state.value.rows.map { row -> row.mapping.remoteRepoSlug } shouldBe listOf("alpha", "zeta")
            viewModel.state.value.syncVisible shouldBe true
            viewModel.state.value.syncEnabled shouldBe true
        }
    }

    @Test
    fun `onWatchedChanged - persists the flag and reloads the rows without a rescan`() {
        // GIVEN
        val dataSource = FakeWatchedProjectsDataSource(mappings = listOf(mapping("alpha")))
        val viewModel = viewModel(dataSource = dataSource)
        viewModel.bindWatchedProjects()

        // WHEN
        viewModel.onWatchedChanged(mapping = mapping("alpha"), watched = true)

        // THEN
        assertSoftly {
            dataSource.mappings.single().watched shouldBe true
            viewModel.state.value.rows.single().mapping.watched shouldBe true
            dataSource.rescanCalls shouldBe 0
        }
    }

    @Test
    fun `rescanWatchedProjects - disables Sync and shows Loading until the scan finishes`() {
        // GIVEN
        val pending = CompletableDeferred<WatchedProjectsDiscoveryResult>()
        val dataSource = FakeWatchedProjectsDataSource(mappings = listOf(mapping("alpha")), rescanResult = pending)
        val viewModel = viewModel(dataSource = dataSource)

        // WHEN
        viewModel.onSyncClicked()
        val whileRunning = viewModel.state.value
        pending.complete(WatchedProjectsDiscoveryResult.Success(mappings = dataSource.mappings))
        val afterFinish = viewModel.state.value

        // THEN
        assertSoftly {
            whileRunning.card shouldBe WatchedProjectsCard.Loading
            whileRunning.syncEnabled shouldBe false
            afterFinish.card shouldBe WatchedProjectsCard.Listing
            afterFinish.syncEnabled shouldBe true
            afterFinish.rescanFailed shouldBe false
        }
    }

    @Test
    fun `rescanWatchedProjects - keeps the previous list and shows the warning when verification fails`() {
        // GIVEN
        val dataSource = FakeWatchedProjectsDataSource(
            mappings = listOf(mapping("alpha")),
            rescanResult = CompletableDeferred(WatchedProjectsDiscoveryResult.VerifyFailed)
        )
        val viewModel = viewModel(dataSource = dataSource)

        // WHEN
        viewModel.rescanWatchedProjects()

        // THEN
        assertSoftly {
            viewModel.state.value.card shouldBe WatchedProjectsCard.Listing
            viewModel.state.value.rescanFailed shouldBe true
        }
    }

    @Test
    fun `rescanWatchedProjects - asks the host for the unreadable-directory dialog`() {
        // GIVEN
        val dataSource = FakeWatchedProjectsDataSource(rescanResult = CompletableDeferred(WatchedProjectsDiscoveryResult.ProjectsDirectoryUnreadable))
        val host = HostCalls()
        val viewModel = viewModel(dataSource = dataSource, host = host)

        // WHEN
        viewModel.rescanWatchedProjects()

        // THEN
        assertSoftly {
            host.unreadable shouldBe 1
            viewModel.state.value.card shouldBe WatchedProjectsCard.Empty
            viewModel.state.value.syncEnabled shouldBe true
        }
    }

    @Test
    fun `rescanWatchedProjects - shows the empty state when the scan reports not configured`() {
        // GIVEN
        val dataSource = FakeWatchedProjectsDataSource(
            mappings = listOf(mapping("alpha")),
            rescanResult = CompletableDeferred(WatchedProjectsDiscoveryResult.NotConfigured)
        )
        val viewModel = viewModel(dataSource = dataSource)

        // WHEN
        viewModel.rescanWatchedProjects()

        // THEN
        viewModel.state.value.card shouldBe WatchedProjectsCard.Empty
    }

    @Test
    fun `rescanWatchedProjects - binds instead of scanning when no organization URL is set`() {
        // GIVEN
        val dataSource = FakeWatchedProjectsDataSource(organizationUrl = null, mappings = listOf(mapping("alpha")))
        val viewModel = viewModel(dataSource = dataSource)

        // WHEN
        viewModel.rescanWatchedProjects(clearFirst = true)

        // THEN
        assertSoftly {
            dataSource.rescanCalls shouldBe 0
            viewModel.state.value.card shouldBe WatchedProjectsCard.Listing
        }
    }

    @Nested
    inner class SearchTest {
        @Test
        fun `onSearchQueryChanged - narrows visibleRows and keeps rows complete`() {
            // GIVEN
            val viewModel = viewModelWithMappings("alpha-api", "beta-web")

            // WHEN
            viewModel.onSearchQueryChanged(query = "beta")

            // THEN
            assertSoftly {
                viewModel.state.value.searchQuery shouldBe "beta"
                viewModel.state.value.visibleRows.map { row -> row.mapping.remoteRepoSlug } shouldBe listOf("beta-web")
                viewModel.state.value.rows.map { row -> row.mapping.remoteRepoSlug } shouldBe listOf("alpha-api", "beta-web")
            }
        }

        @Test
        fun `search query - survives a watched toggle, a rescan and a loading state`() {
            // GIVEN
            val dataSource = FakeWatchedProjectsDataSource(mappings = listOf(mapping("alpha-api"), mapping("beta-web")))
            val viewModel = viewModel(dataSource = dataSource)
            viewModel.bindWatchedProjects()
            viewModel.onSearchQueryChanged(query = "beta")

            // WHEN
            viewModel.onWatchedChanged(mapping = mapping("beta-web"), watched = true)
            viewModel.showResult(mappings = dataSource.mappings, organizationUrl = "https://bitbucket.org/acme/")
            viewModel.showLoading()

            // THEN
            viewModel.state.value.searchQuery shouldBe "beta"
        }

        @Test
        fun `master toggle - reflects only the visible rows`() {
            // GIVEN
            val dataSource = FakeWatchedProjectsDataSource(
                mappings = listOf(
                    mapping("alpha", watched = true),
                    mapping("beta", watched = true),
                    mapping("gamma", watched = false)
                )
            )
            val viewModel = viewModel(dataSource = dataSource)
            viewModel.bindWatchedProjects()

            // WHEN
            viewModel.onSearchQueryChanged(query = "beta")
            val onlyWatched = viewModel.state.value.masterToggle
            viewModel.onSearchQueryChanged(query = "gamma")
            val onlyUnwatched = viewModel.state.value.masterToggle
            viewModel.onSearchQueryChanged(query = "")
            val unfiltered = viewModel.state.value.masterToggle

            // THEN
            assertSoftly {
                onlyWatched shouldBe ToggleableState.On
                onlyUnwatched shouldBe ToggleableState.Off
                unfiltered shouldBe ToggleableState.Indeterminate
            }
        }

        @Test
        fun `onMasterToggleClicked - with a filter watches only the visible rows`() {
            // GIVEN
            val dataSource = FakeWatchedProjectsDataSource(
                mappings = listOf(mapping("alpha-api"), mapping("beta-web"), mapping("gamma-api"))
            )
            val viewModel = viewModel(dataSource = dataSource)
            viewModel.bindWatchedProjects()
            viewModel.onSearchQueryChanged(query = "api")

            // WHEN
            viewModel.onMasterToggleClicked()

            // THEN
            assertSoftly {
                dataSource.setWatchedCalls shouldBe listOf("alpha-api" to true, "gamma-api" to true)
                dataSource.setAllWatchedCalls shouldBe 0
                dataSource.mappings.map { mapping -> mapping.watched } shouldBe listOf(true, false, true)
            }
        }

        @Test
        fun `onMasterToggleClicked - without a filter still watches every project`() {
            // GIVEN
            val dataSource = FakeWatchedProjectsDataSource(mappings = listOf(mapping("alpha"), mapping("beta")))
            val viewModel = viewModel(dataSource = dataSource)
            viewModel.bindWatchedProjects()

            // WHEN
            viewModel.onMasterToggleClicked()

            // THEN
            assertSoftly {
                dataSource.setAllWatchedCalls shouldBe 1
                dataSource.setWatchedCalls shouldBe emptyList()
            }
        }

        @Test
        fun `onMasterToggleClicked - with a filter that matches nothing changes no project`() {
            // GIVEN
            val dataSource = FakeWatchedProjectsDataSource(mappings = listOf(mapping("alpha"), mapping("beta")))
            val viewModel = viewModel(dataSource = dataSource)
            viewModel.bindWatchedProjects()
            viewModel.onSearchQueryChanged(query = "missing")

            // WHEN
            viewModel.onMasterToggleClicked()

            // THEN
            assertSoftly {
                dataSource.setAllWatchedCalls shouldBe 0
                dataSource.setWatchedCalls shouldBe emptyList()
            }
        }

        @Test
        fun `onSearchCleared - restores every row`() {
            // GIVEN
            val viewModel = viewModelWithMappings("alpha-api", "beta-web")
            viewModel.onSearchQueryChanged(query = "beta")

            // WHEN
            viewModel.onSearchCleared()

            // THEN
            assertSoftly {
                viewModel.state.value.searchQuery shouldBe ""
                viewModel.state.value.visibleRows.size shouldBe 2
            }
        }

        private fun viewModelWithMappings(vararg slugs: String): WatchedProjectsViewModel {
            val dataSource = FakeWatchedProjectsDataSource(mappings = slugs.map { slug -> mapping(slug) })
            val viewModel = viewModel(dataSource = dataSource)
            viewModel.bindWatchedProjects()
            return viewModel
        }
    }

    private fun viewModel(
        onPruneWorktrees: () -> Unit = {},
        onOpenWorktreesHelp: () -> Unit = {},
        dataSource: WatchedProjectsDataSource = FakeWatchedProjectsDataSource(),
        host: HostCalls = HostCalls()
    ): WatchedProjectsViewModel {
        return WatchedProjectsViewModel(
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            dataSource = dataSource,
            onShowContentCallback = { host.content++ },
            onShowNotConfiguredCallback = { host.notConfigured++ },
            onProjectsDirectoryUnreadableCallback = { host.unreadable++ },
            onOpenLocalProjectCallback = { _ -> },
            onOpenWorktreeCallback = { _, _ -> },
            onPruneWorktreesCallback = onPruneWorktrees,
            onOpenWorktreesHelpCallback = onOpenWorktreesHelp,
            runOnEdt = { block -> block() }
        )
    }

    private fun mapping(slug: String, watched: Boolean = false): WatchedProjectMapping {
        return WatchedProjectMapping(
            localFolderPath = "/projects/$slug",
            remoteRepoSlug = slug,
            workspace = "acme",
            watched = watched
        )
    }

    private class HostCalls {
        var content = 0
        var notConfigured = 0
        var unreadable = 0
    }

    private class FakeWatchedProjectsDataSource(
        private val organizationUrl: String? = "https://bitbucket.org/acme/",
        private val projectsDirectorySet: Boolean = true,
        var mappings: List<WatchedProjectMapping> = emptyList(),
        private val rescanResult: CompletableDeferred<WatchedProjectsDiscoveryResult> =
            CompletableDeferred(WatchedProjectsDiscoveryResult.Success(mappings = emptyList()))
    ) : WatchedProjectsDataSource {
        var rescanCalls = 0
        val setWatchedCalls = mutableListOf<Pair<String, Boolean>>()
        var setAllWatchedCalls = 0

        override fun organizationUrlOrNull(): String? {
            return organizationUrl
        }

        override fun isProjectsDirectorySet(): Boolean {
            return projectsDirectorySet
        }

        override fun watchedProjectsWithLocalPaths(): List<WatchedProjectMapping> {
            return mappings
        }

        override fun repoAvatarUrls(): Map<String, String> {
            return emptyMap()
        }

        override fun workTreeDirectoryOrNull(): Path? {
            return null
        }

        override fun setWatched(mapping: WatchedProjectMapping, watched: Boolean) {
            setWatchedCalls.add(mapping.remoteRepoSlug to watched)
            mappings = mappings.map { existing ->
                if (existing.remoteRepoSlug == mapping.remoteRepoSlug) existing.copy(watched = watched) else existing
            }
        }

        override fun setAllWatched(watched: Boolean) {
            setAllWatchedCalls++
            mappings = mappings.map { existing -> existing.copy(watched = watched) }
        }

        override suspend fun rescan(clearFirst: Boolean): WatchedProjectsDiscoveryResult {
            rescanCalls++
            return rescanResult.await()
        }
    }
}