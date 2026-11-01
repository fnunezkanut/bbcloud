package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestActivityFeed
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestConversation
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.TimeZone
import javax.swing.Icon
import kotlin.time.Clock

/**
 * Drives the Activity card of the pull-request page. The editor session pushes the activity feed and the conversation
 * through [showActivity]; the composable reads [state] and renders the merged timeline of entries and comment threads.
 *
 * The avatars of the activity entries come from the shared [AuthorAvatarCache]; the cache is prefetched when the feed
 * arrives and the [PullRequestHistoryUiState.avatarRevision] is bumped once the images are ready, so the composable
 * redraws them. The avatars of the comment threads are cached by [PullRequestConversationViewModel].
 */
class PullRequestHistoryViewModel(
    private val pullRequestId: () -> Long?,
    private val avatarCache: AuthorAvatarCache = AuthorAvatarCache(avatarSize = ACTIVITY_AVATAR_SIZE)
) {
    val state: MutableState<PullRequestHistoryUiState> = mutableStateOf(initialState())

    private var coroutineScope: CoroutineScope? = null

    fun bind(coroutineScope: CoroutineScope) {
        this.coroutineScope = coroutineScope
    }

    /** Shows the activity feed and the comment threads of the pull request; a null source is a partial failure. */
    fun showActivity(feed: PullRequestActivityFeed?, conversation: PullRequestConversation?) {
        coroutineScope?.let { scope ->
            avatarCache.prefetch(
                urls = feed?.entries?.flatMap { entry -> entry.actor?.avatarUrl?.let { listOf(it) }.orEmpty() }.orEmpty(),
                coroutineScope = scope,
                onLoaded = { bumpAvatarRevision() }
            )
        }
        render(feed = feed, conversation = conversation)
    }

    fun reset() {
        coroutineScope = null
        state.value = initialState()
    }

    fun avatarIconFor(url: String?): Icon {
        return avatarCache.iconFor(url)
    }

    fun currentPullRequestId(): Long? {
        return pullRequestId()
    }

    private fun render(feed: PullRequestActivityFeed?, conversation: PullRequestConversation?) {
        val model = computeRenderModel(feed = feed, conversation = conversation)
        if (model == null) {
            state.value = state.value.copy(
                loadState = PullRequestHistoryLoadState.Error,
                showPartialFailure = false,
                showTruncation = false,
                groups = emptyList()
            )
            LOG.info("Could not load the activity of pull request #${pullRequestId()}")
            return
        }
        val groups = HistoryDayGroups.group(
            entries = model.entries,
            now = Clock.System.now(),
            timeZone = TimeZone.currentSystemDefault()
        )
        state.value = state.value.copy(
            loadState = PullRequestHistoryLoadState.Loaded,
            showPartialFailure = model.showPartialFailure,
            showTruncation = model.showTruncation,
            groups = groups
        )
        LOG.debug(
            "Rendered ${feed?.entries?.size ?: 0} activity entries and ${conversation?.threads?.size ?: 0} comment threads " +
                "for pull request #${pullRequestId()}"
        )
    }

    private fun bumpAvatarRevision() {
        state.value = state.value.copy(avatarRevision = state.value.avatarRevision + 1)
    }

    private fun initialState(): PullRequestHistoryUiState {
        return PullRequestHistoryUiState(title = BbCloudBundle.message("main.branch.history.title"))
    }

    companion object {
        private val LOG = logger<PullRequestHistoryViewModel>()
        private const val ACTIVITY_AVATAR_SIZE = 24
    }
}