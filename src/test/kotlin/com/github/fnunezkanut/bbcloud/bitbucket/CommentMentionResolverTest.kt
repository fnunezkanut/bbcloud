package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class CommentMentionResolverTest {
    private val annaUuid = "{11111111-1111-1111-1111-111111111111}"
    private val annaKey = "11111111-1111-1111-1111-111111111111"
    private val benUuid = "{22222222-2222-2222-2222-222222222222}"
    private val benKey = "22222222-2222-2222-2222-222222222222"
    private val mentions = mapOf(annaUuid to "Anna", benUuid to "Ben")

    @Nested
    inner class ResolveMentionsTests {
        @Test
        fun `resolveMentions - should replace a known mention with the purple-styled name`() {
            // GIVEN
            val body = "Thanks @{$annaKey}!"

            // WHEN
            val result = CommentMentionResolver.resolveMentions(body = body, mentions = mentions)

            // THEN
            result shouldBe "Thanks **@Anna**!"
        }

        @Test
        fun `resolveMentions - should replace every occurrence of multiple and repeated mentions`() {
            // GIVEN
            val body = "@{$annaKey} and @{$benKey}, then @{$annaKey} again"

            // WHEN
            val result = CommentMentionResolver.resolveMentions(body = body, mentions = mentions)

            // THEN
            result shouldBe "**@Anna** and **@Ben**, then **@Anna** again"
        }

        @Test
        fun `resolveMentions - should match the key regardless of letter case or braces`() {
            // GIVEN
            val body = "@{ABCDEF-1}"
            val bracedKeys = mapOf("{abcdef-1}" to "Anna")
            val bareKeys = mapOf("abcdef-1" to "Anna")

            // WHEN
            val braced = CommentMentionResolver.resolveMentions(body = body, mentions = bracedKeys)
            val bare = CommentMentionResolver.resolveMentions(body = body, mentions = bareKeys)

            // THEN
            braced shouldBe "**@Anna**"
            bare shouldBe "**@Anna**"
        }

        @Test
        fun `resolveMentions - should leave a mention without a name verbatim`() {
            // GIVEN
            val body = "Hello @{33333333-3333-3333-3333-333333333333}"

            // WHEN
            val result = CommentMentionResolver.resolveMentions(body = body, mentions = mentions)

            // THEN
            result shouldBe body
        }

        @Test
        fun `resolveMentions - should return the body unchanged when there are no mentions to resolve`() {
            // GIVEN
            val body = "@{$annaKey} stays literal without a map"

            // WHEN
            val withoutMap = CommentMentionResolver.resolveMentions(body = body, mentions = emptyMap())
            val withoutToken = CommentMentionResolver.resolveMentions(body = "plain text", mentions = mentions)

            // THEN
            withoutMap shouldBe body
            withoutToken shouldBe "plain text"
        }

        @Test
        fun `resolveMentions - should escape Markdown characters in the display name`() {
            // GIVEN
            val names = mapOf(annaUuid to "Ana_*[x]")
            val body = "@{$annaKey}"

            // WHEN
            val result = CommentMentionResolver.resolveMentions(body = body, mentions = names)

            // THEN
            result shouldBe "**@Ana\\_\\*\\[x\\]**"
        }
    }

    @Nested
    inner class CodeRegionTests {
        @Test
        fun `resolveMentions - should leave a mention inside an inline code span literal`() {
            // GIVEN
            val body = "Use `@{$annaKey}` here, not @{$annaKey}"

            // WHEN
            val result = CommentMentionResolver.resolveMentions(body = body, mentions = mentions)

            // THEN
            result shouldBe "Use `@{$annaKey}` here, not **@Anna**"
        }

        @Test
        fun `resolveMentions - should leave mentions inside a fenced block literal and resolve those around it`() {
            // GIVEN
            val body = "Before @{$annaKey}\n```\n@{$annaKey}\n```\nAfter @{$benKey}"

            // WHEN
            val result = CommentMentionResolver.resolveMentions(body = body, mentions = mentions)

            // THEN
            result shouldBe "Before **@Anna**\n```\n@{$annaKey}\n```\nAfter **@Ben**"
        }

        @Test
        fun `resolveMentions - should treat a tilde fence the same way as a backtick fence`() {
            // GIVEN
            val body = "~~~\n@{$annaKey}\n~~~"

            // WHEN
            val result = CommentMentionResolver.resolveMentions(body = body, mentions = mentions)

            // THEN
            result shouldBe body
        }

        @Test
        fun `resolveMentions - should keep an unclosed fence running to the end of the body literal`() {
            // GIVEN
            val body = "```\n@{$annaKey}"

            // WHEN
            val result = CommentMentionResolver.resolveMentions(body = body, mentions = mentions)

            // THEN
            result shouldBe body
        }

        @Test
        fun `resolveMentions - should match a code span only with a closing run of the same length`() {
            // GIVEN
            val body = "``code @{$annaKey} ` inside`` and ` @{$benKey}"

            // WHEN
            val result = CommentMentionResolver.resolveMentions(body = body, mentions = mentions)

            // THEN
            result shouldBe "``code @{$annaKey} ` inside`` and ` **@Ben**"
        }

        @Test
        fun `resolveMentions - should resolve a mention after a backslash-escaped backtick`() {
            // GIVEN
            val body = "\\`@{$annaKey}"

            // WHEN
            val result = CommentMentionResolver.resolveMentions(body = body, mentions = mentions)

            // THEN
            result shouldBe "\\`**@Anna**"
        }
    }

    @Nested
    inner class MentionTokensTests {
        @Test
        fun `mentionTokensIn - should return the raw keys of mentions outside code only`() {
            // GIVEN
            val body = "@{$annaKey} and `@{$benKey}`\n```\n@{33333333-3333-3333-3333-333333333333}\n```"

            // WHEN
            val result = CommentMentionResolver.mentionTokensIn(body = body)

            // THEN
            result shouldBe setOf(annaKey)
        }

        @Test
        fun `mentionTokensIn - should return an empty set for a body without mentions`() {
            // GIVEN
            val body = "No one is mentioned here"

            // WHEN
            val result = CommentMentionResolver.mentionTokensIn(body = body)

            // THEN
            result shouldBe emptySet()
        }
    }

    @Nested
    inner class NormalizeKeyTests {
        @Test
        fun `normalizeKey - should strip one pair of braces, trim, and lowercase`() {
            // WHEN
            val braced = CommentMentionResolver.normalizeKey(rawKey = " {ABC-1} ")
            val nested = CommentMentionResolver.normalizeKey(rawKey = "{{x}}")

            // THEN
            braced shouldBe "abc-1"
            nested shouldBe "{x}"
        }
    }

    @Nested
    inner class LookupSelectorTests {
        @Test
        fun `lookupSelector - should wrap a bare canonical UUID in braces`() {
            // GIVEN
            val rawKey = "11111111-1111-1111-1111-111111111111"

            // WHEN
            val selector = CommentMentionResolver.lookupSelector(rawKey = rawKey)

            // THEN
            selector shouldBe "{11111111-1111-1111-1111-111111111111}"
        }

        @Test
        fun `lookupSelector - should keep an already braced UUID to one pair of braces`() {
            // GIVEN
            val rawKey = "{11111111-1111-1111-1111-111111111111}"

            // WHEN
            val selector = CommentMentionResolver.lookupSelector(rawKey = rawKey)

            // THEN
            selector shouldBe "{11111111-1111-1111-1111-111111111111}"
        }

        @Test
        fun `lookupSelector - should return an Atlassian account id without braces`() {
            // GIVEN
            val rawKey = "712020:00000000-0000-0000-0000-000000000000"

            // WHEN
            val selector = CommentMentionResolver.lookupSelector(rawKey = rawKey)

            // THEN
            selector shouldBe "712020:00000000-0000-0000-0000-000000000000"
        }

        @Test
        fun `lookupSelector - should return a legacy numeric prefixed account id without braces`() {
            // GIVEN
            val rawKey = "557058:0000aaaa-bbbb-cccc-dddd-eeee0000ffff"

            // WHEN
            val selector = CommentMentionResolver.lookupSelector(rawKey = rawKey)

            // THEN
            selector shouldBe "557058:0000aaaa-bbbb-cccc-dddd-eeee0000ffff"
        }

        @Test
        fun `lookupSelector - should normalize a mixed case UUID before wrapping it`() {
            // GIVEN
            val rawKey = "ABCDEFAB-CDEF-ABCD-EFAB-CDEFABCDEFAB"

            // WHEN
            val selector = CommentMentionResolver.lookupSelector(rawKey = rawKey)

            // THEN
            selector shouldBe "{abcdefab-cdef-abcd-efab-cdefabcdefab}"
        }
    }
}