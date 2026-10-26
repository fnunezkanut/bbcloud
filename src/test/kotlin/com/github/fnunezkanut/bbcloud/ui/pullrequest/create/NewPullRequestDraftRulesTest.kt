package com.github.fnunezkanut.bbcloud.ui.pullrequest.create

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class NewPullRequestDraftRulesTest {
    @Test
    fun `hasDraft - should be false for a blank title and description`() {
        assertSoftly {
            NewPullRequestDraftRules.hasDraft(title = "", description = "") shouldBe false
            NewPullRequestDraftRules.hasDraft(title = "   ", description = "   ") shouldBe false
        }
    }

    @Test
    fun `hasDraft - should be true when the title or the description is non-blank`() {
        assertSoftly {
            NewPullRequestDraftRules.hasDraft(title = "a title", description = "") shouldBe true
            NewPullRequestDraftRules.hasDraft(title = "", description = "a body") shouldBe true
        }
    }
}