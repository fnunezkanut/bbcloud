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
    fun `onGoToWorkspaceClicked - invokes the go-to-workspace callback once`() {
        // GIVEN
        var workspaceCalls = 0
        val viewModel = viewModel(onGoToWorkspace = { workspaceCalls++ })

        // WHEN
        viewModel.onGoToWorkspaceClicked()

        // THEN
        workspaceCalls shouldBe 1
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

    @Nested
    inner class WatchedLimitTest {
        @Test
        fun `onWatchedChanged - at the limit refuses a new tick, warns, and leaves the flag unchanged`() {
            // GIVEN
            val host = HostCalls()
            val dataSource = FakeWatchedProjectsDataSource(mappings = watchedAndUnwatched(watched = 20, unwatched = 1))
            val viewModel = viewModel(dataSource = dataSource, host = host)
            viewModel.bindWatchedProjects()

            // WHEN
            viewModel.onWatchedChanged(mapping = mapping("repo-21"), watched = true)

            // THEN
            assertSoftly {
                host.limitReached shouldBe 1
                dataSource.setWatchedCalls shouldBe emptyList()
                dataSource.mappings.last().watched shouldBe false
                viewModel.state.value.rows.last().mapping.watched shouldBe false
            }
        }

        @Test
        fun `onWatchedChanged - below the limit allows the tick without warning`() {
            // GIVEN
            val host = HostCalls()
            val dataSource = FakeWatchedProjectsDataSource(mappings = watchedAndUnwatched(watched = 19, unwatched = 1))
            val viewModel = viewModel(dataSource = dataSource, host = host)
            viewModel.bindWatchedProjects()

            // WHEN
            viewModel.onWatchedChanged(mapping = mapping("repo-20"), watched = true)

            // THEN
            assertSoftly {
                dataSource.mappings.last().watched shouldBe true
                host.limitReached shouldBe 0
            }
        }

        @Test
        fun `onWatchedChanged - unticking is always allowed even at the limit`() {
            // GIVEN
            val host = HostCalls()
            val dataSource = FakeWatchedProjectsDataSource(mappings = watchedAndUnwatched(watched = 20, unwatched = 0))
            val viewModel = viewModel(dataSource = dataSource, host = host)
            viewModel.bindWatchedProjects()

            // WHEN
            viewModel.onWatchedChanged(mapping = mapping("repo-05", watched = true), watched = false)

            // THEN
            assertSoftly {
                dataSource.mappings.count { mapping -> mapping.watched } shouldBe 19
                host.limitReached shouldBe 0
            }
        }

        @Test
        fun `onWatchedChanged - re-ticking an already watched row does not warn`() {
            // GIVEN
            val host = HostCalls()
            val dataSource = FakeWatchedProjectsDataSource(mappings = watchedAndUnwatched(watched = 20, unwatched = 0))
            val viewModel = viewModel(dataSource = dataSource, host = host)
            viewModel.bindWatchedProjects()

            // WHEN
            viewModel.onWatchedChanged(mapping = mapping("repo-01", watched = true), watched = true)

            // THEN
            assertSoftly {
                dataSource.mappings.count { mapping -> mapping.watched } shouldBe 20
                host.limitReached shouldBe 0
            }
        }

        @Test
        fun `onMasterToggleClicked - without a filter and more than 20 projects watches the first 20 and warns`() {
            // GIVEN
            val host = HostCalls()
            val dataSource = FakeWatchedProjectsDataSource(mappings = watchedAndUnwatched(watched = 0, unwatched = 25))
            val viewModel = viewModel(dataSource = dataSource, host = host)
            viewModel.bindWatchedProjects()

            // WHEN
            viewModel.onMasterToggleClicked()

            // THEN
            assertSoftly {
                dataSource.mappings.map { mapping -> mapping.watched } shouldBe List(20) { true } + List(5) { false }
                dataSource.mappings.first { mapping -> mapping.remoteRepoSlug == "repo-20" }.watched shouldBe true
                dataSource.mappings.first { mapping -> mapping.remoteRepoSlug == "repo-21" }.watched shouldBe false
                host.limitReached shouldBe 1
            }
        }

        @Test
        fun `onMasterToggleClicked - without a filter and 20 or fewer projects watches all without warning`() {
            // GIVEN
            val host = HostCalls()
            val dataSource = FakeWatchedProjectsDataSource(mappings = watchedAndUnwatched(watched = 0, unwatched = 20))
            val viewModel = viewModel(dataSource = dataSource, host = host)
            viewModel.bindWatchedProjects()

            // WHEN
            viewModel.onMasterToggleClicked()

            // THEN
            assertSoftly {
                dataSource.mappings.all { mapping -> mapping.watched } shouldBe true
                host.limitReached shouldBe 0
            }
        }

        @Test
        fun `onMasterToggleClicked - unwatch-all is always allowed at the limit`() {
            // GIVEN
            val host = HostCalls()
            val dataSource = FakeWatchedProjectsDataSource(mappings = watchedAndUnwatched(watched = 20, unwatched = 0))
            val viewModel = viewModel(dataSource = dataSource, host = host)
            viewModel.bindWatchedProjects()

            // WHEN
            viewModel.onMasterToggleClicked()

            // THEN
            assertSoftly {
                dataSource.mappings.none { mapping -> mapping.watched } shouldBe true
                host.limitReached shouldBe 0
            }
        }

        @Test
        fun `onMasterToggleClicked - filtered select-all watches only the remaining budget and warns`() {
            // GIVEN
            val host = HostCalls()
            val mappings = slugs(prefix = "api", count = 18).map { slug -> mapping(slug, watched = true) } +
                slugs(prefix = "web", count = 5).map { slug -> mapping(slug) }
            val dataSource = FakeWatchedProjectsDataSource(mappings = mappings)
            val viewModel = viewModel(dataSource = dataSource, host = host)
            viewModel.bindWatchedProjects()
            viewModel.onSearchQueryChanged(query = "web")

            // WHEN
            viewModel.onMasterToggleClicked()

            // THEN
            assertSoftly {
                dataSource.setWatchedCalls shouldBe listOf("web-01" to true, "web-02" to true)
                dataSource.mappings.filter { mapping -> mapping.remoteRepoSlug.startsWith("web") }
                    .map { mapping -> mapping.watched } shouldBe listOf(true, true, false, false, false)
                dataSource.mappings.count { mapping -> mapping.watched } shouldBe 20
                host.limitReached shouldBe 1
            }
        }

        private fun watchedAndUnwatched(watched: Int, unwatched: Int): List<WatchedProjectMapping> {
            return slugs(prefix = "repo", count = watched + unwatched).mapIndexed { index, slug ->
                mapping(slug, watched = index < watched)
            }
        }

        private fun slugs(prefix: String, count: Int): List<String> {
            return (1..count).map { index -> "%s-%02d".format(prefix, index) }
        }
    }

    private fun viewModel(
        onPruneWorktrees: () -> Unit = {},
        onOpenWorktreesHelp: () -> Unit = {},
        onGoToWorkspace: () -> Unit = {},
        dataSource: WatchedProjectsDataSource = FakeWatchedProjectsDataSource(),
        host: HostCalls = HostCalls()
    ): WatchedProjectsViewModel {
        return WatchedProjectsViewModel(
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            dataSource = dataSource,
            onShowContentCallback = { host.content++ },
            onShowNotConfiguredCallback = { host.notConfigured++ },
            onProjectsDirectoryUnreadableCallback = { host.unreadable++ },
            onWatchedLimitReachedCallback = { host.limitReached++ },
            onOpenLocalProjectCallback = { _ -> },
            onOpenWorktreeCallback = { _, _ -> },
            onPruneWorktreesCallback = onPruneWorktrees,
            onOpenWorktreesHelpCallback = onOpenWorktreesHelp,
            onGoToWorkspaceCallback = onGoToWorkspace,
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
        var limitReached = 0
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