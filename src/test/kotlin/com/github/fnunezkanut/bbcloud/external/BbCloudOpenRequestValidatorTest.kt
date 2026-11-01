package com.github.fnunezkanut.bbcloud.external

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BbCloudOpenRequestValidatorTest {
    @Nested
    inner class Accepted {
        @Test
        fun `validate - should accept a well-formed body for the configured workspace`() {
            // GIVEN
            val body = """{"workspace":"acme","repo":"my-repo.name_1","pr":42}"""

            // WHEN
            val result = BbCloudOpenRequestValidator.validate(body = body, configuredWorkspace = "acme")

            // THEN
            result shouldBe BbCloudOpenRequestValidation.Valid(
                request = BbCloudOpenRequest(workspace = "acme", repoSlug = "my-repo.name_1", prNumber = 42L)
            )
        }

        @Test
        fun `validate - should ignore unknown fields`() {
            // GIVEN
            val body = """{"workspace":"acme","repo":"repo","pr":7,"extra":{"nested":true}}"""

            // WHEN
            val result = BbCloudOpenRequestValidator.validate(body = body, configuredWorkspace = "acme")

            // THEN
            result shouldBe BbCloudOpenRequestValidation.Valid(
                request = BbCloudOpenRequest(workspace = "acme", repoSlug = "repo", prNumber = 7L)
            )
        }
    }

    @Nested
    inner class Rejected {
        @Test
        fun `validate - should reject a body that is not a JSON object`() {
            // GIVEN
            val bodies = listOf("", "not json", "[1,2,3]", "\"text\"")

            // WHEN
            val results = bodies.map { body -> BbCloudOpenRequestValidator.validate(body = body, configuredWorkspace = "acme") }

            // THEN
            results.forEach { result ->
                result shouldBe BbCloudOpenRequestValidation.Rejected(message = BbCloudOpenRequestValidator.INVALID_REQUEST)
            }
        }

        @Test
        fun `validate - should reject a workspace that differs from the configured one`() {
            // GIVEN
            val body = """{"workspace":"other","repo":"repo","pr":1}"""

            // WHEN
            val result = BbCloudOpenRequestValidator.validate(body = body, configuredWorkspace = "acme")

            // THEN
            result shouldBe BbCloudOpenRequestValidation.Rejected(message = BbCloudOpenRequestValidator.WRONG_WORKSPACE)
        }

        @Test
        fun `validate - should reject every request while no workspace is configured`() {
            // GIVEN
            val body = """{"workspace":"acme","repo":"repo","pr":1}"""

            // WHEN
            val unset = BbCloudOpenRequestValidator.validate(body = body, configuredWorkspace = null)
            val blank = BbCloudOpenRequestValidator.validate(body = body, configuredWorkspace = "  ")

            // THEN
            unset shouldBe BbCloudOpenRequestValidation.Rejected(message = BbCloudOpenRequestValidator.WRONG_WORKSPACE)
            blank shouldBe BbCloudOpenRequestValidation.Rejected(message = BbCloudOpenRequestValidator.WRONG_WORKSPACE)
        }

        @Test
        fun `validate - should reject a repository slug that is not a single safe segment`() {
            // GIVEN
            val repos = listOf("../etc", "a/b", "a b", "", ".", "..", "repo;rm")

            // WHEN
            val results = repos.map { repo ->
                BbCloudOpenRequestValidator.validate(
                    body = """{"workspace":"acme","repo":"$repo","pr":1}""",
                    configuredWorkspace = "acme"
                )
            }

            // THEN
            results.forEach { result ->
                result shouldBe BbCloudOpenRequestValidation.Rejected(message = BbCloudOpenRequestValidator.INVALID_REPO)
            }
        }

        @Test
        fun `validate - should reject a missing or non-string repository`() {
            // GIVEN
            val bodies = listOf(
                """{"workspace":"acme","pr":1}""",
                """{"workspace":"acme","repo":12,"pr":1}"""
            )

            // WHEN
            val results = bodies.map { body -> BbCloudOpenRequestValidator.validate(body = body, configuredWorkspace = "acme") }

            // THEN
            results.forEach { result ->
                result shouldBe BbCloudOpenRequestValidation.Rejected(message = BbCloudOpenRequestValidator.INVALID_REPO)
            }
        }

        @Test
        fun `validate - should reject a pull request number that is not a positive whole number`() {
            // GIVEN
            val prValues = listOf("\"42\"", "0", "-3", "1.5", "true", "null", "9223372036854775808")

            // WHEN
            val results = prValues.map { pr ->
                BbCloudOpenRequestValidator.validate(
                    body = """{"workspace":"acme","repo":"repo","pr":$pr}""",
                    configuredWorkspace = "acme"
                )
            }

            // THEN
            results.forEach { result ->
                result shouldBe BbCloudOpenRequestValidation.Rejected(message = BbCloudOpenRequestValidator.INVALID_PR)
            }
        }

        @Test
        fun `validate - should reject a missing pull request number`() {
            // GIVEN
            val body = """{"workspace":"acme","repo":"repo"}"""

            // WHEN
            val result = BbCloudOpenRequestValidator.validate(body = body, configuredWorkspace = "acme")

            // THEN
            result shouldBe BbCloudOpenRequestValidation.Rejected(message = BbCloudOpenRequestValidator.INVALID_PR)
        }
    }
}