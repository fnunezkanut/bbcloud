package com.github.fnunezkanut.bbcloud.bitbucket

/**
 * Reads and rewrites the `@{account-key}` mention tokens that Bitbucket writes into comment Markdown.
 *
 * Bitbucket stores a mention as the account UUID (or Atlassian account id) wrapped in braces. The braces and letter case
 * differ from the [ActivityUser.uuid] values, so both sides are compared through [normalizeKey].
 *
 * Mentions inside inline code spans and fenced code blocks stay literal. The code detection is a deliberately small
 * scanner rather than a full CommonMark parser, so an exotic construct can be misjudged. The failure mode is a mention
 * that stays literal or is styled when it should not be, never a body that is corrupted.
 *
 * This object lives with the Bitbucket model rather than the UI because the service layer needs the same token scan to
 * decide which accounts need a name.
 */
internal object CommentMentionResolver {
    private val MENTION_TOKEN = Regex("@\\{([^}]+)\\}")

    // A Bitbucket account UUID in canonical 8-4-4-4-12 hex form, with or without surrounding braces. Account keys that do
    // not match this shape (Atlassian account ids such as "712020:...") are not UUIDs and must be queried without braces.
    private val ACCOUNT_UUID = Regex("\\{?[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\}?")
    private val MARKDOWN_SPECIAL_CHARACTERS = Regex("[\\\\*_`\\[\\]<>~]")

    /** One run of a comment body. [isCode] is true for a fenced block or an inline code span, which is never rewritten. */
    private data class BodySegment(val text: String, val isCode: Boolean)

    /**
     * Returns the account keys of the mentions outside code, exactly as written inside the braces. Callers pass the raw
     * value to Bitbucket and use [normalizeKey] when they match it to a name.
     */
    fun mentionTokensIn(body: String): Set<String> {
        return splitCodeRegions(body = body)
            .filterNot { segment -> segment.isCode }
            .flatMap { segment -> MENTION_TOKEN.findAll(segment.text).map { match -> match.groupValues[1] } }
            .toSet()
    }

    /**
     * Trims the key, strips one leading `{` and one trailing `}`, and lowercases it. A token `@{ABC}` then matches a stored
     * `{abc}` uuid, because the two sides only differ in braces and case.
     */
    fun normalizeKey(rawKey: String): String {
        val trimmed = rawKey.trim()
        return trimmed.removePrefix("{").removeSuffix("}").lowercase()
    }

    /**
     * Returns the path selector Bitbucket's `/2.0/users/{selector}` endpoint expects for [rawKey]. A canonical account
     * UUID is wrapped in braces, because Bitbucket's path form for a UUID is `{uuid}`. Every other form - notably an
     * Atlassian account id like `712020:...` - is returned verbatim, because wrapping it in braces makes the endpoint 404.
     */
    fun lookupSelector(rawKey: String): String {
        val normalized = normalizeKey(rawKey = rawKey)
        if (ACCOUNT_UUID.matches(normalized)) {
            return "{${normalized.removePrefix("{").removeSuffix("}")}}"
        }
        return normalized
    }

    /**
     * Replaces every mention outside code with a purple-styled `@DisplayName` fragment when [mentions] knows the account.
     * Keys of [mentions] may use any form that [normalizeKey] accepts. A mention without an entry is left verbatim.
     */
    fun resolveMentions(body: String, mentions: Map<String, String>): String {
        if (mentions.isEmpty() || !body.contains("@{")) {
            return body
        }
        val namesByKey = mentions.entries.associate { (key, name) -> normalizeKey(rawKey = key) to name }
        return splitCodeRegions(body = body).joinToString(separator = "") { segment ->
            if (segment.isCode) segment.text else replaceTokens(text = segment.text, namesByKey = namesByKey)
        }
    }

    private fun replaceTokens(text: String, namesByKey: Map<String, String>): String {
        return MENTION_TOKEN.replace(input = text) { match ->
            val name = namesByKey[normalizeKey(rawKey = match.groupValues[1])] ?: return@replace match.value
            mentionMarkdown(displayName = name)
        }
    }

    private fun mentionMarkdown(displayName: String): String {
        // TODO: Bold is the only emphasis that can be limited to mentions today. Jewel's inline styling (strong, code and link
        // spans) applies to every span of that kind in the document, so a purple mention needs a mention-scoped style first.
        val escapedName = escapeMarkdown(text = displayName)
        return "**@$escapedName**"
    }

