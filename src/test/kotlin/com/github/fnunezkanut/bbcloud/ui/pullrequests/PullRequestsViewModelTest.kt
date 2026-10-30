package com.github.fnunezkanut.bbcloud.ui.pullrequests

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.settings.PullRequestListFilter
import com.github.fnunezkanut.bbcloud.settings.PullRequestListScope
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

private const val CURRENT_USER = "{5b10ac8d}"

@Tag("unit")
class PullRequestsViewModelTest {
    @Test
    fun `onExcludeDraftsChanged - should persist the filter and recompute without new source loads`() {
        // GIVEN a project view model with one open and one draft row loaded
        val onFilterChanged = mockk<(PullRequestListFilter) -> Unit>(relaxed = true)
        val viewModel =
            projectViewModel(onFilterChanged = onFilterChanged, initialFilter = PullRequestListFilter(excludeResolved = false, excludeDrafts = false))
        val scope = CoroutineScope(SupervisorJob())
        try {
            val generation = viewModel.beginRefresh()
            loadBothProjectSources(
                viewModel = viewModel,
                generation = generation,
                openEntries = listOf(
                    listEntry(id = 1, source = PullRequestListingSource.ProjectOpen, isDraft = false),
                    listEntry(id = 2, source = PullRequestListingSource.ProjectOpen, isDraft = true)
                ),
                scope = scope
            )

            // WHEN the Drafts exclusion is toggled on
            viewModel.onExcludeDraftsChanged(checked = true)

            // THEN the callback is given the updated filter, and the draft row is filtered out without a reload
            verify { onFilterChanged(PullRequestListFilter(excludeResolved = false, excludeMine = false, excludeApprovedByMe = false, excludeDrafts = true)) }
            viewModel.state.value.rows.map { it.pullRequest.summary.id } shouldContainExactly listOf(1L)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `onOpenTitle - should route the matching entry with its source to the callback`() {
        // GIVEN a filter that keeps resolved rows visible, so the resolved entry can be looked up for routing
        val opened = mutableListOf<PullRequestListEntry>()
        val viewModel = projectViewModel(
            onOpenTitle = { entry -> opened.add(entry) },
            initialFilter = PullRequestListFilter(excludeResolved = false, excludeDrafts = false)
        )
        val scope = CoroutineScope(SupervisorJob())
        try {
            val generation = viewModel.beginRefresh()
            val resolvedEntry = listEntry(id = 9, source = PullRequestListingSource.ProjectResolved, state = PullRequestState.Merged)
            loadBothProjectSources(
                viewModel = viewModel,
                generation = generation,
                openEntries = emptyList(),
                resolvedEntries = listOf(resolvedEntry),
                scope = scope
            )

            // WHEN a title click arrives for the resolved pull request
            viewModel.onOpenTitle(pullRequest = resolvedEntry.pullRequest)

            // THEN the matching entry, with its source, is forwarded
            opened.map { it.source } shouldContainExactly listOf(PullRequestListingSource.ProjectResolved)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `failSource - should keep the other source rows visible and list the failed source`() {
        // GIVEN a workspace view model where one source loaded and the other failed
        val viewModel = workspaceViewModel()
        val scope = CoroutineScope(SupervisorJob())
        try {
            val generation = viewModel.beginRefresh()
            viewModel.applySource(
                generation = generation,
                source = PullRequestListingSource.WorkspaceAssigned,
                entries = listOf(listEntry(id = 1, source = PullRequestListingSource.WorkspaceAssigned)),
                isTruncated = false,
                currentUserUuid = CURRENT_USER,
                coroutineScope = scope
            )
            viewModel.failSource(generation = generation, source = PullRequestListingSource.WorkspaceAuthored)

            // THEN the loaded rows stay visible and the failed source is reported
            viewModel.state.value.rows.map { it.pullRequest.summary.id } shouldContainExactly listOf(1L)
            viewModel.state.value.failedSources shouldBe setOf(PullRequestListingSource.WorkspaceAuthored)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `applySource - should ignore a stale generation`() {
        // GIVEN a view model with a current generation
        val viewModel = projectViewModel()
        val scope = CoroutineScope(SupervisorJob())
        try {
            viewModel.beginRefresh()
            val staleGeneration = -1L

            // WHEN a response from a stale generation arrives
            viewModel.applySource(
                generation = staleGeneration,
                source = PullRequestListingSource.ProjectOpen,
                entries = listOf(listEntry(id = 99, source = PullRequestListingSource.ProjectOpen)),
                isTruncated = false,
                currentUserUuid = CURRENT_USER,
                coroutineScope = scope
            )

            // THEN it is dropped, so no rows appear
            viewModel.state.value.rows shouldBe emptyList()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `onCountsChanged - should report the visible open count before and after a filter toggle`() {
        // GIVEN a project view model that records every counts callback
        val counts = mutableListOf<Int>()
        val viewModel = projectViewModel(
            onCountsChanged = { openCount, _ -> counts.add(openCount) },
            initialFilter = PullRequestListFilter(excludeResolved = false, excludeDrafts = false)
        )
        val scope = CoroutineScope(SupervisorJob())
        try {
            val generation = viewModel.beginRefresh()
            loadBothProjectSources(
                viewModel = viewModel,
                generation = generation,
                openEntries = listOf(
                    listEntry(id = 1, source = PullRequestListingSource.ProjectOpen, state = PullRequestState.Open, isDraft = false),
                    listEntry(id = 2, source = PullRequestListingSource.ProjectOpen, state = PullRequestState.Open, isDraft = true)
                ),
                scope = scope
            )
            // Two visible open rows after loading
            counts.last() shouldBe 2

            // WHEN the Drafts exclusion hides one open row
            viewModel.onExcludeDraftsChanged(checked = true)

            // THEN the reported visible open count drops to one (decision 4c)
            counts.last() shouldBe 1
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `workspace scope - should normalize a mistaken excludeResolved to false`() {
        // GIVEN a workspace view model handed a filter with excludeResolved = true
        val viewModel = workspaceViewModel(initialFilter = PullRequestListFilter(excludeResolved = true, excludeDrafts = false))
        val scope = CoroutineScope(SupervisorJob())
        try {
            val generation = viewModel.beginRefresh()
            viewModel.applySource(
                generation = generation,
                source = PullRequestListingSource.WorkspaceAssigned,
                entries = listOf(
                    listEntry(id = 1, source = PullRequestListingSource.WorkspaceAssigned, state = PullRequestState.Open),
                    listEntry(id = 2, source = PullRequestListingSource.WorkspaceAssigned, state = PullRequestState.Merged)
                ),
                isTruncated = false,
                currentUserUuid = CURRENT_USER,
                coroutineScope = scope
            )

            // THEN the filter carries excludeResolved = false, so the resolved row is not hidden (both rows stay;
            // the workspace scope sorts newest-first with equal timestamps tie-broken by id descending).
            viewModel.state.value.filter.excludeResolved shouldBe false
            viewModel.state.value.rows.map { it.pullRequest.summary.id } shouldContainExactly listOf(2L, 1L)
        } finally {
            scope.cancel()
        }
    }

    private fun loadBothProjectSources(
        viewModel: PullRequestsViewModel,
        generation: Long,
        openEntries: List<PullRequestListEntry>,
        resolvedEntries: List<PullRequestListEntry> = emptyList(),
        scope: CoroutineScope
    ) {
        viewModel.applySource(
            generation = generation,
            source = PullRequestListingSource.ProjectOpen,
            entries = openEntries,
            isTruncated = false,
            currentUserUuid = CURRENT_USER,
            coroutineScope = scope
        )
        viewModel.applySource(
            generation = generation,
            source = PullRequestListingSource.ProjectResolved,
            entries = resolvedEntries,
            isTruncated = false,
            currentUserUuid = CURRENT_USER,
            coroutineScope = scope
        )
    }

    private fun projectViewModel(
        onFilterChanged: (PullRequestListFilter) -> Unit = {},
        onOpenTitle: (PullRequestListEntry) -> Unit = {},
        onCountsChanged: (Int, PullRequestsCard) -> Unit = { _, _ -> },
        initialFilter: PullRequestListFilter = PullRequestListFilter()
    ): PullRequestsViewModel {
        return PullRequestsViewModel(
            logName = "Current Project - PRs",
            sourcesOfTab = setOf(PullRequestListingSource.ProjectOpen, PullRequestListingSource.ProjectResolved),
            scope = PullRequestListScope.CurrentProject,
            initialFilter = initialFilter,
            organizationUrl = { "https://bitbucket.org/acme/" },
            onFilterChangedCallback = onFilterChanged,
            onOpenTitleCallback = onOpenTitle,
            onOpenPrOnWebCallback = {},
            onOpenRepositoryCallback = {},
            onCountsChanged = onCountsChanged
        )
    }

    private fun workspaceViewModel(initialFilter: PullRequestListFilter = PullRequestListFilter()): PullRequestsViewModel {
        return PullRequestsViewModel(
            logName = "Workspace - PRs",
            sourcesOfTab = setOf(PullRequestListingSource.WorkspaceAuthored, PullRequestListingSource.WorkspaceAssigned),
            scope = PullRequestListScope.Workspace,
            initialFilter = initialFilter,
            organizationUrl = { "https://bitbucket.org/acme/" },
            onFilterChangedCallback = {},
            onOpenTitleCallback = {},
            onOpenPrOnWebCallback = {},
            onOpenRepositoryCallback = {}
        )
    }
}