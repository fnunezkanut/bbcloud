@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextRange
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The state of one comment composer: the flags of the editor around the draft.
 *
 * The draft text and the caret no longer live here. They are owned by the view model's [TextFieldState]
 * (`CommentComposerViewModel.editorState`), which is the single source of truth for the editor content and keeps the
 * selection across focus loss. That is what lets the styling toolbar wrap the text the user actually selected, instead
 * of the caret the deprecated `TextFieldValue` editor collapsed to when a toolbar button took the focus.
 */
data class CommentComposerUiState(
    val isExpanded: Boolean = false,
    val isPreview: Boolean = false,
    val isSubmitting: Boolean = false,
    val failureMessage: String? = null
)

/**
 * The Markdown styling the composer offers. The platform Markdown actions are not available outside a platform editor,
 * so every style is a plain text transform of the draft, which keeps the behaviour predictable and testable.
 *
 * The toolbar now shows a platform-style icon for every style (see the toolbar icon of the composer screen); the glyph
 * is kept as the short fallback mark and names the style in logs and tests. The tooltip key names the action in the
 * bundle. The styles are ordered the way the toolbar shows them; the Header style stays in the model, but the toolbar
 * shows no button for it.
 */
internal enum class CommentMarkdownStyle(val glyph: String, val tooltipKey: String) {
    Bold("B", "main.branch.comments.styling.bold"),
    Italic("I", "main.branch.comments.styling.italic"),
    Strikethrough("S", "main.branch.comments.styling.strikethrough"),
    UnorderedList("•", "main.branch.comments.styling.list"),
    OrderedList("1.", "main.branch.comments.styling.orderedList"),
    Blockquote(">", "main.branch.comments.styling.quote"),
    InlineCode("</>", "main.branch.comments.styling.code"),
    Link("[]", "main.branch.comments.styling.link"),
    Header("#", "main.branch.comments.styling.header")
}

/** The result of a Markdown styling: the new draft and the new selection inside it. */
internal data class CommentMarkdownTransform(val text: String, val selectionStart: Int, val selectionEnd: Int)

/** The marker of a line that already belongs to an ordered list. */
private val ORDERED_LIST_MARKER = Regex("^\\d+\\. ")

/**
 * Applies a Markdown style to the draft.
 *
 * An inline style wraps the selected text, or inserts its markers and leaves the caret between them when nothing is
 * selected. A line style prefixes every selected line, a list style numbers every selected line, and a link inserts an
 * empty pair of parentheses the caret is placed in.
 */
internal fun applyCommentMarkdownStyle(text: String, selectionStart: Int, selectionEnd: Int, style: CommentMarkdownStyle): CommentMarkdownTransform {
    val start = selectionStart.coerceIn(0, text.length)
    val end = selectionEnd.coerceIn(start, text.length)
    return when (style) {
        CommentMarkdownStyle.Bold -> wrapSelection(text = text, start = start, end = end, prefix = "**", suffix = "**")
        CommentMarkdownStyle.Italic -> wrapSelection(text = text, start = start, end = end, prefix = "_", suffix = "_")
        CommentMarkdownStyle.Strikethrough -> wrapSelection(text = text, start = start, end = end, prefix = "~~", suffix = "~~")
        CommentMarkdownStyle.InlineCode -> wrapSelection(text = text, start = start, end = end, prefix = "`", suffix = "`")
        CommentMarkdownStyle.Link -> insertLink(text = text, start = start, end = end)
        CommentMarkdownStyle.UnorderedList -> prefixLines(text = text, start = start, end = end, prefix = "- ")
        CommentMarkdownStyle.OrderedList -> numberLines(text = text, start = start, end = end)
        CommentMarkdownStyle.Header -> prefixLines(text = text, start = start, end = end, prefix = "# ")
        CommentMarkdownStyle.Blockquote -> prefixLines(text = text, start = start, end = end, prefix = "> ")
    }
}

/** Wraps the selected text in the markers of an inline style. */
private fun wrapSelection(text: String, start: Int, end: Int, prefix: String, suffix: String): CommentMarkdownTransform {
    val selected = text.substring(start, end)
    val newText = text.substring(0, start) + prefix + selected + suffix + text.substring(end)
    return if (selected.isEmpty()) {
        CommentMarkdownTransform(text = newText, selectionStart = start + prefix.length, selectionEnd = start + prefix.length)
    } else {
        CommentMarkdownTransform(
            text = newText,
            selectionStart = start,
            selectionEnd = start + prefix.length + selected.length + suffix.length
        )
    }
}

