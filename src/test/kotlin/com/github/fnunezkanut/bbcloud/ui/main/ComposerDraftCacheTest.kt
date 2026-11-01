package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class ComposerDraftCacheTest {
    @Nested
    inner class ComposerViewModelCaching {
        @Test
        fun `composerViewModel - should return the same instance for the same target and scope`() {
            // GIVEN a cache and one open edit target
            val cache = newCache()
            val target = editTarget(commentId = 5)
            val comment = comment(id = 5, raw = "hello")
            val scope = Any()

            // WHEN the view model is asked for twice with the same target and scope
            val first = cache.composerViewModel(target = target, comment = comment, scope = scope)
            val second = cache.composerViewModel(target = target, comment = comment, scope = scope)

            // THEN the same instance is returned, so the draft survives a rebuild
            first shouldBe second
        }

        @Test
        fun `composerViewModel - should return different instances for different scopes`() {
            // GIVEN a cache, one target, and two surfaces
            val cache = newCache()
            val target = editTarget(commentId = 5)
            val comment = comment(id = 5, raw = "hello")

            // WHEN the view model is asked for with two different scopes
            val first = cache.composerViewModel(target = target, comment = comment, scope = Any())
            val second = cache.composerViewModel(target = target, comment = comment, scope = Any())

            // THEN two separate instances are returned, so the two editors keep separate drafts
            first shouldNotBe second
        }

        @Test
        fun `composerViewModel - should return a new instance after clear`() {
            // GIVEN a cache with one cached view model
            val cache = newCache()
            val target = editTarget(commentId = 5)
            val comment = comment(id = 5, raw = "hello")
            val scope = Any()
            val first = cache.composerViewModel(target = target, comment = comment, scope = scope)

            // WHEN the cache is cleared and the view model is asked for again
            cache.clear()
            val second = cache.composerViewModel(target = target, comment = comment, scope = scope)

            // THEN a fresh instance is returned
            first shouldNotBe second
        }

        @Test
        fun `composerViewModel - should return a new instance after the target comment changes`() {
            // GIVEN a cache with a draft for one comment, then a switch to another comment
            val cache = newCache()
            val scope = Any()
            val first = cache.composerViewModel(target = editTarget(commentId = 5), comment = comment(id = 5, raw = "a"), scope = scope)

            // WHEN the open composer moves to another comment, the host clears the cache and asks again
            cache.clear()
            val second = cache.composerViewModel(target = editTarget(commentId = 6), comment = comment(id = 6, raw = "b"), scope = scope)

            // THEN a fresh instance is returned
            first shouldNotBe second
        }

        @Test
        fun `composerViewModel - should seed an edit draft with the raw comment text`() {
            // GIVEN a cache whose edit input seeds the draft from the comment raw text
            val cache = newCache()
            val comment = comment(id = 5, raw = "existing text")

            // WHEN the edit view model is created
            val viewModel = cache.composerViewModel(target = editTarget(commentId = 5), comment = comment, scope = Any())

            // THEN the draft holds the comment's raw text
            viewModel.currentDraft() shouldBe "existing text"
        }
    }

    @Nested
    inner class ForgetScope {
        @Test
        fun `forgetScope - should drop only the drafts of that scope`() {
            // GIVEN two scopes with a cached view model each
            val cache = newCache()
            val target = editTarget(commentId = 5)
            val comment = comment(id = 5, raw = "hello")
            val keptScope = Any()
            val forgottenScope = Any()
            val kept = cache.composerViewModel(target = target, comment = comment, scope = keptScope)
            cache.composerViewModel(target = target, comment = comment, scope = forgottenScope)

            // WHEN one scope is forgotten
            cache.forgetScope(scope = forgottenScope)

            // THEN the kept scope still returns its instance, and the forgotten scope gets a fresh one
            assertSoftly {
                cache.composerViewModel(target = target, comment = comment, scope = keptScope) shouldBe kept
                cache.composerViewModel(target = target, comment = comment, scope = forgottenScope) shouldNotBe kept
            }
        }
    }

    @Nested
    inner class FocusRestore {
        @Test
        fun `consumeFocusRestore - should return true once for the marked editor and comment`() {
            // GIVEN a focus restore marked for one editor and comment
            val cache = newCache()
            val editor = Any()
            cache.markFocusRestore(editor = editor, commentId = 5)

            // WHEN the restore is consumed for a matching editor, thread and open comment
            val first = cache.consumeFocusRestore(editor = editor, threadCommentIds = setOf(5, 6), openCommentId = 5)
            val second = cache.consumeFocusRestore(editor = editor, threadCommentIds = setOf(5, 6), openCommentId = 5)

            // THEN it fires exactly once
            assertSoftly {
                first shouldBe true
                second shouldBe false
            }
        }

        @Test
        fun `consumeFocusRestore - should return false for another editor`() {
            // GIVEN a focus restore marked for one editor
            val cache = newCache()
            cache.markFocusRestore(editor = Any(), commentId = 5)

            // WHEN the restore is consumed for a different editor
            val restored = cache.consumeFocusRestore(editor = Any(), threadCommentIds = setOf(5), openCommentId = 5)

            // THEN it does not fire
            restored shouldBe false
        }

        @Test
        fun `consumeFocusRestore - should return false after the focus restore is cleared`() {
            // GIVEN a marked focus restore that is then cleared, as closeComposer does
            val cache = newCache()
            val editor = Any()
            cache.markFocusRestore(editor = editor, commentId = 5)
            cache.clearFocusRestore()

            // WHEN the restore is consumed
            val restored = cache.consumeFocusRestore(editor = editor, threadCommentIds = setOf(5), openCommentId = 5)

            // THEN it does not fire
            restored shouldBe false
        }

        @Test
        fun `consumeFocusRestore - should return false when the open comment is no longer the marked one`() {
            // GIVEN a focus restore marked for a comment that is no longer the open composer
            val cache = newCache()
            val editor = Any()
            cache.markFocusRestore(editor = editor, commentId = 5)

            // WHEN the restore is consumed with another open comment
            val restored = cache.consumeFocusRestore(editor = editor, threadCommentIds = setOf(5, 6), openCommentId = 6)

            // THEN it does not fire
            restored shouldBe false
        }
    }

    @Nested
    inner class IsOrphaned {
        @Test
        fun `isOrphaned - should be true when the open comment id is not among the shown ids`() {
            // WHEN the open comment is no longer shown
            val orphaned = ComposerDraftCache.isOrphaned(openCommentId = 5, shownIds = setOf(6, 7))

            // THEN it is reported orphaned
            orphaned shouldBe true
        }

        @Test
        fun `isOrphaned - should be false when the open comment id is still shown`() {
            // WHEN the open comment is still shown
            val orphaned = ComposerDraftCache.isOrphaned(openCommentId = 5, shownIds = setOf(5, 6))

            // THEN it is not orphaned
            orphaned shouldBe false
        }

        @Test
        fun `isOrphaned - should be false when no composer is open`() {
            // WHEN no composer is open
            val orphaned = ComposerDraftCache.isOrphaned(openCommentId = null, shownIds = setOf(5))

            // THEN it is not orphaned
            orphaned shouldBe false
        }
    }

    private fun newCache(): ComposerDraftCache {
        return ComposerDraftCache { target, comment ->
            val input = when (target.kind) {
                ConversationComposerKind.Reply -> {
                    CommentInput(
                        label = "reply",
                        placeholder = "reply",
                        submitLabel = "Reply",
                        submitHint = "Reply",
                        failureMessage = "failed",
                        submit = { _, onResult -> onResult(true) }
                    )
                }
                ConversationComposerKind.Edit -> {
                    CommentInput(
                        label = "comment edit",
                        placeholder = "edit",
                        submitLabel = "Save",
                        submitHint = "Save",
                        failureMessage = "failed",
                        initialText = comment.raw.orEmpty(),
                        submit = { _, onResult -> onResult(true) }
                    )
                }
            }
            CommentComposerViewModel(input = input, startsExpanded = true)
        }
    }

    private fun editTarget(commentId: Long): ConversationComposerTarget {
        return ConversationComposerTarget(commentId = commentId, kind = ConversationComposerKind.Edit)
    }

    private fun comment(id: Long, raw: String?): PullRequestComment {
        return PullRequestComment(
            id = id,
            parentId = null,
            createdOn = Instant.parse("2026-10-31T00:00:00Z"),
            author = null,
            html = "<p>$raw</p>",
            inlinePath = null,
            inlineLine = null,
            inlineSide = null,
            isResolved = false,
            isDeleted = false,
            isPending = false,
            raw = raw
        )
    }
}