package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.git.PrBranchRole
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestHeaderViewModelTest {
    private val warning = HeaderLocalBehindRemote(
        title = "title",
        lines = listOf(LocalBranchBehindLine(role = PrBranchRole.Source, text = "line")),
        explanation = "explanation",
        updateLinkText = "Update source branch"
    )

    @Test
    fun `showLocalBehindRemote - should show the warning`() {
        // GIVEN
        val viewModel = PullRequestHeaderViewModel()

        // WHEN
        viewModel.showLocalBehindRemote(warning)

        // THEN
        viewModel.state.value.localBehindRemote shouldBe warning
    }

    @Test
    fun `showLocalBehindRemote - should reset updateInProgress for a fresh warning`() {
        // GIVEN a warning whose update is in flight
        val viewModel = PullRequestHeaderViewModel()
        viewModel.showLocalBehindRemote(warning)
        viewModel.setLocalBehindRemoteUpdateInProgress(true)

        // WHEN a fresh check shows the warning again
        viewModel.showLocalBehindRemote(warning)

        // THEN the link is enabled again
        viewModel.state.value.localBehindRemote.shouldNotBeNull().updateInProgress shouldBe false
    }

    @Test
    fun `showLocalBehindRemote - should not write the state again for an equal warning`() {
        // GIVEN
        val viewModel = PullRequestHeaderViewModel()
        viewModel.showLocalBehindRemote(warning)
        val first = viewModel.state.value

        // WHEN
        viewModel.showLocalBehindRemote(warning.copy())

        // THEN the very same state instance is kept
        (viewModel.state.value === first) shouldBe true
    }

    @Test
    fun `clearLocalBehindRemote - should hide the warning`() {
        val viewModel = PullRequestHeaderViewModel()
        viewModel.showLocalBehindRemote(warning)

        viewModel.clearLocalBehindRemote()

        viewModel.state.value.localBehindRemote.shouldBeNull()
    }

    @Test
    fun `setLocalBehindRemoteUpdateInProgress - should toggle the flag and ignore a missing warning`() {
        val viewModel = PullRequestHeaderViewModel()
        viewModel.setLocalBehindRemoteUpdateInProgress(true)
        viewModel.state.value.localBehindRemote.shouldBeNull()

        viewModel.showLocalBehindRemote(warning)
        viewModel.setLocalBehindRemoteUpdateInProgress(true)

        assertSoftly {
            viewModel.state.value.localBehindRemote.shouldNotBeNull().updateInProgress shouldBe true
        }
    }
}