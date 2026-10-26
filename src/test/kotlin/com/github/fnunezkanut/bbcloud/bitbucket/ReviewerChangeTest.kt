package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class ReviewerChangeTest {
    @Test
    fun `Add applyTo - should append a new reviewer`() {
        // WHEN
        val result = ReviewerChange.Add("{c}").applyTo(listOf("{a}", "{b}"))

        // THEN
        result shouldBe listOf("{a}", "{b}", "{c}")
    }

    @Test
    fun `Add applyTo - should keep the list unchanged when the reviewer is present`() {
        // WHEN
        val result = ReviewerChange.Add("{a}").applyTo(listOf("{a}", "{b}"))

        // THEN
        result shouldBe listOf("{a}", "{b}")
    }

    @Test
    fun `Remove applyTo - should drop only the removed reviewer`() {
        // WHEN
        val result = ReviewerChange.Remove("{a}").applyTo(listOf("{a}", "{b}"))

        // THEN
        result shouldBe listOf("{b}")
    }

    @Test
    fun `Remove applyTo - should keep the list unchanged when the reviewer is absent`() {
        // WHEN
        val result = ReviewerChange.Remove("{z}").applyTo(listOf("{a}", "{b}"))

        // THEN
        result shouldBe listOf("{a}", "{b}")
    }
}