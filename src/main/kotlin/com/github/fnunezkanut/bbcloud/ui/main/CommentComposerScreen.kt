@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.CommentMentionResolver
import com.github.fnunezkanut.bbcloud.ui.components.commentThreadContentBackground
import com.github.fnunezkanut.bbcloud.ui.components.dottedBorder
import com.github.fnunezkanut.bbcloud.ui.components.reviewBarPurple
import com.github.fnunezkanut.bbcloud.ui.otherprojects.toComposeImageBitmap
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.LocalComponent
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
import org.jetbrains.jewel.ui.theme.textAreaStyle
import javax.swing.Icon

/** Log category for the top-level composables of this file; `thisLogger()` would bind to the wrong Compose receiver. */
private object CommentComposerScreenLogCategory

private val LOG = logger<CommentComposerScreenLogCategory>()

private val COMPOSER_AVATAR_SIZE = 24.dp
private val COMPOSER_EDITOR_MIN_HEIGHT = 80.dp

/** The inner padding between the dotted border and the content of the composer, applied only when the border is shown. */
private val COMPOSER_BORDER_PADDING = 8.dp

/**
 * The default cap of the editor height. Jewel `TextArea` scrolls internally and fails to measure against an unbounded
 * maximum height, and inline diff hosts provide one through `wrapContentHeight(unbounded = true)`, so the cap is always
 * finite. Drafts taller than the cap scroll inside the editor.
 */
internal val COMPOSER_EDITOR_MAX_HEIGHT = 320.dp

/**
 * Returns the finite editor height cap for [requested]. An unspecified or infinite request falls back to
 * [COMPOSER_EDITOR_MAX_HEIGHT], and a finite request is never smaller than [COMPOSER_EDITOR_MIN_HEIGHT], so `heightIn`
 * receives a valid range.
 */
internal fun composerEditorMaxHeight(requested: Dp): Dp {
    if (!requested.value.isFinite()) {
        return COMPOSER_EDITOR_MAX_HEIGHT
    }
    return maxOf(COMPOSER_EDITOR_MIN_HEIGHT, requested)
}

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
 * The comment composer of the Activity timeline. It is a native Jewel editor: a collapsed placeholder that expands into
 * a Markdown text area with a styling toolbar, a write and preview mode, and the submit and cancel actions.
 *
 * The same composer serves the top level comment of the conversation and, with [showsCollapsedField] turned off, the
 * reply and the edit editor of a single thread.
 *
 * With [showsDottedBorder] turned on, the whole composer is outlined with a dotted purple border. It is off by default, so
 * the reply and edit editors of a thread keep only the solid border of the thread card.
 */
