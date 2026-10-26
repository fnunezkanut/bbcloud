package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestBadgeStylesTest {
    @Test
    fun `stateBadgeKind - should return no state badge for open pull requests`() {
        PullRequestBadgeStyles.stateBadgeKind(
            state = PullRequestState.Open,
            isDraft = true
        ) shouldBe PullRequestBadgeStyles.StateBadgeKind.None
    }

    @Test
    fun `stateBadgeKind - should return merged badge for merged pull requests`() {
        PullRequestBadgeStyles.stateBadgeKind(
            state = PullRequestState.Merged,
            isDraft = false
        ) shouldBe PullRequestBadgeStyles.StateBadgeKind.Merged
    }

    @Test
    fun `stateBadgeKind - should return declined badge for declined pull requests`() {
        PullRequestBadgeStyles.stateBadgeKind(
            state = PullRequestState.Declined,
            isDraft = false
        ) shouldBe PullRequestBadgeStyles.StateBadgeKind.Declined
    }
}