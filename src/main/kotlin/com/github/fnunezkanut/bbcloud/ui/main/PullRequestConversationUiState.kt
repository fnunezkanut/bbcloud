package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestConversation
import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

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
 * A request to bring the newest entry back into view. [force] asks for the scroll even when the reader has scrolled
 * away, which is what re-selecting the Conversation tab does; without it, the screen only scrolls when the reader is
 * still near the top.
 */
data class ConversationScrollRequest(val id: Int, val force: Boolean)

/**
 * The Compose state of the Conversation tab: the day-grouped comment threads, the notices around them, and the flags the
 * screen needs to redraw the avatars and to keep an open reply or edit editor alive.
 */
data class PullRequestConversationUiState(
    val loadState: PullRequestConversationLoadState = PullRequestConversationLoadState.Loading,
    val isTruncated: Boolean = false,
    val groups: List<HistoryDayGroup> = emptyList(),
    val currentUserUuid: String? = null,
    val currentUserAvatarUrl: String? = null,
    val avatarRevision: Int = 0,
    val updatingCommentIds: Set<Long> = emptySet(),
    val openComposer: ConversationComposerTarget? = null,
    val scrollRequest: ConversationScrollRequest? = null,
    /** Display names of the `@{account-key}` mentions, from [PullRequestConversation.mentions]. */
    val mentions: Map<String, String> = emptyMap()
)

/**
 * Groups the comment threads of a conversation by the day they were written on, keeping the order of the threads, which
 * Bitbucket returns newest first.
 */
internal fun groupConversationThreads(threads: List<TimelineEntry.CommentThread>, now: Instant, timeZone: TimeZone): List<HistoryDayGroup> {
    return HistoryDayGroups.group(entries = threads, now = now, timeZone = timeZone)
}

/** The comment threads of the state, flattened out of their day groups. */
internal fun PullRequestConversationUiState.threads(): List<TimelineEntry.CommentThread> {
    return groups.flatMap { group -> group.entries.filterIsInstance<TimelineEntry.CommentThread>() }
}

/** True when the conversation holds no comment at all. */
internal fun PullRequestConversationUiState.isEmpty(): Boolean {
    return threads().isEmpty()
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