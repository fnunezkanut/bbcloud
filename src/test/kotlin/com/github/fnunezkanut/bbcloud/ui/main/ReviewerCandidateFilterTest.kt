package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerCandidates
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class ReviewerCandidateFilterTest {
    private val anna = candidate(uuid = "{a}", displayName = "Anna Smith", nickname = "asmith")
    private val bob = candidate(uuid = "{b}", displayName = "Bob Annand", nickname = "bob")
    private val carla = candidate(uuid = "{c}", displayName = "Carla Hannah", nickname = "carla")
    private val dave = candidate(uuid = "{d}", displayName = "Dave Jones", nickname = "dj")

    @Test
    fun `filter - should rank name prefix before word prefix before substring matches`() {
        // GIVEN
        val candidates = candidates(listOf(carla, bob, anna, dave))

        // WHEN
        val result = ReviewerCandidateFilter.filter(candidates = candidates, query = "ann", excludedUuids = emptySet())

        // THEN
        result shouldBe listOf(anna, bob, carla)
    }

    @Test
    fun `filter - should match the nickname case-insensitively`() {
        // GIVEN
        val candidates = candidates(listOf(anna, dave))

        // WHEN
        val result = ReviewerCandidateFilter.filter(candidates = candidates, query = "  DJ ", excludedUuids = emptySet())

        // THEN
        result shouldBe listOf(dave)
    }

    @Test
    fun `filter - should exclude the author and current reviewers`() {
        // GIVEN
        val candidates = candidates(listOf(anna, bob, carla))

        // WHEN
        val result = ReviewerCandidateFilter.filter(candidates = candidates, query = "", excludedUuids = setOf("{a}", "{c}"))

        // THEN
        result shouldBe listOf(bob)
    }

    @Test
    fun `filter - should list default reviewers first when the query is blank`() {
        // GIVEN
        val candidates = candidates(members = listOf(anna, bob, dave, carla), defaultReviewerUuids = setOf("{d}", "{c}"))

        // WHEN
        val result = ReviewerCandidateFilter.filter(candidates = candidates, query = "", excludedUuids = emptySet())

        // THEN
        result shouldBe listOf(carla, dave, anna, bob)
    }

    @Test
    fun `filter - should prefer default reviewers among equally ranked matches`() {
        // GIVEN
        val alan = candidate(uuid = "{e}", displayName = "Alan Wake", nickname = null)
        val candidates = candidates(members = listOf(anna, alan), defaultReviewerUuids = setOf("{a}"))

        // WHEN
        val result = ReviewerCandidateFilter.filter(candidates = candidates, query = "a", excludedUuids = emptySet())

        // THEN
        result shouldBe listOf(anna, alan)
    }

    @Test
    fun `filter - should limit and deduplicate results`() {
        // GIVEN
        val candidates = candidates(listOf(anna, anna, bob, carla))

        // WHEN
        val result = ReviewerCandidateFilter.filter(candidates = candidates, query = "", excludedUuids = emptySet(), limit = 2)

        // THEN
        result shouldBe listOf(anna, bob)
    }

    private fun candidates(members: List<ReviewerCandidate>, defaultReviewerUuids: Set<String> = emptySet()): ReviewerCandidates {
        return ReviewerCandidates(members = members, defaultReviewerUuids = defaultReviewerUuids, isTruncated = false)
    }

    private fun candidate(uuid: String, displayName: String, nickname: String?): ReviewerCandidate {
        return ReviewerCandidate(uuid = uuid, displayName = displayName, nickname = nickname, avatarUrl = null)
    }
}