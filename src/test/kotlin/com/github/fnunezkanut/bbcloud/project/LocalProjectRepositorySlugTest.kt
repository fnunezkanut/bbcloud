package com.github.fnunezkanut.bbcloud.project

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class LocalProjectRepositorySlugTest {
    @Test
    fun `fromBasePath - should return last path segment`() {
        LocalProjectRepositorySlug.fromBasePath("/Users/me/Projects/examplerepo") shouldBe "examplerepo"
    }

    @Test
    fun `fromBasePath - should return null when base path blank`() {
        LocalProjectRepositorySlug.fromBasePath(null) shouldBe null
        LocalProjectRepositorySlug.fromBasePath("   ") shouldBe null
    }

    @Test
    fun `repoSlugForBasePath - should remove the pull request worktree suffix`() {
        LocalProjectRepositorySlug.repoSlugForBasePath("/Users/me/.worktrees/kapidsl--pr-6") shouldBe "kapidsl"
    }

    @Test
    fun `repoSlugForBasePath - should preserve a regular checkout folder name`() {
        LocalProjectRepositorySlug.repoSlugForBasePath("/Users/me/Projects/kapidsl") shouldBe "kapidsl"
    }

    @Test
    fun `repoSlugForBasePath - should return null for a blank base path`() {
        LocalProjectRepositorySlug.repoSlugForBasePath(null) shouldBe null
        LocalProjectRepositorySlug.repoSlugForBasePath("   ") shouldBe null
    }

    @Test
    fun `resolve - an explicit slug wins over a different folder name`() {
        val result = LocalProjectRepositorySlug.resolve(
            explicitSlug = "bbcloud",
            basePath = "/Users/me/Projects/examplerepo"
        )
        result shouldBe "bbcloud"
    }

    @Test
    fun `resolve - a blank explicit slug falls back to the folder name`() {
        val result = LocalProjectRepositorySlug.resolve(
            explicitSlug = "  ",
            basePath = "/Users/me/Projects/examplerepo"
        )
        result shouldBe "examplerepo"
    }

    @Test
    fun `resolve - a null explicit slug falls back to the folder name`() {
        val result = LocalProjectRepositorySlug.resolve(
            explicitSlug = null,
            basePath = "/Users/me/Projects/examplerepo"
        )
        result shouldBe "examplerepo"
    }

    @Test
    fun `resolve - a null explicit slug and a null base path return null`() {
        val result = LocalProjectRepositorySlug.resolve(
            explicitSlug = null,
            basePath = null
        )
        result shouldBe null
    }
}