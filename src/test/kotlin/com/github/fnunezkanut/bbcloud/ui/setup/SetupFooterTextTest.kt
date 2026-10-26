package com.github.fnunezkanut.bbcloud.ui.setup

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class SetupFooterTextTest {
    private val disclaimer = BbCloudBundle.message("setup.disclaimer")
    private val separator = "\u2022"

    @Test
    fun `build - should include the resolved version`() {
        // GIVEN
        val version = "0.1.3"

        // WHEN
        val result = SetupFooterText.build(version)

        // THEN
        assertSoftly {
            result shouldContain "Bitbucket Cloud PR review plugin: $version"
            result shouldContain "$separator $disclaimer"
        }
    }

    @Test
    fun `build - should omit the version suffix when it is unknown`() {
        // GIVEN
        val version = null

        // WHEN
        val result = SetupFooterText.build(version)

        // THEN
        assertSoftly {
            // The ": <version>" suffix is dropped; the disclaimer keeps its own colon.
            result shouldNotContain "Bitbucket Cloud PR review plugin:"
            result shouldNotContain "Unknown"
            result shouldContain "Bitbucket Cloud PR review plugin $separator $disclaimer"
        }
    }

    @Test
    fun `build - should omit the version suffix when it is blank`() {
        // GIVEN
        val version = "   "

        // WHEN
        val result = SetupFooterText.build(version)

        // THEN
        result shouldContain "Bitbucket Cloud PR review plugin $separator $disclaimer"
    }
}