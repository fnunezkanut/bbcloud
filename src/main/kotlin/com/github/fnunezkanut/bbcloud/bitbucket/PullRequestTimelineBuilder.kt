package com.github.fnunezkanut.bbcloud.bitbucket

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestCommentThreads.groupIntoThreads

object PullRequestTimelineBuilder {
    fun buildConversation(comments: PagedItems<PullRequestComment>, currentUserUuid: String?, currentUserAvatarUrl: String?): PullRequestConversation {
        val threads = PullRequestCommentThreads.sortedLikeConversation(groupIntoThreads(comments = comments.items))
        return PullRequestConversation(
            threads = threads,
            currentUserUuid = currentUserUuid,
            currentUserAvatarUrl = currentUserAvatarUrl,
            isTruncated = comments.isTruncated
        )
    }

    fun buildActivityFeed(
        summary: PullRequestSummary,
        activity: PagedItems<PullRequestActivityItem>?,
        tasks: List<PullRequestTask>?,
        builds: List<PullRequestBuildStatus>?,
        commits: PagedItems<PullRequestCommit>?
    ): PullRequestActivityFeed {
        val entries: MutableList<ActivityFeedEntry> = mutableListOf()
        entries.add(openedEntry(summary))
        entries.addAll(stateEntries(activity?.items.orEmpty()))
        entries.addAll(approvalEntries(activity?.items.orEmpty()))
        entries.addAll(taskEntries(tasks.orEmpty()))
        entries.addAll(buildEntries(builds.orEmpty()))
        entries.addAll(commits?.items.orEmpty().map { commit -> TimelineEntry.CommitsPushed(listOf(commit)) })
        val sortedEntries = foldAdjacentCommits(entries.sortedByDescending { entry -> entry.date })
        return PullRequestActivityFeed(
            entries = sortedEntries,
            isTruncated = activity?.isTruncated == true || commits?.isTruncated == true,
            hasPartialFailure = activity == null || commits == null || tasks == null || builds == null
        )
    }

    private fun openedEntry(summary: PullRequestSummary): TimelineEntry.Opened {
        return TimelineEntry.Opened(
            date = summary.createdOn,
            actor = ActivityUser(uuid = null, displayName = summary.authorDisplayName, avatarUrl = summary.authorAvatarUrl)
        )
    }

    /**
     * Updates are also created for pushes and title edits, which keep the state; only a change to merged or declined produces an entry.
     * Updates with a state this plugin does not model are skipped.
     */
    private fun stateEntries(items: List<PullRequestActivityItem>): List<TimelineEntry.StateChanged> {
        val updates = items.filterIsInstance<PullRequestActivityItem.Update>().sortedBy { update -> update.date }
        var previousState: PullRequestState? = null
        return buildList {
            updates.forEach { update ->
                val state = update.state ?: return@forEach
                if (state != previousState && state in CLOSED_STATES) {
                    add(TimelineEntry.StateChanged(date = update.date, actor = update.user, state = state))
                }
                previousState = state
            }
        }
    }

    private fun approvalEntries(items: List<PullRequestActivityItem>): List<TimelineEntry.Approved> {
        return items.filterIsInstance<PullRequestActivityItem.Approval>().map { approval ->
            TimelineEntry.Approved(date = approval.date, actor = approval.user)
        }
    }

    private fun taskEntries(tasks: List<PullRequestTask>): List<ActivityFeedEntry> {
        return tasks.flatMap { task ->
            listOfNotNull(
                task.createdOn?.let { createdOn -> TimelineEntry.TaskCreated(date = createdOn, actor = task.creator, task = task) },
                task.resolvedOn?.let { resolvedOn -> TimelineEntry.TaskResolved(date = resolvedOn, actor = task.resolvedBy, task = task) }
            )
        }
    }

    private fun buildEntries(builds: List<PullRequestBuildStatus>): List<ActivityFeedEntry> {
        return builds.mapNotNull { build -> build.updatedOn?.let { updatedOn -> TimelineEntry.BuildUpdated(date = updatedOn, status = build) } }
    }

    /** [entries] must be sorted newest first; the folded entries keep their commits newest first. */
    private fun foldAdjacentCommits(entries: List<ActivityFeedEntry>): List<ActivityFeedEntry> {
        val folded = mutableListOf<ActivityFeedEntry>()
        entries.forEach { entry ->
            val previous = folded.lastOrNull()
            if (entry is TimelineEntry.CommitsPushed && previous is TimelineEntry.CommitsPushed && authorKey(previous.actor) == authorKey(entry.actor)) {
                folded[folded.lastIndex] = TimelineEntry.CommitsPushed(previous.commits + entry.commits)
            } else {
                folded += entry
            }
        }
        return folded
    }

    private fun authorKey(user: ActivityUser?): String? {
        return user?.uuid ?: user?.displayName
    }

    private val CLOSED_STATES = setOf(PullRequestState.Merged, PullRequestState.Declined)
}