/** Inserts an empty link; the caret ends up between the parentheses, ready for the address. */
private fun insertLink(text: String, start: Int, end: Int): CommentMarkdownTransform {
    val selected = text.substring(start, end)
    val newText = text.substring(0, start) + "[" + selected + "]()" + text.substring(end)
    // The opening parenthesis sits right after the label and the closing bracket, so the caret goes between the two.
    val caret = start + selected.length + 3
    return CommentMarkdownTransform(text = newText, selectionStart = caret, selectionEnd = caret)
}

/**
 * Prefixes every line the selection touches with the marker of a line style. A line that already carries the marker is
 * left alone, so pressing the button twice does not stack the markers.
 */
private fun prefixLines(text: String, start: Int, end: Int, prefix: String): CommentMarkdownTransform {
    val lineStart = lineStartOf(text = text, offset = start)
    val lineEnd = lineEndOf(text = text, offset = end)
    val block = text.substring(lineStart, lineEnd)
    val lines = block.split("\n")
    val prefixedLines = lines.map { line -> if (line.startsWith(prefix)) line else prefix + line }
    val prefixedBlock = prefixedLines.joinToString("\n")
    val newText = text.substring(0, lineStart) + prefixedBlock + text.substring(lineEnd)
    val shifts = prefixShifts(lines = lines, prefix = prefix)
    return CommentMarkdownTransform(
        text = newText,
        selectionStart = start + shiftAt(lines = lines, shifts = shifts, lineStart = lineStart, offset = start),
        selectionEnd = end + shiftAt(lines = lines, shifts = shifts, lineStart = lineStart, offset = end)
    )
}

/**
 * Prefixes every line the selection touches with a number, so the lines become an ordered list.
 *
 * The number of a line is its position in the block, so the markers grow with the number of the line. A line that
 * already carries a numeric marker is left alone, so pressing the button twice does not stack the markers. Because the
 * markers are not of the same length, the caret is moved with a shift that is summed for every line, instead of the
 * constant shift of a line style.
 */
private fun numberLines(text: String, start: Int, end: Int): CommentMarkdownTransform {
    val lineStart = lineStartOf(text = text, offset = start)
    val lineEnd = lineEndOf(text = text, offset = end)
    val block = text.substring(lineStart, lineEnd)
    val lines = block.split("\n")
    val prefixes = lines.mapIndexed { index, line -> if (ORDERED_LIST_MARKER.containsMatchIn(line)) "" else "${index + 1}. " }
    val numberedBlock = lines.mapIndexed { index, line -> prefixes[index] + line }.joinToString("\n")
    val newText = text.substring(0, lineStart) + numberedBlock + text.substring(lineEnd)
    val shifts = numberShifts(prefixes = prefixes)
    return CommentMarkdownTransform(
        text = newText,
        selectionStart = start + shiftAt(lines = lines, shifts = shifts, lineStart = lineStart, offset = start),
        selectionEnd = end + shiftAt(lines = lines, shifts = shifts, lineStart = lineStart, offset = end)
    )
}

/** The offset of the first character of the line that holds [offset]. */
private fun lineStartOf(text: String, offset: Int): Int {
    if (offset <= 0) {
        return 0
    }
    val lineBreak = text.lastIndexOf('\n', offset - 1)
    return if (lineBreak < 0) 0 else lineBreak + 1
}

/** The offset behind the last character of the line that holds [offset]. */
private fun lineEndOf(text: String, offset: Int): Int {
    val lineBreak = text.indexOf('\n', offset)
    return if (lineBreak < 0) text.length else lineBreak
}

/** The number of marker characters inserted before each line of the block. */
private fun prefixShifts(lines: List<String>, prefix: String): List<Int> {
    var cumulative = 0
    return lines.map { line ->
        if (line.startsWith(prefix)) {
            cumulative
        } else {
            cumulative += prefix.length
            cumulative
        }
    }
}

/**
 * The number of marker characters inserted before each line of the block. The markers of an ordered list are not of the
 * same length, so the length of every marker is summed on its own.
 */
private fun numberShifts(prefixes: List<String>): List<Int> {
    var cumulative = 0
    return prefixes.map { prefix ->
        cumulative += prefix.length
        cumulative
    }
}

/** The number of marker characters inserted before [offset] inside the block. */
private fun shiftAt(lines: List<String>, shifts: List<Int>, lineStart: Int, offset: Int): Int {
    var currentLineStart = lineStart
    lines.forEachIndexed { index, line ->
        if (offset <= currentLineStart + line.length) {
            return shifts[index]
        }
        currentLineStart += line.length + 1
    }
    return shifts.lastOrNull() ?: 0
}

/**
 * The notice of an empty preview; null when the draft holds something to render. The preview of a draft that is still empty
 * says so, instead of showing an empty page.
 */
internal fun emptyPreviewMessage(draft: String): String? {
    return if (draft.isBlank()) BbCloudBundle.message("main.branch.comments.preview.empty") else null
}

