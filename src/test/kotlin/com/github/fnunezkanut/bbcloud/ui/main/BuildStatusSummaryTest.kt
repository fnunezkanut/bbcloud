package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.BuildState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestBuildStatus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BuildStatusSummaryTest {
    @Test
    fun `of - should return Empty when there are no builds`() {
        // WHEN
        val summary = BuildStatusSummary.of(emptyList())

        // THEN
        summary shouldBe BuildStatusSummary(kind = BuildSummaryKind.Empty, count = 0, total = 0)
    }

    @Test
    fun `of - should count failed builds when any build failed`() {
        // GIVEN
        val builds = listOf(build(BuildState.Failed), build(BuildState.Successful), build(BuildState.InProgress))

        // WHEN
        val summary = BuildStatusSummary.of(builds)

        // THEN
        summary shouldBe BuildStatusSummary(kind = BuildSummaryKind.Failed, count = 1, total = 3)
    }

    @Test
    fun `of - should count in progress builds when none failed`() {
        // GIVEN
        val builds = listOf(build(BuildState.InProgress), build(BuildState.Successful))

        // WHEN
        val summary = BuildStatusSummary.of(builds)

        // THEN
        summary shouldBe BuildStatusSummary(kind = BuildSummaryKind.InProgress, count = 1, total = 2)
    }

    @Test
    fun `of - should count stopped builds as failed`() {
        // GIVEN
        val builds = listOf(build(BuildState.Successful), build(BuildState.Stopped))

        // WHEN
        val summary = BuildStatusSummary.of(builds)

        // THEN
        summary shouldBe BuildStatusSummary(kind = BuildSummaryKind.Failed, count = 1, total = 2)
    }

    @Test
    fun `of - should return Passed when every build succeeded`() {
        // GIVEN
        val builds = listOf(build(BuildState.Successful), build(BuildState.Successful))

        // WHEN
        val summary = BuildStatusSummary.of(builds)

        // THEN
        summary shouldBe BuildStatusSummary(kind = BuildSummaryKind.Passed, count = 2, total = 2)
    }

    private fun build(state: BuildState): PullRequestBuildStatus {
        return PullRequestBuildStatus(
            key = state.name,
            name = state.name,
            state = state,
            url = "https://ci/${state.name}",
            description = null,
            updatedOn = null
        )
    }
}