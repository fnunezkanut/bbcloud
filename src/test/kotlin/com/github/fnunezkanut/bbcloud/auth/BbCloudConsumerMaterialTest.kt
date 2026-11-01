package com.github.fnunezkanut.bbcloud.auth

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.security.MessageDigest

@Tag("unit")
class BbCloudConsumerMaterialTest {
    @Test
    fun `primary - should match expected digest`() {
        // GIVEN
        val value = BbCloudConsumerMaterial.primary()

        // WHEN
        val digest = sha256Hex(value)

        // THEN
        assertSoftly {
            digest shouldBe EXPECTED_PRIMARY_SHA256
            value.length shouldBe 32
        }
    }

    @Test
    fun `secondary - should match expected digest`() {
        // GIVEN
        val value = BbCloudConsumerMaterial.secondary()

        // WHEN
        val digest = sha256Hex(value)

        // THEN
        assertSoftly {
            digest shouldBe EXPECTED_SECONDARY_SHA256
            value.length shouldBe 76
        }
    }

    @Test
    fun `primaryField - should match expected name`() {
        // GIVEN
        val expected = listOf("client", "id").joinToString(separator = "_")

        // WHEN
        val value = BbCloudConsumerMaterial.primaryField()

        // THEN
        value shouldBe expected
    }

    @Test
    fun `decode - should invert encode`() {
        // GIVEN
        val samples = listOf("sample-value_123", "")

        // WHEN / THEN
        assertSoftly {
            samples.forEach { sample ->
                BbCloudConsumerMaterial.decode(BbCloudConsumerMaterial.encode(sample)) shouldBe sample
            }
        }
    }

    @Test
    fun `primary and secondary - should only contain url safe characters`() {
        // GIVEN
        val pattern = Regex("^[A-Za-z0-9_-]+$")

        // WHEN
        val primary = BbCloudConsumerMaterial.primary()
        val secondary = BbCloudConsumerMaterial.secondary()

        // THEN
        assertSoftly {
            pattern.matches(primary) shouldBe true
            pattern.matches(secondary) shouldBe true
        }
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    companion object {
        private const val EXPECTED_PRIMARY_SHA256 = "cb894e7142877da327eef90ae0149882398391a757d898fd4faf0bafe12b0c38"
        private const val EXPECTED_SECONDARY_SHA256 = "0487706967ada88d1d832aca55427b78ddf3595e6b4220aa2eb783c7fd825a61"
    }
}