package com.github.fnunezkanut.bbcloud.ui.diff

import com.github.fnunezkanut.bbcloud.bitbucket.ActivityUser
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class DiffCommentThreadTextTest {
    @Test
    fun `resolutionText - should name the resolver`() {
        // GIVEN
        val resolver = ActivityUser(uuid = "{resolver}", displayName = "Reviewer", avatarUrl = null)

        // WHEN
        val text = DiffCommentThreadText.resolutionText(resolvedBy = resolver)

        // THEN
        text shouldBe "Reviewer resolved this comment thread."
    }

    @Test
    fun `resolutionText - should fall back when the resolver is unknown`() {
        DiffCommentThreadText.resolutionText(resolvedBy = null) shouldBe "This comment thread was resolved."

        val blank = ActivityUser(uuid = "{resolver}", displayName = " ", avatarUrl = null)
        DiffCommentThreadText.resolutionText(resolvedBy = blank) shouldBe "This comment thread was resolved."
    }
}