package com.github.fnunezkanut.bbcloud.validation

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BitbucketWorkspaceValidatorTest {
    @Test
    fun `validate - should reject a blank slug`(): Unit =
        run {
            val result = BitbucketWorkspaceValidator.validate("   ")

            result.isFailure shouldBe true
            result.exceptionOrNull().shouldBeInstanceOf<BitbucketWorkspaceValidator.ValidationException.Empty>()
        }

    @Test
    fun `validate - should trim and accept a valid slug`(): Unit =
        run {
            val result = BitbucketWorkspaceValidator.validate("  my-workspace.1_x  ")

            result shouldBe Result.success("my-workspace.1_x")
        }

    @Test
    fun `validate - should reject slugs with invalid characters`(): Unit =
        run {
            listOf("https://bitbucket.org/my-workspace/", "my workspace", "a/b", "wörkspace").forEach { raw ->
                val result = BitbucketWorkspaceValidator.validate(raw)

                assertSoftly(raw) {
                    result.isFailure shouldBe true
                    result.exceptionOrNull().shouldBeInstanceOf<BitbucketWorkspaceValidator.ValidationException.InvalidFormat>()
                }
            }
        }
}