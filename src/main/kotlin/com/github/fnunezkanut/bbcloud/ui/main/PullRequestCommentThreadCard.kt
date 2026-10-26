@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.CommentChange
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry
import com.github.fnunezkanut.bbcloud.ui.components.BbCloudCompactOutlinedIconButton
import com.github.fnunezkanut.bbcloud.ui.components.BbCloudPluginIcons
import com.github.fnunezkanut.bbcloud.ui.components.RoundedBadge
import com.github.fnunezkanut.bbcloud.ui.components.commentThreadContentBackground
import com.github.fnunezkanut.bbcloud.ui.components.conversationMetaStripBackground
import com.github.fnunezkanut.bbcloud.ui.components.conversationThreadStripBackground
import com.github.fnunezkanut.bbcloud.ui.components.reviewBarPurple
import com.github.fnunezkanut.bbcloud.ui.otherprojects.toComposeImageBitmap
import com.intellij.openapi.ui.Messages
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

/** Which headline phrase a comment meta block shows. */
enum class ConversationMetaHeadlineKind {
    CommentedOn,
    RepliedOn
}

/** The host contract shared by Conversation and editor/diff comment cards. */
interface CommentThreadCardHost {
    val currentUserUuid: String?
    val currentUserAvatarUrl: String?
    val avatarRevision: Int
    val openComposer: ConversationComposerTarget?

    val embeddedInEditor: Boolean

    fun project(): com.intellij.openapi.project.Project?

    fun currentPullRequestId(): Long?

    /** Returns whether the current user marked this thread viewed at its current content. */
    fun isThreadViewed(thread: TimelineEntry.CommentThread): Boolean

    /** Persists whether the current user marked this thread viewed. */
    fun setThreadViewed(thread: TimelineEntry.CommentThread, viewed: Boolean)

    fun avatarIconFor(url: String?): javax.swing.Icon

    fun isUpdating(commentId: Long): Boolean

    fun openReply(commentId: Long)

    fun openEdit(commentId: Long)

    fun closeComposer()

    fun applyCommentChange(change: CommentChange, onResult: (Boolean) -> Unit = {})

    fun deleteComment(commentId: Long)

    fun replyInput(commentId: Long): CommentInput

    fun editInput(comment: PullRequestComment): CommentInput

    fun openFile(path: String, line: Int?)

    fun copyPath(path: String)
}

private val CARD_AVATAR_SIZE = 16.dp
private val CARD_LOCATION_ICON_SIZE = 16.dp
private val CARD_ACTION_GAP = 6.dp
private val CARD_AVATAR_GAP = 6.dp
private val CARD_MIDDLE_GAP = 8.dp
private val CARD_AUTHOR_HEADLINE_GAP = 8.dp
private val CARD_STRIP_HORIZONTAL_PADDING = 8.dp
private val CARD_STRIP_VERTICAL_PADDING = 6.dp
private val CARD_MIDDLE_VERTICAL_PADDING = 8.dp
private val CARD_META_BODY_PADDING = 8.dp
private val CARD_CORNER = 8.dp
private val CARD_BORDER_WIDTH = 1.dp

/** Renders the full Conversation-style thread card in any host surface. */
@Composable
internal fun PullRequestCommentThreadCard(thread: TimelineEntry.CommentThread, host: CommentThreadCardHost, modifier: Modifier = Modifier) {
    val showsBody = ConversationThreadRules.showsThreadBody(thread)
    val borderColor = if (showsBody) reviewBarPurple() else JewelTheme.globalColors.borders.normal
    val shape = RoundedCornerShape(CARD_CORNER)
    if (!showsBody) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .border(CARD_BORDER_WIDTH, borderColor, shape)
                .background(commentThreadContentBackground(), shape)
                .padding(8.dp)
        ) {
            ResolvedCardThread(thread = thread, host = host)
        }
        return
    }
    val viewed = host.isThreadViewed(thread = thread)
    Column(
        modifier = modifier.fillMaxWidth().border(CARD_BORDER_WIDTH, borderColor, shape).clip(shape)
    ) {
        CardLocationStrip(
            comment = thread.root,
            host = host,
            viewed = viewed,
            onViewedToggled = { checked -> host.setThreadViewed(thread = thread, viewed = checked) },
            showBottomBorder = !viewed
        )
        if (!viewed) {
            CardThreadMiddle(thread = thread, host = host)
            CardActionsFooter(comment = thread.root, host = host)
        }
    }
}

