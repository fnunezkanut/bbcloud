@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer

/** The display size of an author avatar in a thread comment and in-thread composer. */
private val CONVERSATION_THREAD_GAP = 12.dp

/** The Jewel Conversation tab: the top-level composer and day-grouped shared thread cards. */
@Composable
internal fun PullRequestConversationScreen(viewModel: PullRequestConversationViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state
    val scrollState = rememberScrollState()
    Column(modifier = modifier.fillMaxSize()) {
        TitledCardBox {
            VerticallyScrollableContainer(modifier = Modifier.fillMaxSize(), scrollState = scrollState) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ConversationComposer(viewModel = viewModel)
                    ConversationBody(state = state, viewModel = viewModel)
                }
            }
        }
    }
    ConversationScrollEffect(state = state, scrollState = scrollState)
}

/** Brings the newest entry back into view when the tab asks for it. */
@Composable
private fun ConversationScrollEffect(state: PullRequestConversationUiState, scrollState: ScrollState) {
    val scrollRequest = state.scrollRequest ?: return
    LaunchedEffect(scrollRequest.id) {
        if (scrollRequest.force || ConversationThreadRules.shouldSnapToTop(offsetPx = scrollState.value)) {
            scrollState.scrollTo(0)
        }
    }
}

/** The composer of a new top-level comment. */
@Composable
private fun ConversationComposer(viewModel: PullRequestConversationViewModel) {
    val state by viewModel.state
    val pullRequestId = viewModel.currentPullRequestId()
    val input = remember(pullRequestId) { viewModel.topLevelCommentInput() }
    val composerViewModel = remember(pullRequestId) { CommentComposerViewModel(input = input) }
    CommentComposerScreen(
        viewModel = composerViewModel,
        avatarIcon = viewModel.avatarIconFor(state.currentUserAvatarUrl),
        modifier = Modifier.padding(bottom = CONVERSATION_THREAD_GAP),
        showsDottedBorder = true
    )
}

/** The threads of the conversation, with loading and truncation notices. */
@Composable
private fun ConversationBody(state: PullRequestConversationUiState, viewModel: PullRequestConversationViewModel) {
    when (state.loadState) {
        PullRequestConversationLoadState.Loading -> {
            ConversationMessage(BbCloudBundle.message("main.branch.history.loading"))
        }
        PullRequestConversationLoadState.Error -> {
            ConversationMessage(BbCloudBundle.message("main.branch.history.error"))
        }
        PullRequestConversationLoadState.Loaded -> {
            if (state.isEmpty()) {
                ConversationMessage(BbCloudBundle.message("main.branch.history.empty"))
            }
            state.groups.forEach { group ->
                Text(
                    text = historyDayHeaderText(group.day),
                    fontWeight = FontWeight.Bold,
                    color = JewelTheme.globalColors.text.info,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ConversationThreadLayout.verticalGapBetweenCards)
                ) {
                    group.entries.filterIsInstance<TimelineEntry.CommentThread>().forEach { thread ->
                        key(thread.root.id) {
                            PullRequestCommentThreadCard(thread = thread, host = viewModel)
                        }
                    }
                }
            }
            if (state.isTruncated) {
                ConversationMessage(BbCloudBundle.message("main.branch.history.truncated"))
            }
        }
    }
}

@Composable
private fun ConversationMessage(text: String) {
    Text(text = text, color = JewelTheme.globalColors.text.info)
}