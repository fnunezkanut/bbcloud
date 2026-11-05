package com.github.fnunezkanut.bbcloud.ui.air

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.ui.main.AirCommentPrompt
import com.github.fnunezkanut.bbcloud.ui.main.ConversationThreadRules
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestCommentFileOpener
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

/** Turns a pull request comment into the Air prompt and sends it to the Air chat input. */
internal object AirCommentSender {
    private val LOG = logger<AirCommentSender>()

    /** Sends [comment] to the Air chat of [project]. Call it on the EDT from the comment card's button. */
    fun send(project: Project?, comment: PullRequestComment, pullRequestId: Long?, mentions: Map<String, String>) {
        if (project == null) {
            LOG.info("Send to Air ignored for comment ${comment.id}: no project")
            return
        }
        val text = buildPrompt(project = project, comment = comment, pullRequestId = pullRequestId, mentions = mentions)
        LOG.info("User sent comment ${comment.id} to the Air chat from pull request #$pullRequestId")
        AirChatBridge.sendToChatInput(project = project, text = text)
    }

    private fun buildPrompt(project: Project, comment: PullRequestComment, pullRequestId: Long?, mentions: Map<String, String>): String {
        val inlinePath = comment.inlinePath?.takeIf { path -> path.isNotBlank() }
        val absolutePath = inlinePath?.let { path ->
            PullRequestCommentFileOpener.absolutePath(project = project, inlinePath = path) ?: path
        }
        val lineLabel = AirCommentPrompt.lineLabel(
            inlineLine = comment.inlineLine,
            inlineLineEnd = comment.inlineLineEnd,
            side = comment.inlineSide
        )
        val location = AirCommentPrompt.locationLine(
            absolutePath = absolutePath,
            lineLabel = lineLabel,
            pullRequestId = pullRequestId
        )
        val commentText = ConversationThreadRules.commentBodyMarkdown(comment = comment, mentions = mentions)
        return AirCommentPrompt.build(location = location, commentText = commentText)
    }
}