package com.github.fnunezkanut.bbcloud.ui.main

/**
 * Describes one comment input: the collapsed placeholder, how the expanded editor is labelled, what text it starts with,
 * and what submitting it does. The same Markdown editor serves the top level comment, a reply and an edit, so those differ
 * only in this description. [submit] is callback shaped so that a single bridging implementation can hop onto the event
 * dispatch thread and await the result.
 */
class CommentInput(
    /** Names the input in the log, for example "comment", "reply" or "comment edit". */
    val label: String,
    val placeholder: String,
    val submitLabel: String,
    /** Already formatted with the submit shortcut. */
    val submitHint: String,
    /** Shown under the editor when the submission is rejected, while the draft is kept. */
    val failureMessage: String,
    /** The Markdown the editor starts with; empty for a new comment or reply. */
    val initialText: String = "",
    val submit: (content: String, onResult: (Boolean) -> Unit) -> Unit
)