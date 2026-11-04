package com.github.fnunezkanut.bbcloud.ui.pullrequests

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestReviewer
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerStatus
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestListingPeopleTest {
    @Test
    fun `mentionPeopleByWorkspace - should collect the author and reviewers per workspace`() {
        // GIVEN two entries in the same workspace with authors and reviewers
        val entries = listOf(
            listEntry(
                id = 1,
                source = PullRequestListingSource.WorkspaceAuthored,
                workspace = "workspace",
                authorUuid = "{author1}",
                reviewers = listOf(reviewer(uuid = "{r1}"))
            ),
            listEntry(
                id = 2,
                source = PullRequestListingSource.WorkspaceAuthored,
                workspace = "workspace",
                authorUuid = "{author2}",
                reviewers = listOf(reviewer(uuid = "{r2}"))
            )
        )

        // WHEN
        val people = mentionPeopleByWorkspace(entries = entries)

        // THEN authors and reviewers are all collected under the workspace
        people shouldHaveSize 1
        people.getValue("workspace").map { candidate -> candidate.displayName } shouldContainExactlyInAnyOrder
            listOf("Author 1", "Reviewer {r1}", "Author 2", "Reviewer {r2}")
    }

    @Test
    fun `mentionPeopleByWorkspace - should de-duplicate within a workspace`() {
        // GIVEN two entries that share a reviewer
        val sharedReviewer = reviewer(uuid = "{shared}")
        val entries = listOf(
            listEntry(
                id = 1,
                source = PullRequestListingSource.WorkspaceAuthored,
                workspace = "workspace",
                authorUuid = "{author1}",
                reviewers = listOf(sharedReviewer)
            ),
            listEntry(
                id = 2,
                source = PullRequestListingSource.WorkspaceAuthored,
                workspace = "workspace",
                authorUuid = "{author2}",
                reviewers = listOf(sharedReviewer)
            )
        )

        // WHEN
        val people = mentionPeopleByWorkspace(entries = entries).getValue("workspace")

        // THEN the shared reviewer appears once
        people.count { candidate -> candidate.displayName == "Reviewer {shared}" } shouldBe 1
    }

    @Test
    fun `mentionPeopleByWorkspace - should skip an Unknown author`() {
        // GIVEN an entry whose author has the Unknown placeholder name
        val entries = listOf(
            listEntry(id = 1, source = PullRequestListingSource.WorkspaceAuthored, workspace = "workspace", authorUuid = "{author1}")
                .let { entry ->
                    entry.copy(pullRequest = entry.pullRequest.copy(summary = entry.pullRequest.summary.copy(authorDisplayName = "Unknown")))
                }
        )

        // WHEN
        val people = mentionPeopleByWorkspace(entries = entries)

        // THEN the Unknown author contributes nothing
        people.getValue("workspace").any { candidate -> candidate.displayName == "Unknown" } shouldBe false
    }

    private fun reviewer(uuid: String): PullRequestReviewer {
        return PullRequestReviewer(uuid = uuid, displayName = "Reviewer $uuid", avatarUrl = null, status = ReviewerStatus.None)
    }
}