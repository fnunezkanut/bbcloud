package com.github.fnunezkanut.bbcloud.review

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BbCloudKnownUserRowTest {
    @Test
    fun `encode then decode - should round-trip every field including nulls`() {
        // GIVEN a row with null uuid, account id, nickname, and avatar
        val row = BbCloudKnownUserRow(
            uuid = null,
            accountId = null,
            displayName = "Anna",
            nickname = null,
            avatarUrl = null,
            workspaces = setOf("workspace"),
            lastSeen = 7
        )

        // WHEN
        val decoded = BbCloudKnownUserRow.decode(row = row.encode())

        // THEN
        decoded shouldBe row
    }

    @Test
    fun `encode then decode - should round-trip a multi-workspace set`() {
        // GIVEN
        val row = BbCloudKnownUserRow(
            uuid = "{11111111-1111-1111-1111-111111111111}",
            accountId = "712020:aaaa-bbbb",
            displayName = "Anna Lee",
            nickname = "alee",
            avatarUrl = "https://avatar/a",
            workspaces = setOf("workspace", "other-workspace"),
            lastSeen = 42
        )

        // WHEN
        val decoded = BbCloudKnownUserRow.decode(row = row.encode())

        // THEN
        decoded shouldBe row
    }

    @Test
    fun `encode then decode - should round-trip a name with spaces and punctuation`() {
        // GIVEN
        val row = BbCloudKnownUserRow(
            uuid = "{u}",
            accountId = null,
            displayName = "Anna-Maria O'Brien, Jr.",
            nickname = null,
            avatarUrl = null,
            workspaces = setOf("workspace"),
            lastSeen = 1
        )

        // WHEN
        val decoded = BbCloudKnownUserRow.decode(row = row.encode())

        // THEN
        decoded?.displayName shouldBe "Anna-Maria O'Brien, Jr."
    }

    @Test
    fun `decode - should return null for the wrong field count`() {
        // GIVEN a string with too few fields
        val malformed = "a\u001Fb\u001Fc"

        // WHEN
        val decoded = BbCloudKnownUserRow.decode(row = malformed)

        // THEN
        decoded.shouldBeNull()
    }

    @Test
    fun `decode - should return null for a blank display name`() {
        // GIVEN a row whose display-name field is empty
        val row = BbCloudKnownUserRow(
            uuid = "{u}",
            accountId = null,
            displayName = "placeholder",
            nickname = null,
            avatarUrl = null,
            workspaces = setOf("workspace"),
            lastSeen = 1
        ).encode().replace("placeholder", "")

        // WHEN
        val decoded = BbCloudKnownUserRow.decode(row = row)

        // THEN
        decoded.shouldBeNull()
    }

    @Test
    fun `decode - should return null for an unparseable lastSeen`() {
        // GIVEN a row whose last-seen field is not a number
        val row = BbCloudKnownUserRow(
            uuid = "{u}",
            accountId = null,
            displayName = "Anna",
            nickname = null,
            avatarUrl = null,
            workspaces = setOf("workspace"),
            lastSeen = 1
        ).encode().removeSuffix("1") + "xyz"

        // WHEN
        val decoded = BbCloudKnownUserRow.decode(row = row)

        // THEN
        decoded.shouldBeNull()
    }

    @Test
    fun `separators - should not collide with normal field values`() {
        // GIVEN a row of ordinary values
        val encoded = BbCloudKnownUserRow(
            uuid = "{11111111-1111-1111-1111-111111111111}",
            accountId = "712020:aaaa-bbbb",
            displayName = "Anna Lee",
            nickname = "alee",
            avatarUrl = "https://avatar/a",
            workspaces = setOf("workspace"),
            lastSeen = 1
        ).encode()

        // THEN the unit separator appears exactly between the 7 fields, so no value contained one
        encoded.count { character -> character == '\u001F' } shouldBe 6
    }
}