package com.github.fnunezkanut.bbcloud.ui.pullrequests

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.settings.PullRequestListFilter
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

private const val CURRENT_USER = "{5b10ac8d}"

@Tag("unit")
class PullRequestListFilteringTest {
    @Nested
    inner class Resolved {
        @Test
        fun `apply - should hide merged and declined when excludeResolved is checked`() {
            // GIVEN
            val entries = listOf(
                listEntry(id = 1, source = PullRequestListingSource.ProjectOpen, state = PullRequestState.Open),
                listEntry(id = 2, source = PullRequestListingSource.ProjectResolved, state = PullRequestState.Merged),
                listEntry(id = 3, source = PullRequestListingSource.ProjectResolved, state = PullRequestState.Declined)
            )

            // WHEN
            val kept = PullRequestListFiltering.apply(
                entries = entries,
                filter = PullRequestListFilter(excludeResolved = true, excludeDrafts = false),
                currentUserUuid = CURRENT_USER
            )

            // THEN only the open row survives
            kept.map { it.pullRequest.summary.id } shouldContainExactly listOf(1L)
        }

        @Test
        fun `apply - should keep merged and declined when excludeResolved is unchecked`() {
            // GIVEN
            val entries = listOf(
                listEntry(id = 1, source = PullRequestListingSource.ProjectOpen, state = PullRequestState.Open),
                listEntry(id = 2, source = PullRequestListingSource.ProjectResolved, state = PullRequestState.Merged)
            )

            // WHEN
            val kept = PullRequestListFiltering.apply(
                entries = entries,
                filter = PullRequestListFilter(excludeResolved = false, excludeDrafts = false),
                currentUserUuid = CURRENT_USER
            )

            // THEN both survive
            kept.map { it.pullRequest.summary.id } shouldContainExactly listOf(1L, 2L)
        }
    }

    @Nested
    inner class Drafts {
        @Test
        fun `apply - should hide drafts when excludeDrafts is checked`() {
            // GIVEN
            val entries = listOf(
                listEntry(id = 1, source = PullRequestListingSource.ProjectOpen, isDraft = false),
                listEntry(id = 2, source = PullRequestListingSource.ProjectOpen, isDraft = true)
            )

            // WHEN
            val kept = PullRequestListFiltering.apply(
                entries = entries,
                filter = PullRequestListFilter(excludeResolved = false, excludeDrafts = true),
                currentUserUuid = CURRENT_USER
            )

            // THEN the draft is hidden
            kept.map { it.pullRequest.summary.id } shouldContainExactly listOf(1L)
        }
    }

    @Nested
    inner class Mine {
        @Test
        fun `apply - should hide own entries including a brace and case mismatch and keep others`() {
            // GIVEN one mine (case and brace differ), one by someone else, one with no author uuid
            val entries = listOf(
                listEntry(id = 1, source = PullRequestListingSource.ProjectOpen, authorUuid = "5B10AC8D"),
                listEntry(id = 2, source = PullRequestListingSource.ProjectOpen, authorUuid = "{other}"),
                listEntry(id = 3, source = PullRequestListingSource.ProjectOpen, authorUuid = null)
            )

            // WHEN
            val kept = PullRequestListFiltering.apply(
                entries = entries,
                filter = PullRequestListFilter(excludeResolved = false, excludeDrafts = false, excludeMine = true),
                currentUserUuid = CURRENT_USER
            )

            // THEN the mine row is hidden; the other-author and null-author rows stay
            kept.map { it.pullRequest.summary.id } shouldContainExactly listOf(2L, 3L)
        }

        @Test
        fun `apply - should always hide WorkspaceAuthored rows and keep WorkspaceAssigned when Mine is checked`() {
            // GIVEN authored and assigned rows, with a null current user uuid
            val entries = listOf(
                listEntry(id = 1, source = PullRequestListingSource.WorkspaceAuthored),
                listEntry(id = 2, source = PullRequestListingSource.WorkspaceAssigned)
            )

            // WHEN
            val kept = PullRequestListFiltering.apply(
                entries = entries,
                filter = PullRequestListFilter(excludeResolved = false, excludeDrafts = false, excludeMine = true),
                currentUserUuid = null
            )

            // THEN the authored row is mine by construction even with a null uuid; the assigned row stays
            kept.map { it.pullRequest.summary.id } shouldContainExactly listOf(2L)
        }

        @Test
        fun `apply - should not hide non-authored rows when the current user uuid is null`() {
            // GIVEN project rows whose author uuid cannot be matched because the current user is unknown
            val entries = listOf(
                listEntry(id = 1, source = PullRequestListingSource.ProjectOpen, authorUuid = "5B10AC8D"),
                listEntry(id = 2, source = PullRequestListingSource.ProjectOpen, authorUuid = "{other}")
            )

            // WHEN
            val kept = PullRequestListFiltering.apply(
                entries = entries,
                filter = PullRequestListFilter(excludeResolved = false, excludeDrafts = false, excludeMine = true),
                currentUserUuid = null
            )

            // THEN nothing is hidden, because no row can be proven to be mine
            kept.map { it.pullRequest.summary.id } shouldContainExactly listOf(1L, 2L)
        }
    }

