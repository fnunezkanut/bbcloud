package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.intellij.diff.EditorDiffViewer
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.tools.util.side.OnesideTextDiffViewer
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.vcs.changes.Change
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class ReviewBarCommentTargetTest {
    @Nested
    inner class ScopeFor {
        @Test
        fun `scopeFor - should return PullRequestLevel for a deleted file`() {
            // GIVEN a deleted change

            // WHEN
            val scope = ReviewBarCommentScopes.scopeFor(changeType = Change.Type.DELETED, onesideSide = null)

            // THEN
            scope shouldBe ReviewBarCommentScope.PullRequestLevel
        }

        @Test
        fun `scopeFor - should return PullRequestLevel for a left-only oneside diff without a change type`() {
            // GIVEN a left-only oneside diff and no change type

            // WHEN
            val scope = ReviewBarCommentScopes.scopeFor(changeType = null, onesideSide = Side.LEFT)

            // THEN
            scope shouldBe ReviewBarCommentScope.PullRequestLevel
        }

        @Test
        fun `scopeFor - should return FileScoped for modified, new and moved files`() {
            // GIVEN / WHEN / THEN
            assertSoftly {
                ReviewBarCommentScopes.scopeFor(
                    changeType = Change.Type.MODIFICATION,
                    onesideSide = null
                ) shouldBe ReviewBarCommentScope.FileScoped
                ReviewBarCommentScopes.scopeFor(
                    changeType = Change.Type.NEW,
                    onesideSide = Side.RIGHT
                ) shouldBe ReviewBarCommentScope.FileScoped
                ReviewBarCommentScopes.scopeFor(
                    changeType = Change.Type.MOVED,
                    onesideSide = null
                ) shouldBe ReviewBarCommentScope.FileScoped
            }
        }

        @Test
        fun `scopeFor - should return FileScoped when the change type and side are unknown`() {
            // GIVEN no change type and no oneside side

            // WHEN
            val scope = ReviewBarCommentScopes.scopeFor(changeType = null, onesideSide = null)

            // THEN
            scope shouldBe ReviewBarCommentScope.FileScoped
        }
    }

    @Nested
    inner class CommentEditorFor {
        @Test
        fun `commentEditorFor - should use the single editor of a oneside text diff viewer`() {
            // GIVEN
            val editor = mockk<EditorEx>()
            val viewer = mockk<OnesideTextDiffViewer>()
            every { viewer.getEditor() } returns editor

            // WHEN
            val result = ReviewBarDiffEditors.commentEditorFor(viewer = viewer)

            // THEN
            result shouldBe editor
        }

        @Test
        fun `commentEditorFor - should use the right editor of a twoside text diff viewer`() {
            // GIVEN
            val editor = mockk<EditorEx>()
            val viewer = mockk<TwosideTextDiffViewer>()
            every { viewer.getEditor(Side.RIGHT) } returns editor

            // WHEN
            val result = ReviewBarDiffEditors.commentEditorFor(viewer = viewer)

            // THEN
            result shouldBe editor
        }

        @Test
        fun `commentEditorFor - should resolve the active oneside viewer of a diff request processor`() {
            // GIVEN
            val editor = mockk<EditorEx>()
            val onesideViewer = mockk<OnesideTextDiffViewer>()
            every { onesideViewer.getEditor() } returns editor
            val processor = mockk<DiffRequestProcessor>()
            every { processor.activeViewer } returns onesideViewer

            // WHEN
            val result = ReviewBarDiffEditors.commentEditorFor(viewer = processor)

            // THEN
            result shouldBe editor
        }

        @Test
        fun `commentEditorFor - should use the current editor of a unified diff viewer`() {
            // GIVEN
            val editor = mockk<EditorEx>()
            val viewer = mockk<EditorDiffViewer>()
            every { viewer.currentEditor } returns editor

            // WHEN
            val result = ReviewBarDiffEditors.commentEditorFor(viewer = viewer)

            // THEN
            result shouldBe editor
        }

        @Test
        fun `commentEditorFor - should return null for a null viewer`() {
            // GIVEN no viewer

            // WHEN
            val result = ReviewBarDiffEditors.commentEditorFor(viewer = null)

            // THEN
            result shouldBe null
        }
    }
}