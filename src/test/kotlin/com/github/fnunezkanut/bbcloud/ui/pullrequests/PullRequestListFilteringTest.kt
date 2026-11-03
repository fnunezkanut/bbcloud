package com.github.fnunezkanut.bbcloud.ui.pullrequests

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.settings.PullRequestAuthorScope
import com.github.fnunezkanut.bbcloud.settings.PullRequestListFilter
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
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
    inner class CreatedBy {
        @Test
        fun `apply - should keep only own entries including a brace and case mismatch when Created by me is selected`() {
            // GIVEN one mine (case and brace differ), one by someone else, one with no author uuid
            val entries = listOf(
                listEntry(id = 1, source = PullRequestListingSource.ProjectOpen, authorUuid = "5B10AC8D"),
                listEntry(id = 2, source = PullRequestListingSource.ProjectOpen, authorUuid = "{other}"),
                listEntry(id = 3, source = PullRequestListingSource.ProjectOpen, authorUuid = null)
            )

            // WHEN
            val kept = PullRequestListFiltering.apply(
                entries = entries,
                filter = PullRequestListFilter(
                    excludeResolved = false,
                    excludeDrafts = false,
                    authorScope = PullRequestAuthorScope.Me
                ),
                currentUserUuid = CURRENT_USER
            )

            // THEN only the own row stays; the other-author and null-author rows are hidden
            kept.map { it.pullRequest.summary.id } shouldContainExactly listOf(1L)
        }

        @Test
        fun `apply - should always keep WorkspaceAuthored rows and drop WorkspaceAssigned when Created by me is selected with a null uuid`() {
            // GIVEN authored and assigned rows, with a null current user uuid
            val entries = listOf(
                listEntry(id = 1, source = PullRequestListingSource.WorkspaceAuthored),
                listEntry(id = 2, source = PullRequestListingSource.WorkspaceAssigned)
            )

            // WHEN
            val kept = PullRequestListFiltering.apply(
                entries = entries,
                filter = PullRequestListFilter(
                    excludeResolved = false,
                    excludeDrafts = false,
                    authorScope = PullRequestAuthorScope.Me
                ),
                currentUserUuid = null
            )

            // THEN the authored row is mine by construction even with a null uuid; the assigned row is hidden
            kept.map { it.pullRequest.summary.id } shouldContainExactly listOf(1L)
        }

        @Test
        fun `apply - should keep no project rows when Created by me is selected and the current user uuid is null`() {
            // GIVEN project rows whose author uuid cannot be matched because the current user is unknown
            val entries = listOf(
                listEntry(id = 1, source = PullRequestListingSource.ProjectOpen, authorUuid = "5B10AC8D"),
                listEntry(id = 2, source = PullRequestListingSource.ProjectOpen, authorUuid = "{other}")
            )

            // WHEN
            val kept = PullRequestListFiltering.apply(
                entries = entries,
                filter = PullRequestListFilter(
                    excludeResolved = false,
                    excludeDrafts = false,
                    authorScope = PullRequestAuthorScope.Me
                ),
                currentUserUuid = null
            )

            // THEN nothing is kept, because no project row can be proven to be mine
            kept.shouldBeEmpty()
        }

        @Test
        fun `apply - should keep every row when Created by any user is selected`() {
            // GIVEN rows authored by the current user and by someone else
            val entries = listOf(
                listEntry(id = 1, source = PullRequestListingSource.ProjectOpen, authorUuid = CURRENT_USER),
                listEntry(id = 2, source = PullRequestListingSource.ProjectOpen, authorUuid = "{other}")
            )

            // WHEN
            val kept = PullRequestListFiltering.apply(
                entries = entries,
                filter = PullRequestListFilter(
                    excludeResolved = false,
                    excludeDrafts = false,
                    authorScope = PullRequestAuthorScope.AnyUser
                ),
                currentUserUuid = CURRENT_USER
            )

            // THEN both stay
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
        fun `apply - should apply every exclusion and the Created by me scope at once`() {
            // GIVEN one of each kind to hide, plus one row that passes everything
            val entries = listOf(
                listEntry(id = 1, source = PullRequestListingSource.ProjectResolved, state = PullRequestState.Merged),
                listEntry(id = 2, source = PullRequestListingSource.ProjectOpen, isDraft = true),
                listEntry(id = 3, source = PullRequestListingSource.ProjectOpen, authorUuid = CURRENT_USER),
                listEntry(id = 4, source = PullRequestListingSource.ProjectOpen, approverUuids = setOf(CURRENT_USER)),
                listEntry(id = 5, source = PullRequestListingSource.ProjectOpen, authorUuid = "{other}")
            )

            // WHEN resolved, drafts and approved-by-me are excluded and only my own pull requests are kept
            val kept = PullRequestListFiltering.apply(
                entries = entries,
                filter = PullRequestListFilter(
                    excludeResolved = true,
                    excludeApprovedByMe = true,
                    excludeDrafts = true,
                    authorScope = PullRequestAuthorScope.Me
                ),
                currentUserUuid = CURRENT_USER
            )

            // THEN only id 3 survives: id 1 is resolved, id 2 is a draft, id 4 is approved by me, id 5 is not mine
            kept.map { it.pullRequest.summary.id } shouldContainExactly listOf(3L)
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
                filter = PullRequestListFilter(excludeResolved = false, excludeApprovedByMe = false, excludeDrafts = true),
                currentUserUuid = CURRENT_USER
            )

            // THEN both open rows stay
            kept.map { it.pullRequest.summary.id } shouldContainExactly listOf(1L, 2L)
        }
    }

    @Nested
    inner class IsApprovedBy {
        @Test
        fun `isApprovedBy - should be true when the user uuid is among the approvers`() {
            // GIVEN a pull request approved by the current user
            val summary = listEntry(id = 1, source = PullRequestListingSource.ProjectOpen, approverUuids = setOf(CURRENT_USER)).pullRequest.summary

            // WHEN
            val approved = PullRequestListFiltering.isApprovedBy(summary = summary, userUuid = CURRENT_USER)

            // THEN
            approved shouldBe true
        }

        @Test
        fun `isApprovedBy - should be false when the user uuid is not among the approvers`() {
            // GIVEN a pull request approved only by someone else
            val summary = listEntry(id = 1, source = PullRequestListingSource.ProjectOpen, approverUuids = setOf("{other}")).pullRequest.summary

            // WHEN
            val approved = PullRequestListFiltering.isApprovedBy(summary = summary, userUuid = CURRENT_USER)

            // THEN
            approved shouldBe false
        }

        @Test
        fun `isApprovedBy - should match a brace and case mismatch`() {
            // GIVEN the approver uuid written without braces and in upper case
            val summary = listEntry(id = 1, source = PullRequestListingSource.ProjectOpen, approverUuids = setOf("5B10AC8D")).pullRequest.summary

            // WHEN
            val approved = PullRequestListFiltering.isApprovedBy(summary = summary, userUuid = CURRENT_USER)

            // THEN
            approved shouldBe true
        }

        @Test
        fun `isApprovedBy - should be false for a null or blank user uuid even when approvers exist`() {
            // GIVEN a pull request with approvers
            val summary = listEntry(id = 1, source = PullRequestListingSource.ProjectOpen, approverUuids = setOf(CURRENT_USER)).pullRequest.summary

            // WHEN
            val approvedByNull = PullRequestListFiltering.isApprovedBy(summary = summary, userUuid = null)
            val approvedByBlank = PullRequestListFiltering.isApprovedBy(summary = summary, userUuid = " ")

            // THEN
            assertSoftly {
                approvedByNull shouldBe false
                approvedByBlank shouldBe false
            }
        }
    }
}