package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestConversation
import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry

/** Whether the conversation of the pull request could be loaded. */
enum class PullRequestConversationLoadState {
    Loading,
    Loaded,
    Error
}

/** The comment editor that is open under a thread: a reply or an edit of that comment. */
enum class ConversationComposerKind {
    Reply,
    Edit
}

/** The comment an open composer belongs to. */
data class ConversationComposerTarget(val commentId: Long, val kind: ConversationComposerKind)

/**
 * A request to bring the top of the pull-request page back into view. [force] asks for the scroll even when the reader has
 * scrolled away, which is what a different pull request needs; without it, the page only scrolls when the reader is
 * still near the top.
 */
data class ConversationScrollRequest(val id: Int, val force: Boolean)

/**
 * The Compose state of the comment threads and the top-level composer of the Activity card. The threads are a flat list
 * in the order Bitbucket returns them, newest first; the Activity timeline interleaves them with the activity entries.
 * The state also holds the flags the screen needs to redraw the avatars and to keep an open reply or edit editor alive.
 */
data class PullRequestConversationUiState(
    val loadState: PullRequestConversationLoadState = PullRequestConversationLoadState.Loading,
    val isTruncated: Boolean = false,
    val threads: List<TimelineEntry.CommentThread> = emptyList(),
    val currentUserUuid: String? = null,
    val currentUserAvatarUrl: String? = null,
    val avatarRevision: Int = 0,
    val updatingCommentIds: Set<Long> = emptySet(),
    val openComposer: ConversationComposerTarget? = null,
    val scrollRequest: ConversationScrollRequest? = null,
    /** Display names of the `@{account-key}` mentions, from [PullRequestConversation.mentions]. */
    val mentions: Map<String, String> = emptyMap(),
    /** True while some mention in [mentions] is still being looked up. */
    val mentionsPending: Boolean = false
)

/** True when the conversation holds no comment at all. */
internal fun PullRequestConversationUiState.isEmpty(): Boolean {
    return threads.isEmpty()
}

/** The state of a conversation that could not be loaded. */
internal fun failedConversationState(): PullRequestConversationUiState {
    return PullRequestConversationUiState(loadState = PullRequestConversationLoadState.Error)
}

/** The state of a conversation that Bitbucket reported as empty. */
internal fun emptyConversationState(conversation: PullRequestConversation?): PullRequestConversationUiState {
    return PullRequestConversationUiState(
        loadState = PullRequestConversationLoadState.Loaded,
        isTruncated = conversation?.isTruncated ?: false
    )
}