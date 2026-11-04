package com.github.fnunezkanut.bbcloud.ui.main

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PersonNameMatchTest {
    @Test
    fun `rank - should rank a full-name prefix highest`() {
        // GIVEN
        val names = listOf("John Smith", "jsmith")

        // WHEN
        val rank = PersonNameMatch.rank(names = names, query = "john")

        // THEN
        rank shouldBe PersonNameMatch.RANK_NAME_PREFIX
    }

    @Test
    fun `rank - should rank a word prefix below a name prefix`() {
        // GIVEN
        val names = listOf("John Smith")

        // WHEN
        val rank = PersonNameMatch.rank(names = names, query = "smi")

        // THEN
        rank shouldBe PersonNameMatch.RANK_WORD_PREFIX
    }

    @Test
    fun `rank - should rank a substring match lowest`() {
        // GIVEN
        val names = listOf("Jonathan")

        // WHEN
        val rank = PersonNameMatch.rank(names = names, query = "nath")

        // THEN
        rank shouldBe PersonNameMatch.RANK_SUBSTRING
    }

    @Test
    fun `rank - should return null when no name matches`() {
        // GIVEN
        val names = listOf("John Smith")

        // WHEN
        val rank = PersonNameMatch.rank(names = names, query = "zzz")

        // THEN
        rank.shouldBeNull()
    }

    @Test
    fun `rank - should ignore a null name`() {
        // GIVEN
        val names = listOf("John Smith", null)

        // WHEN
        val rank = PersonNameMatch.rank(names = names, query = "john")

        // THEN
        rank shouldBe PersonNameMatch.RANK_NAME_PREFIX
    }

    @Test
    fun `rank - should fold diacritics`() {
        // GIVEN
        val names = listOf("José")

        // WHEN
        val rank = PersonNameMatch.rank(names = names, query = "jose")

        // THEN
        rank shouldBe PersonNameMatch.RANK_NAME_PREFIX
    }

    @Test
    fun `rank - should be case insensitive`() {
        // GIVEN
        val names = listOf("John Smith")

        // WHEN
        val rank = PersonNameMatch.rank(names = names, query = "JOHN")

        // THEN
        rank shouldBe PersonNameMatch.RANK_NAME_PREFIX
    }

    @Test
    fun `rank - should return null for a blank query`() {
        // GIVEN
        val names = listOf("John Smith")

        // WHEN
        val rank = PersonNameMatch.rank(names = names, query = "   ")

        // THEN
        rank.shouldBeNull()
    }
}