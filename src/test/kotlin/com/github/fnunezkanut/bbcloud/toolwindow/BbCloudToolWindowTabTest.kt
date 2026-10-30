package com.github.fnunezkanut.bbcloud.toolwindow

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BbCloudToolWindowTabTest {
    @Test
    fun `entries - should list the four tabs in header order`() {
        // GIVEN
        val expected = listOf(
            BbCloudToolWindowTab.ProjectPrs,
            BbCloudToolWindowTab.WorkspacePrs,
            BbCloudToolWindowTab.WorkspaceWatchedProjects,
            BbCloudToolWindowTab.Settings
        )

        // WHEN
        val actual = BbCloudToolWindowTab.entries

        // THEN
        actual shouldBe expected
    }
}