package com.github.fnunezkanut.bbcloud.ui.markdowndiff

/**
 * One top-level block of a Markdown document, as source text. [startLine] is the zero-based line where it starts.
 *
 * The diff compares blocks by [normalizedText], so trailing whitespace and line-ending differences do not count as
 * changes.
 */
data class MarkdownSourceBlock(val text: String, val startLine: Int) {
    val normalizedText: String = text.lines().joinToString(separator = "\n") { line -> line.trimEnd() }.trim()
}

/**
 * Splits Markdown source into top-level blocks that can be rendered on their own.
 *
 * - Blank lines separate blocks.
 * - A fenced code block (``` or ~~~) is one block, blank lines included.
 * - An ATX heading (`# Title`) is always its own block, even without blank lines around it.
 * - A YAML front matter block (`---` on the first line) is one block.
 * - An indented line after a blank line continues the previous list block, so a list item with several paragraphs
 *   stays one block and renders as one list.
 */
object MarkdownSourceBlocks {
    fun split(markdown: String): List<MarkdownSourceBlock> {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = mutableListOf<MarkdownSourceBlock>()
        val current = mutableListOf<String>()
        var currentStart = 0
        var pendingBlankLines = 0
        var index = 0

        fun flush() {
            if (current.isNotEmpty()) {
                blocks += MarkdownSourceBlock(text = current.joinToString(separator = "\n"), startLine = currentStart)
                current.clear()
            }
            pendingBlankLines = 0
        }

        fun start(lineIndex: Int) {
            flush()
            currentStart = lineIndex
        }

        if (lines.firstOrNull()?.trimEnd() == FRONT_MATTER_DELIMITER) {
            val end = (1 until lines.size).firstOrNull { lineIndex -> lines[lineIndex].trimEnd() == FRONT_MATTER_DELIMITER }
            if (end != null) {
                current += lines.subList(0, end + 1)
                flush()
                index = end + 1
            }
        }

        while (index < lines.size) {
            val line = lines[index]
            val fence = fenceOf(line)
            when {
                fence != null -> {
                    start(lineIndex = index)
                    current += line
                    index++
                    while (index < lines.size) {
                        current += lines[index]
                        val closes = closesFence(line = lines[index], fence = fence)
                        index++
                        if (closes) break
                    }
                    flush()
                    continue
                }

                line.isBlank() -> {
                    if (current.isNotEmpty()) pendingBlankLines++
                }

                ATX_HEADING.matches(line) -> {
                    start(lineIndex = index)
                    current += line
                    flush()
                }

                current.isNotEmpty() && pendingBlankLines > 0 && continuesList(block = current, line = line) -> {
                    repeat(pendingBlankLines) { current += "" }
                    pendingBlankLines = 0
                    current += line
                }

                current.isEmpty() || pendingBlankLines > 0 -> {
                    start(lineIndex = index)
                    current += line
                }

                else -> {
                    current += line
                }
            }
            index++
        }
        flush()
        return blocks
    }

    private data class Fence(val marker: Char, val length: Int)

    private fun fenceOf(line: String): Fence? {
        val match = FENCE_OPEN.matchEntire(line) ?: return null
        val markers = match.groupValues[1]
        return Fence(marker = markers.first(), length = markers.length)
    }

    private fun closesFence(line: String, fence: Fence): Boolean {
        val trimmed = line.trim()
        return trimmed.length >= fence.length && trimmed.all { character -> character == fence.marker }
    }

    /** True when [line] is indented and [block] starts as a list, so the line belongs to the last list item. */
    private fun continuesList(block: List<String>, line: String): Boolean {
        val indented = line.startsWith("  ") || line.startsWith("\t")
        return indented && LIST_ITEM.containsMatchIn(block.first())
    }

    private const val FRONT_MATTER_DELIMITER = "---"
    private val FENCE_OPEN = Regex("""^ {0,3}(`{3,}|~{3,}).*$""")
    private val ATX_HEADING = Regex("""^ {0,3}#{1,6}(\s.*)?$""")
    private val LIST_ITEM = Regex("""^ {0,3}([-*+]|\d{1,9}[.)])\s""")
}