package com.github.fnunezkanut.bbcloud.ui.otherprojects

import androidx.compose.ui.state.ToggleableState
import com.github.fnunezkanut.bbcloud.project.WatchedProjectMapping
import com.github.fnunezkanut.bbcloud.project.WatchedProjectWorktreeRow
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

@Tag("unit")
class WatchedProjectsUiStateTest {
    @Test
    fun `deriveMasterToggle - reflects none all and partial watched rows`() {
        // GIVEN
        val rows = listOf(
            row(remoteRepoSlug = "first", watched = false),
            row(remoteRepoSlug = "second", watched = true)
        )

        // WHEN
        val results = listOf(
            deriveMasterToggle(emptyList()),
            deriveMasterToggle(rows.map { it.copy(mapping = it.mapping.copy(watched = false)) }),
            deriveMasterToggle(rows.map { it.copy(mapping = it.mapping.copy(watched = true)) }),
            deriveMasterToggle(rows)
        )

        // THEN
        assertSoftly(results) {
            this[0] shouldBe ToggleableState.Off
            this[1] shouldBe ToggleableState.Off
            this[2] shouldBe ToggleableState.On
            this[3] shouldBe ToggleableState.Indeterminate
        }
    }

    @Nested
    inner class FilterWatchedProjectRowsTest {
        @Test
        fun `blank query - returns all rows in the original order`() {
            // GIVEN
            val rows = listOf(row(remoteRepoSlug = "zeta"), row(remoteRepoSlug = "alpha"))

            // WHEN
            val blank = filterWatchedProjectRows(rows = rows, query = "")
            val whitespace = filterWatchedProjectRows(rows = rows, query = "   ")

            // THEN
            assertSoftly {
                blank shouldBe rows
                whitespace shouldBe rows
            }
        }

        @Test
        fun `remote slug - matches ignoring case`() {
            // GIVEN
            val rows = listOf(row(remoteRepoSlug = "api-service"), row(remoteRepoSlug = "web-app"))

            // WHEN
            val result = filterWatchedProjectRows(rows = rows, query = "API")

            // THEN
            result.map { row -> row.mapping.remoteRepoSlug } shouldBe listOf("api-service")
        }

        @Test
        fun `local slug and local folder path - match when they differ from the remote slug`() {
            // GIVEN
            val rows = listOf(
                row(remoteRepoSlug = "billing-platform", localFolderPath = "/Users/dev/checkouts/billing-local"),
                row(remoteRepoSlug = "other", localFolderPath = "/Users/dev/other")
            )

            // WHEN
            val bySlug = filterWatchedProjectRows(rows = rows, query = "billing-local")
            val byPath = filterWatchedProjectRows(rows = rows, query = "checkouts")

            // THEN
            assertSoftly {
                bySlug.map { row -> row.mapping.remoteRepoSlug } shouldBe listOf("billing-platform")
                byPath.map { row -> row.mapping.remoteRepoSlug } shouldBe listOf("billing-platform")
            }
        }

        @Test
        fun `worktree branch and pull request number - match`() {
            // GIVEN
            val rows = listOf(
                row(remoteRepoSlug = "alpha", worktrees = listOf(worktree(prNumber = 42, branchName = "feature/login"))),
                row(remoteRepoSlug = "beta")
            )

            // WHEN
            val byBranch = filterWatchedProjectRows(rows = rows, query = "login")
            val byPrNumber = filterWatchedProjectRows(rows = rows, query = "#42")

            // THEN
            assertSoftly {
                byBranch.map { row -> row.mapping.remoteRepoSlug } shouldBe listOf("alpha")
                byPrNumber.map { row -> row.mapping.remoteRepoSlug } shouldBe listOf("alpha")
            }
        }

        @Test
        fun `multiple tokens - every token must match some field`() {
            // GIVEN
            val rows = listOf(
                row(remoteRepoSlug = "api-service", worktrees = listOf(worktree(prNumber = 7, branchName = "main-fix"))),
                row(remoteRepoSlug = "api-service-two"),
                row(remoteRepoSlug = "web", worktrees = listOf(worktree(prNumber = 8, branchName = "main")))
            )

            // WHEN
            val result = filterWatchedProjectRows(rows = rows, query = "api main")

            // THEN
            result.map { row -> row.mapping.remoteRepoSlug } shouldBe listOf("api-service")
        }

        @Test
        fun `no match - returns an empty list`() {
            // GIVEN
            val rows = listOf(row(remoteRepoSlug = "alpha"))

            // WHEN
            val result = filterWatchedProjectRows(rows = rows, query = "missing")

            // THEN
            result shouldBe emptyList()
        }

        @Test
        fun `missing branch name - is not matched by the text null`() {
            // GIVEN
            val rows = listOf(row(remoteRepoSlug = "alpha", worktrees = listOf(worktree(prNumber = 3, branchName = null))))

            // WHEN
            val result = filterWatchedProjectRows(rows = rows, query = "null")

            // THEN
            result shouldBe emptyList()
        }

        @Test
        fun `visibleRows - follows the search query and keeps rows complete`() {
            // GIVEN
            val state = WatchedProjectsUiState(
                rows = listOf(row(remoteRepoSlug = "alpha"), row(remoteRepoSlug = "beta")),
                searchQuery = "beta"
            )

            // WHEN
            val visible = state.visibleRows

            // THEN
            assertSoftly {
                visible.map { row -> row.mapping.remoteRepoSlug } shouldBe listOf("beta")
                state.rows.size shouldBe 2
            }
        }

        @Test
        fun `visibleRows - show only watched keeps watched rows and leaves rows complete`() {
            // GIVEN
            val state = WatchedProjectsUiState(
                rows = listOf(row(remoteRepoSlug = "alpha", watched = true), row(remoteRepoSlug = "beta")),
                showOnlyWatched = true
            )

            // WHEN
            val visible = state.visibleRows

            // THEN
            assertSoftly {
                visible.map { row -> row.mapping.remoteRepoSlug } shouldBe listOf("alpha")
                state.rows.size shouldBe 2
            }
        }

        @Test
        fun `visibleRows - show only watched combines with the search query`() {
            // GIVEN
            val state = WatchedProjectsUiState(
                rows = listOf(
                    row(remoteRepoSlug = "alpha-api", watched = true),
                    row(remoteRepoSlug = "alpha-web"),
                    row(remoteRepoSlug = "beta-api", watched = true)
                ),
                searchQuery = "api",
                showOnlyWatched = true
            )

            // WHEN
            val visible = state.visibleRows

            // THEN
            visible.map { row -> row.mapping.remoteRepoSlug } shouldBe listOf("alpha-api", "beta-api")
        }

        @Test
        fun `visibleRows - show only watched is off by default`() {
            // GIVEN
            val state = WatchedProjectsUiState(rows = listOf(row(remoteRepoSlug = "alpha"), row(remoteRepoSlug = "beta")))

            // WHEN
            val visible = state.visibleRows

            // THEN
            visible.size shouldBe 2
        }
    }

    private fun row(
        remoteRepoSlug: String,
        watched: Boolean = false,
        localFolderPath: String = "/tmp/$remoteRepoSlug",
        worktrees: List<WatchedProjectWorktreeRow> = emptyList()
    ): WatchedProjectRow {
        return buildWatchedProjectRow(
            mapping = WatchedProjectMapping(
                localFolderPath = localFolderPath,
                remoteRepoSlug = remoteRepoSlug,
                workspace = "workspace",
                watched = watched
            ),
            organizationUrl = null,
            avatarUrl = null,
            worktrees = worktrees
        )
    }

    private fun worktree(prNumber: Long, branchName: String?): WatchedProjectWorktreeRow {
        return WatchedProjectWorktreeRow(
            prNumber = prNumber,
            branchName = branchName,
            worktreePath = Path.of("/tmp/worktrees/pr-$prNumber")
        )
    }
}