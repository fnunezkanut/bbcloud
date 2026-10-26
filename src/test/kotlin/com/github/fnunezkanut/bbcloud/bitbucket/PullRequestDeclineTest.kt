package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestDeclineTest {
    @Test
    fun `confirmed - trims the reason while preserving blank confirmation`() {
        PullRequestDeclineDecision.confirmed("  reason  ") shouldBe PullRequestDeclineDecision.Confirmed("reason")
        PullRequestDeclineDecision.confirmed("  ") shouldBe PullRequestDeclineDecision.Confirmed("")
        PullRequestDeclineDecision.Cancel shouldBe PullRequestDeclineDecision.Cancel
    }

    @Test
    fun `declinedCommentBody - uses the shared prefix and trimmed reason`() {
        declinedCommentBody("  not needed  ") shouldBe "Declined: not needed"
    }
}