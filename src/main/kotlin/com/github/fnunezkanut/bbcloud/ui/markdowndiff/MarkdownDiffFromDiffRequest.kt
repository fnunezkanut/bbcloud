package com.github.fnunezkanut.bbcloud.ui.markdowndiff

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.contents.EmptyContent
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

/**
 * Turns the request of an IDE diff viewer into a rendered Markdown diff. Shared by the toolbar action and the link strip
 * inside the diff viewer.
 */
internal object MarkdownDiffFromDiffRequest {
    private val LOG = logger<MarkdownDiffFromDiffRequest>()

    /** One side of the diff: the text of a live document, or nothing for the missing side of an added or deleted file. */
    sealed interface MarkdownSide {
        fun text(): String

        fun identity(): Int

        class Document(private val content: DocumentContent) : MarkdownSide {
            override fun text(): String {
                val document = content.document
                return ReadAction.computeBlocking<String, RuntimeException> { document.text }
            }

            override fun identity(): Int {
                return System.identityHashCode(content.document)
            }
        }

        data object Empty : MarkdownSide {
            override fun text(): String {
                return ""
            }

            override fun identity(): Int {
                return 0
            }
        }
    }

    data class MarkdownContents(val left: MarkdownSide, val right: MarkdownSide, val fileName: String)

    /**
     * Returns the two sides when the request compares exactly two contents, each a document or empty, and at least one
     * document is Markdown.
     */
    fun markdownContentsOf(request: ContentDiffRequest): MarkdownContents? {
        val contents = request.contents
        if (contents.size != 2) return null
        val left = sideOf(content = contents[0]) ?: return null
        val right = sideOf(content = contents[1]) ?: return null
        val documents = listOf(contents[1], contents[0]).filterIsInstance<DocumentContent>()
        val fileName = documents.firstNotNullOfOrNull { content -> markdownFileNameOf(content = content) } ?: return null
        return MarkdownContents(left = left, right = right, fileName = fileName)
    }

    fun sourceFor(request: ContentDiffRequest, contents: MarkdownContents): FixedMarkdownDiffContentSource {
        val titles = request.contentTitles
        val fileName = contents.fileName
        val leftLabel = titles.getOrNull(0) ?: BbCloudBundle.message("markdownDiff.side.before")
        val rightLabel = titles.getOrNull(1) ?: BbCloudBundle.message("markdownDiff.side.after")
        val left = contents.left
        val right = contents.right
        return FixedMarkdownDiffContentSource(
            key = "diff:$fileName:$leftLabel:$rightLabel:${left.identity()}:${right.identity()}",
            fileName = fileName,
            comparisonLabel = withoutPathSuffix(title = request.title, fileName = fileName),
            leftLabel = leftLabel,
            rightLabel = rightLabel,
            leftText = { left.text() },
            rightText = { right.text() }
        )
    }

    /** The IDE diff title looks like `SKILL.md (/full/path/SKILL.md's folder)`; the full path is noise, so drop it. */
    internal fun withoutPathSuffix(title: String?, fileName: String): String {
        if (title.isNullOrBlank()) return fileName
        val pathSuffix = Regex("""\s*\((?:[A-Za-z]:)?[/\\][^)]*\)\s*$""")
        return title.replace(regex = pathSuffix, replacement = "").ifBlank { fileName }
    }

    /** Opens the rendered Markdown diff of [request]. Returns false when the request is not a Markdown two-sided diff. */
    fun open(project: Project, request: ContentDiffRequest): Boolean {
        val contents = markdownContentsOf(request = request)
        if (contents == null) {
            LOG.info("Show rendered Markdown diff skipped: the diff does not compare two Markdown documents")
            return false
        }
        BbCloudMarkdownDiffEditorOpener.getInstance(project).open(source = sourceFor(request = request, contents = contents))
        return true
    }

    private fun sideOf(content: DiffContent): MarkdownSide? {
        return when (content) {
            is EmptyContent -> MarkdownSide.Empty
            is DocumentContent -> MarkdownSide.Document(content = content)
            else -> null
        }
    }

    private fun markdownFileNameOf(content: DocumentContent): String? {
        val highlightName = content.highlightFile?.name
        if (MarkdownFiles.isMarkdownFileName(fileName = highlightName)) {
            return highlightName
        }
        val extension = content.contentType?.defaultExtension
        if (MarkdownFiles.isMarkdownFileName(fileName = "file.$extension")) {
            return highlightName ?: "file.$extension"
        }
        return null
    }
}