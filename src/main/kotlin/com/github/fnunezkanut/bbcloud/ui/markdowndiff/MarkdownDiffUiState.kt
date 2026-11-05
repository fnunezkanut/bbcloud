package com.github.fnunezkanut.bbcloud.ui.markdowndiff

/** A request to scroll the diff to [rowIndex]. [serial] changes on every request, so repeating a target still scrolls. */
data class MarkdownDiffScrollRequest(val rowIndex: Int, val serial: Int)

data class MarkdownDiffUiState(
    val comparisons: List<MarkdownDiffComparison> = emptyList(),
    val selectedComparisonIndex: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val leftLabel: String = "",
    val rightLabel: String = "",
    val rows: List<MarkdownDiffRow> = emptyList(),
    val currentChangeIndex: Int = -1,
    val showWordChanges: Boolean = true,
    val scrollRequest: MarkdownDiffScrollRequest? = null
) {
    /** The row indices of every changed row, in document order. Navigation and the change map use them. */
    val changeRowIndices: List<Int> = changeRowIndicesOf(rows = rows)

    val addedCount: Int = rows.count { row -> row.kind == MarkdownDiffKind.ADDED }
    val removedCount: Int = rows.count { row -> row.kind == MarkdownDiffKind.REMOVED }
    val modifiedCount: Int = rows.count { row -> row.kind == MarkdownDiffKind.MODIFIED }

    val hasChanges: Boolean = changeRowIndices.isNotEmpty()

    val isPreviousChangeEnabled: Boolean = hasChanges && currentChangeIndex != 0
    val isNextChangeEnabled: Boolean = hasChanges && currentChangeIndex < changeRowIndices.lastIndex
}

/**
 * The indices of changed rows. Consecutive changed rows form one change, so navigation jumps from one change hunk to the
 * next and stops at its first row.
 */
internal fun changeRowIndicesOf(rows: List<MarkdownDiffRow>): List<Int> {
    return rows.indices.filter { index ->
        rows[index].kind != MarkdownDiffKind.UNCHANGED && (index == 0 || rows[index - 1].kind == MarkdownDiffKind.UNCHANGED)
    }
}