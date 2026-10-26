@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.setTextAndSelectAll
import androidx.compose.ui.text.TextRange
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class CommentComposerViewModelTest {
    @Nested
    inner class ApplyCommentMarkdownStyleTests {
        @Test
        fun `bold - should wrap the selected text`() {
            // GIVEN
            val text = "hello world"

            // WHEN
            val result = applyCommentMarkdownStyle(
                text = text,
                selectionStart = 6,
                selectionEnd = 11,
                style = CommentMarkdownStyle.Bold
            )

            // THEN
            assertSoftly {
                result.text shouldBe "hello **world**"
                result.selectionStart shouldBe 6
                result.selectionEnd shouldBe 15
            }
        }

        @Test
        fun `bold - should insert the markers and place the caret between them when nothing is selected`() {
            // GIVEN
            val text = "hello"

            // WHEN
            val result = applyCommentMarkdownStyle(
                text = text,
                selectionStart = 5,
                selectionEnd = 5,
                style = CommentMarkdownStyle.Bold
            )

            // THEN
            assertSoftly {
                result.text shouldBe "hello****"
                result.selectionStart shouldBe 7
                result.selectionEnd shouldBe 7
            }
        }

        @Test
        fun `italic - should wrap the selected text`() {
            // GIVEN
            val text = "hello world"

            // WHEN
            val result = applyCommentMarkdownStyle(
                text = text,
                selectionStart = 0,
                selectionEnd = 5,
                style = CommentMarkdownStyle.Italic
            )

            // THEN
            result.text shouldBe "_hello_ world"
        }

        @Test
        fun `strikethrough - should wrap the selected text`() {
            // GIVEN
            val text = "hello"

            // WHEN
            val result = applyCommentMarkdownStyle(
                text = text,
                selectionStart = 0,
                selectionEnd = 5,
                style = CommentMarkdownStyle.Strikethrough
            )

            // THEN
            result.text shouldBe "~~hello~~"
        }

        @Test
        fun `inline code - should wrap the selected text`() {
            // GIVEN
            val text = "hello"

            // WHEN
            val result = applyCommentMarkdownStyle(
                text = text,
                selectionStart = 0,
                selectionEnd = 5,
                style = CommentMarkdownStyle.InlineCode
            )

            // THEN
            result.text shouldBe "`hello`"
        }

        @Test
        fun `link - should insert an empty pair of parentheses with the caret inside`() {
            // GIVEN
            val text = "see "

            // WHEN
            val result = applyCommentMarkdownStyle(
                text = text,
                selectionStart = 4,
                selectionEnd = 4,
                style = CommentMarkdownStyle.Link
            )

            // THEN
            assertSoftly {
                result.text shouldBe "see []()"
                result.selectionStart shouldBe 7
                result.selectionEnd shouldBe 7
            }
        }

        @Test
        fun `link - should keep the selected text as the label`() {
            // GIVEN
            val text = "see docs"

            // WHEN
            val result = applyCommentMarkdownStyle(
                text = text,
                selectionStart = 4,
                selectionEnd = 8,
                style = CommentMarkdownStyle.Link
            )

            // THEN
            result.text shouldBe "see [docs]()"
        }

        @Test
        fun `list - should prefix every selected line`() {
            // GIVEN
            val text = "one\ntwo"

            // WHEN
            val result = applyCommentMarkdownStyle(
                text = text,
                selectionStart = 0,
                selectionEnd = 7,
                style = CommentMarkdownStyle.UnorderedList
            )

            // THEN
            result.text shouldBe "- one\n- two"
        }

        @Test
        fun `header - should prefix every selected line`() {
            // GIVEN
            val text = "Title"

            // WHEN
            val result = applyCommentMarkdownStyle(
                text = text,
                selectionStart = 0,
                selectionEnd = 5,
                style = CommentMarkdownStyle.Header
            )

            // THEN
            result.text shouldBe "# Title"
        }

        @Test
        fun `blockquote - should prefix every selected line`() {
            // GIVEN
            val text = "one\ntwo"

            // WHEN
            val result = applyCommentMarkdownStyle(
                text = text,
                selectionStart = 0,
                selectionEnd = 7,
                style = CommentMarkdownStyle.Blockquote
            )

            // THEN
            result.text shouldBe "> one\n> two"
        }

        @Test
        fun `list - should not stack the marker on a line that already carries it`() {
            // GIVEN
            val text = "- one"

            // WHEN
            val result = applyCommentMarkdownStyle(
                text = text,
                selectionStart = 0,
                selectionEnd = 5,
                style = CommentMarkdownStyle.UnorderedList
            )

            // THEN
            result.text shouldBe "- one"
        }

        @Test
        fun `list - should move the caret along with the inserted marker`() {
            // GIVEN the selection starts at the first character of the second line
            val text = "one\ntwo"

            // WHEN
            val result = applyCommentMarkdownStyle(
                text = text,
                selectionStart = 4,
                selectionEnd = 7,
                style = CommentMarkdownStyle.UnorderedList
            )

            // THEN only the selected line grows, and the caret follows its text
            assertSoftly {
                result.text shouldBe "one\n- two"
                result.selectionStart shouldBe 6
                result.selectionEnd shouldBe 9
            }
        }

        @Test
        fun `bold - should clamp a selection that points outside the draft`() {
            // GIVEN
            val text = "hello"

            // WHEN
            val result = applyCommentMarkdownStyle(
                text = text,
                selectionStart = 2,
                selectionEnd = 99,
                style = CommentMarkdownStyle.Bold
            )

            // THEN
            result.text shouldBe "he**llo**"
        }
    }

    @Nested
    inner class OrderedListTests {
        @Test
        fun `ordered list - should number the selected line`() {
            // GIVEN
            val text = "line"

            // WHEN
            val result = applyCommentMarkdownStyle(
                text = text,
                selectionStart = 0,
                selectionEnd = 4,
                style = CommentMarkdownStyle.OrderedList
            )

            // THEN the caret ends up behind the number of the line
            assertSoftly {
                result.text shouldBe "1. line"
                result.selectionStart shouldBe 3
                result.selectionEnd shouldBe 7
            }
        }

        @Test
        fun `ordered list - should number every selected line`() {
            // GIVEN
            val text = "one\ntwo\nthree"

            // WHEN
            val result = applyCommentMarkdownStyle(
                text = text,
                selectionStart = 0,
                selectionEnd = 13,
                style = CommentMarkdownStyle.OrderedList
            )

            // THEN
            assertSoftly {
                result.text shouldBe "1. one\n2. two\n3. three"
                result.selectionStart shouldBe 3
                result.selectionEnd shouldBe 22
            }
        }

        @Test
        fun `ordered list - should not stack the number on a line that already carries it`() {
            // GIVEN
            val text = "1. one"

            // WHEN
            val result = applyCommentMarkdownStyle(
                text = text,
                selectionStart = 0,
                selectionEnd = 6,
                style = CommentMarkdownStyle.OrderedList
            )

            // THEN
            assertSoftly {
                result.text shouldBe "1. one"
                result.selectionStart shouldBe 0
                result.selectionEnd shouldBe 6
            }
        }

        @Test
        fun `ordered list - should move the caret along with the wider number of the tenth line`() {
            // GIVEN the numbers of the lines grow, so the tenth line carries a wider number
            val text = "a\nb\nc\nd\ne\nf\ng\nh\ni\nj"

            // WHEN
            val result = applyCommentMarkdownStyle(
                text = text,
                selectionStart = 0,
                selectionEnd = 19,
                style = CommentMarkdownStyle.OrderedList
            )

            // THEN the caret follows the text, even though the numbers are not of the same length
            assertSoftly {
                result.text shouldBe "1. a\n2. b\n3. c\n4. d\n5. e\n6. f\n7. g\n8. h\n9. i\n10. j"
                result.selectionStart shouldBe 3
                result.selectionEnd shouldBe 50
            }
        }

        @Test
        fun `ordered list - should move the caret along with the number of the line it sits on`() {
            // GIVEN the caret sits on the last line, which is the first line the selection touches
            val text = "one\ntwo"

            // WHEN
            val result = applyCommentMarkdownStyle(
                text = text,
                selectionStart = 7,
                selectionEnd = 7,
                style = CommentMarkdownStyle.OrderedList
            )

            // THEN only the selected line grows, and the caret follows its text
            assertSoftly {
                result.text shouldBe "one\n1. two"
                result.selectionStart shouldBe 10
                result.selectionEnd shouldBe 10
            }
        }
    }

    @Nested
    inner class PreviewTests {
        @Test
        fun `emptyPreviewMessage - should show the empty notice for a blank draft`() {
            // WHEN
            val message = emptyPreviewMessage(draft = "   ")

            // THEN the composer shows the empty preview notice instead of a rendered draft
            message shouldBe "Nothing to preview"
        }

        @Test
        fun `emptyPreviewMessage - should be null when the draft has something to show`() {
            // WHEN
            val message = emptyPreviewMessage(draft = "**bold**")

            // THEN
            message shouldBe null
        }

        @Test
        fun `preview - should keep the draft of a non blank comment`() {
            // GIVEN
            val viewModel = composerViewModel()

            // WHEN
            viewModel.editorState.setTextAndPlaceCursorAtEnd("**bold**")

            // THEN
            viewModel.currentDraft() shouldBe "**bold**"
        }

        @Test
        fun `applyStyle - should wrap the selection the editor state holds`() {
            // GIVEN the editor holds a whole-text selection
            val viewModel = composerViewModel()
            viewModel.editorState.setTextAndSelectAll("world")

            // WHEN
            viewModel.applyStyle(style = CommentMarkdownStyle.Bold)

            // THEN the markers wrap the selected text and the caret ends after the styled region
            assertSoftly {
                viewModel.currentDraft() shouldBe "**world**"
                viewModel.editorState.selection shouldBe TextRange(index = 9)
            }
        }
    }

    @Nested
    inner class CollapsedTests {
        @Test
        fun `expand - should open the editor`() {
            // GIVEN
            val viewModel = composerViewModel()

            // WHEN
            viewModel.expand()

            // THEN
            viewModel.state.value.isExpanded shouldBe true
        }

        @Test
        fun `collapse - should drop the draft`() {
            // GIVEN
            val viewModel = composerViewModel()
            viewModel.expand()
            viewModel.editorState.setTextAndPlaceCursorAtEnd("hello")

            // WHEN
            viewModel.collapse()

            // THEN
            assertSoftly {
                viewModel.state.value.isExpanded shouldBe false
                viewModel.currentDraft() shouldBe ""
            }
        }

        @Test
        fun `collapse - should drop the failure message of a rejected submission`() {
            // GIVEN
            val viewModel = composerViewModel()
            viewModel.expand()

            // WHEN
            viewModel.collapse()

            // THEN
            viewModel.state.value.failureMessage shouldBe null
        }
    }

    @Nested
    inner class PreviewModeTests {
        @Test
        fun `togglePreview - should switch between the write and the preview mode`() {
            // GIVEN
            val viewModel = composerViewModel()

            // WHEN
            viewModel.togglePreview()

            // THEN
            viewModel.state.value.isPreview shouldBe true
        }

        @Test
        fun `togglePreview - should switch back to the write mode`() {
            // GIVEN
            val viewModel = composerViewModel()
            viewModel.togglePreview()

            // WHEN
            viewModel.togglePreview()

            // THEN
            viewModel.state.value.isPreview shouldBe false
        }
    }

    private fun composerViewModel(): CommentComposerViewModel {
        return CommentComposerViewModel(
            input = CommentInput(
                label = "comment",
                placeholder = "What do you want to say?",
                submitLabel = "Add comment now",
                submitHint = "Ctrl+Enter to add the comment",
                failureMessage = "Could not add the comment.",
                submit = { _, _ -> }
            )
        )
    }
}