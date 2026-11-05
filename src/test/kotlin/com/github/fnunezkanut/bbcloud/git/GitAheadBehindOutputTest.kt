package com.github.fnunezkanut.bbcloud.git

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class GitAheadBehindOutputTest {
    @Test
    fun `parse - should read tab separated counts`() {
        GitAheadBehindOutput.parse("2\t5\n") shouldBe AheadBehind(ahead = 2, behind = 5)
    }

    @Test
    fun `parse - should read space separated counts`() {
        GitAheadBehindOutput.parse("0 3") shouldBe AheadBehind(ahead = 0, behind = 3)
    }

    @Test
    fun `parse - should ignore the echoed command line`() {
        val output = "$ git rev-list --left-right --count a...b\n1\t0"

        GitAheadBehindOutput.parse(output) shouldBe AheadBehind(ahead = 1, behind = 0)
    }

    @Test
    fun `parse - should return null for blank output`() {
        GitAheadBehindOutput.parse("  \n ").shouldBeNull()
    }

    @Test
    fun `parse - should return null for malformed output`() {
        GitAheadBehindOutput.parse("abc def").shouldBeNull()
        GitAheadBehindOutput.parse("1").shouldBeNull()
        GitAheadBehindOutput.parse("1 2 3").shouldBeNull()
    }

    @Test
    fun `parse - should return null for negative counts`() {
        GitAheadBehindOutput.parse("-1\t2").shouldBeNull()
    }
}