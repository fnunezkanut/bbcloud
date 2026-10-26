package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.CommentChange
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestConversation
import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.review.BbCloudPullRequestViewedThreads
import com.github.fnunezkanut.bbcloud.review.PullRequestThreadFingerprint
import com.github.fnunezkanut.bbcloud.review.PullRequestViewedThreadKey
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.TimeZone
import javax.swing.Icon
import kotlin.time.Clock

/**
 * Drives the Jewel Conversation tab. The editor session pushes the conversation through [showConversation]; the composable
 * reads [state] and renders the threads.
 *
 * The avatars come from the shared [AuthorAvatarCache]; the cache is prefetched when the conversation arrives and the
 * [PullRequestConversationUiState.avatarRevision] is bumped once the images are ready, so the composable redraws them.
 */
class PullRequestConversationViewModel(
    private val pullRequestId: () -> Long?,
    private val commentEditor: CommentEditor,
    private val avatarCache: AuthorAvatarCache = AuthorAvatarCache(avatarSize = CONVERSATION_AVATAR_SIZE)
) : CommentThreadCardHost {
    val state: MutableState<PullRequestConversationUiState> = mutableStateOf(initialState())

    override val currentUserUuid: String?
        get() = state.value.currentUserUuid
    override val currentUserAvatarUrl: String?
        get() = state.value.currentUserAvatarUrl
    override val avatarRevision: Int
        get() = state.value.avatarRevision
    override val openComposer: ConversationComposerTarget?
        get() = state.value.openComposer

    private val viewedThreads by lazy { BbCloudPullRequestViewedThreads.getInstance() }
    private var coroutineScope: CoroutineScope? = null
    private var displayedPullRequestId: Long? = null
    private var scrollRequestId = 0

    fun bind(coroutineScope: CoroutineScope) {
        this.coroutineScope = coroutineScope
    }

    fun showConversation(conversation: PullRequestConversation?, coroutineScope: CoroutineScope, inPlaceRefresh: Boolean = false) {
        bind(coroutineScope = coroutineScope)
        val currentPullRequestId = pullRequestId()
        val isSamePullRequest = displayedPullRequestId != null && displayedPullRequestId == currentPullRequestId
        displayedPullRequestId = currentPullRequestId
        if (conversation == null) {
            LOG.info("Could not load the conversation of pull request #$currentPullRequestId")
            state.value = state.value.copy(
                loadState = PullRequestConversationLoadState.Error,
                isTruncated = false,
                groups = emptyList()
            )
            return
        }
        val groups = groupConversationThreads(
            threads = conversation.threads,
            now = Clock.System.now(),
            timeZone = TimeZone.currentSystemDefault()
        )
        state.value = state.value.copy(
            loadState = PullRequestConversationLoadState.Loaded,
            isTruncated = conversation.isTruncated,
            groups = groups,
            currentUserUuid = conversation.currentUserUuid,
            currentUserAvatarUrl = conversation.currentUserAvatarUrl,
            scrollRequest = newScrollRequest(force = !inPlaceRefresh && !isSamePullRequest)
        )
        LOG.debug("Rendered ${conversation.threads.size} conversation threads for pull request #$currentPullRequestId")
        avatarCache.prefetch(
            urls = conversation.threads.flatMap { thread -> avatarUrls(thread) } + listOfNotNull(conversation.currentUserAvatarUrl),
            coroutineScope = coroutineScope,
            onLoaded = { bumpAvatarRevision() }
        )
    }

    /** Shows the newest entry again when the Conversation tab is re-selected. */
    fun onConversationTabShown() {
        LOG.debug("Conversation tab shown for pull request #${pullRequestId()}; scrolling to the newest entry")
        state.value = state.value.copy(scrollRequest = newScrollRequest(force = true))
    }

    fun reset() {
        coroutineScope = null
        displayedPullRequestId = null
        state.value = initialState()
    }

    override val embeddedInEditor: Boolean
        get() = false

    override fun avatarIconFor(url: String?): Icon {
        return avatarCache.iconFor(url)
    }

    override fun currentPullRequestId(): Long? {
        return pullRequestId()
    }

    override fun isThreadViewed(thread: TimelineEntry.CommentThread): Boolean {
        val currentPullRequestId = pullRequestId() ?: return false
        return viewedThreads.isViewed(
            workspaceSlug = workspaceSlug(),
            pullRequestId = currentPullRequestId,
            relativePath = threadPath(thread = thread),
            rootCommentId = thread.root.id,
            fingerprint = PullRequestThreadFingerprint.of(thread = thread)
        )
    }

    override fun setThreadViewed(thread: TimelineEntry.CommentThread, viewed: Boolean) {
        val currentPullRequestId = pullRequestId() ?: return
        LOG.info("User set thread-viewed=$viewed for thread ${thread.root.id} in pull request #$currentPullRequestId")
        viewedThreads.setViewed(
            workspaceSlug = workspaceSlug(),
            pullRequestId = currentPullRequestId,
            relativePath = threadPath(thread = thread),
            rootCommentId = thread.root.id,
            fingerprint = PullRequestThreadFingerprint.of(thread = thread),
            viewed = viewed
        )
    }

    /** The project the pull request is opened in; null when the view is not bound to one. */
    override fun project(): Project? {
        val project = commentEditor.project()
        return if (project == null || project.isDisposed) null else project
    }

    private fun workspaceSlug(): String {
        return BbCloudConfigurationService.getInstance().workspaceSlugOrNull().orEmpty()
    }

    private fun threadPath(thread: TimelineEntry.CommentThread): String {
        return thread.root.inlinePath ?: PullRequestViewedThreadKey.PR_LEVEL_PATH_SENTINEL
    }

    override fun openFile(path: String, line: Int?) {
        LOG.info("User opened $path from the conversation of pull request #${currentPullRequestId()}")
        val currentProject = project() ?: return
        val file = PullRequestCommentFileOpener.resolve(project = currentProject, inlinePath = path) ?: return
        PullRequestCommentFileOpener.open(project = currentProject, file = file, fileLine = line)
    }

    override fun copyPath(path: String) {
        LOG.info("User copied the file location $path in the conversation of pull request #${currentPullRequestId()}")
        CopyPasteManager.getInstance().setContents(java.awt.datatransfer.StringSelection(path))
    }

    /** Opens the reply editor of a comment. */
    override fun openReply(commentId: Long) {
        LOG.info("User opened the reply editor on comment $commentId in the conversation of pull request #${pullRequestId()}")
        state.value = state.value.copy(openComposer = ConversationComposerTarget(commentId = commentId, kind = ConversationComposerKind.Reply))
    }

    /** Opens the edit editor of a comment. */
    override fun openEdit(commentId: Long) {
        LOG.info("User opened the edit editor on comment $commentId in the conversation of pull request #${pullRequestId()}")
        state.value = state.value.copy(openComposer = ConversationComposerTarget(commentId = commentId, kind = ConversationComposerKind.Edit))
    }

    /** Closes the open reply or edit editor and hands the row back. */
    override fun closeComposer() {
        state.value = state.value.copy(openComposer = null)
    }

    /** True while a change of that comment is still in flight, so its actions stay disabled. */
    override fun isUpdating(commentId: Long): Boolean {
        return commentId in state.value.updatingCommentIds
    }

    /** Applies a change that needs no editor, such as resolving a comment. */
    fun applyCommentChange(change: CommentChange) {
        applyCommentChange(change = change, onResult = {})
    }

    override fun applyCommentChange(change: CommentChange, onResult: (Boolean) -> Unit) {
        LOG.info("User applied ${change::class.simpleName} on comment ${change.commentId} in pull request #${currentPullRequestId()}")
        val commentId = change.commentId ?: return
        if (isUpdating(commentId = commentId)) {
            LOG.debug("Ignoring ${change::class.simpleName} on comment $commentId; another change on this comment is still in progress")
            onResult(false)
            return
        }
        setUpdating(commentId = commentId, isUpdating = true)
        commentEditor.applyCommentChange(change = change) { isAccepted ->
            setUpdating(commentId = commentId, isUpdating = false)
            if (!isAccepted) {
                LOG.info("The ${change::class.simpleName} action was rejected on comment $commentId of pull request #${pullRequestId()}")
            }
            onResult(isAccepted)
        }
    }

    /** Returns a root or reply from the currently rendered conversation. */
    fun commentById(commentId: Long): PullRequestComment? {
        return state.value.threads().asSequence()
            .flatMap { thread -> sequenceOf(thread.root) + thread.replies.asSequence() }
            .firstOrNull { comment -> comment.id == commentId }
    }

    /** Deletes a comment after the UI has confirmed the operation. */
    override fun deleteComment(commentId: Long) {
        applyCommentChange(change = CommentChange.Delete(commentId = commentId))
    }

    /** The input of the comment composer at the top of the conversation. */
    fun topLevelCommentInput(): CommentInput {
        return CommentInput(
            label = "comment",
            placeholder = BbCloudBundle.message("main.branch.comments.placeholder"),
            submitLabel = BbCloudBundle.message("main.branch.comments.submit"),
            submitHint = BbCloudBundle.message("main.branch.comments.submitHint", SUBMIT_SHORTCUT_HINT),
            failureMessage = BbCloudBundle.message("main.branch.comments.failed.inline"),
            submit = { content, onResult -> commentEditor.addComment(content = content, onResult = onResult) }
        )
    }

    /** The input of the reply composer of a comment. */
    override fun replyInput(commentId: Long): CommentInput {
        return CommentInput(
            label = "reply",
            placeholder = BbCloudBundle.message("main.branch.comments.reply.placeholder"),
            submitLabel = BbCloudBundle.message("main.branch.comments.reply.submit"),
            submitHint = BbCloudBundle.message("main.branch.comments.reply.submitHint", SUBMIT_SHORTCUT_HINT),
            failureMessage = BbCloudBundle.message("main.branch.comments.failed.inline"),
            submit = { content, onResult ->
                applyCommentChange(change = CommentChange.Reply(commentId = commentId, content = content), onResult = onResult)
            }
        )
    }

    /** The input of the edit composer of a comment. */
    override fun editInput(comment: PullRequestComment): CommentInput {
        return CommentInput(
            label = "comment edit",
            placeholder = BbCloudBundle.message("main.branch.comments.edit.placeholder"),
            submitLabel = BbCloudBundle.message("main.branch.comments.edit.submit"),
            submitHint = BbCloudBundle.message("main.branch.comments.edit.submitHint", SUBMIT_SHORTCUT_HINT),
            failureMessage = BbCloudBundle.message("main.branch.comments.failed.inline"),
            initialText = comment.raw.orEmpty(),
            submit = { content, onResult ->
                applyCommentChange(change = CommentChange.Edit(commentId = comment.id, content = content), onResult = onResult)
            }
        )
    }

    private fun avatarUrls(thread: TimelineEntry.CommentThread): List<String> {
        // Resolved threads render no replies, so fetching their avatars would be wasted HTTP traffic.
        val replyAvatarUrls = if (ConversationThreadRules.showsThreadBody(thread)) {
            thread.replies.mapNotNull { reply -> reply.author?.avatarUrl }
        } else {
            emptyList()
        }
        return listOfNotNull(thread.actor?.avatarUrl) + replyAvatarUrls
    }

    private fun setUpdating(commentId: Long, isUpdating: Boolean) {
        val updatingCommentIds = if (isUpdating) {
            state.value.updatingCommentIds + commentId
        } else {
            state.value.updatingCommentIds - commentId
        }
        state.value = state.value.copy(updatingCommentIds = updatingCommentIds)
    }

    private fun bumpAvatarRevision() {
        state.value = state.value.copy(avatarRevision = state.value.avatarRevision + 1)
    }

    private fun newScrollRequest(force: Boolean): ConversationScrollRequest {
        scrollRequestId += 1
        return ConversationScrollRequest(id = scrollRequestId, force = force)
    }

    private fun initialState(): PullRequestConversationUiState {
        return PullRequestConversationUiState(loadState = PullRequestConversationLoadState.Loading)
    }

    companion object {
        private val LOG = logger<PullRequestConversationViewModel>()

        // The avatar pixel size the cache fetches for conversation thread comments, replies, and the in-thread reply/edit
        // composer. It must stay equal to the CONVERSATION_AVATAR_SIZE display size in PullRequestConversationScreen.kt,
        // so AuthorAvatarCache scales the fetched image to the size the Image actually draws.
        private const val CONVERSATION_AVATAR_SIZE = 16

        /**
         * The platform shortcut of the collaboration comment editor; the Jewel composer wires the same key itself, so the
         * hint stays accurate.
         */
        private val SUBMIT_SHORTCUT_HINT: String = CommentInputActionsComponentFactory.submitShortcutText
    }
}