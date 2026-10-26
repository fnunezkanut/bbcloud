package com.github.fnunezkanut.bbcloud.git

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class GitCommandOutputTest {
    @Test
    fun `format - should join command line, output and error output on separate lines`() {
        // GIVEN
        val commandLine = "git checkout ABC-663_test"
        val output = "some output\n"
        val errorOutput = "error: pathspec 'ABC-663_test' did not match any file(s) known to git"

        // WHEN
        val result = GitCommandOutput.format(
            commandLine = commandLine,
            output = output,
            errorOutput = errorOutput
        )

        // THEN
        result shouldBe "$ git checkout ABC-663_test\nsome output\n" +
            "error: pathspec 'ABC-663_test' did not match any file(s) known to git"
    }

    @Test
    fun `format - should omit empty output sections`() {
        // GIVEN
        val commandLine = "git fetch origin ABC-663_test"

        // WHEN
        val result = GitCommandOutput.format(
            commandLine = commandLine,
            output = "",
            errorOutput = "fatal: couldn't find remote ref ABC-663_test"
        )

        // THEN
        result shouldBe "$ git fetch origin ABC-663_test\nfatal: couldn't find remote ref ABC-663_test"
    }
}