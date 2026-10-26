package com.github.fnunezkanut.bbcloud.ui.main

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class NormalizeDescriptionTest {
    @Test
    fun `normalizeDescription - should convert a labeled markdown image into a link`() {
        // GIVEN
        val markdown = "![alt](https://example.com/x.png)"

        // WHEN
        val result = normalizeDescription(markdown)

        // THEN
        assertSoftly {
            result shouldBe "[alt](https://example.com/x.png)"
            result shouldNotContain "!["
        }
    }

    @Test
    fun `normalizeDescription - should convert the real unlabeled sample into a link with the file name label`() {
        // GIVEN
        val url =
            "https://bitbucket.org/%7B%7D/%7B5719e521-ec82-48d3-8264-d0bc5a495eda%7D/images/" +
                "3903675492-Screenshot%202026-10-18%20at%2018.24.54.png"
        val markdown = "![]($url){: data-layout='center' }"

        // WHEN
        val result = normalizeDescription(markdown)

        // THEN
        assertSoftly {
            result shouldBe "[3903675492-Screenshot 2026-10-18 at 18.24.54.png]($url)"
            result shouldContain url
            result shouldNotContain "!["
            result shouldNotContain "{:"
            result shouldNotContain "data-layout"
        }
    }

    @Test
    fun `normalizeDescription - should preserve text alongside a converted image`() {
        // GIVEN
        val markdown = "Description\n\n![diagram](https://example.com/diagram.png)"

        // WHEN
        val result = normalizeDescription(markdown)

        // THEN
        assertSoftly {
            result shouldContain "Description"
            result shouldContain "[diagram](https://example.com/diagram.png)"
            result shouldNotContain "!["
        }
    }

    @Test
    fun `normalizeDescription - should convert multiple images in one description`() {
        // GIVEN
        val markdown = "![one](https://example.com/one.png) and ![two](https://example.com/two.png)"

        // WHEN
        val result = normalizeDescription(markdown)

        // THEN
        assertSoftly {
            result shouldContain "[one](https://example.com/one.png)"
            result shouldContain "[two](https://example.com/two.png)"
            result shouldNotContain "!["
        }
    }

    @Test
    fun `normalizeDescription - should fall back to the generic label for a malformed image URL`() {
        // GIVEN
        val markdown = "![](not a valid url)"

        // WHEN
        val result = normalizeDescription(markdown)

        // THEN
        assertSoftly {
            result shouldBe "[View image](not a valid url)"
            result shouldNotContain "!["
        }
    }

    @Test
    fun `normalizeDescription - should sanitize raw HTML to plain text`() {
        // GIVEN
        val markdown = "<p>Hello <strong>world</strong></p>"

        // WHEN
        val result = normalizeDescription(markdown)

        // THEN
        assertSoftly {
            result shouldBe "Hello world"
            result shouldNotContain "<p>"
            result shouldNotContain "<strong>"
        }
    }

    @Test
    fun `normalizeDescription - should drop raw HTML images`() {
        // GIVEN
        val markdown = "<p>Before <img src=\"https://example.com/x.png\" alt=\"x\"> after</p>"

        // WHEN
        val result = normalizeDescription(markdown)

        // THEN
        assertSoftly {
            result shouldContain "Before"
            result shouldContain "after"
            result shouldNotContain "<img"
            result shouldNotContain "https://example.com/x.png"
        }
    }
}