/**
 * Holds the state of one comment composer. The same composer serves the top level comment of the conversation and, with
 * the collapsed field turned off, the reply and the edit editor of a single thread.
 */
class CommentComposerViewModel(private val input: CommentInput, private val startsExpanded: Boolean = false) {
    val state: MutableState<CommentComposerUiState> = mutableStateOf(initialState())

    /**
     * The single source of truth for the editor's text and caret. A [TextFieldState] owns the selection internally and,
     * unlike the deprecated `TextFieldValue` editor, keeps it across focus loss; so when a toolbar button takes the
     * focus the styling can still wrap the text the user selected. The caret starts at the end of the initial text, the
     * way the Swing composer opened.
     */
    val editorState: TextFieldState = TextFieldState(
        initialText = input.initialText,
        initialSelection = TextRange(index = input.initialText.length)
    )

    /** The current draft, read straight from the editor state. */
    fun currentDraft(): String {
        return editorState.text.toString()
    }

    /** The placeholder of the collapsed field. */
    fun placeholder(): String {
        return input.placeholder
    }

    /** The label of the submit button. */
    fun submitLabel(): String {
        return input.submitLabel
    }

    /** The hint of the submit button, which names the shortcut that submits the draft. */
    fun submitHint(): String {
        return input.submitHint
    }

    /** Opens the editor; a composer that was opened by a button has no collapsed field of its own. */
    fun expand() {
        if (state.value.isExpanded) {
            return
        }
        LOG.info("User opened the ${input.label} editor")
        // The editor honours the same Ctrl or Cmd with Enter shortcut the platform comment editor used, so the hint of
        // the submit button stays accurate.
        LOG.debug("The ${input.label} editor submits on Ctrl or Cmd with Enter")
        state.value = state.value.copy(isExpanded = true, isPreview = false)
    }

    /** Closes the editor and drops the draft, the way the Swing composer did when it collapsed. */
    fun collapse() {
        editorState.setTextAndPlaceCursorAtEnd("")
        state.value = state.value.copy(
            isExpanded = false,
            isPreview = false,
            failureMessage = null
        )
    }

    /** Switches between the write and the preview mode of the editor. */
    fun togglePreview() {
        val isPreview = !state.value.isPreview
        LOG.info("User switched the ${input.label} editor to ${if (isPreview) "preview" else "write"} mode")
        state.value = state.value.copy(isPreview = isPreview)
    }

    /**
     * Applies a Markdown style to the draft. The text and the live selection are read from [editorState], passed
     * through the pure [applyCommentMarkdownStyle] transform, and written back atomically so the styling wraps the text
     * the user selected.
     *
     * The caret is left at the end of the styled region. This Compose build exposes no public API to re-select an
     * arbitrary range inside the buffer (`selectCharsIn` does not exist here and the range setter is internal), so the
     * caret is placed rather than the wrapped text re-selected; the user still sees their text wrapped, and the caret
     * sits ready to keep typing.
     */
    internal fun applyStyle(style: CommentMarkdownStyle) {
        LOG.info("User applied the ${style.name} style in the ${input.label} editor")
        val text = editorState.text.toString()
        val selection = editorState.selection
        val transform = applyCommentMarkdownStyle(
            text = text,
            selectionStart = selection.start,
            selectionEnd = selection.end,
            style = style
        )
        editorState.edit {
            replace(0, length, transform.text)
            val caret = transform.selectionEnd.coerceIn(0, length)
            if (caret >= length) {
                placeCursorAtEnd()
            } else {
                placeCursorBeforeCharAt(caret)
            }
        }
    }

    /**
     * Hands the draft to Bitbucket. The draft is kept when the submission is rejected, so the author does not lose what
     * they wrote; the failure message of the input is shown instead.
     */
    suspend fun submit(): Boolean {
        if (state.value.isSubmitting) {
            return false
        }
        val content = currentDraft()
        if (content.isBlank()) {
            return false
        }
        state.value = state.value.copy(isSubmitting = true, failureMessage = null)
        val isAccepted = submitToBitbucket(content = content)
        state.value = state.value.copy(isSubmitting = false)
        if (!isAccepted) {
            state.value = state.value.copy(failureMessage = input.failureMessage)
            return false
        }
        collapse()
        return true
    }

    /** Runs on the view model scope; returns true when Bitbucket accepted the comment. */
    private suspend fun submitToBitbucket(content: String): Boolean {
        val result = CompletableDeferred<Boolean>()
        withContext(Dispatchers.EDT) {
            LOG.info("User submitted a ${input.label}")
            input.submit(content) { isAccepted -> result.complete(isAccepted) }
        }
        return result.await()
    }

    private fun initialState(): CommentComposerUiState {
        return CommentComposerUiState(isExpanded = startsExpanded)
    }

    companion object {
        private val LOG = logger<CommentComposerViewModel>()
    }
}