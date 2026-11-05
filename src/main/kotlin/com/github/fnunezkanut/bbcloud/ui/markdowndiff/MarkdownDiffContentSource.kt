package com.github.fnunezkanut.bbcloud.ui.markdowndiff

/** One side of a comparison: the label shown above its pane and the Markdown text. */
data class MarkdownDiffSide(val label: String, val text: String)

/** One comparison a source offers, such as "Last commit vs Working tree". [id] is stable; [label] is shown in the UI. */
data class MarkdownDiffComparison(val id: String, val label: String)

sealed interface MarkdownDiffContentResult {
    data class Loaded(val left: MarkdownDiffSide, val right: MarkdownDiffSide) : MarkdownDiffContentResult

    data class Failed(val message: String) : MarkdownDiffContentResult
}

/**
 * Supplies the two Markdown texts of the diff tab. A source offers one or more comparisons; the tab shows a dropdown
 * when it offers more than one.
 *
 * [load] may block (for example to run git), so callers run it off the EDT.
 */
interface MarkdownDiffContentSource {
    /** Identifies the source, so opening the same diff twice focuses the existing tab. */
    val key: String

    /** The file name shown in the tab title. */
    val fileName: String

    val comparisons: List<MarkdownDiffComparison>

    fun load(comparison: MarkdownDiffComparison): MarkdownDiffContentResult
}

/**
 * A source with one fixed comparison, for example the two sides of a diff that the IDE diff viewer shows. The texts are
 * read through [leftText] and [rightText] on every load, so a reload picks up edits of a side backed by a live document.
 */
class FixedMarkdownDiffContentSource(
    override val key: String,
    override val fileName: String,
    comparisonLabel: String,
    private val leftLabel: String,
    private val rightLabel: String,
    private val leftText: () -> String,
    private val rightText: () -> String
) : MarkdownDiffContentSource {
    override val comparisons: List<MarkdownDiffComparison> = listOf(MarkdownDiffComparison(id = FIXED_COMPARISON_ID, label = comparisonLabel))

    override fun load(comparison: MarkdownDiffComparison): MarkdownDiffContentResult {
        return MarkdownDiffContentResult.Loaded(
            left = MarkdownDiffSide(label = leftLabel, text = leftText()),
            right = MarkdownDiffSide(label = rightLabel, text = rightText())
        )
    }

    companion object {
        const val FIXED_COMPARISON_ID = "fixed"
    }
}