package com.github.fnunezkanut.bbcloud.ui.diff

import com.github.fnunezkanut.bbcloud.bitbucket.ActivityUser
import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentAnchor
import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentSide
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.tools.util.side.OnesideTextDiffViewer
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.ex.EditorEx
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class PullRequestCommentableDiffViewerTest {
    @Nested
    inner class From {
        @Test
        fun `from - should wrap a twoside text diff viewer with both editors`() {
            // GIVEN a side-by-side viewer with distinct left and right editors
            val leftEditor = mockk<EditorEx>()
            val rightEditor = mockk<EditorEx>()
            val viewer = mockk<TwosideTextDiffViewer>()
            every { viewer.getEditor(Side.LEFT) } returns leftEditor
            every { viewer.getEditor(Side.RIGHT) } returns rightEditor

            // WHEN
            val wrapper = PullRequestCommentableDiffViewer.from(viewer = viewer)

            // THEN
            assertSoftly(wrapper!!) {
                editorFor(side = InlineCommentSide.BEFORE) shouldBe leftEditor
                editorFor(side = InlineCommentSide.AFTER) shouldBe rightEditor
                isOneside shouldBe false
                this.viewer shouldBe viewer
            }
        }

        @Test
        fun `from - should wrap a oneside text diff viewer of an added file with only the after editor`() {
            // GIVEN an added-file oneside viewer (right side)
            val editor = mockk<EditorEx>()
            val viewer = mockk<OnesideTextDiffViewer>()
            every { viewer.side } returns Side.RIGHT
            every { viewer.getEditor() } returns editor

            // WHEN
            val wrapper = PullRequestCommentableDiffViewer.from(viewer = viewer)

            // THEN
            assertSoftly(wrapper!!) {
                editorFor(side = InlineCommentSide.AFTER) shouldBe editor
                editorFor(side = InlineCommentSide.BEFORE) shouldBe null
                isOneside shouldBe true
            }
        }

        @Test
        fun `from - should not wrap a oneside text diff viewer of a deleted file`() {
            // GIVEN a deleted-file oneside viewer (left side)
            val viewer = mockk<OnesideTextDiffViewer>()
            every { viewer.side } returns Side.LEFT

            // WHEN
            val wrapper = PullRequestCommentableDiffViewer.from(viewer = viewer)

            // THEN
            wrapper shouldBe null
        }

        @Test
        fun `from - should not wrap other viewers`() {
            // GIVEN unrelated viewer handles

            // WHEN / THEN
            PullRequestCommentableDiffViewer.from(viewer = null) shouldBe null
            PullRequestCommentableDiffViewer.from(viewer = Any()) shouldBe null
            PullRequestCommentableDiffViewer.from(viewer = mockk<FrameDiffTool.DiffViewer>()) shouldBe null
        }
    }

    @Nested
    inner class SideOf {
        @Test
        fun `sideOf - should return AFTER for the single editor of an added-file oneside viewer`() {
            // GIVEN
            val editor = mockk<EditorEx>()
            val viewer = mockk<OnesideTextDiffViewer>()
            every { viewer.side } returns Side.RIGHT
            every { viewer.getEditor() } returns editor
            val wrapper = PullRequestCommentableDiffViewer.from(viewer = viewer)!!

            // WHEN / THEN
            wrapper.sideOf(editor = editor) shouldBe InlineCommentSide.AFTER
        }

        @Test
        fun `sideOf - should return BEFORE for the left editor of a twoside viewer`() {
            // GIVEN
            val leftEditor = mockk<EditorEx>()
            val rightEditor = mockk<EditorEx>()
            val viewer = mockk<TwosideTextDiffViewer>()
            every { viewer.getEditor(Side.LEFT) } returns leftEditor
            every { viewer.getEditor(Side.RIGHT) } returns rightEditor
            val wrapper = PullRequestCommentableDiffViewer.from(viewer = viewer)!!

            // WHEN / THEN
            assertSoftly(wrapper) {
                sideOf(editor = leftEditor) shouldBe InlineCommentSide.BEFORE
                sideOf(editor = rightEditor) shouldBe InlineCommentSide.AFTER
            }
        }

        @Test
        fun `sideOf - should return null for an unrelated editor`() {
            // GIVEN
            val editor = mockk<EditorEx>()
            val unrelated = mockk<EditorEx>()
            val viewer = mockk<OnesideTextDiffViewer>()
            every { viewer.side } returns Side.RIGHT
            every { viewer.getEditor() } returns editor
            val wrapper = PullRequestCommentableDiffViewer.from(viewer = viewer)!!

            // WHEN / THEN
            wrapper.sideOf(editor = unrelated) shouldBe null
        }
    }

    @Nested
    inner class SideOfEditor {
        @Test
        fun `sideOfEditor - should resolve BEFORE for the left editor of a processor's active twoside viewer`() {
            // GIVEN
            val leftEditor = mockk<EditorEx>()
            val rightEditor = mockk<EditorEx>()
            val viewer = mockk<TwosideTextDiffViewer>()
            every { viewer.getEditor(Side.LEFT) } returns leftEditor
            every { viewer.getEditor(Side.RIGHT) } returns rightEditor
            val processor = mockk<DiffRequestProcessor>()
            every { processor.activeViewer } returns viewer

            // WHEN / THEN
            sideOfEditor(editor = leftEditor, viewers = listOf(processor)) shouldBe InlineCommentSide.BEFORE
        }

        @Test
        fun `sideOfEditor - should resolve AFTER for the editor of a processor's active added-file oneside viewer`() {
            // GIVEN
            val editor = mockk<EditorEx>()
            val viewer = mockk<OnesideTextDiffViewer>()
            every { viewer.side } returns Side.RIGHT
            every { viewer.getEditor() } returns editor
            val processor = mockk<DiffRequestProcessor>()
            every { processor.activeViewer } returns viewer

            // WHEN / THEN
            sideOfEditor(editor = editor, viewers = listOf(processor)) shouldBe InlineCommentSide.AFTER
        }

        @Test
        fun `sideOfEditor - should return null when no viewer owns the editor`() {
            // GIVEN a processor with no active viewer and a deleted-file oneside viewer
            val editor = mockk<EditorEx>()
            val emptyProcessor = mockk<DiffRequestProcessor>()
            every { emptyProcessor.activeViewer } returns null
            val deletedViewer = mockk<OnesideTextDiffViewer>()
            every { deletedViewer.side } returns Side.LEFT

            // WHEN / THEN
            sideOfEditor(editor = editor, viewers = listOf(emptyProcessor, deletedViewer)) shouldBe null
        }
    }

    @Nested
    inner class IsOneside {
        @Test
        fun `isOneside - should be true only for the added-file oneside wrapper`() {
            // GIVEN a oneside and a twoside wrapper
            val onesideEditor = mockk<EditorEx>()
            val oneside = mockk<OnesideTextDiffViewer>()
            every { oneside.side } returns Side.RIGHT
            every { oneside.getEditor() } returns onesideEditor
            val twoside = mockk<TwosideTextDiffViewer>()
            every { twoside.getEditor(Side.LEFT) } returns mockk<EditorEx>()
            every { twoside.getEditor(Side.RIGHT) } returns mockk<EditorEx>()

            // WHEN / THEN
            assertSoftly {
                PullRequestCommentableDiffViewer.from(viewer = oneside)!!.isOneside shouldBe true
                PullRequestCommentableDiffViewer.from(viewer = twoside)!!.isOneside shouldBe false
            }
        }
    }

    @Nested
    inner class ThreadInlayLine {
        @Test
        fun `threadInlayLine - should place a file-scoped thread at line zero`() {
            // GIVEN a file-scoped anchor
            val anchor = anchor(startLine = 7, isFileScoped = true)

            // WHEN / THEN
            threadInlayLine(anchor = anchor, mappedLine = 6, forceTopOfFile = false) shouldBe 0
        }

        @Test
        fun `threadInlayLine - should place a forced-top thread at line zero even without a mapped line`() {
            // GIVEN an orphan BEFORE anchor forced to the top of the file
            val anchor = anchor(startLine = 7, side = InlineCommentSide.BEFORE)

            // WHEN / THEN
            threadInlayLine(anchor = anchor, mappedLine = null, forceTopOfFile = true) shouldBe 0
        }

        @Test
        fun `threadInlayLine - should use the mapped line for a normal anchor`() {
            // GIVEN a normal anchor with a mapped line
            val anchor = anchor(startLine = 7)

            // WHEN / THEN
            threadInlayLine(anchor = anchor, mappedLine = 6, forceTopOfFile = false) shouldBe 6
        }

        @Test
        fun `threadInlayLine - should skip a normal anchor whose line could not be mapped`() {
            // GIVEN a normal anchor with no mapped line
            val anchor = anchor(startLine = 7)

            // WHEN / THEN
            threadInlayLine(anchor = anchor, mappedLine = null, forceTopOfFile = false) shouldBe null
        }
    }

    private fun anchor(startLine: Int, side: InlineCommentSide = InlineCommentSide.AFTER, isFileScoped: Boolean = false): InlineCommentAnchor {
        val comment = PullRequestComment(
            id = startLine.toLong(),
            parentId = null,
            createdOn = Instant.parse("2026-09-30T10:00:00Z"),
            author = ActivityUser(uuid = "{author}", displayName = "Author", avatarUrl = null),
            html = "<p>Comment</p>",
            inlinePath = "docs/guide.md",
            inlineLine = startLine,
            inlineSide = side,
            isResolved = false,
            isDeleted = false,
            isPending = false
        )
        return InlineCommentAnchor(
            startLine = startLine,
            endLine = startLine,
            side = side,
            thread = TimelineEntry.CommentThread(root = comment, replies = emptyList()),
            isFileScoped = isFileScoped
        )
    }
}