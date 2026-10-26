package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.ActivityUser
import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentSide
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry
import com.intellij.util.ui.JBUI

/**
 * The rules of the Conversation tab that do not depend on how the tab is drawn: which threads still show their body,
 * which line of an inline comment can be opened, how far from the top an in-place refresh still counts as being at the
 * top, and how a comment body, a location and a resolver are derived from what Bitbucket reported.
 *
 * They used to live in the companion object of the Swing `ActivityTimelineSection`, which the Jewel conversation screen
 * replaced; the helpers themselves are unchanged, so the diff inline-comment panel keeps rendering the same way.
 */
object ConversationThreadRules {
    private val IMAGE_TAG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)

    /** Bitbucket-hosted comment images require authentication, which Swing's HTML renderer cannot provide. */
    fun removeImages(html: String): String {
        return IMAGE_TAG.replace(html, "")
    }

    /**
     * The plugin checks out the source branch, so a before-side line number describes the destination side of the diff and
     * has no caret destination in the working tree.
     */
    fun navigableLine(comment: PullRequestComment): Int? {
        return if (comment.inlineSide == InlineCommentSide.BEFORE) null else comment.inlineLine
    }

    /** Resolved threads keep only the resolver card. Bitbucket still returns their comments and replies, so rendering the bodies would suggest the thread is still open. */
    fun showsThreadBody(thread: TimelineEntry.CommentThread): Boolean {
        return !thread.root.isResolved
    }

    /** How far from the top an in-place refresh still counts as being at the top. */
    private val NEAR_TOP_THRESHOLD = JBUI.scale(48)

    /**
     * An in-place refresh only returns to the newest entry when the reader has not scrolled away; scrolling is never
     * yanked from someone who is reading older activity.
     */
    fun shouldSnapToTop(offsetPx: Int): Boolean {
        return offsetPx <= NEAR_TOP_THRESHOLD
    }

    /**
     * The Markdown of a comment body. Bitbucket reports the Markdown source as [PullRequestComment.raw], but only
     * sometimes; when it is missing, the rendered HTML is stripped down to its text so that the comment never renders
     * blank.
     */
    fun commentBodyMarkdown(comment: PullRequestComment): String {
        val raw = comment.raw
        if (!raw.isNullOrBlank()) {
            return raw
        }
        return normalizeDescription(comment.html)
    }

    /**
     * The location of a thread: the `basename:line` of an anchored file, or null for a pull-request-level thread, which
     * carries the pull-request merge icon instead.
     */
    fun locationText(comment: PullRequestComment): String? {
        val inlinePath = comment.inlinePath ?: return null
        val displayName = inlinePath.substringAfterLast('/')
        val displayLine = comment.inlineLine
        return if (displayLine != null) "$displayName:$displayLine" else displayName
    }

    /** The full repository-relative path of an anchored comment, with the line number when Bitbucket reported one. */
    fun locationPath(comment: PullRequestComment): String? {
        val inlinePath = comment.inlinePath ?: return null
        val inlineLine = comment.inlineLine
        return if (inlineLine != null) "$inlinePath:$inlineLine" else inlinePath
    }

    /** The notice of a resolved thread; Bitbucket omits the resolver on some threads, so the name falls back to "Unknown user". */
    fun resolvedByText(resolvedBy: ActivityUser?): String {
        val name = resolvedBy?.displayName?.takeIf { displayName -> displayName.isNotBlank() }
            ?: BbCloudBundle.message("main.branch.activity.unknownUser")
        return BbCloudBundle.message("main.branch.activity.resolvedBy", name)
    }

    /** The name of a comment author; Bitbucket does not always report the author of a deleted comment. */
    fun authorName(author: ActivityUser?): String {
        return author?.displayName?.takeIf { displayName -> displayName.isNotBlank() }
            ?: BbCloudBundle.message("main.branch.activity.unknownUser")
    }
}