@Composable
private fun CardLocationStrip(
    comment: PullRequestComment,
    host: CommentThreadCardHost,
    viewed: Boolean,
    onViewedToggled: (Boolean) -> Unit,
    showBottomBorder: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(conversationThreadStripBackground())
            .then(if (showBottomBorder) Modifier.cardBottomBorder(reviewBarPurple()) else Modifier)
            .padding(horizontal = CARD_STRIP_HORIZONTAL_PADDING, vertical = CARD_STRIP_VERTICAL_PADDING),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundedBadge(
            text = BbCloudBundle.message("main.branch.comments.thread.viewed"),
            fillColor = reviewBarPurple(),
            modifier = Modifier.clickable { onViewedToggled(!viewed) }.pointerHoverIcon(PointerIcon.Hand),
            leadingContent = { Checkbox(checked = viewed, onCheckedChange = onViewedToggled) }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val path = comment.inlinePath
            if (path == null) {
                Tooltip(tooltip = { Text(text = BbCloudBundle.message("main.branch.activity.location.pr.tooltip")) }) {
                    Icon(
                        key = BbCloudPluginIcons.pr,
                        contentDescription = BbCloudBundle.message("main.branch.activity.location.pr"),
                        modifier = Modifier.size(CARD_LOCATION_ICON_SIZE)
                    )
                }
            } else {
                val locationText = ConversationThreadRules.locationText(comment)
                val fullPath = ConversationThreadRules.locationPath(comment) ?: path
                if (host.embeddedInEditor || host.project() == null) {
                    Text(text = locationText ?: path, color = JewelTheme.globalColors.text.info)
                } else {
                    Link(
                        text = locationText ?: path,
                        onClick = {
                            host.openFile(path = path, line = ConversationThreadRules.navigableLine(comment))
                        }
                    )
                }
                Tooltip(tooltip = { Text(text = BbCloudBundle.message("main.branch.activity.location.copy.tooltip")) }) {
                    IconButton(
                        onClick = { host.copyPath(path = fullPath) }
                    ) {
                        Icon(
                            key = org.jetbrains.jewel.ui.icons.AllIconsKeys.Actions.Copy,
                            contentDescription = BbCloudBundle.message("main.branch.activity.location.copy.tooltip")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardThreadMiddle(thread: TimelineEntry.CommentThread, host: CommentThreadCardHost) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(commentThreadContentBackground())
            .padding(bottom = CARD_MIDDLE_VERTICAL_PADDING),
        verticalArrangement = Arrangement.spacedBy(CARD_MIDDLE_GAP)
    ) {
        CardMetaBlock(comment = thread.root, headlineKind = ConversationMetaHeadlineKind.CommentedOn, host = host)
        thread.replies.forEach { reply ->
            key(reply.id) {
                CardMetaBlock(comment = reply, headlineKind = ConversationMetaHeadlineKind.RepliedOn, host = host)
            }
        }
        if (host.openComposer?.kind == ConversationComposerKind.Reply && host.openComposer?.commentId == thread.root.id) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = CARD_STRIP_HORIZONTAL_PADDING)) {
                CardOpenComposer(
                    comment = thread.root,
                    host = host,
                    modifier = if (host.embeddedInEditor) Modifier.fillMaxWidth() else Modifier
                )
            }
        }
    }
}

@Composable
private fun CardMetaBlock(comment: PullRequestComment, headlineKind: ConversationMetaHeadlineKind, host: CommentThreadCardHost) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(conversationMetaStripBackground())
                .cardMetaHeaderBorder(JewelTheme.globalColors.borders.normal)
                .padding(horizontal = CARD_STRIP_HORIZONTAL_PADDING, vertical = CARD_STRIP_VERTICAL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CARD_AVATAR_GAP)
        ) {
            val avatarUrl = comment.author?.avatarUrl
            val bitmap = remember(avatarUrl, host.avatarRevision) {
                host.avatarIconFor(avatarUrl).toComposeImageBitmap()
            }
            Image(bitmap = bitmap, contentDescription = "Author avatar", modifier = Modifier.size(CARD_AVATAR_SIZE), filterQuality = FilterQuality.High)
            CardMetaHeadline(
                authorName = ConversationThreadRules.authorName(comment.author),
                headlineKind = headlineKind,
                date = comment.createdOn,
                modifier = Modifier.weight(1f)
            )
            CardMetaActions(comment = comment, headlineKind = headlineKind, host = host)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(commentThreadContentBackground())
                .cardMetaBodyBorder(JewelTheme.globalColors.borders.normal)
                .padding(CARD_META_BODY_PADDING)
        ) {
            if (comment.isDeleted) {
                Text(text = BbCloudBundle.message("main.branch.activity.comment.deleted"))
            } else {
                MarkdownDescription(markdown = ConversationThreadRules.commentBodyMarkdown(comment))
            }
        }
        if (host.openComposer?.kind == ConversationComposerKind.Edit && host.openComposer?.commentId == comment.id) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = CARD_STRIP_HORIZONTAL_PADDING)) {
                CardOpenComposer(
                    comment = comment,
                    host = host,
                    modifier = if (host.embeddedInEditor) Modifier.fillMaxWidth() else Modifier
                )
            }
        }
    }
}