@Composable
internal fun CommentComposerScreen(
    viewModel: CommentComposerViewModel,
    avatarIcon: Icon,
    modifier: Modifier = Modifier,
    showsCollapsedField: Boolean = true,
    showsDottedBorder: Boolean = false,
    avatarSize: Dp = COMPOSER_AVATAR_SIZE,
    editorMaxHeight: Dp = COMPOSER_EDITOR_MAX_HEIGHT,
    requestEditorFocus: Boolean = false,
    focusRequestKey: Any? = null,
    onEditorFocusRequested: () -> Unit = {},
    onCollapsed: () -> Unit = {},
    onSubmitted: () -> Unit = {}
) {
    val state by viewModel.state
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(commentThreadContentBackground())
            .then(
                if (showsDottedBorder) {
                    Modifier
                        .dottedBorder(color = reviewBarPurple())
                        .padding(COMPOSER_BORDER_PADDING)
                } else {
                    Modifier
                }
            ),
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
                ExpandedCommentEditor(
                    viewModel = viewModel,
                    onCollapsed = onCollapsed,
                    onSubmitted = onSubmitted,
                    editorMaxHeight = editorMaxHeight,
                    requestEditorFocus = requestEditorFocus,
                    focusRequestKey = focusRequestKey,
                    onEditorFocusRequested = onEditorFocusRequested
                )
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
private fun ExpandedCommentEditor(
    viewModel: CommentComposerViewModel,
    onCollapsed: () -> Unit,
    onSubmitted: () -> Unit,
    editorMaxHeight: Dp,
    requestEditorFocus: Boolean = false,
    focusRequestKey: Any? = null,
    onEditorFocusRequested: () -> Unit = {}
) {
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
            // In preview mode CommentEditorArea is not composed, so its focus LaunchedEffect never runs; nothing to do.
            CommentPreview(draft = viewModel.currentDraft(), mentionNames = viewModel.mentionNames)
        } else {
            CommentEditorArea(
                viewModel = viewModel,
                onSubmit = submit,
                editorMaxHeight = editorMaxHeight,
                requestEditorFocus = requestEditorFocus,
                focusRequestKey = focusRequestKey,
                onEditorFocusRequested = onEditorFocusRequested
            )
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
private fun CommentEditorArea(
    viewModel: CommentComposerViewModel,
    onSubmit: () -> Unit,
    editorMaxHeight: Dp = COMPOSER_EDITOR_MAX_HEIGHT,
    requestEditorFocus: Boolean = false,
    focusRequestKey: Any? = null,
    onEditorFocusRequested: () -> Unit = {}
) {
    // The editor is driven by the view model's TextFieldState: it owns the text and the selection, and keeps the
    // selection across focus loss, so the styling toolbar wraps the text the user selected even after a button takes
    // the focus.
    val focusRequester = remember { FocusRequester() }
    if (requestEditorFocus) {
        // One frame is awaited so the FocusRequester is attached before requestFocus is called. The caller moves AWT
        // focus to the host surface in onEditorFocusRequested first, so the Compose request can take effect. The draft
        // keeps its caret at the end of the text (see CommentComposerViewModel.editorState), so no selection is reset.
        LaunchedEffect(focusRequestKey) {
            withFrameNanos { }
            onEditorFocusRequested()
            runCatching { focusRequester.requestFocus() }.onFailure { failure ->
                LOG.debug("Could not focus the comment editor: ${failure.message}")
            }
        }
    }
    val suggestions = viewModel.mentionSuggestions
    val suggestionScope = rememberCoroutineScope()
    if (suggestions != null) {
        LaunchedEffect(viewModel) {
            snapshotFlow { viewModel.editorState.text.toString() to viewModel.editorState.selection }
                .collect { (text, selection) ->
                    suggestions.onEditorChanged(
                        text = text,
                        selectionStart = selection.start,
                        selectionEnd = selection.end,
                        coroutineScope = suggestionScope
                    )
                }
        }
    }
    val textLayout = remember { mutableStateOf<(() -> TextLayoutResult?)?>(null) }
    val textAreaBounds = remember { mutableStateOf<Rect?>(null) }
    val scrollState = rememberScrollState()
    val mentionNamesEmpty = viewModel.mentionNames.isEmpty()
    val outputTransformation = remember(viewModel) {
        if (suggestions == null && mentionNamesEmpty) {
            null
        } else {
            OutputTransformation {
                val replacements = mentionDisplayReplacements(text = toString(), names = viewModel.mentionNames)
                replacements.asReversed().forEach { replacement -> replace(replacement.start, replacement.end, replacement.display) }
            }
        }
    }
    TextArea(
        state = viewModel.editorState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = COMPOSER_EDITOR_MIN_HEIGHT, max = composerEditorMaxHeight(requested = editorMaxHeight))
            .focusRequester(focusRequester)
            .onFocusChanged { focusState -> if (!focusState.isFocused) suggestions?.close() }
            .onGloballyPositioned { coordinates -> textAreaBounds.value = coordinates.boundsInRoot() }
            .onPreviewKeyEvent { keyEvent ->
                handleEditorKeyEvent(
                    keyEvent = keyEvent,
                    viewModel = viewModel,
                    suggestions = suggestions,
                    onSubmit = onSubmit
                )
            },
        onTextLayout = { getResult -> textLayout.value = getResult },
        scrollState = scrollState,
        outputTransformation = outputTransformation,
        placeholder = { Text(text = viewModel.placeholder()) }
    )
    if (suggestions != null) {
        CommentMentionPopupHost(
            viewModel = viewModel,
            suggestions = suggestions,
            textLayout = textLayout.value,
            textAreaBounds = textAreaBounds.value,
            scrollState = scrollState,
            focusRequester = focusRequester,
            onEditorFocusRequested = onEditorFocusRequested
        )
    }
}

/**
 * Routes a key event while the editor is focused. Ctrl/Cmd+Enter always submits. While the suggestion popup is open,
 * Up/Down move the selection, Enter/Tab pick the selected candidate, and Escape dismisses the list; everything else
 * falls through so normal typing keeps working.
 */
private fun handleEditorKeyEvent(
    keyEvent: KeyEvent,
    viewModel: CommentComposerViewModel,
    suggestions: CommentMentionSuggestions?,
    onSubmit: () -> Unit
): Boolean {
    if (keyEvent.type == KeyEventType.KeyDown &&
        keyEvent.key == Key.Enter &&
        (keyEvent.isCtrlPressed || keyEvent.isMetaPressed)
    ) {
        suggestions?.close()
        onSubmit()
        return true
    }
    if (suggestions == null || keyEvent.type != KeyEventType.KeyDown || !suggestions.state.value.isOpen) {
        return false
    }
    val hasModifier = keyEvent.isCtrlPressed || keyEvent.isMetaPressed || keyEvent.isAltPressed
    return when {
        keyEvent.key == Key.DirectionDown -> {
            suggestions.moveSelection(delta = 1)
            true
        }
        keyEvent.key == Key.DirectionUp -> {
            suggestions.moveSelection(delta = -1)
            true
        }
        (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter || keyEvent.key == Key.Tab) && !hasModifier -> {
            val candidate = suggestions.selected() ?: return false
            viewModel.insertMention(candidate = candidate)
            suggestions.onPicked(candidate = candidate, via = "keyboard")
            true
        }
        keyEvent.key == Key.Escape -> {
            suggestions.dismiss(byUser = true)
            true
        }
        else -> {
            false
        }
    }
}

/**
 * Hosts the @mention suggestion popup for [viewModel]. It owns the popup's avatar cache and prefetch, opens and
 * positions a plugin-owned [CommentMentionPopup] under the `@` caret while the list is open, and closes it on dispose.
 */
@Composable
private fun CommentMentionPopupHost(
    viewModel: CommentComposerViewModel,
    suggestions: CommentMentionSuggestions,
    textLayout: (() -> TextLayoutResult?)?,
    textAreaBounds: Rect?,
    scrollState: ScrollState,
    focusRequester: FocusRequester,
    onEditorFocusRequested: () -> Unit
) {
    val state = suggestions.state.value
    val owner = LocalCommentPopupOwner.current?.invoke() ?: LocalComponent.current
    val density = LocalDensity.current
    val contentPadding = JewelTheme.textAreaStyle.metrics.contentPadding
    val coroutineScope = rememberCoroutineScope()
    val avatarCache = remember { AuthorAvatarCache(avatarSize = 20) }
    val avatarRevision = remember { mutableIntStateOf(0) }
    LaunchedEffect(state.items) {
        avatarCache.prefetch(
            urls = state.items.mapNotNull { candidate -> candidate.avatarUrl },
            coroutineScope = coroutineScope,
            onLoaded = { avatarRevision.intValue++ }
        )
    }
    val popup = remember { mutableStateOf<CommentMentionPopup?>(null) }
    val hasStatusRow = state.isLoadingWorkspace || state.items.isEmpty() || state.workspaceLoadFailed || state.isWorkspaceTruncated
    LaunchedEffect(state.isOpen, state.items, state.isLoadingWorkspace, textAreaBounds, state.query?.anchorStart) {
        val bounds = textAreaBounds
        if (state.isOpen && owner.isShowing && bounds != null) {
            val current = popup.value ?: CommentMentionPopup(
                owner = owner,
                suggestions = suggestions,
                avatarCache = avatarCache,
                avatarRevision = avatarRevision,
                onPick = { candidate ->
                    viewModel.insertMention(candidate = candidate)
                    suggestions.onPicked(candidate = candidate, via = "mouse")
                    onEditorFocusRequested()
                    runCatching { focusRequester.requestFocus() }
                },
                onClosedExternally = { suggestions.close() }
            ).also { popup.value = it }
            current.update(rowCount = state.items.size, hasStatusRow = hasStatusRow)
            val anchor = state.query?.anchorStart
            val cursorRect = if (anchor != null) textLayout?.invoke()?.getCursorRect(anchor) else null
            val point = with(density) {
                mentionPopupLocation(
                    textAreaBoundsInRoot = bounds,
                    cursorRect = cursorRect,
                    contentPaddingLeftPx = contentPadding.calculateLeftPadding(LayoutDirection.Ltr).toPx(),
                    contentPaddingTopPx = contentPadding.calculateTopPadding().toPx(),
                    scrollOffsetPx = scrollState.value.toFloat(),
                    density = density.density
                )
            }
            current.showAt(point = RelativePoint(owner, point))
        } else {
            popup.value?.close()
            popup.value = null
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            popup.value?.close()
            popup.value = null
        }
    }
}

/**
 * The Markdown draft editor on its own: the styling toolbar and either the preview or the text area, with no avatar and
 * no action buttons. The new pull request form reuses this for its description field, so the description editor matches
 * the comment composer's write and preview experience.
 *
 * Jewel's `TextArea` scrolls internally and fails to measure against an unbounded height, so the editor height is always
 * capped; [editorMaxHeight] defaults to [COMPOSER_EDITOR_MAX_HEIGHT] and is coerced to a finite value by
 * [composerEditorMaxHeight].
 */
@Composable
internal fun MarkdownDraftEditor(viewModel: CommentComposerViewModel, modifier: Modifier = Modifier, editorMaxHeight: Dp = COMPOSER_EDITOR_MAX_HEIGHT) {
    val state by viewModel.state
    Column(modifier = modifier.fillMaxWidth()) {
        CommentMarkdownToolbar(viewModel = viewModel)
        if (state.isPreview) {
            CommentPreview(draft = viewModel.currentDraft(), mentionNames = viewModel.mentionNames)
        } else {
            CommentEditorArea(viewModel = viewModel, onSubmit = {}, editorMaxHeight = editorMaxHeight)
        }
    }
}

/** The preview of the draft, rendered the same way an existing comment is rendered, with mention tokens resolved. */
@Composable
private fun CommentPreview(draft: String, mentionNames: Map<String, String>) {
    val emptyMessage = emptyPreviewMessage(draft = draft)
    if (emptyMessage != null) {
        Text(text = emptyMessage, color = JewelTheme.globalColors.text.info)
        return
    }
    MarkdownDescription(markdown = CommentMentionResolver.resolveMentions(body = draft, mentions = mentionNames))
}