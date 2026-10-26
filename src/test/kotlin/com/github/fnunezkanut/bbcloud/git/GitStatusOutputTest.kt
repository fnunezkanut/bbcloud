package com.github.fnunezkanut.bbcloud.git

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class GitStatusOutputTest {
    @Test
    fun `hasUncommittedChanges - should return false for empty output`() {
        // GIVEN
        val output = ""

        // WHEN
        val result = GitStatusOutput.hasUncommittedChanges(output)

        // THEN
        result shouldBe false
    }

    @Test
    fun `hasUncommittedChanges - should return false for blank and whitespace-only lines`() {
        // GIVEN
        val output = "\n   \n\t\n"

        // WHEN
        val result = GitStatusOutput.hasUncommittedChanges(output)

        // THEN
        result shouldBe false
    }

    @Test
    fun `hasUncommittedChanges - should return true for a modified tracked file`() {
        // GIVEN
        val output = " M src/Foo.kt"

        // WHEN
        val result = GitStatusOutput.hasUncommittedChanges(output)

        // THEN
        result shouldBe true
    }

    @Test
    fun `hasUncommittedChanges - should return true for a staged file`() {
        // GIVEN
        val output = "M  src/Foo.kt"

        // WHEN
        val result = GitStatusOutput.hasUncommittedChanges(output)

        // THEN
        result shouldBe true
    }

    @Test
    fun `hasUncommittedChanges - should return true for an untracked file`() {
        // GIVEN
        val output = "?? notes.txt"

        // WHEN
        val result = GitStatusOutput.hasUncommittedChanges(output)

        // THEN
        result shouldBe true
    }

    @Test
    fun `hasUncommittedChanges - should return true when output ends with a newline`() {
        // GIVEN
        val output = "?? notes.txt\n"

        // WHEN
        val result = GitStatusOutput.hasUncommittedChanges(output)

        // THEN
        result shouldBe true
    }
}