package com.github.fnunezkanut.bbcloud.validation

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BitbucketRepositorySlugValidatorTest {
    @Test
    fun `validate - should accept a simple slug`() {
        BitbucketRepositorySlugValidator.validate("sample-repo").isSuccess shouldBe true
    }

    @Test
    fun `validate - should accept slugs with dots, dashes, and underscores`() {
        BitbucketRepositorySlugValidator.validate("my-repo.1_x").isSuccess shouldBe true
    }

    @Test
    fun `validate - should accept uppercase slugs`() {
        BitbucketRepositorySlugValidator.validate("MyRepo").isSuccess shouldBe true
    }

    @Test
    fun `validate - should reject an empty string`() {
        val result = BitbucketRepositorySlugValidator.validate("")
        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<BitbucketRepositorySlugValidator.SlugValidationException.Empty>()
    }

    @Test
    fun `validate - should reject a blank string`() {
        val result = BitbucketRepositorySlugValidator.validate("   ")
        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<BitbucketRepositorySlugValidator.SlugValidationException.Empty>()
    }

    @Test
    fun `validate - should reject a slug with a slash`() {
        BitbucketRepositorySlugValidator.validate("my/repo").isSuccess shouldBe false
    }

    @Test
    fun `validate - should reject a slug with spaces`() {
        BitbucketRepositorySlugValidator.validate("my repo").isSuccess shouldBe false
    }

    @Test
    fun `validate - should reject a slug with url fragment`() {
        BitbucketRepositorySlugValidator.validate("my-repo#section").isSuccess shouldBe false
    }

    @Test
    fun `validate - should trim surrounding whitespace before validation`() {
        BitbucketRepositorySlugValidator.validate("  my-repo  ").getOrThrow() shouldBe "my-repo"
    }
}