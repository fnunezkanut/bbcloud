package com.github.fnunezkanut.bbcloud.ui.main

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class ResolvedThreadExpansionTest {
    @Test
    fun `get - should be collapsed by default`() {
        // GIVEN
        val expansion = ResolvedThreadExpansion()

        // WHEN
        val result = expansion[1L]

        // THEN
        result shouldBe false
    }

    @Test
    fun `set - should expand only the toggled thread`() {
        // GIVEN
        val expansion = ResolvedThreadExpansion()

        // WHEN
        expansion[1L] = true

        // THEN
        assertSoftly {
            expansion[1L] shouldBe true
            expansion[2L] shouldBe false
        }
    }

    @Test
    fun `set - should collapse an expanded thread again`() {
        // GIVEN
        val expansion = ResolvedThreadExpansion()
        expansion[1L] = true

        // WHEN
        expansion[1L] = false

        // THEN
        expansion[1L] shouldBe false
    }
}