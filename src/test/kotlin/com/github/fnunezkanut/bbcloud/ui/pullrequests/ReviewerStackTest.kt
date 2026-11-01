package com.github.fnunezkanut.bbcloud.ui.pullrequests

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestReviewer
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerStatus
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class ReviewerStackTest {
    @Test
    fun `buildReviewerStack - should return EMPTY for no reviewers`() {
        // WHEN
        val stack = buildReviewerStack(reviewers = emptyList())

        // THEN
        assertSoftly(stack) {
            isEmpty shouldBe true
            hiddenCount shouldBe 0
        }
    }

    @Test
    fun `buildReviewerStack - should keep one or two reviewers all visible`() {
        // GIVEN two reviewers
        val reviewers = listOf(reviewer(uuid = "{a}"), reviewer(uuid = "{b}"))

        // WHEN
        val stack = buildReviewerStack(reviewers = reviewers)

        // THEN both are visible, nothing hidden
        assertSoftly(stack) {
            visible.map { it.uuid } shouldBe listOf("{a}", "{b}")
            hiddenCount shouldBe 0
        }
    }

    @Test
    fun `buildReviewerStack - should show two and fold the rest for five reviewers`() {
        // GIVEN five not-yet-reviewed reviewers in a known order
        val reviewers = (1..5).map { index -> reviewer(uuid = "{$index}") }

        // WHEN
        val stack = buildReviewerStack(reviewers = reviewers)

        // THEN two visible, three hidden, the hidden keep the sorted order
        assertSoftly(stack) {
            visible.map { it.uuid } shouldBe listOf("{1}", "{2}")
            hiddenCount shouldBe 3
            hidden.map { it.uuid } shouldBe listOf("{3}", "{4}", "{5}")
        }
    }

    @Test
    fun `buildReviewerStack - should sort approvals first, newest participatedOn first and null last`() {
        // GIVEN a pending, an approval without a timestamp, and two timed approvals
        val pending = reviewer(uuid = "{pending}", status = ReviewerStatus.None)
        val approvedNoDate = reviewer(uuid = "{nodate}", status = ReviewerStatus.Approved)
        val approvedOld = reviewer(
            uuid = "{old}",
            status = ReviewerStatus.Approved,
            participatedOn = Instant.parse("2026-09-28T10:00:00+00:00")
        )
        val approvedNew = reviewer(
            uuid = "{new}",
            status = ReviewerStatus.Approved,
            participatedOn = Instant.parse("2026-09-29T10:00:00+00:00")
        )

        // WHEN
        val stack = buildReviewerStack(
            reviewers = listOf(pending, approvedNoDate, approvedOld, approvedNew),
            maxVisible = 4
        )

        // THEN approvals come first by recency, undated approval after dated ones, pending last
        stack.visible.map { it.uuid } shouldBe listOf("{new}", "{old}", "{nodate}", "{pending}")
    }

    @Test
    fun `buildReviewerStack - should sort changes requested before pending and keep input order on ties`() {
        // GIVEN two changes-requested reviewers and two pending ones, interleaved
        val changesA = reviewer(uuid = "{ca}", status = ReviewerStatus.ChangesRequested)
        val pendingA = reviewer(uuid = "{pa}", status = ReviewerStatus.None)
        val changesB = reviewer(uuid = "{cb}", status = ReviewerStatus.ChangesRequested)
        val pendingB = reviewer(uuid = "{pb}", status = ReviewerStatus.None)

        // WHEN
        val stack = buildReviewerStack(
            reviewers = listOf(pendingA, changesA, pendingB, changesB),
            maxVisible = 4
        )

        // THEN changes-requested precede pending; ties keep the original relative order
        stack.visible.map { it.uuid } shouldBe listOf("{ca}", "{cb}", "{pa}", "{pb}")
    }

    @Test
    fun `buildReviewerStack - should coerce a maxVisible below one up to one`() {
        // GIVEN two reviewers and a maxVisible of zero
        val reviewers = listOf(reviewer(uuid = "{a}"), reviewer(uuid = "{b}"))

        // WHEN
        val stack = buildReviewerStack(reviewers = reviewers, maxVisible = 0)

        // THEN exactly one is visible
        assertSoftly(stack) {
            visible.map { it.uuid } shouldBe listOf("{a}")
            hiddenCount shouldBe 1
        }
    }

    @Test
    fun `cappedOverflowCount - should cap at 99 and pass smaller counts through`() {
        // GIVEN / WHEN / THEN
        assertSoftly {
            cappedOverflowCount(hiddenCount = 150) shouldBe 99
            cappedOverflowCount(hiddenCount = 3) shouldBe 3
        }
    }

    private fun reviewer(uuid: String, status: ReviewerStatus = ReviewerStatus.None, participatedOn: Instant? = null): PullRequestReviewer {
        return PullRequestReviewer(
            uuid = uuid,
            displayName = "Reviewer $uuid",
            avatarUrl = null,
            status = status,
            participatedOn = participatedOn
        )
    }
}