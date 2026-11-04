package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.CommentMentionResolver
import com.github.fnunezkanut.bbcloud.bitbucket.MarkdownCodeRegions
import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidate

/** A detected `@` mention query: [anchorStart] is the index of `@`, [caret] is the caret, [query] is the text between them. */
internal data class MentionQuery(val anchorStart: Int, val caret: Int, val query: String)

/** Detects the `@` mention query under the caret in a comment draft. Pure. */
internal object MentionQueryDetector {
    const val MAX_QUERY_LENGTH = 40

    private val ALLOWED_PRECEDING = charArrayOf('(', '[', '{', '"', '\'', '>')

    /**
     * Returns the mention query under the caret, or null when there is none. The selection must be collapsed. Walks
     * back from the caret to the nearest `@`, at most [MAX_QUERY_LENGTH] characters, stopping on a newline. The `@`
     * must begin a mention (start of text or after whitespace or one of `([{"'>`), the query must not start with `{`
     * (an existing token) or whitespace, must have no two consecutive spaces, and must lie outside Markdown code.
     */
    fun find(text: String, selectionStart: Int, selectionEnd: Int): MentionQuery? {
        if (selectionStart != selectionEnd) {
            return null
        }
        val caret = selectionStart.coerceIn(0, text.length)
        var index = caret - 1
        var anchor = -1
        var scanned = 0
        while (index >= 0 && scanned <= MAX_QUERY_LENGTH) {
            val character = text[index]
            if (character == '\n') {
                return null
            }
            if (character == '@') {
                anchor = index
                break
            }
            index -= 1
            scanned += 1
        }
        if (anchor == -1) {
            return null
        }
        if (!isMentionStart(text = text, anchor = anchor)) {
            return null
        }
        val query = text.substring(anchor + 1, caret)
        if (query.startsWith("{") || query.startsWith(" ") || query.contains("  ")) {
            return null
        }
        if (!isOutsideCode(text = text, caret = caret, anchor = anchor)) {
            return null
        }
        return MentionQuery(anchorStart = anchor, caret = caret, query = query)
    }

    private fun isMentionStart(text: String, anchor: Int): Boolean {
        if (anchor == 0) {
            return true
        }
        val preceding = text[anchor - 1]
        return preceding.isWhitespace() || preceding in ALLOWED_PRECEDING
    }

    private fun isOutsideCode(text: String, caret: Int, anchor: Int): Boolean {
        val prefix = text.substring(0, caret)
        var offset = 0
        for (segment in MarkdownCodeRegions.splitCodeRegions(body = prefix)) {
            val end = offset + segment.text.length
            if (anchor in offset until end) {
                return !segment.isCode
            }
            offset = end
        }
        return true
    }
}

/** Ranks mention candidates against a query. Pure. */
internal object MentionCandidateRanker {
    const val MAX_SUGGESTIONS = 8

    /**
     * Ranks [local] and [workspace] candidates for [query]. De-duplicates across both by identity (higher tier wins),
     * drops a later entry sharing an earlier one's account id, drops candidates with no mention key, drops the current
     * user, and for a non-blank query keeps name matches sorted by tier, then match rank, then display name. A blank
     * query keeps the input order. Returns at most [limit].
     */
    fun rank(
        query: String,
        local: List<MentionCandidate>,
        workspace: List<MentionCandidate>,
        excludedUuid: String?,
        limit: Int = MAX_SUGGESTIONS
    ): List<MentionCandidate> {
        val deduplicated = deduplicate(candidates = local + workspace)
        val excludedKey = excludedUuid?.let { uuid -> CommentMentionResolver.normalizeKey(rawKey = uuid) }
        val eligible = deduplicated.filter { candidate ->
            candidate.mentionKey() != null && !isExcluded(candidate = candidate, excludedKey = excludedKey)
        }
        if (query.isBlank()) {
            return eligible.take(limit)
        }
        return eligible
            .mapNotNull { candidate ->
                PersonNameMatch.rank(names = listOf(candidate.displayName, candidate.nickname), query = query)?.let { matchRank -> candidate to matchRank }
            }
            .sortedWith(
                compareBy<Pair<MentionCandidate, Int>> { (candidate, _) -> candidate.tier.ordinal }
                    .thenBy { (_, matchRank) -> matchRank }
                    .thenBy { (candidate, _) -> candidate.displayName.lowercase() }
            )
            .map { (candidate, _) -> candidate }
            .take(limit)
    }

    private fun deduplicate(candidates: List<MentionCandidate>): List<MentionCandidate> {
        val seenIdentities = mutableSetOf<String>()
        val seenAccountIds = mutableSetOf<String>()
        val result = mutableListOf<MentionCandidate>()
        for (candidate in candidates) {
            val identity = candidate.identityKey()
            if (identity != null && !seenIdentities.add(identity)) {
                continue
            }
            val accountId = candidate.accountId?.takeIf { it.isNotBlank() }?.lowercase()
            if (accountId != null && !seenAccountIds.add(accountId)) {
                continue
            }
            result.add(candidate)
        }
        return result
    }

    private fun isExcluded(candidate: MentionCandidate, excludedKey: String?): Boolean {
        if (excludedKey == null) {
            return false
        }
        val candidateUuidKey = candidate.uuid?.let { uuid -> CommentMentionResolver.normalizeKey(rawKey = uuid) }
        return candidateUuidKey == excludedKey
    }
}

/**
 * Replaces the active `@query` in [text] with the mention token for [mentionKey] plus a trailing space, returning the
 * new text and a collapsed caret after the inserted text. The extra space is omitted when a space already follows.
 */
internal fun insertMention(text: String, query: MentionQuery, mentionKey: String): CommentMarkdownTransform {
    val anchorStart = query.anchorStart.coerceIn(0, text.length)
    val caret = query.caret.coerceIn(anchorStart, text.length)
    val followedBySpace = text.getOrNull(caret)?.isWhitespace() == true
    val token = CommentMentionResolver.mentionToken(accountKey = mentionKey)
    val insertion = if (followedBySpace) token else "$token "
    val newText = text.substring(0, anchorStart) + insertion + text.substring(caret)
    val newCaret = anchorStart + insertion.length
    return CommentMarkdownTransform(text = newText, selectionStart = newCaret, selectionEnd = newCaret)
}

/** A range of [start], [end) in the draft text that the editor shows as [display] instead of the raw token. */
internal data class MentionDisplayReplacement(val start: Int, val end: Int, val display: String)

/**
 * Builds the display replacements for every known `@{...}` token in [text], mapping it to `@DisplayName`. Tokens whose
 * key is not in [names] and tokens inside code are left alone. Replacements are returned in ascending order with no
 * overlaps.
 */
internal fun mentionDisplayReplacements(text: String, names: Map<String, String>): List<MentionDisplayReplacement> {
    if (names.isEmpty()) {
        return emptyList()
    }
    val namesByKey = names.entries.associate { (key, name) -> CommentMentionResolver.normalizeKey(rawKey = key) to name }
    return CommentMentionResolver.mentionTokenRanges(body = text)
        .mapNotNull { (range, rawKey) ->
            val name = namesByKey[CommentMentionResolver.normalizeKey(rawKey = rawKey)] ?: return@mapNotNull null
            MentionDisplayReplacement(start = range.first, end = range.last + 1, display = "@$name")
        }
        .sortedBy { replacement -> replacement.start }
}