package com.github.fnunezkanut.bbcloud.ui.main

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class ProjectOpenPrCountIndicatorTest {
    @Nested
    inner class `isVisible` {
        @Test
        fun `Listing card with one or more rows - should be visible`() {
            // GIVEN
            val card = ProjectPullRequestsCard.Listing
            val rows = 1

            // WHEN
            val visible = ProjectOpenPrCountIndicator.isVisible(card = card, rows = rows)

            // THEN
            visible shouldBe true
        }

        @Test
        fun `Listing card with zero rows - should be hidden`() {
            // GIVEN
            val card = ProjectPullRequestsCard.Listing
            val rows = 0

            // WHEN
            val visible = ProjectOpenPrCountIndicator.isVisible(card = card, rows = rows)

            // THEN
            visible shouldBe false
        }

        @Test
        fun `Hidden card with three rows - should be hidden`() {
            // GIVEN
            val card = ProjectPullRequestsCard.Hidden
            val rows = 3

            // WHEN
            val visible = ProjectOpenPrCountIndicator.isVisible(card = card, rows = rows)

            // THEN
            visible shouldBe false
        }

        @Test
        fun `Loading card with three rows - should be hidden`() {
            // GIVEN
            val card = ProjectPullRequestsCard.Loading
            val rows = 3

            // WHEN
            val visible = ProjectOpenPrCountIndicator.isVisible(card = card, rows = rows)

            // THEN
            visible shouldBe false
        }

        @Test
        fun `Empty card with three rows - should be hidden`() {
            // GIVEN
            val card = ProjectPullRequestsCard.Empty
            val rows = 3

            // WHEN
            val visible = ProjectOpenPrCountIndicator.isVisible(card = card, rows = rows)

            // THEN
            visible shouldBe false
        }

        @Test
        fun `Error card with three rows - should be hidden`() {
            // GIVEN
            val card = ProjectPullRequestsCard.Error
            val rows = 3

            // WHEN
            val visible = ProjectOpenPrCountIndicator.isVisible(card = card, rows = rows)

            // THEN
            visible shouldBe false
        }
    }
}