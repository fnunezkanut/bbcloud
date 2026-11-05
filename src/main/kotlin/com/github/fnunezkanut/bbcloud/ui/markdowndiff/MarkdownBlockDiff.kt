package com.github.fnunezkanut.bbcloud.ui.markdowndiff

/** How one row of the side-by-side diff changed between the left (old) and right (new) document. */
enum class MarkdownDiffKind {
    UNCHANGED,
    ADDED,
    REMOVED,
    MODIFIED
}

/**
 * One aligned row of the side-by-side diff. [left] is null for an added block and [right] is null for a removed block.
 *
 * For a [MarkdownDiffKind.MODIFIED] row, [leftWords] and [rightWords] hold the word-level changes of the source text.
 */
data class MarkdownDiffRow(
    val kind: MarkdownDiffKind,
    val left: MarkdownSourceBlock?,
    val right: MarkdownSourceBlock?,
    val leftWords: List<WordDiffSegment> = emptyList(),
    val rightWords: List<WordDiffSegment> = emptyList()
)

/**
 * Computes the block-level diff of two Markdown documents.
 *
 * 1. Both documents are split into top-level blocks ([MarkdownSourceBlocks]).
 * 2. The longest common subsequence of equal blocks becomes the unchanged rows.
 * 3. In each gap between unchanged rows, removed and added blocks are paired in order. A pair whose words are similar
 *    enough becomes one modified row with word-level changes; the rest stay separate removed and added rows.
 */
object MarkdownBlockDiff {
    fun compute(oldMarkdown: String, newMarkdown: String): List<MarkdownDiffRow> {
        return computeBlocks(
            oldBlocks = MarkdownSourceBlocks.split(markdown = oldMarkdown),
            newBlocks = MarkdownSourceBlocks.split(markdown = newMarkdown)
        )
    }

    fun computeBlocks(oldBlocks: List<MarkdownSourceBlock>, newBlocks: List<MarkdownSourceBlock>): List<MarkdownDiffRow> {
        val matches = Lcs.matches(
            left = oldBlocks.map { block -> block.normalizedText },
            right = newBlocks.map { block -> block.normalizedText }
        )
        val rows = mutableListOf<MarkdownDiffRow>()
        var oldIndex = 0
        var newIndex = 0
        for ((matchedOld, matchedNew) in matches + (oldBlocks.size to newBlocks.size)) {
            rows += gapRows(removed = oldBlocks.subList(oldIndex, matchedOld), added = newBlocks.subList(newIndex, matchedNew))
            if (matchedOld < oldBlocks.size && matchedNew < newBlocks.size) {
                rows += MarkdownDiffRow(kind = MarkdownDiffKind.UNCHANGED, left = oldBlocks[matchedOld], right = newBlocks[matchedNew])
            }
            oldIndex = matchedOld + 1
            newIndex = matchedNew + 1
        }
        return rows
    }

    /** Pairs the removed and added blocks of one gap into modified rows when they are similar. */
    private fun gapRows(removed: List<MarkdownSourceBlock>, added: List<MarkdownSourceBlock>): List<MarkdownDiffRow> {
        val rows = mutableListOf<MarkdownDiffRow>()
        var removedIndex = 0
        var addedIndex = 0
        while (removedIndex < removed.size || addedIndex < added.size) {
            val oldBlock = removed.getOrNull(removedIndex)
            val newBlock = added.getOrNull(addedIndex)
            when {
                oldBlock != null && newBlock != null -> {
                    val words = WordDiff.compute(oldText = oldBlock.text, newText = newBlock.text)
                    if (words.similarity >= MODIFIED_SIMILARITY) {
                        rows += MarkdownDiffRow(
                            kind = MarkdownDiffKind.MODIFIED,
                            left = oldBlock,
                            right = newBlock,
                            leftWords = words.left,
                            rightWords = words.right
                        )
                        removedIndex++
                        addedIndex++
                    } else if (removed.size - removedIndex >= added.size - addedIndex) {
                        rows += MarkdownDiffRow(kind = MarkdownDiffKind.REMOVED, left = oldBlock, right = null)
                        removedIndex++
                    } else {
                        rows += MarkdownDiffRow(kind = MarkdownDiffKind.ADDED, left = null, right = newBlock)
                        addedIndex++
                    }
                }

                oldBlock != null -> {
                    rows += MarkdownDiffRow(kind = MarkdownDiffKind.REMOVED, left = oldBlock, right = null)
                    removedIndex++
                }

                newBlock != null -> {
                    rows += MarkdownDiffRow(kind = MarkdownDiffKind.ADDED, left = null, right = newBlock)
                    addedIndex++
                }
            }
        }
        return rows
    }

    /** The share of unchanged words above which a removed and an added block count as one modified block. */
    private const val MODIFIED_SIMILARITY = 0.4
}

/** Longest common subsequence over lists of comparable items. Common prefixes and suffixes are matched first. */
internal object Lcs {
    /** Returns the index pairs of matching items, in increasing order on both sides. */
    fun <T> matches(left: List<T>, right: List<T>): List<Pair<Int, Int>> {
        var prefix = 0
        while (prefix < left.size && prefix < right.size && left[prefix] == right[prefix]) {
            prefix++
        }
        var suffix = 0
        while (
            suffix < left.size - prefix &&
            suffix < right.size - prefix &&
            left[left.size - 1 - suffix] == right[right.size - 1 - suffix]
        ) {
            suffix++
        }
        val result = mutableListOf<Pair<Int, Int>>()
        (0 until prefix).forEach { index -> result += index to index }
        result += middleMatches(
            left = left.subList(prefix, left.size - suffix),
            right = right.subList(prefix, right.size - suffix)
        ).map { (leftIndex, rightIndex) -> (leftIndex + prefix) to (rightIndex + prefix) }
        (0 until suffix).forEach { offset ->
            result += (left.size - suffix + offset) to (right.size - suffix + offset)
        }
        return result
    }

    private fun <T> middleMatches(left: List<T>, right: List<T>): List<Pair<Int, Int>> {
        if (left.isEmpty() || right.isEmpty()) {
            return emptyList()
        }
        val columns = right.size + 1
        val lengths = IntArray((left.size + 1) * columns)
        for (leftIndex in left.indices.reversed()) {
            for (rightIndex in right.indices.reversed()) {
                lengths[leftIndex * columns + rightIndex] = if (left[leftIndex] == right[rightIndex]) {
                    lengths[(leftIndex + 1) * columns + rightIndex + 1] + 1
                } else {
                    maxOf(lengths[(leftIndex + 1) * columns + rightIndex], lengths[leftIndex * columns + rightIndex + 1])
                }
            }
        }
        val result = mutableListOf<Pair<Int, Int>>()
        var leftIndex = 0
        var rightIndex = 0
        while (leftIndex < left.size && rightIndex < right.size) {
            when {
                left[leftIndex] == right[rightIndex] -> {
                    result += leftIndex to rightIndex
                    leftIndex++
                    rightIndex++
                }

                lengths[(leftIndex + 1) * columns + rightIndex] >= lengths[leftIndex * columns + rightIndex + 1] -> {
                    leftIndex++
                }

                else -> {
                    rightIndex++
                }
            }
        }
        return result
    }
}