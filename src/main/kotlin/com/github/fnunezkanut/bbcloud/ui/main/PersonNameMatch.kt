package com.github.fnunezkanut.bbcloud.ui.main

import java.text.Normalizer

/**
 * Ranks a person's names against a query, the way the reviewer search and the @mention suggestions both need it.
 * The ranks are ordered best first: a full-name prefix beats a word prefix, which beats a substring match.
 */
internal object PersonNameMatch {
    const val RANK_NAME_PREFIX = 0
    const val RANK_WORD_PREFIX = 1
    const val RANK_SUBSTRING = 2

    private val WORD_SEPARATORS = charArrayOf(' ', '.', '-', '_')

    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    /**
     * Returns the best rank of [query] against any of [names], or null when none matches. Null names are ignored.
     * [query] and the names are normalized with [normalize] before comparing.
     */
    fun rank(names: List<String?>, query: String): Int? {
        val normalizedQuery = normalize(text = query)
        if (normalizedQuery.isEmpty()) {
            return null
        }
        val normalizedNames = names.filterNotNull().map { name -> normalize(text = name) }.filter { name -> name.isNotEmpty() }
        return when {
            normalizedNames.any { name -> name.startsWith(normalizedQuery) } -> RANK_NAME_PREFIX
            normalizedNames.any { name -> name.split(*WORD_SEPARATORS).any { word -> word.startsWith(normalizedQuery) } } -> RANK_WORD_PREFIX
            normalizedNames.any { name -> name.contains(normalizedQuery) } -> RANK_SUBSTRING
            else -> null
        }
    }

    /** Trims, lowercases, and folds diacritics, so `José` and `jose` compare equal. */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
        return COMBINING_MARKS.replace(decomposed, replacement = "").lowercase()
    }
}