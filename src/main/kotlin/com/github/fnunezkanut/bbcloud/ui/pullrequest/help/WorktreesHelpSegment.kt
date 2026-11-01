package com.github.fnunezkanut.bbcloud.ui.pullrequest.help

import com.github.fnunezkanut.bbcloud.bitbucket.MarkdownCodeRegions

/** One block of the Work Trees help page: Markdown prose for Jewel, or an image that Compose draws itself. */
internal sealed interface WorktreesHelpSegment {
    /** Markdown prose rendered with the Jewel Markdown composable. */
    data class Prose(val markdown: String) : WorktreesHelpSegment

    /** An image line such as `![alt](docs/worktrees.jpeg)`, loaded from the classpath path [resourcePath]. */
    data class Picture(val resourcePath: String, val altText: String) : WorktreesHelpSegment
}

/**
 * Splits [markdown] into prose and picture segments. Jewel has no loader for repository-relative image paths, so each
 * picture is drawn with Compose.
 *
 * Only a line that holds nothing but an image from the `docs/` folder becomes a picture. Image syntax inside a fenced
 * block or an inline code span stays prose.
 */
internal fun splitWorktreesHelpSegments(markdown: String): List<WorktreesHelpSegment> {
    val segments = mutableListOf<WorktreesHelpSegment>()
    var cursor = 0
    for (image in findPictureLines(markdown = markdown)) {
        appendProse(segments = segments, text = markdown.substring(cursor, image.start))
        segments += WorktreesHelpSegment.Picture(resourcePath = image.resourcePath, altText = image.altText)
        cursor = image.endExclusive
    }
    appendProse(segments = segments, text = markdown.substring(cursor))
    return segments
}

private data class PictureLine(val start: Int, val endExclusive: Int, val altText: String, val resourcePath: String)

/** Finds the picture lines outside code regions. Offsets index into [markdown], so the boundary check uses it directly. */
private fun findPictureLines(markdown: String): List<PictureLine> {
    val found = mutableListOf<PictureLine>()
    var regionStart = 0
    MarkdownCodeRegions.splitCodeRegions(body = markdown).forEach { region ->
        if (!region.isCode) {
            PICTURE_LINE.findAll(region.text).forEach { match ->
                val start = regionStart + match.range.first
                val endExclusive = regionStart + match.range.last + 1
                if (isWholeLine(markdown = markdown, start = start, endExclusive = endExclusive)) {
                    found += PictureLine(
                        start = start,
                        endExclusive = endExclusive,
                        altText = match.groupValues[1],
                        resourcePath = "$HELP_RESOURCE_FOLDER/${match.groupValues[2]}"
                    )
                }
            }
        }
        regionStart += region.text.length
    }
    return found
}

/** A match is a whole line only when it starts after a newline or at the start, and ends before a newline or at the end. */
private fun isWholeLine(markdown: String, start: Int, endExclusive: Int): Boolean {
    val startsLine = start == 0 || markdown[start - 1] == '\n'
    val endsLine = endExclusive == markdown.length || markdown[endExclusive] == '\n'
    return startsLine && endsLine
}

private fun appendProse(segments: MutableList<WorktreesHelpSegment>, text: String) {
    if (text.isNotBlank()) {
        segments += WorktreesHelpSegment.Prose(markdown = text)
    }
}

private const val HELP_RESOURCE_FOLDER = "/help"

/** Matches an image that is the only content of its line, with the file name taken from the repository `docs/` folder. */
private val PICTURE_LINE = Regex("""(?m)^[ \t]*!\[([^\]\n]*)]\(docs/([A-Za-z0-9_.-]+)\)[ \t\r]*$""")