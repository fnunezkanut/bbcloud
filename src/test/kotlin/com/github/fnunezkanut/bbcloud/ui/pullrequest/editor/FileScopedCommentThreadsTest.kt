package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.bitbucket.ActivityUser
import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentSide
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class FileScopedCommentThreadsTest {
    private val author = ActivityUser(uuid = "{author}", displayName = "Author", avatarUrl = null)
    private val date = Instant.parse("2026-09-30T10:00:00Z")

    @Test
    fun `fileScopedThreadsForPath - should keep matching path-only roots and their replies`() {
        val threads = fileScopedThreadsForPath(
            comments = listOf(
                comment(id = 1, path = "src\\Foo.kt"),
                comment(id = 2, path = "src/Foo.kt", parentId = 1, line = 4),
                comment(id = 3, path = "src/Other.kt"),
                comment(id = 4, path = null)
            ),
            projectBasePath = "/repo",
            relativePath = "src/Foo.kt"
        )

        threads shouldHaveSize 1
        threads.single().root.id shouldBe 1L
        threads.single().replies.map { reply -> reply.id } shouldBe listOf(2L)
    }

    @Test
    fun `fileScopedThreadsForPath - should include line-anchored roots and sort like Conversation`() {
        val threads = fileScopedThreadsForPath(
            comments = listOf(
                comment(id = 1, path = "src/Foo.kt", line = 4).copy(createdOn = Instant.parse("2026-09-30T10:00:00Z")),
                comment(id = 2, path = "src/Foo.kt").copy(createdOn = Instant.parse("2026-09-30T11:00:00Z"))
            ),
            projectBasePath = null,
            relativePath = "src/Foo.kt"
        )

        threads.map { thread -> thread.root.id } shouldBe listOf(2L, 1L)
    }

    @Test
    fun `fileScopedThreadsForPath - should exclude comments without a path`() {
        val threads = fileScopedThreadsForPath(
            comments = listOf(comment(id = 1, path = null)),
            projectBasePath = null,
            relativePath = "src/Foo.kt"
        )

        threads shouldHaveSize 0
    }

    private fun comment(id: Long, path: String?, parentId: Long? = null, line: Int? = null): PullRequestComment {
        return PullRequestComment(
            id = id,
            parentId = parentId,
            createdOn = date,
            author = author,
            html = "<p>Comment $id</p>",
            inlinePath = path,
            inlineLine = line,
            inlineSide = line?.let { InlineCommentSide.AFTER },
            isResolved = false,
            isDeleted = false,
            isPending = false,
            raw = "Comment $id"
        )
    }
}