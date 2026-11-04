package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.ActivityUser
import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidateTier
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestDetails
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestReviewer
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerStatus
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class PullRequestMentionPeopleTest {
    @Test
    fun `candidates - should list details author, reviewers, participants, then comment people in the pull-request tier`() {
        // GIVEN details and comments
        val people = PullRequestMentionPeople()
        people.updateDetails(
            details = PullRequestDetails(
                summary = summary(authorDisplayName = "Author"),
                isApprovedByCurrentUser = false,
                reviewers = listOf(PullRequestReviewer(uuid = "{r}", displayName = "Reviewer", avatarUrl = null, status = ReviewerStatus.None)),
                participants = listOf(ActivityUser(uuid = "{p}", displayName = "Participant", avatarUrl = null))
            )
        )
        people.updateComments(
            comments = listOf(comment(id = 1, author = ActivityUser(uuid = "{c}", displayName = "Commenter", avatarUrl = null)))
        )

        // WHEN
        val candidates = people.candidates()

        // THEN order is author, reviewer, participant, then commenter; all in the pull-request tier
        candidates.map { it.displayName } shouldContainExactly listOf("Author", "Reviewer", "Participant", "Commenter")
        candidates.map { it.tier } shouldContainExactly List(4) { MentionCandidateTier.PullRequest }
    }

    @Test
    fun `clear - should forget the stored people`() {
        // GIVEN some stored people
        val people = PullRequestMentionPeople()
        people.updateComments(comments = listOf(comment(id = 1, author = ActivityUser(uuid = "{c}", displayName = "Commenter", avatarUrl = null))))

        // WHEN
        people.clear()

        // THEN
        people.candidates() shouldContainExactly emptyList()
    }

    private fun summary(authorDisplayName: String): PullRequestSummary {
        return PullRequestSummary(
            id = 1,
            title = "PR 1",
            authorDisplayName = authorDisplayName,
            authorAvatarUrl = null,
            createdOn = Instant.parse("2026-09-28T10:00:00Z"),
            updatedOn = Instant.parse("2026-09-28T10:00:00Z"),
            sourceBranch = "feature",
            destinationBranch = "main",
            commentCount = 0,
            webUrl = "https://bitbucket.org/workspace/repo/pull-requests/1",
            state = PullRequestState.Open,
            isDraft = false,
            authorUuid = "{author}"
        )
    }

    private fun comment(id: Long, author: ActivityUser): PullRequestComment {
        return PullRequestComment(
            id = id,
            parentId = null,
            createdOn = Instant.parse("2026-09-28T10:00:00Z"),
            author = author,
            html = "",
            inlinePath = null,
            inlineLine = null,
            inlineSide = null,
            isResolved = false,
            isDeleted = false,
            isPending = false
        )
    }
}