@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.ui.components.commentThreadContentBackground
import com.github.fnunezkanut.bbcloud.ui.otherprojects.toComposeImageBitmap
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import javax.swing.Icon

private val COMPOSER_AVATAR_SIZE = 24.dp
private val COMPOSER_EDITOR_MIN_HEIGHT = 80.dp

/** The styles the toolbar of the composer offers, in the order the toolbar shows them. */
private val COMPOSER_TOOLBAR_STYLES = listOf(
    CommentMarkdownStyle.Bold,
    CommentMarkdownStyle.Italic,
    CommentMarkdownStyle.Strikethrough,
    CommentMarkdownStyle.UnorderedList,
    CommentMarkdownStyle.OrderedList,
    CommentMarkdownStyle.Blockquote,
    CommentMarkdownStyle.InlineCode,
    CommentMarkdownStyle.Link
)

/** The height of the separator that divides the toolbar from the preview button. */
private val COMPOSER_TOOLBAR_DIVIDER_HEIGHT = 16.dp

private val composerActionButtonModifier = Modifier.pointerHoverIcon(PointerIcon.Hand)

/**
 * The comment composer of the Conversation tab. It is a native Jewel editor: a collapsed placeholder that expands into
 * a Markdown text area with a styling toolbar, a write and preview mode, and the submit and cancel actions.
 *
 * The same composer serves the top level comment of the conversation and, with [showsCollapsedField] turned off, the
 * reply and the edit editor of a single thread.
 */
@Composable
internal fun CommentComposerScreen(
    viewModel: CommentComposerViewModel,
    avatarIcon: Icon,
    modifier: Modifier = Modifier,
    showsCollapsedField: Boolean = true,
    avatarSize: Dp = COMPOSER_AVATAR_SIZE,
    onCollapsed: () -> Unit = {},
    onSubmitted: () -> Unit = {}
) {
    val state by viewModel.state
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(commentThreadContentBackground()),
        verticalAlignment = Alignment.Top
    ) {
        Image(
            bitmap = avatarIcon.toComposeImageBitmap(),
            contentDescription = "Author avatar",
            modifier = Modifier.size(avatarSize),
            filterQuality = FilterQuality.High
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            if (!state.isExpanded) {
                if (showsCollapsedField) {
                    CollapsedCommentField(
                        placeholder = viewModel.placeholder(),
                        onClick = { viewModel.expand() }
                    )
                }
            } else {
                ExpandedCommentEditor(viewModel = viewModel, onCollapsed = onCollapsed, onSubmitted = onSubmitted)
            }
        }
    }
}

/**
 * The collapsed composer: a one-line Jewel field that shows the placeholder of the input and opens the editor when it
 * takes the focus.
 */
@Composable
private fun CollapsedCommentField(placeholder: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused) {
        if (isFocused) {
            onClick()
        }
    }
    TextField(
        value = remember { TextFieldValue() },
        onValueChange = { },
        modifier = Modifier.fillMaxWidth(),
        readOnly = true,
        interactionSource = interactionSource,
        placeholder = { Text(text = placeholder) }
    )
}

/** The expanded composer: the styling toolbar, the editor or its preview, and the submit and cancel actions. */
@Composable
private fun ExpandedCommentEditor(viewModel: CommentComposerViewModel, onCollapsed: () -> Unit, onSubmitted: () -> Unit) {
    val state by viewModel.state
    val coroutineScope = rememberCoroutineScope()
    val submit: () -> Unit = {
        coroutineScope.launch {
            if (viewModel.submit()) {
                onSubmitted()
            }
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        CommentMarkdownToolbar(viewModel = viewModel)
        if (state.isPreview) {
            CommentPreview(draft = viewModel.currentDraft())
        } else {
            CommentEditorArea(viewModel = viewModel, onSubmit = submit)
        }
        state.failureMessage?.let { message ->
            Text(
                text = message,
                color = MainPanelStatusSupport.errorForeground(),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                modifier = composerActionButtonModifier,
                onClick = {
                    viewModel.collapse()
                    onCollapsed()
                }
            ) {
                Text(text = BbCloudBundle.message("main.branch.comments.cancel"))
            }
            Tooltip(tooltip = { Text(text = viewModel.submitHint()) }) {
                DefaultButton(
                    modifier = composerActionButtonModifier,
                    onClick = submit,
                    enabled = !state.isSubmitting
                ) {
                    Text(text = viewModel.submitLabel())
                }
            }
        }
    }
}

/**
 * The styling toolbar of the composer. The platform Markdown actions are not available outside a platform editor, so
 * every button is still a plain text transform of the draft.
 *
 * Every button carries a platform-style icon, so the toolbar reads the way the platform Markdown editor toolbar reads.
 * The preview button carries a real platform icon, and its tooltip names the mode it switches to.
 */
@Composable
private fun CommentMarkdownToolbar(viewModel: CommentComposerViewModel) {
    val state by viewModel.state
    Row(
        modifier = Modifier.padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        COMPOSER_TOOLBAR_STYLES.forEach { style ->
            CommentMarkdownStyleButton(style = style, onClick = { viewModel.applyStyle(style) })
        }
        Divider(
            orientation = Orientation.Vertical,
            modifier = Modifier.height(COMPOSER_TOOLBAR_DIVIDER_HEIGHT)
        )
        val previewLabel = if (state.isPreview) {
            BbCloudBundle.message("main.branch.comments.preview.toggleBack")
        } else {
            BbCloudBundle.message("main.branch.comments.preview")
        }
        Tooltip(tooltip = { Text(text = previewLabel) }) {
            IconButton(onClick = { viewModel.togglePreview() }) {
                Icon(
                    key = AllIconsKeys.Actions.Preview,
                    contentDescription = previewLabel
                )
            }
        }
    }
}

/**
 * One style button of the toolbar. The button carries the platform-style icon of the style, so it reads as the control
 * it is, the way the platform Markdown editor toolbar reads. The tooltip names the style and doubles as the content
 * description of the icon for assistive technology.
 */
@Composable
private fun CommentMarkdownStyleButton(style: CommentMarkdownStyle, onClick: () -> Unit) {
    val tooltip = BbCloudBundle.message(style.tooltipKey)
    Tooltip(tooltip = { Text(text = tooltip) }) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.semantics { contentDescription = tooltip }
        ) {
            Icon(
                key = style.toolbarIconKey(),
                contentDescription = tooltip
            )
        }
    }
}

