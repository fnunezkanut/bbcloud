package com.github.fnunezkanut.bbcloud.git

/** The pull request title and description a commit message prefills the new pull request form with. */
data class ParsedCommitMessage(val title: String, val description: String)

/**
 * Splits a raw commit message into a pull request title (its first line) and description (the body after the subject).
 *
 * Pure function with no IntelliJ dependencies, so it is unit-testable without the platform.
 */
object CommitMessageParser {
    /** The maximum length of a Bitbucket pull request title. */
    const val MAX_TITLE_LENGTH = 255

    /** Parses [rawMessage] into a title and description. Line endings are normalised to `\n` first. */
    fun parse(rawMessage: String): ParsedCommitMessage {
        val normalized = rawMessage.replace("\r\n", "\n").replace('\r', '\n')
        val firstLineBreak = normalized.indexOf('\n')
        val subject = if (firstLineBreak < 0) normalized else normalized.substring(0, firstLineBreak)
        val title = subject.trim().take(MAX_TITLE_LENGTH)
        val body = if (firstLineBreak < 0) "" else normalized.substring(firstLineBreak + 1)
        return ParsedCommitMessage(title = title, description = dropSeparatorLine(body).trimEnd())
    }

    /** Removes the single blank line that separates the subject from the body; other leading lines are kept. */
    private fun dropSeparatorLine(body: String): String {
        val firstLineBreak = body.indexOf('\n')
        val firstLine = if (firstLineBreak < 0) body else body.substring(0, firstLineBreak)
        return if (firstLine.isBlank()) body.drop(firstLine.length + 1) else body
    }
}