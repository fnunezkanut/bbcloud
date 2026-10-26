package com.github.fnunezkanut.bbcloud.ui.diff

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.CommentChange
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestCommentThreads
import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.review.BbCloudPullRequestViewedThreads
import com.github.fnunezkanut.bbcloud.review.BbCloudWorktreePullRequestContext
import com.github.fnunezkanut.bbcloud.review.PullRequestThreadFingerprint
import com.github.fnunezkanut.bbcloud.review.PullRequestViewedThreadKey
import com.github.fnunezkanut.bbcloud.ui.main.AuthorAvatarCache
import com.github.fnunezkanut.bbcloud.ui.main.CommentInput
import com.github.fnunezkanut.bbcloud.ui.main.CommentThreadCardHost
import com.github.fnunezkanut.bbcloud.ui.main.ConversationComposerKind
import com.github.fnunezkanut.bbcloud.ui.main.ConversationComposerTarget
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestCommentFileOpener
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import java.awt.datatransfer.StringSelection
import javax.swing.Icon

/** Owns embedded thread interaction state independently of a rebuilt editor inlay. */
internal class EditorCommentThreadController(
    private val projectValue: Project,
    private val pullRequestIdValue: Long,
    private val relativePath: String,
    private val coroutineScope: CoroutineScope,
    private val context: BbCloudWorktreePullRequestContext = BbCloudWorktreePullRequestContext.getInstance(projectValue),
    private val avatarCache: AuthorAvatarCache = AuthorAvatarCache(avatarSize = 16)
) : CommentThreadCardHost {
    private val viewedThreads = BbCloudPullRequestViewedThreads.getInstance()

    override var openComposer: ConversationComposerTarget? by mutableStateOf(null)
        private set
    private var updatingCommentIds: Set<Long> by mutableStateOf(emptySet())
    override var avatarRevision: Int by mutableIntStateOf(0)
        private set

    override val currentUserUuid: String?
        get() = context.binding?.currentUserUuid ?: context.currentUserUuid
    override val currentUserAvatarUrl: String?
        get() = context.binding?.currentUserAvatarUrl ?: context.currentUserAvatarUrl

    override val embeddedInEditor: Boolean
        get() = true

    override fun project(): Project {
        return projectValue
    }

    override fun currentPullRequestId(): Long {
        return pullRequestIdValue
    }

    override fun isThreadViewed(thread: TimelineEntry.CommentThread): Boolean {
        return viewedThreads.isViewed(
            workspaceSlug = workspaceSlug(),
            pullRequestId = pullRequestIdValue,
            relativePath = threadPath(thread = thread),
            rootCommentId = thread.root.id,
            fingerprint = PullRequestThreadFingerprint.of(thread = thread)
        )
    }

    override fun setThreadViewed(thread: TimelineEntry.CommentThread, viewed: Boolean) {
        LOG.info("User set thread-viewed=$viewed for thread ${thread.root.id} in pull request #$pullRequestIdValue")
        viewedThreads.setViewed(
            workspaceSlug = workspaceSlug(),
            pullRequestId = pullRequestIdValue,
            relativePath = threadPath(thread = thread),
            rootCommentId = thread.root.id,
            fingerprint = PullRequestThreadFingerprint.of(thread = thread),
            viewed = viewed
        )
    }

    override fun avatarIconFor(url: String?): Icon {
        return avatarCache.iconFor(url)
    }

    fun prefetch(thread: TimelineEntry.CommentThread) {
        val urls = listOfNotNull(thread.root.author?.avatarUrl) + thread.replies.mapNotNull { reply -> reply.author?.avatarUrl }
        if (urls.isEmpty()) return
        avatarCache.prefetch(urls = urls, coroutineScope = coroutineScope, onLoaded = { avatarRevision++ })
    }

    override fun isUpdating(commentId: Long): Boolean {
        return commentId in updatingCommentIds
    }

    override fun openReply(commentId: Long) {
        LOG.info("User opened reply for comment $commentId in pull request #$pullRequestIdValue")
        openComposer = ConversationComposerTarget(commentId = commentId, kind = ConversationComposerKind.Reply)
    }

    override fun openEdit(commentId: Long) {
        LOG.info("User opened edit for comment $commentId in pull request #$pullRequestIdValue")
        openComposer = ConversationComposerTarget(commentId = commentId, kind = ConversationComposerKind.Edit)
    }

    override fun closeComposer() {
        openComposer = null
    }

    override fun applyCommentChange(change: CommentChange, onResult: (Boolean) -> Unit) {
        LOG.info("User applied ${change::class.simpleName} on comment ${change.commentId} in pull request #$pullRequestIdValue")
        val commentId = change.commentId
        if (commentId != null) {
            if (isUpdating(commentId = commentId)) {
                onResult(false)
                return
            }
            updatingCommentIds = updatingCommentIds + commentId
        }
        context.applyCommentChange(change = change) { accepted ->
            if (commentId != null) {
                updatingCommentIds = updatingCommentIds - commentId
            }
            if (accepted) {
                closeComposer()
            }
            onResult(accepted)
        }
    }

    override fun deleteComment(commentId: Long) {
        applyCommentChange(change = CommentChange.Delete(commentId = commentId))
    }

    override fun replyInput(commentId: Long): CommentInput {
        return CommentInput(
            label = "reply",
            placeholder = BbCloudBundle.message("main.branch.comments.reply.placeholder"),
            submitLabel = BbCloudBundle.message("main.branch.comments.reply.submit"),
            submitHint = BbCloudBundle.message("main.branch.comments.reply.submitHint", CommentInputActionsComponentFactory.submitShortcutText),
            failureMessage = BbCloudBundle.message("main.branch.comments.failed.inline"),
            submit = { content, onResult -> applyCommentChange(CommentChange.Reply(commentId, content), onResult) }
        )
    }

    override fun editInput(comment: PullRequestComment): CommentInput {
        return CommentInput(
            label = "comment edit",
            placeholder = BbCloudBundle.message("main.branch.comments.edit.placeholder"),
            submitLabel = BbCloudBundle.message("main.branch.comments.edit.submit"),
            submitHint = BbCloudBundle.message("main.branch.comments.edit.submitHint", CommentInputActionsComponentFactory.submitShortcutText),
            failureMessage = BbCloudBundle.message("main.branch.comments.failed.inline"),
            initialText = comment.raw.orEmpty(),
            submit = { content, onResult -> applyCommentChange(CommentChange.Edit(comment.id, content), onResult) }
        )
    }

    override fun openFile(path: String, line: Int?) {
        LOG.info("User opened $path from embedded comment in pull request #$pullRequestIdValue")
        val file = PullRequestCommentFileOpener.resolve(project = projectValue, inlinePath = path) ?: return
        PullRequestCommentFileOpener.open(project = projectValue, file = file, fileLine = line)
    }

    override fun copyPath(path: String) {
        LOG.info("User copied the file location $path in pull request #$pullRequestIdValue")
        CopyPasteManager.getInstance().setContents(StringSelection(path))
    }

    private fun workspaceSlug(): String {
        return BbCloudConfigurationService.getInstance().workspaceSlugOrNull().orEmpty()
    }

    private fun threadPath(thread: TimelineEntry.CommentThread): String {
        return thread.root.inlinePath ?: PullRequestViewedThreadKey.PR_LEVEL_PATH_SENTINEL
    }

    fun commentById(commentId: Long): PullRequestComment? {
        val threads = PullRequestCommentThreads.threadsForFilePath(
            comments = context.comments,
            projectBasePath = projectValue.basePath,
            relativePath = relativePath
        )
        return threads.asSequence().flatMap { thread -> sequenceOf(thread.root) + thread.replies.asSequence() }
            .firstOrNull { comment -> comment.id == commentId }
    }

    companion object {
        private val LOG = logger<EditorCommentThreadController>()
    }
}