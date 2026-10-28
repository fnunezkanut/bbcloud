package com.github.fnunezkanut.bbcloud.review

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class WorktreeBannerEditorNotificationRefreshTest {
    @Test
    fun `refresh - should be requested when the blue bar state changes`() {
        assertSoftly {
            shouldRefreshEditorNotifications(previousIsWorktree = false, nextIsWorktree = true).shouldBeTrue()
            shouldRefreshEditorNotifications(previousIsWorktree = true, nextIsWorktree = false).shouldBeTrue()
        }
    }

    @Test
    fun `refresh - should be skipped when the blue bar state is unchanged`() {
        assertSoftly {
            shouldRefreshEditorNotifications(previousIsWorktree = false, nextIsWorktree = false).shouldBeFalse()
            shouldRefreshEditorNotifications(previousIsWorktree = true, nextIsWorktree = true).shouldBeFalse()
        }
    }
}