package com.github.fnunezkanut.bbcloud.project

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestWorktreePathsTest {
    private val root = "/home/me/projects/.worktrees"

    @Test
    fun `isPullRequestWorktreePath - should accept a work tree directly under the root`() {
        // GIVEN
        val path = "$root/repo--pr-12"

        // WHEN
        val result = isPullRequestWorktreePath(path = path, workTreeDirectory = root)

        // THEN
        result shouldBe true
    }

    @Test
    fun `isPullRequestWorktreePath - should accept a trailing slash and dot-dot segments that land in the root`() {
        // GIVEN / WHEN / THEN
        assertSoftly {
            isPullRequestWorktreePath(path = "$root/repo--pr-12/", workTreeDirectory = root) shouldBe true
            isPullRequestWorktreePath(path = "$root/x/../repo--pr-12", workTreeDirectory = root) shouldBe true
        }
    }

    @Test
    fun `isPullRequestWorktreePath - should reject everything else`() {
        // GIVEN / WHEN / THEN
        assertSoftly {
            isPullRequestWorktreePath(path = "/home/me/projects/repo--pr-12", workTreeDirectory = root) shouldBe false
            isPullRequestWorktreePath(path = "$root/x/repo--pr-12", workTreeDirectory = root) shouldBe false
            isPullRequestWorktreePath(path = "$root/repo", workTreeDirectory = root) shouldBe false
            isPullRequestWorktreePath(path = "$root/repo--pr-abc", workTreeDirectory = root) shouldBe false
            isPullRequestWorktreePath(path = "$root/repo--pr-12", workTreeDirectory = null) shouldBe false
            isPullRequestWorktreePath(path = "$root/repo--pr-12", workTreeDirectory = "  ") shouldBe false
            isPullRequestWorktreePath(path = "", workTreeDirectory = root) shouldBe false
            isPullRequestWorktreePath(path = "$root/re\u0000po--pr-12", workTreeDirectory = root) shouldBe false
        }
    }

    @Test
    fun `repoSlugForRecentProject - should prefer the work tree slug over a mapping`() {
        // GIVEN
        val mappings = mapOf("$root/repo--pr-12" to "other")

        // WHEN
        val slug = repoSlugForRecentProject(path = "$root/repo--pr-12", workTreeDirectory = root, mappingsByFolder = mappings)

        // THEN
        slug shouldBe "repo"
    }

    @Test
    fun `repoSlugForRecentProject - should use the mapping with a normalized key`() {
        // GIVEN
        val mappings = mapOf("/home/me/projects/clone" to "real-slug")

        // WHEN
        val slug = repoSlugForRecentProject(path = "/home/me/projects/x/../clone/", workTreeDirectory = root, mappingsByFolder = mappings)

        // THEN
        slug shouldBe "real-slug"
    }

    @Test
    fun `repoSlugForRecentProject - should return null for an unknown folder instead of guessing`() {
        // GIVEN / WHEN
        val slug = repoSlugForRecentProject(path = "/home/me/projects/clone", workTreeDirectory = root, mappingsByFolder = emptyMap())

        // THEN
        slug shouldBe null
    }
}