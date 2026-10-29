package com.github.fnunezkanut.bbcloud.bitbucket

import com.intellij.openapi.diagnostic.logger

/**
 * Replaces the emoji markup that Bitbucket writes into comment Markdown with the emoji itself.
 *
 * An emoji picked in the Bitbucket editor is stored as a shortcode followed by a kramdown inline attribute list, for
 * example `:slight_smile:{: data-emoji-id='1f642' data-emoji-text='🙂' }`. Jewel's Markdown parser has no attribute-list
 * support, so the fragment would otherwise be printed as text.
 *
 * The replacement is the `data-emoji-text` value when it is present. Otherwise the emoji is rebuilt from the hex code
 * points in `data-emoji-id`, joined by `-`. When neither yields an emoji, the shortcode is looked up in [EmojiShortcodes],
 * and failing that the bare `:shortcode:` is kept, so the attribute fragment never reaches the user.
 *
 * A plain shortcode without an attribute list, for example `:thumbs_up:`, is replaced through [EmojiShortcodes] when the
 * table knows it, and is left as written otherwise.
 *
 * Emoji markup inside inline code spans and fenced code blocks stays literal, as it does for mentions. The code
 * detection is shared through [MarkdownCodeRegions].
 */
internal object CommentEmojiResolver {
    private val LOG = logger<CommentEmojiResolver>()

    // The kramdown attribute list (group 2) is optional, so plain `:shortcode:` tokens match too.
    private val EMOJI_TOKEN = Regex(":([A-Za-z0-9_+\\-]+):(?:\\{:([^}]*)\\})?")

    // Matches `name='value'` and `name="value"`; the closing quote must equal the opening quote.
    private val ATTRIBUTE = Regex("([A-Za-z][A-Za-z0-9_-]*)\\s*=\\s*(['\"])(.*?)\\2")

    /**
     * Returns [body] with every emoji token outside code replaced by its emoji. A body without a `:` is returned unchanged,
     * which is the common case.
     */
    fun resolveEmoji(body: String): String {
        if (!body.contains(':')) {
            return body
        }
        var replacementCount = 0
        val resolved = MarkdownCodeRegions.splitCodeRegions(body = body).joinToString(separator = "") { segment ->
            if (segment.isCode) {
                segment.text
            } else {
                EMOJI_TOKEN.replace(input = segment.text) { match ->
                    val shortcode = match.groupValues[1]
                    val attributes = match.groups[2]?.value
                    val replacement = if (attributes == null) {
                        EmojiShortcodes.emojiFor(shortcode = shortcode) ?: match.value
                    } else {
                        emojiFor(shortcode = shortcode, attributes = attributes)
                    }
                    if (replacement != match.value) {
                        replacementCount += 1
                    }
                    replacement
                }
            }
        }
        if (replacementCount > 0) {
            LOG.debug("Resolving Bitbucket emoji markup with replacementCount=$replacementCount")
        }
        return resolved
    }

    private fun emojiFor(shortcode: String, attributes: String): String {
        val values = ATTRIBUTE.findAll(input = attributes).associate { match -> match.groupValues[1] to match.groupValues[3] }
        val emojiText = values["data-emoji-text"]
        if (!emojiText.isNullOrBlank()) {
            return emojiText
        }
        return decodeCodePoints(hexCodePoints = values["data-emoji-id"].orEmpty())
            ?: EmojiShortcodes.emojiFor(shortcode = shortcode)
            ?: ":$shortcode:"
    }

    /**
     * Decodes `-`-separated hex code points, for example `1f44d-1f3fb` or a ZWJ sequence `1f468-200d-1f4bb`. Returns null
     * when the value is blank or any part is not a valid code point, so the caller can fall back to the shortcode.
     */
    private fun decodeCodePoints(hexCodePoints: String): String? {
        if (hexCodePoints.isBlank()) {
            return null
        }
        val builder = StringBuilder()
        for (part in hexCodePoints.split('-')) {
            val codePoint = part.toIntOrNull(radix = 16)
            if (codePoint == null || !Character.isValidCodePoint(codePoint)) {
                return null
            }
            builder.appendCodePoint(codePoint)
        }
        return builder.toString()
    }
}