/** The platform-style icon of a toolbar style. */
private fun CommentMarkdownStyle.toolbarIconKey(): IconKey {
    return when (this) {
        CommentMarkdownStyle.Bold -> BbCloudCommentToolbarIcons.bold
        CommentMarkdownStyle.Italic -> BbCloudCommentToolbarIcons.italic
        CommentMarkdownStyle.Strikethrough -> BbCloudCommentToolbarIcons.strikethrough
        CommentMarkdownStyle.UnorderedList -> BbCloudCommentToolbarIcons.unorderedList
        CommentMarkdownStyle.OrderedList -> BbCloudCommentToolbarIcons.orderedList
        CommentMarkdownStyle.Blockquote -> BbCloudCommentToolbarIcons.blockquote
        CommentMarkdownStyle.InlineCode -> BbCloudCommentToolbarIcons.inlineCode
        CommentMarkdownStyle.Link -> BbCloudCommentToolbarIcons.link
        // The Header style stays in the model but the toolbar shows no button for it, so it never asks for an icon.
        CommentMarkdownStyle.Header -> BbCloudCommentToolbarIcons.bold
    }
}

/**
 * The text area the draft is written in. The submit shortcut of the platform comment editor, a Ctrl or Cmd with Enter, is
 * honoured here as well, so the hint of the submit button stays accurate.
 */
@Composable
private fun CommentEditorArea(viewModel: CommentComposerViewModel, onSubmit: () -> Unit, editorMaxHeight: Dp = Dp.Unspecified) {
    // The editor is driven by the view model's TextFieldState: it owns the text and the selection, and keeps the
    // selection across focus loss, so the styling toolbar wraps the text the user selected even after a button takes
    // the focus.
    TextArea(
        state = viewModel.editorState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = COMPOSER_EDITOR_MIN_HEIGHT, max = editorMaxHeight)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.key == Key.Enter &&
                    (keyEvent.isCtrlPressed || keyEvent.isMetaPressed)
                ) {
                    onSubmit()
                    true
                } else {
                    false
                }
            },
        placeholder = { Text(text = viewModel.placeholder()) }
    )
}

/**
 * The Markdown draft editor on its own: the styling toolbar and either the preview or the text area, with no avatar and
 * no action buttons. The new pull request form reuses this for its description field, so the description editor matches
 * the comment composer's write and preview experience.
 *
 * Jewel's `TextArea` scrolls internally and fails to measure against an unbounded height, so a caller placed inside a
 * vertically scrollable parent must pass a bounded [editorMaxHeight].
 */
@Composable
internal fun MarkdownDraftEditor(viewModel: CommentComposerViewModel, modifier: Modifier = Modifier, editorMaxHeight: Dp = Dp.Unspecified) {
    val state by viewModel.state
    Column(modifier = modifier.fillMaxWidth()) {
        CommentMarkdownToolbar(viewModel = viewModel)
        if (state.isPreview) {
            CommentPreview(draft = viewModel.currentDraft())
        } else {
            CommentEditorArea(viewModel = viewModel, onSubmit = {}, editorMaxHeight = editorMaxHeight)
        }
    }
}

/** The preview of the draft, rendered the same way an existing comment is rendered. */
@Composable
private fun CommentPreview(draft: String) {
    val emptyMessage = emptyPreviewMessage(draft = draft)
    if (emptyMessage != null) {
        Text(text = emptyMessage, color = JewelTheme.globalColors.text.info)
        return
    }
    MarkdownDescription(markdown = draft)
}