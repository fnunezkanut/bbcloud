package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidateTier
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class CommentMentionTextTest {
    @Nested
    inner class Detector {
        @Test
        fun `find - should detect an at sign at the start`() {
            // WHEN
            val query = find(text = "@jo", caret = 3)

            // THEN
            query.shouldNotBeNull()
            query.anchorStart shouldBe 0
            query.query shouldBe "jo"
        }

        @Test
        fun `find - should detect after a space`() {
            // WHEN
            val query = find(text = "hi @jo", caret = 6)

            // THEN
            query?.query shouldBe "jo"
        }

        @Test
        fun `find - should detect after an opening parenthesis`() {
            // WHEN
            val query = find(text = "(@jo", caret = 4)

            // THEN
            query?.query shouldBe "jo"
        }

        @Test
        fun `find - should reject an email`() {
            // WHEN
            val query = find(text = "name@example", caret = 12)

            // THEN
            query.shouldBeNull()
        }

        @Test
        fun `find - should reject inside an inline code span`() {
            // WHEN
            val query = find(text = "`@jo`", caret = 3)

            // THEN
            query.shouldBeNull()
        }

        @Test
        fun `find - should reject inside a fenced code block`() {
            // GIVEN a fenced block opened on the first line
            val text = "```\n@jo"

            // WHEN
            val query = find(text = text, caret = text.length)

            // THEN
            query.shouldBeNull()
        }

        @Test
        fun `find - should reject an existing token`() {
            // WHEN the caret sits inside an existing @{ token
            val query = find(text = "@{712020", caret = 8)

            // THEN
            query.shouldBeNull()
        }

        @Test
        fun `find - should stop at a newline`() {
            // WHEN the caret is on the line after an at sign
            val query = find(text = "@jo\nmore", caret = 8)

            // THEN
            query.shouldBeNull()
        }

        @Test
        fun `find - should allow a single inner space`() {
            // WHEN
            val query = find(text = "@john sm", caret = 8)

            // THEN
            query?.query shouldBe "john sm"
        }

        @Test
        fun `find - should reject a double space`() {
            // WHEN
            val query = find(text = "@john  sm", caret = 9)

            // THEN
            query.shouldBeNull()
        }

        @Test
        fun `find - should reject a non-collapsed selection`() {
            // WHEN start and end differ
            val query = MentionQueryDetector.find(text = "@jo", selectionStart = 1, selectionEnd = 3)

            // THEN
            query.shouldBeNull()
        }

        @Test
        fun `find - should respect the maximum query length`() {
            // GIVEN an at sign too far back
            val text = "@" + "a".repeat(45)

            // WHEN
            val query = find(text = text, caret = text.length)

            // THEN the at sign is beyond the walk-back window
            query.shouldBeNull()
        }

        @Test
        fun `find - should detect a query in the middle of the text`() {
            // WHEN the caret is in the middle, right after a query
            val query = find(text = "hi @jo there", caret = 6)

            // THEN
            query?.query shouldBe "jo"
        }

        private fun find(text: String, caret: Int): MentionQuery? {
            return MentionQueryDetector.find(text = text, selectionStart = caret, selectionEnd = caret)
        }
    }

    @Nested
    inner class Ranker {
        @Test
        fun `rank - should let the tier order win over the match rank`() {
            // GIVEN a workspace exact prefix and a pull-request substring, same query
            val workspace = candidate(uuid = "{w}", name = "John", tier = MentionCandidateTier.Workspace)
            val pullRequest = candidate(uuid = "{p}", name = "Bojohn", tier = MentionCandidateTier.PullRequest)

            // WHEN
            val ranked = MentionCandidateRanker.rank(query = "john", local = listOf(pullRequest), workspace = listOf(workspace), excludedUuid = null)

            // THEN the pull-request tier comes first despite the weaker match
            ranked.map { it.displayName } shouldContainExactly listOf("Bojohn", "John")
        }

        @Test
        fun `rank - should de-duplicate keeping the pull request tier`() {
            // GIVEN the same person in both tiers
            val pullRequest = candidate(uuid = "{a}", name = "Anna", tier = MentionCandidateTier.PullRequest)
            val workspace = candidate(uuid = "{a}", name = "Anna", tier = MentionCandidateTier.Workspace)

            // WHEN
            val ranked = MentionCandidateRanker.rank(query = "", local = listOf(pullRequest), workspace = listOf(workspace), excludedUuid = null)

            // THEN the single kept entry is the pull-request tier one
            ranked.single().tier shouldBe MentionCandidateTier.PullRequest
        }

        @Test
        fun `rank - should exclude the current user`() {
            // GIVEN
            val me = candidate(uuid = "{me}", name = "Me")
            val other = candidate(uuid = "{other}", name = "Other")

            // WHEN
            val ranked = MentionCandidateRanker.rank(query = "", local = listOf(me, other), workspace = emptyList(), excludedUuid = "{ME}")

            // THEN
            ranked.map { it.displayName } shouldContainExactly listOf("Other")
        }

        @Test
        fun `rank - should keep the input order for a blank query`() {
            // GIVEN
            val a = candidate(uuid = "{a}", name = "Anna")
            val b = candidate(uuid = "{b}", name = "Ben")

            // WHEN
            val ranked = MentionCandidateRanker.rank(query = "   ", local = listOf(b, a), workspace = emptyList(), excludedUuid = null)

            // THEN
            ranked.map { it.displayName } shouldContainExactly listOf("Ben", "Anna")
        }

        @Test
        fun `rank - should honor the limit`() {
            // GIVEN more candidates than the limit
            val candidates = (1..5).map { index -> candidate(uuid = "{$index}", name = "User $index") }

            // WHEN
            val ranked = MentionCandidateRanker.rank(query = "", local = candidates, workspace = emptyList(), excludedUuid = null, limit = 2)

            // THEN
            ranked.size shouldBe 2
        }

        @Test
        fun `rank - should match a nickname`() {
            // GIVEN a candidate whose display name does not match but the nickname does
            val candidate =
                MentionCandidate(
                    uuid = "{a}",
                    accountId = null,
                    displayName = "Anna Lee",
                    nickname = "bolt",
                    avatarUrl = null,
                    tier = MentionCandidateTier.PullRequest
                )

            // WHEN
            val ranked = MentionCandidateRanker.rank(query = "bolt", local = listOf(candidate), workspace = emptyList(), excludedUuid = null)

            // THEN
            ranked.single().displayName shouldBe "Anna Lee"
        }

        @Test
        fun `rank - should fold diacritics`() {
            // GIVEN
            val candidate = candidate(uuid = "{a}", name = "José")

            // WHEN
            val ranked = MentionCandidateRanker.rank(query = "jose", local = listOf(candidate), workspace = emptyList(), excludedUuid = null)

            // THEN
            ranked.single().displayName shouldBe "José"
        }

        private fun candidate(uuid: String, name: String, tier: MentionCandidateTier = MentionCandidateTier.PullRequest): MentionCandidate {
            return MentionCandidate(uuid = uuid, accountId = null, displayName = name, nickname = null, avatarUrl = null, tier = tier)
        }
    }

    @Nested
    inner class InsertMention {
        @Test
        fun `insertMention - should replace the query with the token`() {
            // GIVEN
            val text = "hi @jo"
            val query = MentionQuery(anchorStart = 3, caret = 6, query = "jo")

            // WHEN
            val transform = insertMention(text = text, query = query, mentionKey = "712020:aaaa")

            // THEN
            transform.text shouldBe "hi @{712020:aaaa} "
        }

        @Test
        fun `insertMention - should not add a double space when a space already follows`() {
            // GIVEN the caret is followed by a space
            val text = "hi @jo there"
            val query = MentionQuery(anchorStart = 3, caret = 6, query = "jo")

            // WHEN
            val transform = insertMention(text = text, query = query, mentionKey = "712020:aaaa")

            // THEN no extra space is inserted
            transform.text shouldBe "hi @{712020:aaaa} there"
        }

        @Test
        fun `insertMention - should place a collapsed caret after the inserted text`() {
            // GIVEN
            val text = "@jo"
            val query = MentionQuery(anchorStart = 0, caret = 3, query = "jo")

            // WHEN
            val transform = insertMention(text = text, query = query, mentionKey = "712020:aaaa")

            // THEN the caret sits after the token and trailing space
            transform.selectionStart shouldBe transform.selectionEnd
            transform.selectionStart shouldBe "@{712020:aaaa} ".length
        }
    }

    @Nested
    inner class DisplayReplacements {
        @Test
        fun `mentionDisplayReplacements - should replace a known token`() {
            // GIVEN
            val text = "Hi @{712020:aaaa}!"
            val names = mapOf("712020:aaaa" to "Anna")

            // WHEN
            val replacements = mentionDisplayReplacements(text = text, names = names)

            // THEN
            replacements.single().let { replacement ->
                replacement.display shouldBe "@Anna"
                text.substring(replacement.start, replacement.end) shouldBe "@{712020:aaaa}"
            }
        }

        @Test
        fun `mentionDisplayReplacements - should leave an unknown token alone`() {
            // GIVEN a token whose key is not named
            val text = "Hi @{712020:bbbb}!"
            val names = mapOf("712020:aaaa" to "Anna")

            // WHEN
            val replacements = mentionDisplayReplacements(text = text, names = names)

            // THEN
            replacements shouldBe emptyList()
        }

        @Test
        fun `mentionDisplayReplacements - should leave a token inside code alone`() {
            // GIVEN a token inside an inline code span
            val text = "`@{712020:aaaa}`"
            val names = mapOf("712020:aaaa" to "Anna")

            // WHEN
            val replacements = mentionDisplayReplacements(text = text, names = names)

            // THEN
            replacements shouldBe emptyList()
        }

        @Test
        fun `mentionDisplayReplacements - should give correct offsets for multiple tokens`() {
            // GIVEN two known tokens
            val text = "@{712020:aaaa} and @{712020:bbbb}"
            val names = mapOf("712020:aaaa" to "Anna", "712020:bbbb" to "Ben")

            // WHEN
            val replacements = mentionDisplayReplacements(text = text, names = names)

            // THEN each replacement's range covers exactly its token, in order
            replacements.map { replacement -> text.substring(replacement.start, replacement.end) } shouldContainExactly
                listOf("@{712020:aaaa}", "@{712020:bbbb}")
            replacements.map { it.display } shouldContainExactly listOf("@Anna", "@Ben")
        }
    }
}