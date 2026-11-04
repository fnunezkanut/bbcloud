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
import com.github.fnunezkanut.bbcloud.ui.main.CommentComposerViewModel
import com.github.fnunezkanut.bbcloud.ui.main.CommentInput
import com.github.fnunezkanut.bbcloud.ui.main.CommentMentionSource
import com.github.fnunezkanut.bbcloud.ui.main.CommentThreadCardHost
import com.github.fnunezkanut.bbcloud.ui.main.ComposerDraftCache
import com.github.fnunezkanut.bbcloud.ui.main.ConversationComposerKind
import com.github.fnunezkanut.bbcloud.ui.main.ConversationComposerTarget
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestCommentFileOpener
import com.github.fnunezkanut.bbcloud.ui.main.ResolvedThreadExpansion
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

    /**
     * Keeps the composer drafts (one per `(target, scope)` where scope is the host editor) and the focus-restore
     * request alive across inlay rebuilds. Accessed only on the EDT, like all composer state here.
     */
    private val draftCache = ComposerDraftCache { target, comment ->
        val input = when (target.kind) {
            ConversationComposerKind.Reply -> replyInput(commentId = target.commentId)
            ConversationComposerKind.Edit -> editInput(comment = comment)
        }
        CommentComposerViewModel(input = input, startsExpanded = true, mentionSource = context.mentionSource, initialMentionNames = context.mentionNames)
    }

    override val mentionSource: CommentMentionSource
        get() = context.mentionSource

    override var openComposer: ConversationComposerTarget? by mutableStateOf(null)
        private set
    private var updatingCommentIds: Set<Long> by mutableStateOf(emptySet())
    private val resolvedThreadExpansion = ResolvedThreadExpansion()
    override var avatarRevision: Int by mutableIntStateOf(0)
        private set

    override val currentUserUuid: String?
        get() = context.binding?.currentUserUuid ?: context.currentUserUuid
    override val currentUserAvatarUrl: String?
        get() = context.binding?.currentUserAvatarUrl ?: context.currentUserAvatarUrl

    /** Read from the shared context on every access, so the inline cards follow the same snapshot as the comments they show. */
    override val mentions: Map<String, String>
        get() = context.mentionNames

    override val mentionsPending: Boolean
        get() = context.mentionsPending

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

    override fun isResolvedThreadExpanded(rootCommentId: Long): Boolean {
        return resolvedThreadExpansion[rootCommentId]
    }

    override fun setResolvedThreadExpanded(rootCommentId: Long, expanded: Boolean) {
        LOG.info("User set resolved-thread-expanded=$expanded for thread $rootCommentId in pull request #$pullRequestIdValue")
        resolvedThreadExpansion[rootCommentId] = expanded
    }

    override fun isUpdating(commentId: Long): Boolean {
        return commentId in updatingCommentIds
    }

    override fun openReply(commentId: Long) {
        LOG.info("User opened reply for comment $commentId in pull request #$pullRequestIdValue")
        switchComposerTarget(target = ConversationComposerTarget(commentId = commentId, kind = ConversationComposerKind.Reply))
    }

    override fun openEdit(commentId: Long) {
        LOG.info("User opened edit for comment $commentId in pull request #$pullRequestIdValue")
        switchComposerTarget(target = ConversationComposerTarget(commentId = commentId, kind = ConversationComposerKind.Edit))
    }

    /** Only one composer is open per controller, so moving to another target drops the drafts of the previous one. */
    private fun switchComposerTarget(target: ConversationComposerTarget) {
        if (openComposer != target) {
            draftCache.clear()
        }
        openComposer = target
    }

    override fun closeComposer() {
        openComposer = null
        draftCache.clear()
        draftCache.clearFocusRestore()
    }

    override fun composerViewModel(target: ConversationComposerTarget, comment: PullRequestComment, scope: Any): CommentComposerViewModel {
        return draftCache.composerViewModel(target = target, comment = comment, scope = scope)
    }

    /** Drops the cached drafts whose surface scope is [scope]; called when an editor that showed the card is released. */
    fun forgetScope(scope: Any) {
        draftCache.forgetScope(scope = scope)
    }

    /** Records that the composer of [commentId] in [editor] should take keyboard focus after the next inlay rebuild. */
    fun markFocusRestore(editor: Any, commentId: Long) {
        draftCache.markFocusRestore(editor = editor, commentId = commentId)
    }

    /** Consumes a pending focus-restore request for [editor] and [threadCommentIds]; see [ComposerDraftCache]. */
    fun consumeFocusRestore(editor: Any, threadCommentIds: Set<Long>): Boolean {
        return draftCache.consumeFocusRestore(editor = editor, threadCommentIds = threadCommentIds, openCommentId = openComposer?.commentId)
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

    /** The inlay is drawn inside the file it comments on, so that file always exists. */
    override fun fileExists(path: String): Boolean {
        return true
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