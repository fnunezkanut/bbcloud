package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestActivityFeed
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.TimeZone
import javax.swing.Icon
import kotlin.time.Clock

/**
 * Drives the Jewel activity feed of the Changes tab. The editor session pushes the feed through
 * [showActivityFeed]; the composable reads [state] and renders the entries.
 *
 * The avatars come from the shared [AuthorAvatarCache]; the cache is prefetched when the feed arrives and the
 * [PullRequestActivityUiState.avatarRevision] is bumped once the images are ready, so the composable redraws them.
 */
class PullRequestActivityViewModel(
    private val pullRequestId: () -> Long?,
    private val avatarCache: AuthorAvatarCache = AuthorAvatarCache(avatarSize = ACTIVITY_AVATAR_SIZE)
) {
    val state: MutableState<PullRequestActivityUiState> = mutableStateOf(initialState())

    private var coroutineScope: CoroutineScope? = null

    fun bind(coroutineScope: CoroutineScope) {
        this.coroutineScope = coroutineScope
    }

    fun showActivityFeed(feed: PullRequestActivityFeed?) {
        coroutineScope?.let { scope ->
            avatarCache.prefetch(
                urls = feed?.entries?.flatMap { entry -> entry.actor?.avatarUrl?.let { listOf(it) }.orEmpty() }.orEmpty(),
                coroutineScope = scope,
                onLoaded = { bumpAvatarRevision() }
            )
        }
        render(feed)
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

    private fun render(feed: PullRequestActivityFeed?) {
        val model = computeRenderModel(feed)
        if (model == null) {
            state.value = state.value.copy(
                showPartialFailure = false,
                showTruncation = false,
                groups = emptyList()
            )
            return
        }
        val groups = ActivityDayGroups.group(
            entries = model.entries,
            now = Clock.System.now(),
            timeZone = TimeZone.currentSystemDefault()
        )
        state.value = state.value.copy(
            showPartialFailure = model.showPartialFailure,
            showTruncation = model.showTruncation,
            groups = groups
        )
        LOG.debug("Rendered ${model.entries.size} activity feed entries for pull request #${pullRequestId()}")
    }

    private fun bumpAvatarRevision() {
        state.value = state.value.copy(avatarRevision = state.value.avatarRevision + 1)
    }

    private fun initialState(): PullRequestActivityUiState {
        return PullRequestActivityUiState(title = BbCloudBundle.message("main.branch.activity.title"))
    }

    companion object {
        private val LOG = logger<PullRequestActivityViewModel>()

        private const val ACTIVITY_AVATAR_SIZE = 24
    }
}