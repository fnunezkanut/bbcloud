package com.github.fnunezkanut.bbcloud.ui.pullrequests

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestListMergerTest {
    @Test
    fun `merge - should order newest updatedOn first across sources when openFirst is false`() {
        // GIVEN rows from two sources with mixed update times
        val entriesBySource = mapOf(
            PullRequestListingSource.WorkspaceAuthored to listOf(
                listEntry(id = 1, source = PullRequestListingSource.WorkspaceAuthored, repoSlug = "repo-a", updatedOn = "2026-09-01T12:00:00Z")
            ),
            PullRequestListingSource.WorkspaceAssigned to listOf(
                listEntry(id = 2, source = PullRequestListingSource.WorkspaceAssigned, repoSlug = "repo-b", updatedOn = "2026-10-01T12:00:00Z")
            )
        )

        // WHEN
        val merged = PullRequestListMerger.merge(entriesBySource = entriesBySource, openFirst = false)

        // THEN newest-updated first, interleaved across sources
        merged.map { it.pullRequest.summary.id } shouldContainExactly listOf(2L, 1L)
    }

    @Test
    fun `merge - should list open rows before resolved rows when openFirst is true`() {
        // GIVEN an older open row and a newer resolved row
        val entriesBySource = mapOf(
            PullRequestListingSource.ProjectOpen to listOf(
                listEntry(id = 1, source = PullRequestListingSource.ProjectOpen, state = PullRequestState.Open, updatedOn = "2026-09-01T12:00:00Z")
            ),
            PullRequestListingSource.ProjectResolved to listOf(
                listEntry(id = 2, source = PullRequestListingSource.ProjectResolved, state = PullRequestState.Merged, updatedOn = "2026-10-01T12:00:00Z")
            )
        )

        // WHEN
        val merged = PullRequestListMerger.merge(entriesBySource = entriesBySource, openFirst = true)

        // THEN the open row leads even though the resolved row is newer
        merged.map { it.pullRequest.summary.id } shouldContainExactly listOf(1L, 2L)
    }

    @Test
    fun `merge - should interleave open and resolved by time when openFirst is false`() {
        // GIVEN the same rows as above
        val entriesBySource = mapOf(
            PullRequestListingSource.ProjectOpen to listOf(
                listEntry(id = 1, source = PullRequestListingSource.ProjectOpen, state = PullRequestState.Open, updatedOn = "2026-09-01T12:00:00Z")
            ),
            PullRequestListingSource.ProjectResolved to listOf(
                listEntry(id = 2, source = PullRequestListingSource.ProjectResolved, state = PullRequestState.Merged, updatedOn = "2026-10-01T12:00:00Z")
            )
        )

        // WHEN openFirst is false
        val merged = PullRequestListMerger.merge(entriesBySource = entriesBySource, openFirst = false)

        // THEN purely newest first, so the resolved row leads
        merged.map { it.pullRequest.summary.id } shouldContainExactly listOf(2L, 1L)
    }

    @Test
    fun `merge - should de-duplicate by key with WorkspaceAuthored taking precedence over WorkspaceAssigned`() {
        // GIVEN the same pull request appearing in both sources, with different slug casing
        val entriesBySource = mapOf(
            PullRequestListingSource.WorkspaceAuthored to listOf(
                listEntry(id = 7, source = PullRequestListingSource.WorkspaceAuthored, repoSlug = "Repo")
            ),
            PullRequestListingSource.WorkspaceAssigned to listOf(
                listEntry(id = 7, source = PullRequestListingSource.WorkspaceAssigned, repoSlug = "repo")
            )
        )

        // WHEN
        val merged = PullRequestListMerger.merge(entriesBySource = entriesBySource, openFirst = false)

        // THEN only one row remains, and it is the authored one (declaration order wins); slug match is case-insensitive
        merged.map { it.source } shouldContainExactly listOf(PullRequestListingSource.WorkspaceAuthored)
    }

    @Test
    fun `merge - should break ties by repoSlug then id descending for a stable order`() {
        // GIVEN rows that share an updatedOn timestamp
        val entriesBySource = mapOf(
            PullRequestListingSource.WorkspaceAuthored to listOf(
                listEntry(id = 1, source = PullRequestListingSource.WorkspaceAuthored, repoSlug = "repo-a", updatedOn = "2026-09-01T12:00:00Z"),
                listEntry(id = 2, source = PullRequestListingSource.WorkspaceAuthored, repoSlug = "repo-b", updatedOn = "2026-09-01T12:00:00Z"),
                listEntry(id = 3, source = PullRequestListingSource.WorkspaceAuthored, repoSlug = "repo-b", updatedOn = "2026-09-01T12:00:00Z")
            )
        )

        // WHEN
        val merged = PullRequestListMerger.merge(entriesBySource = entriesBySource, openFirst = false)

        // THEN repoSlug descending, then id descending within a slug (repo-b/3, repo-b/2, repo-a/1)
        merged.map { it.pullRequest.summary.id } shouldContainExactly listOf(3L, 2L, 1L)
    }
}