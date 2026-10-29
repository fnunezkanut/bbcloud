package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class MarkdownCodeRegionsTest {
    @Test
    fun `splitCodeRegions - should return prose only when there is no code`() {
        // GIVEN
        val body = "plain text"

        // WHEN
        val segments = MarkdownCodeRegions.splitCodeRegions(body = body)

        // THEN
        assertSoftly {
            segments.size shouldBe 1
            segments.single().text shouldBe "plain text"
            segments.single().isCode.shouldBeFalse()
        }
    }

    @Test
    fun `splitCodeRegions - should mark an inline code span as code`() {
        // GIVEN
        val body = "before `code` after"

        // WHEN
        val segments = MarkdownCodeRegions.splitCodeRegions(body = body)

        // THEN
        assertSoftly {
            segments.map { segment -> segment.text } shouldBe listOf("before ", "`code`", " after")
            segments.map { segment -> segment.isCode } shouldBe listOf(false, true, false)
        }
    }

    @Test
    fun `splitCodeRegions - should mark a fenced block with backticks as code`() {
        // GIVEN
        val body = "text\n```\n:smile:\n```\nafter"

        // WHEN
        val segments = MarkdownCodeRegions.splitCodeRegions(body = body)

        // THEN
        assertSoftly {
            segments.map { segment -> segment.isCode } shouldBe listOf(false, true, false)
            segments[1].text shouldBe "```\n:smile:\n```\n"
        }
    }

    @Test
    fun `splitCodeRegions - should mark a fenced block with tildes as code`() {
        // GIVEN
        val body = "~~~\n@{11111111-1111-1111-1111-111111111111}\n~~~"

        // WHEN
        val segments = MarkdownCodeRegions.splitCodeRegions(body = body)

        // THEN
        segments.single().isCode.shouldBeTrue()
    }

    @Test
    fun `splitCodeRegions - should run an unclosed fence to the end of the body`() {
        // GIVEN
        val body = "```\ncode without a closer"

        // WHEN
        val segments = MarkdownCodeRegions.splitCodeRegions(body = body)

        // THEN
        assertSoftly {
            segments.size shouldBe 1
            segments.single().isCode.shouldBeTrue()
        }
    }

    @Test
    fun `splitCodeRegions - should keep a backslash-escaped backtick as prose`() {
        // GIVEN
        val body = "\\`not code`"

        // WHEN
        val segments = MarkdownCodeRegions.splitCodeRegions(body = body)

        // THEN
        assertSoftly {
            segments.all { segment -> !segment.isCode }.shouldBeTrue()
            segments.joinToString(separator = "") { segment -> segment.text } shouldBe body
        }
    }

    @Test
    fun `splitCodeRegions - should return segments that join back to the input`() {
        // GIVEN
        val body = "a `b` c\n```\nd\n```\ne `f"

        // WHEN
        val segments = MarkdownCodeRegions.splitCodeRegions(body = body)

        // THEN
        segments.joinToString(separator = "") { segment -> segment.text } shouldBe body
    }

    @Test
    fun `splitCodeRegions - should return no segments for an empty body`() {
        // GIVEN
        val body = ""

        // WHEN
        val segments = MarkdownCodeRegions.splitCodeRegions(body = body)

        // THEN
        segments.shouldBeEmpty()
    }
}