package com.github.fnunezkanut.bbcloud.bitbucket

import kotlin.time.Instant

data class ActivityUser(val uuid: String?, val displayName: String, val avatarUrl: String?)

/** Activity log entries; only the documented `update` and `approval` shapes are kept. */
sealed interface PullRequestActivityItem {
    val date: Instant
    val user: ActivityUser?

    /** A null [state] means Bitbucket reported a state this plugin does not model, such as `SUPERSEDED`. */
    data class Update(override val date: Instant, override val user: ActivityUser?, val state: PullRequestState?) : PullRequestActivityItem

    data class Approval(override val date: Instant, override val user: ActivityUser?) : PullRequestActivityItem
}

data class ActivityPage(val items: List<PullRequestActivityItem>, val nextUrl: String?)

/**
 * [html] is the Bitbucket-rendered comment body; [inlineLine] is the first line on the new side, or the old side for
 * removed lines. [inlineLineEnd] is the inclusive end line when Bitbucket reports a multi-line inline range.
 */
data class PullRequestComment(
    val id: Long,
    val parentId: Long?,
    val createdOn: Instant,
    val author: ActivityUser?,
    val html: String,
    val inlinePath: String?,
    val inlineLine: Int?,
    val inlineSide: InlineCommentSide?,
    val isResolved: Boolean,
    val isDeleted: Boolean,
    val isPending: Boolean,
    /** Bitbucket only reports the resolver of a resolved thread, so both are null for unresolved comments. */
    val resolvedBy: ActivityUser? = null,
    val resolvedOn: Instant? = null,
    /** The Markdown source, needed to seed the editor with what the author wrote; null when Bitbucket did not report it. */
    val raw: String? = null,
    /** The inclusive end line when Bitbucket reports a multi-line inline range; null for legacy single-line payloads. */
    val inlineLineEnd: Int? = null
)

data class CommentPage(val comments: List<PullRequestComment>, val nextUrl: String?)

data class PullRequestCommit(val hash: String, val message: String, val date: Instant, val author: ActivityUser?, val url: String?)

data class CommitPage(val commits: List<PullRequestCommit>, val nextUrl: String?)

/** [isTruncated] is true when the listing stopped at the page limit before reaching the last page. */
data class PagedItems<T>(val items: List<T>, val isTruncated: Boolean)

sealed interface TimelineEntry {
    val date: Instant
    val actor: ActivityUser?

    data class Opened(override val date: Instant, override val actor: ActivityUser?) :
        TimelineEntry,
        ActivityFeedEntry

    /** Only [PullRequestState.Merged] and [PullRequestState.Declined] are produced. */
    data class StateChanged(override val date: Instant, override val actor: ActivityUser?, val state: PullRequestState) :
        TimelineEntry,
        ActivityFeedEntry

    data class Approved(override val date: Instant, override val actor: ActivityUser?) :
        TimelineEntry,
        ActivityFeedEntry

    /** [replies] are sorted oldest first and include replies to replies. */
    data class CommentThread(val root: PullRequestComment, val replies: List<PullRequestComment>) : TimelineEntry {
        override val date: Instant
            get() = root.createdOn
        override val actor: ActivityUser?
            get() = root.author
    }

    data class TaskCreated(override val date: Instant, override val actor: ActivityUser?, val task: PullRequestTask) :
        TimelineEntry,
        ActivityFeedEntry

    data class TaskResolved(override val date: Instant, override val actor: ActivityUser?, val task: PullRequestTask) :
        TimelineEntry,
        ActivityFeedEntry

    /** [commits] are sorted newest first and share one author. */
    data class CommitsPushed(val commits: List<PullRequestCommit>) :
        TimelineEntry,
        ActivityFeedEntry {
        override val date: Instant
            get() = commits.first().date
        override val actor: ActivityUser?
            get() = commits.first().author
    }

    data class BuildUpdated(override val date: Instant, val status: PullRequestBuildStatus) :
        TimelineEntry,
        ActivityFeedEntry {
        override val actor: ActivityUser?
            get() = null
    }
}

/** Sealed sub-interface for entries that belong to the activity feed (Overview tab). [CommentThread] does not implement this. */
sealed interface ActivityFeedEntry : TimelineEntry

/**
 * Comment threads for the Conversation tab.
 * Threads are sorted newest first.
 * [mentions] maps a normalized account UUID to the display name shown for `@{uuid}` mentions; unresolved UUIDs map to
 * the localized unknown-user label.
 */
data class PullRequestConversation(
    val threads: List<TimelineEntry.CommentThread>,
    val currentUserUuid: String?,
    val currentUserAvatarUrl: String?,
    val isTruncated: Boolean,
    val mentions: Map<String, String> = emptyMap()
)

/**
 * Non-comment activity entries for the Overview tab.
 * Entries are sorted newest first.
 * [isTruncated] is true when activity or commits listing stopped at the page limit.
 * [hasPartialFailure] is true when commits, tasks, builds, or activity could not be loaded.
 */
data class PullRequestActivityFeed(val entries: List<ActivityFeedEntry>, val isTruncated: Boolean, val hasPartialFailure: Boolean)