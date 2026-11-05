package com.github.fnunezkanut.bbcloud.ui.markdowndiff

/** Recognizes Markdown files by extension, so the diff actions show only where they apply. */
internal object MarkdownFiles {
    private val EXTENSIONS = setOf("md", "markdown", "mdown", "mkd", "mkdn")

    fun isMarkdownFileName(fileName: String?): Boolean {
        val extension = fileName?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase() ?: return false
        return extension in EXTENSIONS
    }
}