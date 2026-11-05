package com.github.fnunezkanut.bbcloud.ui.markdowndiff

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class MarkdownDiffViewModelTest {
    private class FakeSource(private val results: Map<String, MarkdownDiffContentResult>) : MarkdownDiffContentSource {
        var loads = 0
        override val key: String = "fake"
        override val fileName: String = "README.md"
        override val comparisons: List<MarkdownDiffComparison> = results.keys.map { id -> MarkdownDiffComparison(id = id, label = id) }

        override fun load(comparison: MarkdownDiffComparison): MarkdownDiffContentResult {
            loads++
            return results.getValue(comparison.id)
        }
    }

    private fun loaded(left: String, right: String): MarkdownDiffContentResult {
        return MarkdownDiffContentResult.Loaded(
            left = MarkdownDiffSide(label = "Left", text = left),
            right = MarkdownDiffSide(label = "Right", text = right)
        )
    }

    private fun viewModelOf(source: MarkdownDiffContentSource): MarkdownDiffViewModel {
        return MarkdownDiffViewModel(
            source = source,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            runOnEdt = { block -> block() },
            failureMessage = { exception -> "failed: ${exception.message}" },
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun `reload - should publish rows and labels of the selected comparison`() {
        // GIVEN
        val source = FakeSource(results = mapOf("a" to loaded(left = "One\n\nTwo", right = "One\n\nTwo changed\n\nThree")))
        val viewModel = viewModelOf(source = source)

        // WHEN
        viewModel.reload()

        // THEN
        val state = viewModel.state.value
        assertSoftly {
            state.isLoading shouldBe false
            state.errorMessage shouldBe null
            state.leftLabel shouldBe "Left"
            state.rightLabel shouldBe "Right"
            state.rows.size shouldBe 3
            state.modifiedCount shouldBe 1
            state.addedCount shouldBe 1
            state.changeRowIndices shouldBe listOf(1)
        }
    }

    @Test
    fun `onNextChange - should move through changes and request a scroll to each`() {
        // GIVEN
        val source = FakeSource(results = mapOf("a" to loaded(left = "A\n\nB\n\nC\n\nD", right = "A\n\nX added\n\nB\n\nC\n\nD\n\nY added")))
        val viewModel = viewModelOf(source = source)
        viewModel.reload()

        // WHEN
        viewModel.onNextChange()
        val first = viewModel.state.value
        viewModel.onNextChange()
        val second = viewModel.state.value

        // THEN
        assertSoftly {
            first.currentChangeIndex shouldBe 0
            first.scrollRequest?.rowIndex shouldBe 1
            second.currentChangeIndex shouldBe 1
            second.scrollRequest?.rowIndex shouldBe 5
            second.isNextChangeEnabled shouldBe false
            second.isPreviousChangeEnabled shouldBe true
        }
    }

    @Test
    fun `onComparisonSelected - should load the other comparison`() {
        // GIVEN
        val source = FakeSource(
            results = mapOf(
                "a" to loaded(left = "Same", right = "Same"),
                "b" to MarkdownDiffContentResult.Failed(message = "not staged")
            )
        )
        val viewModel = viewModelOf(source = source)
        viewModel.reload()

        // WHEN
        viewModel.onComparisonSelected(index = 1)

        // THEN
        val state = viewModel.state.value
        assertSoftly {
            source.loads shouldBe 2
            state.selectedComparisonIndex shouldBe 1
            state.errorMessage shouldBe "not staged"
            state.rows shouldBe emptyList()
        }
    }

    @Test
    fun `reload - should show the failure message when the source throws`() {
        // GIVEN
        val source = object : MarkdownDiffContentSource {
            override val key: String = "throws"
            override val fileName: String = "README.md"
            override val comparisons: List<MarkdownDiffComparison> = listOf(MarkdownDiffComparison(id = "a", label = "a"))

            override fun load(comparison: MarkdownDiffComparison): MarkdownDiffContentResult {
                throw IllegalStateException("boom")
            }
        }
        val viewModel = viewModelOf(source = source)

        // WHEN
        viewModel.reload()

        // THEN
        viewModel.state.value.errorMessage shouldBe "failed: boom"
    }
}