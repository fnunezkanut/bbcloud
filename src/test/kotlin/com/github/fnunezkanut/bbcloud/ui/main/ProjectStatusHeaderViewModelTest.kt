package com.github.fnunezkanut.bbcloud.ui.main

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class ProjectStatusHeaderViewModelTest {
    @Test
    fun `default state - hides the create pull request button`() {
        // GIVEN
        val viewModel = ProjectStatusHeaderViewModel(onRefreshRequested = {})

        // THEN
        viewModel.state.value.isCreatePullRequestVisible shouldBe false
    }

    @Test
    fun `setCreatePullRequestVisible - toggles the button`() {
        // GIVEN
        val viewModel = ProjectStatusHeaderViewModel(onRefreshRequested = {})

        // WHEN
        viewModel.setCreatePullRequestVisible(visible = true)

        // THEN
        viewModel.state.value.isCreatePullRequestVisible shouldBe true

        // WHEN
        viewModel.setCreatePullRequestVisible(visible = false)

        // THEN
        viewModel.state.value.isCreatePullRequestVisible shouldBe false
    }

    @Test
    fun `onCreatePullRequestClicked - invokes the callback`() {
        // GIVEN
        var clicked = false
        val viewModel = ProjectStatusHeaderViewModel(
            onRefreshRequested = {},
            onCreatePullRequestRequested = { clicked = true }
        )

        // WHEN
        viewModel.onCreatePullRequestClicked()

        // THEN
        assertSoftly {
            clicked shouldBe true
        }
    }
}