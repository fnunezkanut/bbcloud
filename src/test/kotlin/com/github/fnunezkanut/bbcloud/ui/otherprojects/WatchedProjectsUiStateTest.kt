package com.github.fnunezkanut.bbcloud.ui.otherprojects

import androidx.compose.ui.state.ToggleableState
import com.github.fnunezkanut.bbcloud.project.WatchedProjectMapping
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class WatchedProjectsUiStateTest {
    @Test
    fun `deriveMasterToggle - reflects none all and partial watched rows`() {
        // GIVEN
        val rows = listOf(
            row(remoteRepoSlug = "first", watched = false),
            row(remoteRepoSlug = "second", watched = true)
        )

        // WHEN
        val results = listOf(
            deriveMasterToggle(emptyList()),
            deriveMasterToggle(rows.map { it.copy(mapping = it.mapping.copy(watched = false)) }),
            deriveMasterToggle(rows.map { it.copy(mapping = it.mapping.copy(watched = true)) }),
            deriveMasterToggle(rows)
        )

        // THEN
        assertSoftly(results) {
            this[0] shouldBe ToggleableState.Off
            this[1] shouldBe ToggleableState.Off
            this[2] shouldBe ToggleableState.On
            this[3] shouldBe ToggleableState.Indeterminate
        }
    }

    private fun row(remoteRepoSlug: String, watched: Boolean): WatchedProjectRow {
        return buildWatchedProjectRow(
            mapping = WatchedProjectMapping(
                localFolderPath = "/tmp/$remoteRepoSlug",
                remoteRepoSlug = remoteRepoSlug,
                workspace = "workspace",
                watched = watched
            ),
            organizationUrl = null,
            avatarUrl = null
        )
    }
}