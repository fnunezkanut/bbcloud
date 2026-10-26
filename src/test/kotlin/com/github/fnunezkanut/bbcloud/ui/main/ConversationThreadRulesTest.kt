package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.ActivityUser
import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentSide
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class ConversationThreadRulesTest {
    private var lastId = 0L
    private val date = Instant.parse("2026-09-30T10:00:00Z")
    private val author = ActivityUser(uuid = "{author}", displayName = "Author", avatarUrl = null)
    private val reviewer = ActivityUser(uuid = "{reviewer}", displayName = "Reviewer", avatarUrl = null)

    @Nested
    inner class RemoveImagesTests {
        @Test
        fun `removeImages - should drop an img tag`() {
            // GIVEN
            val html = """<p>Text</p><img src="https://bitbucket.org/x.png">"""

            // WHEN
            val result = ConversationThreadRules.removeImages(html)

            // THEN
            result shouldBe "<p>Text</p>"
        }
    }

    @Nested
    inner class ShowsThreadBodyTests {
        @Test
        fun `showsThreadBody - should be true for an unresolved thread`() {
            // GIVEN
            val thread = thread(rootAuthor = reviewer)

            // WHEN
            val result = ConversationThreadRules.showsThreadBody(thread)

            // THEN
            result shouldBe true
        }

        @Test
        fun `showsThreadBody - should be false for a resolved thread with replies`() {
            // GIVEN
            val thread = thread(rootAuthor = reviewer, replyAuthor = author, isResolved = true)

            // WHEN
            val result = ConversationThreadRules.showsThreadBody(thread)

            // THEN
            result shouldBe false
        }

        @Test
        fun `showsThreadBody - should be false for a resolved thread without replies`() {
            // GIVEN
            val thread = thread(rootAuthor = reviewer, isResolved = true)

            // WHEN
            val result = ConversationThreadRules.showsThreadBody(thread)

            // THEN
            result shouldBe false
        }

        @Test
        fun `showsThreadBody - should hide the body only because of the resolved root`() {
            // GIVEN an unresolved root with an unresolved reply and a resolved root with the same reply
            val unresolved = thread(rootAuthor = reviewer, replyAuthor = author)
            val resolved = thread(rootAuthor = reviewer, replyAuthor = author, isResolved = true)

            // WHEN
            val unresolvedResult = ConversationThreadRules.showsThreadBody(unresolved)
            val resolvedResult = ConversationThreadRules.showsThreadBody(resolved)

            // THEN replies alone never re-open a collapsed thread
            assertSoftly {
                unresolvedResult shouldBe true
                resolvedResult shouldBe false
                resolved.replies.map { reply -> reply.isResolved } shouldBe listOf(false)
            }
        }
    }

    @Nested
    inner class NavigableLineTests {
        @Test
        fun `navigableLine - should keep the line of an after side comment`() {
            // GIVEN the plugin checks out the source branch, so after side lines exist in the working tree
            val root = comment(
                author = reviewer,
                isResolved = false,
                inlinePath = "README.md",
                inlineLine = 3,
                inlineSide = InlineCommentSide.AFTER
            )

            // WHEN
            val result = ConversationThreadRules.navigableLine(root)

            // THEN
            result shouldBe 3
        }

        @Test
        fun `navigableLine - should drop the line of a before side comment`() {
            // GIVEN a before side line belongs to the destination branch, which is not checked out
            val root = comment(
                author = reviewer,
                isResolved = false,
                inlinePath = "README.md",
                inlineLine = 3,
                inlineSide = InlineCommentSide.BEFORE
            )

            // WHEN
            val result = ConversationThreadRules.navigableLine(root)

            // THEN the file still opens, without a caret jump
            result shouldBe null
        }

        @Test
        fun `navigableLine - should keep the line when Bitbucket reports no side`() {
            // GIVEN
            val root = comment(
                author = reviewer,
                isResolved = false,
                inlinePath = "README.md",
                inlineLine = 3,
                inlineSide = null
            )

            // WHEN
            val result = ConversationThreadRules.navigableLine(root)

            // THEN
            result shouldBe 3
        }

        @Test
        fun `navigableLine - should be null without a comment line`() {
            // GIVEN
            val root = comment(
                author = reviewer,
                isResolved = false,
                inlinePath = "README.md",
                inlineLine = null,
                inlineSide = InlineCommentSide.AFTER
            )

            // WHEN
            val result = ConversationThreadRules.navigableLine(root)

            // THEN
            result shouldBe null
        }
    }

    @Nested
    inner class CommentBodyMarkdownTests {
        @Test
        fun `commentBodyMarkdown - should use the Markdown source when Bitbucket reported it`() {
            // GIVEN
            val root = comment(author = reviewer, isResolved = false, raw = "**bold** text")

            // WHEN
            val result = ConversationThreadRules.commentBodyMarkdown(root)

            // THEN
            result shouldBe "**bold** text"
        }

        @Test
        fun `commentBodyMarkdown - should fall back to the html when the Markdown source is missing`() {
            // GIVEN
            val root = comment(author = reviewer, isResolved = false, html = "<p>Comment</p>")

            // WHEN
            val result = ConversationThreadRules.commentBodyMarkdown(root)

            // THEN the comment never renders blank
            result shouldBe "Comment"
        }

        @Test
        fun `commentBodyMarkdown - should fall back to the html when the Markdown source is blank`() {
            // GIVEN
            val root = comment(author = reviewer, isResolved = false, raw = "   ", html = "<p>Comment</p>")

            // WHEN
            val result = ConversationThreadRules.commentBodyMarkdown(root)

            // THEN
            result shouldBe "Comment"
        }
    }

    @Nested
    inner class LocationTests {
        @Test
        fun `locationText - should be null for a pull request level comment`() {
            // GIVEN
            val root = comment(author = reviewer, isResolved = false)

            // WHEN
            val result = ConversationThreadRules.locationText(root)

            // THEN
            result shouldBe null
        }

        @Test
        fun `locationText - should show the file name and the line`() {
            // GIVEN
            val root = comment(author = reviewer, isResolved = false, inlinePath = "src/main/README.md", inlineLine = 7)

            // WHEN
            val result = ConversationThreadRules.locationText(root)

            // THEN
            result shouldBe "README.md:7"
        }

        @Test
        fun `locationText - should show only the file name when Bitbucket reported none`() {
            // GIVEN
            val root = comment(author = reviewer, isResolved = false, inlinePath = "src/main/README.md", inlineLine = null)

            // WHEN
            val result = ConversationThreadRules.locationText(root)

            // THEN
            result shouldBe "README.md"
        }

        @Test
        fun `locationText - should show only the file name when Bitbucket reported no line`() {
            // GIVEN
            val root = comment(author = reviewer, isResolved = false, inlinePath = "src/main/README.md", inlineLine = null)

            // WHEN
            val result = ConversationThreadRules.locationText(root)

            // THEN
            result shouldBe "README.md"
        }

        @Test
        fun `locationPath - should keep the full repository relative path`() {
            // GIVEN
            val root = comment(author = reviewer, isResolved = false, inlinePath = "src/main/README.md", inlineLine = 7)

            // WHEN
            val result = ConversationThreadRules.locationPath(root)

            // THEN
            result shouldBe "src/main/README.md:7"
        }

        @Test
        fun `locationPath - should drop the line when Bitbucket reported none`() {
            // GIVEN
            val root = comment(author = reviewer, isResolved = false, inlinePath = "src/main/README.md", inlineLine = null)

            // WHEN
            val result = ConversationThreadRules.locationPath(root)

            // THEN
            result shouldBe "src/main/README.md"
        }

        @Test
        fun `locationPath - should be null for a pull request level comment`() {
            // GIVEN
            val root = comment(author = reviewer, isResolved = false)

            // WHEN
            val result = ConversationThreadRules.locationPath(root)

            // THEN
            result shouldBe null
        }
    }

    @Nested
    inner class AuthorNameTests {
        @Test
        fun `authorName - should show the display name of the author`() {
            // GIVEN
            val root = comment(author = reviewer, isResolved = false)

            // WHEN
            val result = ConversationThreadRules.authorName(root.author)

            // THEN
            result shouldBe "Reviewer"
        }

        @Test
        fun `authorName - should fall back to the unknown user when there is no author`() {
            // GIVEN
            val root = comment(author = reviewer, isResolved = false).copy(author = null)

            // WHEN
            val result = ConversationThreadRules.authorName(root.author)

            // THEN
            result shouldBe "Unknown user"
        }
    }

    @Nested
    inner class ResolvedByTextTests {
        @Test
        fun `resolvedByText - should name the resolver`() {
            // GIVEN
            val root = comment(author = reviewer, isResolved = true).copy(resolvedBy = reviewer)

            // WHEN
            val result = ConversationThreadRules.resolvedByText(root.resolvedBy)

            // THEN
            result shouldBe "Resolved by Reviewer"
        }

        @Test
        fun `resolvedByText - should fall back to the unknown user when Bitbucket reported no resolver`() {
            // GIVEN
            val root = comment(author = reviewer, isResolved = true)

            // WHEN
            val result = ConversationThreadRules.resolvedByText(root.resolvedBy)

            // THEN
            result shouldBe "Resolved by Unknown user"
        }
    }

    @Nested
    inner class ShouldSnapToTopTests {
        @Test
        fun `shouldSnapToTop - should be true at the top`() {
            // WHEN
            val result = ConversationThreadRules.shouldSnapToTop(offsetPx = 0)

            // THEN
            result shouldBe true
        }

        @Test
        fun `shouldSnapToTop - should be true just below the top`() {
            // WHEN
            val result = ConversationThreadRules.shouldSnapToTop(offsetPx = 10)

            // THEN
            result shouldBe true
        }

        @Test
        fun `shouldSnapToTop - should be false once the reader scrolled away`() {
            // WHEN
            val result = ConversationThreadRules.shouldSnapToTop(offsetPx = 500)

            // THEN
            result shouldBe false
        }
    }

    private fun thread(rootAuthor: ActivityUser, replyAuthor: ActivityUser? = null, isResolved: Boolean = false): TimelineEntry.CommentThread {
        val root = comment(author = rootAuthor, isResolved = isResolved)
        val replies = listOfNotNull(replyAuthor?.let { replier -> comment(author = replier, isResolved = false) })
        return TimelineEntry.CommentThread(root = root, replies = replies)
    }

    private fun comment(
        author: ActivityUser,
        isResolved: Boolean,
        inlinePath: String? = null,
        inlineLine: Int? = null,
        inlineSide: InlineCommentSide? = null,
        html: String = "<p>Comment</p>",
        raw: String? = null
    ): PullRequestComment {
        return PullRequestComment(
            id = nextId(),
            parentId = null,
            createdOn = date,
            author = author,
            html = html,
            inlinePath = inlinePath,
            inlineLine = inlineLine,
            inlineSide = inlineSide,
            isResolved = isResolved,
            isDeleted = false,
            isPending = false,
            raw = raw
        )
    }

    private fun nextId(): Long {
        lastId++
        return lastId
    }
}