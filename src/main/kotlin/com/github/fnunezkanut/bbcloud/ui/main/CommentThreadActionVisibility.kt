package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.intellij.icons.AllIcons
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import javax.swing.Icon

/** The actions offered under a comment; the order here is also the order the buttons are rendered in. */
enum class CommentThreadAction {
    Reply,
    Edit,
    Resolve,
    Delete
}

/** Decides which comment actions are offered, and how they are labelled and decorated. */
object CommentThreadActionVisibility {
    /** The legacy footer action set; the Conversation footer currently renders only Reply. */
    fun visibleActions(comment: PullRequestComment, currentUserUuid: String?): List<CommentThreadAction> {
        val authorUuid = comment.author?.uuid
        val isOwnedByCurrentUser = currentUserUuid != null && authorUuid != null && authorUuid == currentUserUuid
        return buildList {
            add(CommentThreadAction.Reply)
            if (isOwnedByCurrentUser && !comment.isDeleted) {
                add(CommentThreadAction.Edit)
            }
        }
    }

    /**
     * Returns the icon actions shown in a gray conversation meta header. Thread resolution is offered in the footer
     * of each open thread rather than in this header.
     */
    internal fun metaHeaderActions(
        comment: PullRequestComment,
        headlineKind: ConversationMetaHeadlineKind,
        currentUserUuid: String?
    ): List<CommentThreadAction> {
        if (currentUserUuid == null || comment.isDeleted) {
            return emptyList()
        }
        val isOwnedByCurrentUser = comment.author?.uuid == currentUserUuid
        return buildList {
            if (isOwnedByCurrentUser) {
                add(CommentThreadAction.Edit)
                add(CommentThreadAction.Delete)
            }
        }
    }

    /** The visible label used by the footer and as the accessible description of a meta action. */
    fun label(action: CommentThreadAction): String {
        return when (action) {
            CommentThreadAction.Reply -> BbCloudBundle.message("main.branch.comments.actions.reply")
            CommentThreadAction.Edit -> BbCloudBundle.message("main.branch.comments.actions.edit")
            CommentThreadAction.Resolve -> BbCloudBundle.message("main.branch.comments.actions.resolve")
            CommentThreadAction.Delete -> BbCloudBundle.message("main.branch.comments.actions.delete")
        }
    }

    fun icon(action: CommentThreadAction): Icon {
        return when (action) {
            CommentThreadAction.Reply -> AllIcons.Actions.Back
            CommentThreadAction.Edit -> AllIcons.Actions.Edit
            CommentThreadAction.Resolve -> AllIcons.Actions.Checked
            CommentThreadAction.Delete -> AllIcons.Actions.Close
        }
    }

    fun iconKey(action: CommentThreadAction): IconKey {
        return when (action) {
            CommentThreadAction.Reply -> AllIconsKeys.Actions.Back
            CommentThreadAction.Edit -> AllIconsKeys.Actions.Edit
            CommentThreadAction.Resolve -> AllIconsKeys.Actions.Checked
            CommentThreadAction.Delete -> AllIconsKeys.Actions.Close
        }
    }

    fun tooltip(action: CommentThreadAction): String {
        return label(action = action)
    }

    fun outlineColor(action: CommentThreadAction): androidx.compose.ui.graphics.Color {
        return when (action) {
            CommentThreadAction.Resolve -> ActionButtonOutlines.blue()
            CommentThreadAction.Edit -> ActionButtonOutlines.orange()
            CommentThreadAction.Delete -> ActionButtonOutlines.red()
            CommentThreadAction.Reply -> ActionButtonOutlines.blue()
        }
    }
}