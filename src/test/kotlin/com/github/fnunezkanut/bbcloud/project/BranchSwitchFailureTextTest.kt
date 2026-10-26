package com.github.fnunezkanut.bbcloud.project

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BranchSwitchFailureTextTest {
    @Test
    fun `a blank input returns an empty string`() {
        // GIVEN
        val input = "   \n\t  \n"

        // WHEN
        val result = BranchSwitchFailureText.trimmedOutput(output = input)

        // THEN
        result shouldBe ""
    }

    @Test
    fun `an input shorter than the limit is returned as is after trimming`() {
        // GIVEN
        val input = "  error: short message  "
        val maxLength = BranchSwitchFailureText.DEFAULT_MAX_LENGTH

        // WHEN
        val result = BranchSwitchFailureText.trimmedOutput(output = input, maxLength = maxLength)

        // THEN
        result shouldBe "error: short message"
    }

    @Test
    fun `an input exactly at the limit is returned as is`() {
        // GIVEN
        val input = "a".repeat(BranchSwitchFailureText.DEFAULT_MAX_LENGTH)
        val maxLength = BranchSwitchFailureText.DEFAULT_MAX_LENGTH

        // WHEN
        val result = BranchSwitchFailureText.trimmedOutput(output = input, maxLength = maxLength)

        // THEN
        result shouldBe input
    }

    @Test
    fun `an input one character over the limit is truncated and suffixed with ellipsis`() {
        // GIVEN
        val input = "a".repeat(BranchSwitchFailureText.DEFAULT_MAX_LENGTH + 1)
        val maxLength = BranchSwitchFailureText.DEFAULT_MAX_LENGTH

        // WHEN
        val result = BranchSwitchFailureText.trimmedOutput(output = input, maxLength = maxLength)

        // THEN
        result shouldBe "${"a".repeat(maxLength)}..."
        result.length shouldBe maxLength + 3
    }

    @Test
    fun `surrounding whitespace is trimmed before measuring against the limit`() {
        // GIVEN
        val core = "b".repeat(BranchSwitchFailureText.DEFAULT_MAX_LENGTH)
        val input = "  $core  "
        val maxLength = BranchSwitchFailureText.DEFAULT_MAX_LENGTH

        // WHEN
        val result = BranchSwitchFailureText.trimmedOutput(output = input, maxLength = maxLength)

        // THEN
        result shouldBe core
    }
}