package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class CommentEmojiResolverTest {
    @Test
    fun `resolveEmoji - should replace the screenshot sample with the emoji`() {
        // GIVEN
        val body = "a stage-0-handoff.md :slight_smile:{: data-emoji-id='1f642' data-emoji-text='🙂' }  as its"

        // WHEN
        val result = CommentEmojiResolver.resolveEmoji(body = body)

        // THEN
        result shouldBe "a stage-0-handoff.md 🙂  as its"
    }

    @Test
    fun `resolveEmoji - should accept double-quoted attributes`() {
        // GIVEN
        val body = ":smile:{: data-emoji-id=\"1f642\" data-emoji-text=\"🙂\" }"

        // WHEN
        val result = CommentEmojiResolver.resolveEmoji(body = body)

        // THEN
        result shouldBe "🙂"
    }

    @Test
    fun `resolveEmoji - should accept attributes in reverse order`() {
        // GIVEN
        val body = ":smile:{: data-emoji-text='🙂' data-emoji-id='1f642' }"

        // WHEN
        val result = CommentEmojiResolver.resolveEmoji(body = body)

        // THEN
        result shouldBe "🙂"
    }

    @Test
    fun `resolveEmoji - should rebuild a skin-tone emoji from the id when the text is missing`() {
        // GIVEN
        val body = ":thumbsup_tone1:{: data-emoji-id='1f44d-1f3fb' }"

        // WHEN
        val result = CommentEmojiResolver.resolveEmoji(body = body)

        // THEN
        result shouldBe "👍🏻"
    }

    @Test
    fun `resolveEmoji - should rebuild a ZWJ sequence from the id`() {
        // GIVEN
        val body = ":technologist:{: data-emoji-id='1f468-200d-1f4bb' }"

        // WHEN
        val result = CommentEmojiResolver.resolveEmoji(body = body)

        // THEN
        result shouldBe "👨‍💻"
    }

    @Test
    fun `resolveEmoji - should keep the shortcode when the id is not a valid code point`() {
        // GIVEN
        val body = ":broken:{: data-emoji-id='zz' }"

        // WHEN
        val result = CommentEmojiResolver.resolveEmoji(body = body)

        // THEN
        result shouldBe ":broken:"
    }

    @Test
    fun `resolveEmoji - should keep the shortcode when the code point is out of range`() {
        // GIVEN
        val body = ":broken:{: data-emoji-id='110000' }"

        // WHEN
        val result = CommentEmojiResolver.resolveEmoji(body = body)

        // THEN
        result shouldBe ":broken:"
    }

    @Test
    fun `resolveEmoji - should keep the shortcode when the attribute list is empty`() {
        // GIVEN
        val body = ":not_an_emoji:{: }"

        // WHEN
        val result = CommentEmojiResolver.resolveEmoji(body = body)

        // THEN
        result shouldBe ":not_an_emoji:"
    }

    @Test
    fun `resolveEmoji - should replace two emoji on one line and at the edges of the body`() {
        // GIVEN
        val body = ":a:{: data-emoji-text='🙂' } text :b:{: data-emoji-text='😠' }"

        // WHEN
        val result = CommentEmojiResolver.resolveEmoji(body = body)

        // THEN
        result shouldBe "🙂 text 😠"
    }

    @Test
    fun `resolveEmoji - should accept shortcodes with plus and minus signs`() {
        // GIVEN
        val body = ":+1:{: data-emoji-text='👍' } :-1:{: data-emoji-text='👎' }"

        // WHEN
        val result = CommentEmojiResolver.resolveEmoji(body = body)

        // THEN
        result shouldBe "👍 👎"
    }

    @Test
    fun `resolveEmoji - should keep emoji markup inside an inline code span`() {
        // GIVEN
        val body = "`:smile:{: data-emoji-text='🙂' }`"

        // WHEN
        val result = CommentEmojiResolver.resolveEmoji(body = body)

        // THEN
        result shouldBe body
    }

    @Test
    fun `resolveEmoji - should keep emoji markup inside a fenced code block`() {
        // GIVEN
        val body = "```\n:smile:{: data-emoji-text='🙂' }\n```"

        // WHEN
        val result = CommentEmojiResolver.resolveEmoji(body = body)

        // THEN
        result shouldBe body
    }

    @Test
    fun `resolveEmoji - should keep an unknown plain shortcode without an attribute list`() {
        // GIVEN
        val body = "type :not_an_emoji: here"

        // WHEN
        val result = CommentEmojiResolver.resolveEmoji(body = body)

        // THEN
        result shouldBe body
    }

    @Test
    fun `resolveEmoji - should replace a known plain shortcode without an attribute list`() {
        // GIVEN
        val body = "I thought it would be better for consistency, but can change it :thumbs_up:"

        // WHEN
        val result = CommentEmojiResolver.resolveEmoji(body = body)

        // THEN
        result shouldBe "I thought it would be better for consistency, but can change it 👍"
    }

    @Test
    fun `resolveEmoji - should use the shortcode table when the attribute list has no emoji`() {
        // GIVEN
        val body = ":smile:{: }"

        // WHEN
        val result = CommentEmojiResolver.resolveEmoji(body = body)

        // THEN
        result shouldBe "😄"
    }

    @Test
    fun `resolveEmoji - should keep a known plain shortcode inside an inline code span`() {
        // GIVEN
        val body = "`:thumbs_up:`"

        // WHEN
        val result = CommentEmojiResolver.resolveEmoji(body = body)

        // THEN
        result shouldBe body
    }

    @Test
    fun `resolveEmoji - should keep clock times unchanged`() {
        // GIVEN
        val body = "deployed at 10:30:45 today"

        // WHEN
        val result = CommentEmojiResolver.resolveEmoji(body = body)

        // THEN
        result shouldBe body
    }

    @Test
    fun `resolveEmoji - should return a body without emoji markup unchanged`() {
        // GIVEN
        val body = "no emoji here, just 10:30 text"

        // WHEN
        val result = CommentEmojiResolver.resolveEmoji(body = body)

        // THEN
        result shouldBe body
    }

    @Test
    fun `resolveEmoji - should leave no attribute fragment in the result`() {
        // GIVEN
        val body = ":smile:{: data-emoji-id='1f642' data-emoji-text='🙂' }"

        // WHEN
        val result = CommentEmojiResolver.resolveEmoji(body = body)

        // THEN
        result shouldNotContain "{:"
    }
}