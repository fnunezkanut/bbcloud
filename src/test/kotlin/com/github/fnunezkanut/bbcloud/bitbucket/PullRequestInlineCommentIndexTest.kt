package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class PullRequestInlineCommentIndexTest {
    private val author = ActivityUser(uuid = "{author}", displayName = "Author", avatarUrl = null)

    @Nested
    inner class PathMatcherTests {
        @Test
        fun `matches - should treat equivalent paths as the same file`() {
            PullRequestPathMatcher.matches(
                projectBasePath = "/Users/me/examplerepo",
                changeRelativePath = "README.md",
                bitbucketPath = "README.md"
            ) shouldBe true
            PullRequestPathMatcher.matches(
                projectBasePath = "/Users/me/examplerepo",
                changeRelativePath = "README.md",
                bitbucketPath = "./README.md"
            ) shouldBe true
        }

        @Test
        fun `relativeProjectPath - should strip the project base path`() {
            PullRequestPathMatcher.relativeProjectPath(
                projectBasePath = "/Users/me/examplerepo",
                absoluteOrRelativePath = "/Users/me/examplerepo/README.md"
            ) shouldBe "README.md"
        }
    }

    @Nested
    inner class IndexTests {
        @Test
        fun `from - should index inline threads by path line and side`() {
            val comments = listOf(
                PullRequestComment(
                    id = 1,
                    parentId = null,
                    createdOn = Instant.parse("2026-09-30T10:00:00Z"),
                    author = author,
                    html = "<p>root</p>",
                    inlinePath = "README.md",
                    inlineLine = 3,
                    inlineSide = InlineCommentSide.AFTER,
                    isResolved = false,
                    isDeleted = false,
                    isPending = false
                ),
                PullRequestComment(
                    id = 2,
                    parentId = 1,
                    createdOn = Instant.parse("2026-09-30T10:01:00Z"),
                    author = author,
                    html = "<p>reply</p>",
                    inlinePath = "README.md",
                    inlineLine = 3,
                    inlineSide = InlineCommentSide.AFTER,
                    isResolved = false,
                    isDeleted = false,
                    isPending = false
                )
            )
            val index = PullRequestInlineCommentIndex.from(comments = comments)
            val anchors = index.anchorsForPath(projectBasePath = "/repo", changeRelativePath = "README.md")
            anchors shouldHaveSize 1
            assertSoftly(anchors.single()) {
                startLine shouldBe 3
                endLine shouldBe 3
                side shouldBe InlineCommentSide.AFTER
                thread.root.id shouldBe 1
                thread.replies shouldHaveSize 1
            }
            index.totalInlineAnchorCount() shouldBe 1
            index.indexedPaths() shouldBe setOf("README.md")
        }

        @Test
        fun `from - should preserve a multi-line inline range`() {
            val index = PullRequestInlineCommentIndex.from(
                comments = listOf(comment(id = 10, path = "README.md", line = 5, endLine = 7, side = InlineCommentSide.AFTER))
            )
            val anchor = index.anchorsForPath(projectBasePath = "/repo", changeRelativePath = "README.md").single()

            assertSoftly(anchor) {
                startLine shouldBe 5
                endLine shouldBe 7
                side shouldBe InlineCommentSide.AFTER
            }
        }

        @Test
        fun `from - should anchor file scoped roots at line one on the source side`() {
            val index = PullRequestInlineCommentIndex.from(
                comments = listOf(comment(id = 9, path = "README.md", line = null, side = null))
            )
            val anchors = index.anchorsForPath(projectBasePath = "/repo", changeRelativePath = "README.md")
            anchors shouldHaveSize 1
            assertSoftly(anchors.single()) {
                startLine shouldBe 1
                endLine shouldBe 1
                side shouldBe InlineCommentSide.AFTER
                isFileScoped shouldBe true
                thread.root.id shouldBe 9
            }
        }

        @Test
        fun `from - should order mixed file and line threads like Conversation`() {
            val comments = listOf(
                comment(id = 1, path = "README.md", line = 4, side = InlineCommentSide.AFTER)
                    .copy(createdOn = Instant.parse("2026-09-30T10:00:00Z")),
                comment(id = 2, path = "README.md", line = null, side = null)
                    .copy(createdOn = Instant.parse("2026-09-30T11:00:00Z")),
                comment(id = 3, path = "OTHER.md", line = null, side = null)
                    .copy(createdOn = Instant.parse("2026-09-30T12:00:00Z"))
            )
            val anchors = PullRequestInlineCommentIndex.from(comments = comments)
                .anchorsForPath(projectBasePath = null, changeRelativePath = "README.md")

            anchors.map { anchor -> anchor.thread.root.id } shouldBe listOf(2L, 1L)
            anchors.map { anchor -> anchor.isFileScoped } shouldBe listOf(true, false)
        }

        @Test
        fun `from - should ignore general comments without inline path`() {
            val comments = listOf(comment(id = 1, path = null, line = null, side = null))
            val index = PullRequestInlineCommentIndex.from(comments = comments)
            index.anchorsForPath(projectBasePath = null, changeRelativePath = "README.md") shouldHaveSize 0
        }
    }

    @Nested
    inner class AnchorsForFileViewTests {
        @Test
        fun `anchorsForFileView - should keep source side threads`() {
            val index = PullRequestInlineCommentIndex.from(
                comments = listOf(comment(id = 1, path = "README.md", line = 3, side = InlineCommentSide.AFTER))
            )
            val anchors = index.anchorsForFileView(projectBasePath = "/repo", fileRelativePath = "README.md")
            anchors shouldHaveSize 1
            anchors.single().thread.root.id shouldBe 1
        }

        @Test
        fun `anchorsForFileView - should drop pre image threads because those lines are not in the file`() {
            val index = PullRequestInlineCommentIndex.from(
                comments = listOf(comment(id = 1, path = "README.md", line = 3, side = InlineCommentSide.BEFORE))
            )
            val anchors = index.anchorsForFileView(projectBasePath = "/repo", fileRelativePath = "README.md")
            anchors shouldHaveSize 0
            index.anchorsForPath(projectBasePath = "/repo", changeRelativePath = "README.md") shouldHaveSize 1
        }

        @Test
        fun `anchorsForFileView - should keep only the source side of a mixed thread set`() {
            val index = PullRequestInlineCommentIndex.from(
                comments = listOf(
                    comment(id = 1, path = "README.md", line = 3, side = InlineCommentSide.BEFORE),
                    comment(id = 2, path = "README.md", line = 7, side = InlineCommentSide.AFTER)
                )
            )
            val anchors = index.anchorsForFileView(projectBasePath = "/repo", fileRelativePath = "README.md")
            assertSoftly {
                anchors shouldHaveSize 1
                anchors.single().startLine shouldBe 7
                anchors.single().endLine shouldBe 7
                anchors.single().side shouldBe InlineCommentSide.AFTER
            }
        }

        @Test
        fun `anchorsForFileView - should include file scoped roots`() {
            val index = PullRequestInlineCommentIndex.from(
                comments = listOf(comment(id = 1, path = "README.md", line = null, side = null))
            )
            val anchors = index.anchorsForFileView(projectBasePath = "/repo", fileRelativePath = "README.md")
            anchors shouldHaveSize 1
            anchors.single().startLine shouldBe 1
            anchors.single().endLine shouldBe 1
            anchors.single().isFileScoped shouldBe true
        }

        @Test
        fun `anchorsForFileView - should ignore threads of other files`() {
            val index = PullRequestInlineCommentIndex.from(
                comments = listOf(comment(id = 1, path = "OTHER.md", line = 3, side = InlineCommentSide.AFTER))
            )
            val anchors = index.anchorsForFileView(projectBasePath = "/repo", fileRelativePath = "README.md")
            anchors shouldHaveSize 0
        }
    }

    private fun comment(id: Long, path: String?, line: Int?, side: InlineCommentSide?, endLine: Int? = null): PullRequestComment {
        return PullRequestComment(
            id = id,
            parentId = null,
            createdOn = Instant.parse("2026-09-30T10:00:00Z"),
            author = author,
            html = "<p>root</p>",
            inlinePath = path,
            inlineLine = line,
            inlineLineEnd = endLine,
            inlineSide = side,
            isResolved = false,
            isDeleted = false,
            isPending = false
        )
    }
}