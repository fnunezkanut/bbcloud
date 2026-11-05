package com.github.fnunezkanut.bbcloud.ui.markdowndiff

/** A run of source text on one side of a modified block. [changed] marks text that the other side does not have. */
data class WordDiffSegment(val text: String, val changed: Boolean)

/**
 * The word-level diff of one modified block. [similarity] is the share of tokens both sides have in common, from 0 to 1.
 */
data class WordDiffResult(val left: List<WordDiffSegment>, val right: List<WordDiffSegment>, val similarity: Double)

/**
 * Word-level diff of two source texts. The texts are split into words, whitespace runs, and single punctuation
 * characters, and the longest common subsequence of those tokens is kept. Adjacent tokens with the same state are merged.
 */
object WordDiff {
    fun compute(oldText: String, newText: String): WordDiffResult {
        val oldTokens = tokenize(text = oldText)
        val newTokens = tokenize(text = newText)
        if (oldTokens.size.toLong() * newTokens.size.toLong() > MAX_TOKEN_PRODUCT) {
            return WordDiffResult(
                left = listOf(WordDiffSegment(text = oldText, changed = true)),
                right = listOf(WordDiffSegment(text = newText, changed = true)),
                similarity = 0.0
            )
        }
        val matches = Lcs.matches(left = oldTokens, right = newTokens)
        val oldMatched = matches.map { (oldIndex, _) -> oldIndex }.toSet()
        val newMatched = matches.map { (_, newIndex) -> newIndex }.toSet()
        val significantTotal = oldTokens.count(::isSignificant) + newTokens.count(::isSignificant)
        val significantMatched = matches.count { (oldIndex, _) -> isSignificant(oldTokens[oldIndex]) } * 2
        val similarity = if (significantTotal == 0) 1.0 else significantMatched.toDouble() / significantTotal
        return WordDiffResult(
            left = segments(tokens = oldTokens, matched = oldMatched),
            right = segments(tokens = newTokens, matched = newMatched),
            similarity = similarity
        )
    }

    internal fun tokenize(text: String): List<String> {
        return TOKEN.findAll(text).map { match -> match.value }.toList()
    }

    private fun isSignificant(token: String): Boolean {
        return token.isNotBlank()
    }

    private fun segments(tokens: List<String>, matched: Set<Int>): List<WordDiffSegment> {
        val segments = mutableListOf<WordDiffSegment>()
        val builder = StringBuilder()
        var currentChanged: Boolean? = null
        tokens.forEachIndexed { index, token ->
            // Whitespace between two changed words counts as changed even when the LCS matched it to some other
            // whitespace, so a replaced phrase highlights as one run.
            val changed = if (token.isBlank()) {
                currentChanged == true && index + 1 < tokens.size && (index + 1) !in matched
            } else {
                index !in matched
            }
            if (currentChanged != null && changed != currentChanged) {
                segments += WordDiffSegment(text = builder.toString(), changed = currentChanged == true)
                builder.clear()
            }
            currentChanged = changed
            builder.append(token)
        }
        if (currentChanged != null) {
            segments += WordDiffSegment(text = builder.toString(), changed = currentChanged == true)
        }
        return segments
    }

    /** Above this many token comparisons the block is shown as fully changed, to bound memory and time. */
    private const val MAX_TOKEN_PRODUCT = 4_000_000L
    private val TOKEN = Regex("""[\p{L}\p{N}_]+|\s+|[^\p{L}\p{N}_\s]""")
}