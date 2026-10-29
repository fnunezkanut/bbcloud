package com.github.fnunezkanut.bbcloud.bitbucket

/**
 * Reads and rewrites the `@{account-key}` mention tokens that Bitbucket writes into comment Markdown.
 *
 * Bitbucket stores a mention as the account UUID (or Atlassian account id) wrapped in braces. The braces and letter case
 * differ from the [ActivityUser.uuid] values, so both sides are compared through [normalizeKey].
 *
 * Mentions inside inline code spans and fenced code blocks stay literal. The code detection is a deliberately small
 * scanner rather than a full CommonMark parser, so an exotic construct can be misjudged. The failure mode is a mention
 * that stays literal or is styled when it should not be, never a body that is corrupted. The scanner lives in
 * [MarkdownCodeRegions], which it shares with [CommentEmojiResolver].
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

    /**
     * Returns the account keys of the mentions outside code, exactly as written inside the braces. Callers pass the raw
     * value to Bitbucket and use [normalizeKey] when they match it to a name.
     */
    fun mentionTokensIn(body: String): Set<String> {
        return MarkdownCodeRegions.splitCodeRegions(body = body)
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
        return MarkdownCodeRegions.splitCodeRegions(body = body).joinToString(separator = "") { segment ->
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
}