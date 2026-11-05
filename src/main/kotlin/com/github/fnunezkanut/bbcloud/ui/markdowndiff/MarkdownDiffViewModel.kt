package com.github.fnunezkanut.bbcloud.ui.markdowndiff

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

/**
 * Holds the state of one Markdown diff tab: the selected comparison, the loaded texts turned into aligned diff rows,
 * the current change for previous and next navigation, and the word-changes toggle.
 *
 * Loading runs [source] on [ioDispatcher] and computes the diff there too. A load generation makes sure a slow load
 * never overwrites a newer one.
 */
class MarkdownDiffViewModel internal constructor(
    private val source: MarkdownDiffContentSource,
    private val coroutineScope: CoroutineScope,
    private val runOnEdt: (() -> Unit) -> Unit,
    private val failureMessage: (Throwable) -> String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    val state: MutableState<MarkdownDiffUiState> = mutableStateOf(MarkdownDiffUiState(comparisons = source.comparisons))

    private val loadGeneration = AtomicInteger(0)
    private val scrollSerial = AtomicInteger(0)

    fun reload() {
        val comparison = source.comparisons.getOrNull(state.value.selectedComparisonIndex) ?: run {
            LOG.info("Markdown diff has no comparison to load for ${source.fileName}")
            state.value = state.value.copy(isLoading = false, errorMessage = null, rows = emptyList())
            return
        }
        val generation = loadGeneration.incrementAndGet()
        state.value = state.value.copy(isLoading = true, errorMessage = null)
        coroutineScope.launch {
            val outcome = try {
                withContext(ioDispatcher) { loadRows(comparison = comparison) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                LOG.warn("Could not load the Markdown diff of ${source.fileName} (${comparison.id})", exception)
                LoadOutcome.Failed(message = failureMessage(exception))
            }
            runOnEdt {
                if (generation == loadGeneration.get()) {
                    apply(outcome = outcome)
                }
            }
        }
    }

    fun onComparisonSelected(index: Int) {
        val comparison = source.comparisons.getOrNull(index) ?: return
        if (index == state.value.selectedComparisonIndex) return
        LOG.info("User selected the Markdown diff comparison ${comparison.id} for ${source.fileName}")
        state.value = state.value.copy(selectedComparisonIndex = index, currentChangeIndex = -1)
        reload()
    }

    fun onRefreshRequested() {
        LOG.info("User refreshed the Markdown diff of ${source.fileName}")
        reload()
    }

    fun onWordChangesToggled(enabled: Boolean) {
        LOG.info("User turned word changes ${if (enabled) "on" else "off"} in the Markdown diff")
        state.value = state.value.copy(showWordChanges = enabled)
    }

    fun onNextChange() {
        LOG.info("User moved to the next change in the Markdown diff")
        moveToChange(index = state.value.currentChangeIndex + 1)
    }

    fun onPreviousChange() {
        LOG.info("User moved to the previous change in the Markdown diff")
        val current = state.value.currentChangeIndex
        moveToChange(index = if (current < 0) 0 else current - 1)
    }

    /** Called when the user clicks a marker of the change map, which points at a row. */
    fun onRowSelected(rowIndex: Int) {
        LOG.info("User jumped to a row from the Markdown diff change map")
        val changeIndex = state.value.changeRowIndices.indexOfLast { changeRow -> changeRow <= rowIndex }
        state.value = state.value.copy(
            currentChangeIndex = changeIndex,
            scrollRequest = MarkdownDiffScrollRequest(rowIndex = rowIndex, serial = scrollSerial.incrementAndGet())
        )
    }

    private fun moveToChange(index: Int) {
        val changeRows = state.value.changeRowIndices
        val rowIndex = changeRows.getOrNull(index) ?: return
        state.value = state.value.copy(
            currentChangeIndex = index,
            scrollRequest = MarkdownDiffScrollRequest(rowIndex = rowIndex, serial = scrollSerial.incrementAndGet())
        )
    }

    private fun loadRows(comparison: MarkdownDiffComparison): LoadOutcome {
        return when (val result = source.load(comparison = comparison)) {
            is MarkdownDiffContentResult.Loaded -> {
                val rows = MarkdownBlockDiff.compute(oldMarkdown = result.left.text, newMarkdown = result.right.text)
                LOG.debug("Computed ${rows.size} Markdown diff rows for ${source.fileName} (${comparison.id})")
                LoadOutcome.Loaded(left = result.left, right = result.right, rows = rows)
            }

            is MarkdownDiffContentResult.Failed -> {
                LOG.info("Markdown diff of ${source.fileName} (${comparison.id}) is unavailable: ${result.message}")
                LoadOutcome.Failed(message = result.message)
            }
        }
    }

    private fun apply(outcome: LoadOutcome) {
        state.value = when (outcome) {
            is LoadOutcome.Loaded -> {
                val keepsRows = outcome.rows.size == state.value.rows.size
                state.value.copy(
                    isLoading = false,
                    errorMessage = null,
                    leftLabel = outcome.left.label,
                    rightLabel = outcome.right.label,
                    rows = outcome.rows,
                    currentChangeIndex = if (keepsRows) state.value.currentChangeIndex else -1
                )
            }

            is LoadOutcome.Failed -> {
                state.value.copy(isLoading = false, errorMessage = outcome.message, rows = emptyList())
            }
        }
    }

    private sealed interface LoadOutcome {
        data class Loaded(val left: MarkdownDiffSide, val right: MarkdownDiffSide, val rows: List<MarkdownDiffRow>) : LoadOutcome

        data class Failed(val message: String) : LoadOutcome
    }

    companion object {
        private val LOG = logger<MarkdownDiffViewModel>()
    }
}