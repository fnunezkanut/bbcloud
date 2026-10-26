package com.github.fnunezkanut.bbcloud.project

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

@Tag("unit")
class PullRequestWorktreeListingTest {
    @Test
    fun `worktreesForRepo - filters malformed and other repository folders and sorts by pull request`() {
        // GIVEN
        val root = Path.of("/tmp/worktrees")
        val children = listOf(
            root.resolve("other-repo--pr-3"),
            root.resolve("watched-repo--pr-20"),
            root.resolve("watched-repo--pr-2"),
            root.resolve("watched-repo--pr-nope"),
            root.resolve("watched-repo"),
            root.resolve("watched-repo--pr-0")
        )

        // WHEN
        val result = PullRequestWorktreeListing.worktreesForRepo(
            workTreeDirectory = root,
            repoSlug = "watched-repo",
            listChildren = { children }
        )

        // THEN
        result shouldHaveSize 2
        assertSoftly(result) {
            get(0).prNumber shouldBe 2L
            get(0).worktreePath shouldBe root.resolve("watched-repo--pr-2")
            get(1).prNumber shouldBe 20L
            get(1).repoSlug shouldBe "watched-repo"
        }
    }

    @Test
    fun `worktreesForRepo - should return empty when child listing fails`() {
        // GIVEN
        val root = Path.of("/tmp/missing-worktrees")

        // WHEN
        val result = PullRequestWorktreeListing.worktreesForRepo(
            workTreeDirectory = root,
            repoSlug = "watched-repo",
            listChildren = { throw IllegalStateException("unreadable") }
        )

        // THEN
        result shouldBe emptyList()
    }
}