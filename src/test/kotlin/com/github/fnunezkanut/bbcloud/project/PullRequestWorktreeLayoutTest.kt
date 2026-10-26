package com.github.fnunezkanut.bbcloud.project

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Paths

@Tag("unit")
class PullRequestWorktreeLayoutTest {
    @Test
    fun `worktreePath - joins repoSlug and prNumber with a double dash and pr prefix under the work tree root`() {
        // GIVEN
        val workTreeDirectory = "/tmp/worktrees"
        val repoSlug = "my-repo"
        val prNumber = 42L

        // WHEN
        val result = PullRequestWorktreeLayout.worktreePath(
            workTreeDirectory = workTreeDirectory,
            repoSlug = repoSlug,
            prNumber = prNumber
        )

        // THEN
        val expected = Paths.get("/tmp/worktrees/my-repo--pr-42").toAbsolutePath().normalize()
        result shouldBe expected
    }

    @Test
    fun `worktreePath - normalizes redundant segments`() {
        // GIVEN
        val workTreeDirectory = "/tmp/worktrees/../worktrees"
        val repoSlug = "my-repo"
        val prNumber = 7L

        // WHEN
        val result = PullRequestWorktreeLayout.worktreePath(
            workTreeDirectory = workTreeDirectory,
            repoSlug = repoSlug,
            prNumber = prNumber
        )

        // THEN
        val expected = Paths.get("/tmp/worktrees/my-repo--pr-7").toAbsolutePath().normalize()
        result shouldBe expected
    }

    @Test
    fun `repoSlugFromWorktreeFolderName - should parse a repository slug and pull request number`() {
        // GIVEN a flattened worktree folder name
        val folderName = "my-repo--pr-42"

        // WHEN the folder name is parsed
        val result = PullRequestWorktreeLayout.repoSlugFromWorktreeFolderName(folderName)

        // THEN only the repository slug is returned
        result shouldBe "my-repo"
    }

    @Test
    fun `repoSlugFromWorktreeFolderName - should preserve hyphens in a repository slug`() {
        // GIVEN a flattened worktree folder name with a hyphenated slug
        val folderName = "some-long-repo-name--pr-7"

        // WHEN the folder name is parsed
        val result = PullRequestWorktreeLayout.repoSlugFromWorktreeFolderName(folderName)

        // THEN all slug hyphens are preserved
        result shouldBe "some-long-repo-name"
    }

    @Test
    fun `repoSlugFromWorktreeFolderName - should round trip a composed worktree path`() {
        // GIVEN a known repository slug and pull request number
        val worktreePath = PullRequestWorktreeLayout.worktreePath(
            workTreeDirectory = "/tmp/worktrees",
            repoSlug = "some-long-repo-name",
            prNumber = 7L
        )

        // WHEN the composed folder name is parsed
        val result = PullRequestWorktreeLayout.repoSlugFromWorktreeFolderName(
            folderName = worktreePath.fileName.toString()
        )

        // THEN the original slug is recovered
        result shouldBe "some-long-repo-name"
    }

    @Test
    fun `repoSlugFromWorktreeFolderName - should reject malformed worktree names`() {
        // GIVEN names that do not encode a positive pull request number and non-empty slug
        val invalidNames = listOf(
            "my-repo",
            "my-repo--pr-",
            "my-repo--pr-abc",
            "--pr-5"
        )

        // WHEN each name is parsed
        val results = invalidNames.map { folderName ->
            PullRequestWorktreeLayout.repoSlugFromWorktreeFolderName(folderName)
        }

        // THEN malformed names are rejected
        assertSoftly(results) {
            forEach { result -> result.shouldBeNull() }
        }
    }

    @Test
    fun `prNumberFromWorktreeFolderName - should parse a positive pull request number`() {
        // GIVEN
        val folderName = "my-repo--pr-123"

        // WHEN
        val result = PullRequestWorktreeLayout.prNumberFromWorktreeFolderName(folderName)

        // THEN
        result shouldBe 123L
    }

    @Test
    fun `prNumberFromWorktreeFolderName - should reject malformed numbers`() {
        // GIVEN
        val invalidNames = listOf(
            "my-repo--pr-",
            "my-repo--pr-abc",
            "my-repo--pr-0",
            "my-repo--pr--1",
            "my-repo",
            "--pr-5"
        )

        // WHEN
        val results = invalidNames.map { folderName ->
            PullRequestWorktreeLayout.prNumberFromWorktreeFolderName(folderName)
        }

        // THEN
        assertSoftly(results) {
            forEach { result -> result.shouldBeNull() }
        }
    }

    @Test
    fun `prNumberFromWorktreeFolderName - should use the last separator`() {
        // GIVEN a slug-like name containing an earlier separator
        val folderName = "repo--pr-description--pr-9"

        // WHEN
        val result = PullRequestWorktreeLayout.prNumberFromWorktreeFolderName(folderName)

        // THEN
        result shouldBe 9L
    }

    @Test
    fun `parentDirectory - returns the work tree root`() {
        // GIVEN
        val worktreePath = PullRequestWorktreeLayout.worktreePath(
            workTreeDirectory = "/tmp/worktrees",
            repoSlug = "my-repo",
            prNumber = 42L
        )

        // WHEN
        val parent = PullRequestWorktreeLayout.parentDirectory(worktreePath = worktreePath)

        // THEN under the flattened layout the parent is the work tree root itself.
        val expectedParent = Paths.get("/tmp/worktrees").toAbsolutePath().normalize()
        assertSoftly {
            parent shouldBe expectedParent
            parent shouldBe worktreePath.parent
        }
    }
}