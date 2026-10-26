package com.github.fnunezkanut.bbcloud.ui.main

object DiffstatLinesEmoji {
    private const val SMILING = "\uD83D\uDE42" // 🙂
    private const val NEUTRAL = "\uD83D\uDE10" // 😐
    private const val SAD = "\uD83D\uDE41" // 🙁
    private const val ANGRY = "\uD83D\uDE20" // 😠

    fun forLinesAdded(linesAdded: Int): String {
        return when {
            linesAdded < 100 -> SMILING
            linesAdded < 500 -> NEUTRAL
            linesAdded < 1000 -> SAD
            else -> ANGRY
        }
    }
}