    private fun escapeMarkdown(text: String): String {
        return MARKDOWN_SPECIAL_CHARACTERS.replace(input = text) { match -> "\\${match.value}" }
    }

    /**
     * Splits [body] into prose and code runs. The segments join back to exactly [body], so the prose can be rewritten and
     * the code copied through unchanged.
     */
    private fun splitCodeRegions(body: String): List<BodySegment> {
        val segments = mutableListOf<BodySegment>()
        val pending = StringBuilder()
        var fenceMarker: String? = null
        val lines = body.split('\n')
        lines.forEachIndexed { index, line ->
            val terminated = if (index < lines.lastIndex) "$line\n" else line
            val marker = fenceMarker
            if (marker == null) {
                val opener = fenceOpenerOf(line = line)
                if (opener != null) {
                    segments += splitInlineCode(text = pending.toString())
                    pending.clear()
                    fenceMarker = opener
                }
                pending.append(terminated)
            } else {
                pending.append(terminated)
                if (isFenceCloser(line = line, marker = marker)) {
                    addSegment(segments = segments, text = pending.toString(), isCode = true)
                    pending.clear()
                    fenceMarker = null
                }
            }
        }
        if (fenceMarker != null) {
            // An unclosed fence runs to the end of the body, as it does in CommonMark.
            addSegment(segments = segments, text = pending.toString(), isCode = true)
        } else {
            segments += splitInlineCode(text = pending.toString())
        }
        return segments
    }

    /** Splits prose into text and inline code spans; a backtick run without a matching closer stays literal text. */
    private fun splitInlineCode(text: String): List<BodySegment> {
        val segments = mutableListOf<BodySegment>()
        val prose = StringBuilder()
        var index = 0
        while (index < text.length) {
            val character = text[index]
            if (character == '\\' && text.getOrNull(index + 1) == '`') {
                // A backslash-escaped backtick is text, not the start of a code span.
                prose.append(text, index, index + 2)
                index += 2
            } else if (character != '`') {
                prose.append(character)
                index += 1
            } else {
                val openLength = backtickRunLength(text = text, start = index)
                val closeEnd = closingBacktickRunEnd(text = text, from = index + openLength, length = openLength)
                if (closeEnd == -1) {
                    prose.append(text, index, index + openLength)
                    index += openLength
                } else {
                    addSegment(segments = segments, text = prose.toString(), isCode = false)
                    prose.clear()
                    addSegment(segments = segments, text = text.substring(index, closeEnd), isCode = true)
                    index = closeEnd
                }
            }
        }
        addSegment(segments = segments, text = prose.toString(), isCode = false)
        return segments
    }

    private fun backtickRunLength(text: String, start: Int): Int {
        var end = start
        while (end < text.length && text[end] == '`') {
            end += 1
        }
        return end - start
    }

    /** Returns the index just past the first backtick run of exactly [length] at or after [from], or -1 when there is none. */
    private fun closingBacktickRunEnd(text: String, from: Int, length: Int): Int {
        var index = from
        while (index < text.length) {
            if (text[index] == '`') {
                val runLength = backtickRunLength(text = text, start = index)
                if (runLength == length) {
                    return index + runLength
                }
                index += runLength
            } else {
                index += 1
            }
        }
        return -1
    }

    /** Returns the fence marker (three or more backticks or tildes) when [line] opens a fenced block, otherwise null. */
    private fun fenceOpenerOf(line: String): String? {
        val trimmed = line.trimStart()
        val fenceCharacter = trimmed.firstOrNull() ?: return null
        if (fenceCharacter != '`' && fenceCharacter != '~') {
            return null
        }
        val run = trimmed.takeWhile { character -> character == fenceCharacter }
        return run.takeIf { candidate -> candidate.length >= FENCE_MIN_LENGTH }
    }

    /** A closing fence repeats the opener's character at least as many times, with nothing else on the line. */
    private fun isFenceCloser(line: String, marker: String): Boolean {
        val trimmed = line.trim()
        return trimmed.length >= marker.length && trimmed.all { character -> character == marker.first() }
    }

    private fun addSegment(segments: MutableList<BodySegment>, text: String, isCode: Boolean) {
        if (text.isEmpty()) {
            return
        }
        segments += BodySegment(text = text, isCode = isCode)
    }

    private const val FENCE_MIN_LENGTH = 3
}