@Composable
private fun CardMetaHeadline(authorName: String, headlineKind: ConversationMetaHeadlineKind, date: kotlin.time.Instant, modifier: Modifier) {
    val actionKey = when (headlineKind) {
        ConversationMetaHeadlineKind.CommentedOn -> "main.branch.activity.action.commented"
        ConversationMetaHeadlineKind.RepliedOn -> "main.branch.activity.action.replied"
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CARD_AUTHOR_HEADLINE_GAP)
    ) {
        Text(text = authorName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false), overflow = TextOverflow.Ellipsis, maxLines = 1)
        Text(
            text =
                BbCloudBundle.message(actionKey) + " " + BbCloudBundle.message("main.branch.comments.reply.headlineOn") + " " +
                    PullRequestRelativeTime.format(date),
            color = JewelTheme.globalColors.text.info,
            modifier = Modifier.weight(1f),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )
    }
}

@Composable
private fun CardMetaActions(comment: PullRequestComment, headlineKind: ConversationMetaHeadlineKind, host: CommentThreadCardHost) {
    val actions = CommentThreadActionVisibility.metaHeaderActions(
        comment = comment,
        headlineKind = headlineKind,
        currentUserUuid = host.currentUserUuid
    )
    if (actions.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(CARD_ACTION_GAP), verticalAlignment = Alignment.CenterVertically) {
        actions.forEach { action ->
            val tooltip = CommentThreadActionVisibility.tooltip(action = action)
            Tooltip(tooltip = { Text(text = tooltip) }) {
                BbCloudCompactOutlinedIconButton(
                    icon = CommentThreadActionVisibility.iconKey(action = action),
                    contentDescription = tooltip,
                    outlineColor = CommentThreadActionVisibility.outlineColor(action = action),
                    enabled = !host.isUpdating(commentId = comment.id),
                    onClick = {
                        if (action == CommentThreadAction.Delete) {
                            val project = host.project()
                            if (project != null && Messages.showYesNoDialog(
                                    project,
                                    BbCloudBundle.message("main.branch.comments.delete.confirm.message"),
                                    BbCloudBundle.message("main.branch.comments.delete.confirm.title"),
                                    Messages.getQuestionIcon()
                                ) == Messages.YES
                            ) {
                                host.deleteComment(commentId = comment.id)
                            }
                        } else {
                            when (action) {
                                CommentThreadAction.Reply -> {
                                    host.openReply(commentId = comment.id)
                                }
                                CommentThreadAction.Edit -> {
                                    host.openEdit(commentId = comment.id)
                                }
                                CommentThreadAction.Delete -> {
                                    host.deleteComment(commentId = comment.id)
                                }
                                else -> {}
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CardActionsFooter(comment: PullRequestComment, host: CommentThreadCardHost) {
    Box(
        modifier = Modifier.fillMaxWidth().background(conversationThreadStripBackground()).cardTopBorder(reviewBarPurple())
            .padding(horizontal = CARD_STRIP_HORIZONTAL_PADDING, vertical = CARD_STRIP_VERTICAL_PADDING)
    ) {
        val actions = CommentThreadActionVisibility.visibleActions(comment = comment, currentUserUuid = host.currentUserUuid)
            .filter { action -> action == CommentThreadAction.Reply }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEach { action ->
                BbCloudCompactOutlinedIconButton(
                    icon = CommentThreadActionVisibility.iconKey(action = action),
                    contentDescription = CommentThreadActionVisibility.label(action = action),
                    outlineColor = ActionButtonOutlines.green(),
                    enabled = !host.isUpdating(commentId = comment.id),
                    onClick = { host.openReply(commentId = comment.id) },
                    showOutline = true,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    trailingContent = {
                        Text(text = CommentThreadActionVisibility.label(action = action))
                    }
                )
            }
            if (host.currentUserUuid != null) {
                BbCloudCompactOutlinedIconButton(
                    icon = CommentThreadActionVisibility.iconKey(action = CommentThreadAction.Resolve),
                    contentDescription = BbCloudBundle.message("main.branch.comments.actions.resolveThread"),
                    outlineColor = reviewBarPurple(),
                    enabled = !host.isUpdating(commentId = comment.id),
                    onClick = { host.applyCommentChange(CommentChange.Resolve(commentId = comment.id)) },
                    showOutline = true,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    trailingContent = {
                        Text(text = BbCloudBundle.message("main.branch.comments.actions.resolveThread"))
                    }
                )
            }
        }
    }
}

@Composable
private fun ResolvedCardThread(thread: TimelineEntry.CommentThread, host: CommentThreadCardHost) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = ConversationThreadRules.resolvedByText(thread.root.resolvedBy), color = JewelTheme.globalColors.text.info, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = { host.applyCommentChange(CommentChange.Unresolve(commentId = thread.root.id)) },
            enabled = !host.isUpdating(commentId = thread.root.id)
        ) {
            Text(text = BbCloudBundle.message("main.branch.comments.actions.unresolve"))
        }
    }
}

@Composable
private fun CardOpenComposer(comment: PullRequestComment, host: CommentThreadCardHost, modifier: Modifier = Modifier) {
    val openComposer = host.openComposer ?: return
    if (openComposer.commentId != comment.id) return
    val input = remember(openComposer.commentId, openComposer.kind, comment.raw) {
        when (openComposer.kind) {
            ConversationComposerKind.Reply -> host.replyInput(commentId = openComposer.commentId)
            ConversationComposerKind.Edit -> host.editInput(comment = comment)
        }
    }
    val composerViewModel = remember(openComposer.commentId, openComposer.kind, comment.raw) {
        CommentComposerViewModel(input = input, startsExpanded = true)
    }
    CommentComposerScreen(
        viewModel = composerViewModel,
        avatarIcon = host.avatarIconFor(host.currentUserAvatarUrl),
        modifier = modifier,
        showsCollapsedField = false,
        avatarSize = CARD_AVATAR_SIZE,
        onCollapsed = { host.closeComposer() },
        onSubmitted = { host.closeComposer() }
    )
}

private fun Modifier.cardBottomBorder(color: Color): Modifier {
    return drawBehind {
        val strokeWidth = CARD_BORDER_WIDTH.toPx()
        val y = size.height - strokeWidth / 2f
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth)
    }
}

private fun Modifier.cardTopBorder(color: Color): Modifier {
    return drawBehind {
        val strokeWidth = CARD_BORDER_WIDTH.toPx()
        val y = strokeWidth / 2f
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth)
    }
}

private fun Modifier.cardMetaHeaderBorder(color: Color): Modifier {
    return drawBehind {
        val strokeWidth = CARD_BORDER_WIDTH.toPx()
        val half = strokeWidth / 2f
        drawLine(color, Offset(0f, half), Offset(size.width, half), strokeWidth)
        drawLine(color, Offset(half, 0f), Offset(half, size.height), strokeWidth)
        drawLine(color, Offset(size.width - half, 0f), Offset(size.width - half, size.height), strokeWidth)
    }
}

private fun Modifier.cardMetaBodyBorder(color: Color): Modifier {
    return drawBehind {
        val strokeWidth = CARD_BORDER_WIDTH.toPx()
        val half = strokeWidth / 2f
        drawLine(color, Offset(half, 0f), Offset(half, size.height), strokeWidth)
        drawLine(color, Offset(size.width - half, 0f), Offset(size.width - half, size.height), strokeWidth)
        drawLine(color, Offset(0f, size.height - half), Offset(size.width, size.height - half), strokeWidth)
    }
}