    @Nested
    inner class ApprovedByMe {
        @Test
        fun `apply - should hide entries the current user approves including a brace and case mismatch`() {
            // GIVEN one approved by me (case and brace differ in the approver set), one approved by someone else
            val entries = listOf(
                listEntry(id = 1, source = PullRequestListingSource.ProjectOpen, approverUuids = setOf("5B10AC8D")),
                listEntry(id = 2, source = PullRequestListingSource.ProjectOpen, approverUuids = setOf("{other}"))
            )

            // WHEN
            val kept = PullRequestListFiltering.apply(
                entries = entries,
                filter = PullRequestListFilter(excludeResolved = false, excludeDrafts = false, excludeApprovedByMe = true),
                currentUserUuid = CURRENT_USER
            )

            // THEN only the one I did not approve stays
            kept.map { it.pullRequest.summary.id } shouldContainExactly listOf(2L)
        }

        @Test
        fun `apply - should have no effect when the current user uuid is null`() {
            // GIVEN
            val entries = listOf(
                listEntry(id = 1, source = PullRequestListingSource.ProjectOpen, approverUuids = setOf(CURRENT_USER))
            )

            // WHEN
            val kept = PullRequestListFiltering.apply(
                entries = entries,
                filter = PullRequestListFilter(excludeResolved = false, excludeDrafts = false, excludeApprovedByMe = true),
                currentUserUuid = null
            )

            // THEN nothing is hidden
            kept.map { it.pullRequest.summary.id } shouldContainExactly listOf(1L)
        }
    }

    @Nested
    inner class Combination {
        @Test
        fun `apply - should apply all four exclusions at once`() {
            // GIVEN one of each kind to hide, plus one row that passes everything
            val entries = listOf(
                listEntry(id = 1, source = PullRequestListingSource.ProjectResolved, state = PullRequestState.Merged),
                listEntry(id = 2, source = PullRequestListingSource.ProjectOpen, isDraft = true),
                listEntry(id = 3, source = PullRequestListingSource.ProjectOpen, authorUuid = CURRENT_USER),
                listEntry(id = 4, source = PullRequestListingSource.ProjectOpen, approverUuids = setOf(CURRENT_USER)),
                listEntry(id = 5, source = PullRequestListingSource.ProjectOpen, authorUuid = "{other}")
            )

            // WHEN all four exclusions are on
            val kept = PullRequestListFiltering.apply(
                entries = entries,
                filter = PullRequestListFilter(
                    excludeResolved = true,
                    excludeMine = true,
                    excludeApprovedByMe = true,
                    excludeDrafts = true
                ),
                currentUserUuid = CURRENT_USER
            )

            // THEN only the untouched open row survives
            kept.map { it.pullRequest.summary.id } shouldContainExactly listOf(5L)
        }

        @Test
        fun `apply - should keep every open row that passes the other flags for the workspace filter`() {
            // GIVEN a workspace filter (excludeResolved is always false) with the other flags off
            val entries = listOf(
                listEntry(id = 1, source = PullRequestListingSource.WorkspaceAssigned, state = PullRequestState.Open),
                listEntry(id = 2, source = PullRequestListingSource.WorkspaceAssigned, state = PullRequestState.Open)
            )

            // WHEN
            val kept = PullRequestListFiltering.apply(
                entries = entries,
                filter = PullRequestListFilter(excludeResolved = false, excludeMine = false, excludeApprovedByMe = false, excludeDrafts = true),
                currentUserUuid = CURRENT_USER
            )

            // THEN both open rows stay
            kept.map { it.pullRequest.summary.id } shouldContainExactly listOf(1L, 2L)
        }
    }
}