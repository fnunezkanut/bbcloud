package com.github.fnunezkanut.bbcloud.ui.markdowndiff

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class WordDiffTest {
    @Test
    fun `compute - should merge a replaced phrase into one changed run`() {
        // WHEN
        val result = WordDiff.compute(oldText = "use the old api now", newText = "use a brand new api now")

        // THEN
        assertSoftly {
            result.left.filter { segment -> segment.changed }.map { segment -> segment.text } shouldContainExactly listOf("the old")
            result.right.filter { segment -> segment.changed }.map { segment -> segment.text } shouldContainExactly listOf("a brand new")
            result.left.joinToString(separator = "") { segment -> segment.text } shouldBe "use the old api now"
        }
    }

    @Test
    fun `compute - should report full similarity for equal texts`() {
        // WHEN
        val result = WordDiff.compute(oldText = "same text", newText = "same text")

        // THEN
        assertSoftly {
            result.similarity shouldBe 1.0
            result.left.any { segment -> segment.changed } shouldBe false
        }
    }

    @Test
    fun `tokenize - should split words, whitespace and punctuation`() {
        // WHEN
        val tokens = WordDiff.tokenize(text = "**bold** text.")

        // THEN
        tokens shouldContainExactly listOf("*", "*", "bold", "*", "*", " ", "text", ".")
    }
}