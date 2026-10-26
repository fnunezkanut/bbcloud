package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class PullRequestFileCommentCountsTest {
    private val author = ActivityUser(uuid = "{author}", displayName = "Author", avatarUrl = null)

    @Test
    fun `from - should count file level and inline comments`() {
        // GIVEN
        val comments = listOf(
            comment(id = 1, path = "src/Main.kt"),
            comment(id = 2, path = "src/Main.kt", line = 12)
        )

        // WHEN
        val counts = PullRequestFileCommentCounts.from(comments)

        // THEN
        assertSoftly {
            counts.countForChangePath(projectBasePath = "/repo", changeRelativePath = "src/Main.kt") shouldBe 2
            counts.pathsWithComments().shouldContainExactly("src/Main.kt")
        }
    }

    @Test
    fun `from - should exclude pull request deleted and pending comments`() {
        // GIVEN
        val comments = listOf(
            comment(id = 1, path = "README.md"),
            comment(id = 2, path = null),
            comment(id = 3, path = "README.md", isDeleted = true),
            comment(id = 4, path = "README.md", isPending = true)
        )

        // WHEN
        val counts = PullRequestFileCommentCounts.from(comments)

        // THEN
        counts.countForChangePath(projectBasePath = null, changeRelativePath = "README.md") shouldBe 1
    }

    @Test
    fun `from - should count replies as separate visible comments`() {
        // GIVEN
        val comments = listOf(
            comment(id = 1, path = "README.md"),
            comment(id = 2, path = "README.md", parentId = 1, line = 3)
        )

        // WHEN
        val counts = PullRequestFileCommentCounts.from(comments)

        // THEN
        counts.countForChangePath(projectBasePath = null, changeRelativePath = "README.md") shouldBe 2
    }

    @Test
    fun `countForChangePath - should tally multiple files independently`() {
        // GIVEN
        val comments = listOf(
            comment(id = 1, path = "src/One.kt"),
            comment(id = 2, path = "src/Two.kt"),
            comment(id = 3, path = "src/Two.kt", line = 8)
        )
        val counts = PullRequestFileCommentCounts.from(comments)

        // WHEN / THEN
        assertSoftly {
            counts.countForChangePath(projectBasePath = "/repo", changeRelativePath = "src/One.kt") shouldBe 1
            counts.countForChangePath(projectBasePath = "/repo", changeRelativePath = "src/Two.kt") shouldBe 2
            counts.countForChangePath(projectBasePath = "/repo", changeRelativePath = "src/Three.kt") shouldBe 0
        }
    }

    @Test
    fun `countForChangePath - should match exact suffix fuzzy and normalized paths`() {
        // GIVEN
        val comments = listOf(
            comment(id = 1, path = "src\\main\\App.kt"),
            comment(id = 2, path = "./docs/README.md"),
            comment(id = 3, path = "lib/Util.kt")
        )
        val counts = PullRequestFileCommentCounts.from(comments)

        // WHEN / THEN
        assertSoftly {
            counts.countForChangePath(
                projectBasePath = "/Users/me/repo",
                changeRelativePath = "src/main/App.kt"
            ) shouldBe 1
            counts.countForChangePath(
                projectBasePath = "/Users/me/repo",
                changeRelativePath = "README.md"
            ) shouldBe 1
            counts.countForChangePath(
                projectBasePath = "/Users/me/repo",
                changeRelativePath = "/Users/me/repo/lib\\Util.kt"
            ) shouldBe 1
        }
    }

    private fun comment(
        id: Long,
        path: String?,
        line: Int? = null,
        parentId: Long? = null,
        isDeleted: Boolean = false,
        isPending: Boolean = false
    ): PullRequestComment {
        return PullRequestComment(
            id = id,
            parentId = parentId,
            createdOn = Instant.parse("2026-09-30T10:00:00Z"),
            author = author,
            html = "<p>comment $id</p>",
            inlinePath = path,
            inlineLine = line,
            inlineSide = line?.let { InlineCommentSide.AFTER },
            isResolved = false,
            isDeleted = isDeleted,
            isPending = isPending
        )
    }
}