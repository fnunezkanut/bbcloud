package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class PullRequestTimelineBuilderTest {
    private val author = ActivityUser(uuid = "{author}", displayName = "John Smith", avatarUrl = null)
    private val reviewer = ActivityUser(uuid = "{reviewer}", displayName = "Jane Doe", avatarUrl = "https://avatar/jane")
    private val summary = PullRequestSummary(
        id = 273,
        title = "PR 273",
        authorDisplayName = "John Smith",
        authorAvatarUrl = "https://avatar/john",
        createdOn = at("2026-09-28T09:00:00Z"),
        updatedOn = at("2026-09-30T12:00:00Z"),
        sourceBranch = "feature/273",
        destinationBranch = "main",
        commentCount = 0,
        webUrl = "https://bitbucket.org/workspace/repo/pull-requests/273",
        state = PullRequestState.Open,
        isDraft = false
    )

    @Nested
    inner class StateTests {
        @Test
        fun `buildActivityFeed - should add the opened entry from the summary`() {
            // WHEN
            val feed = buildActivityFeed()

            // THEN
            feed.entries shouldBe listOf(
                TimelineEntry.Opened(
                    date = summary.createdOn,
                    actor = ActivityUser(uuid = null, displayName = "John Smith", avatarUrl = "https://avatar/john")
                )
            )
        }

        @Test
        fun `buildActivityFeed - should add one entry when the state changes to merged`() {
            // GIVEN
            val activity = listOf(
                update(date = "2026-09-30T12:00:00Z", state = PullRequestState.Merged, user = reviewer),
                update(date = "2026-09-29T10:00:00Z", state = PullRequestState.Open, user = author),
                update(date = "2026-09-28T09:00:00Z", state = PullRequestState.Open, user = author),
                update(date = "2026-09-30T13:00:00Z", state = PullRequestState.Merged, user = reviewer)
            )

            // WHEN
            val feed = buildActivityFeed(activity = activity.paged())

            // THEN
            feed.entries.filterIsInstance<TimelineEntry.StateChanged>() shouldBe listOf(
                TimelineEntry.StateChanged(date = at("2026-09-30T12:00:00Z"), actor = reviewer, state = PullRequestState.Merged)
            )
        }

        @Test
        fun `buildActivityFeed - should add one entry when the state changes to declined`() {
            // GIVEN
            val activity = listOf(
                update(date = "2026-09-28T09:00:00Z", state = PullRequestState.Open, user = author),
                update(date = "2026-09-29T09:00:00Z", state = PullRequestState.Declined, user = reviewer)
            )

            // WHEN
            val feed = buildActivityFeed(activity = activity.paged())

            // THEN
            feed.entries.filterIsInstance<TimelineEntry.StateChanged>().map { entry -> entry.state } shouldBe listOf(PullRequestState.Declined)
        }

        @Test
        fun `buildActivityFeed - should add no entry for updates that keep the open state or have an unmodeled state`() {
            // GIVEN
            val activity = listOf(
                update(date = "2026-09-28T09:00:00Z", state = PullRequestState.Open, user = author),
                update(date = "2026-09-29T09:00:00Z", state = null, user = author),
                update(date = "2026-09-30T09:00:00Z", state = PullRequestState.Open, user = author)
            )

            // WHEN
            val feed = buildActivityFeed(activity = activity.paged())

            // THEN
            feed.entries.filterIsInstance<TimelineEntry.StateChanged>() shouldBe emptyList()
        }

        @Test
        fun `buildActivityFeed - should add approvals`() {
            // GIVEN
            val activity = listOf(PullRequestActivityItem.Approval(date = at("2026-09-29T09:00:00Z"), user = reviewer))

            // WHEN
            val feed = buildActivityFeed(activity = activity.paged())

            // THEN
            feed.entries.first() shouldBe TimelineEntry.Approved(date = at("2026-09-29T09:00:00Z"), actor = reviewer)
        }
    }

    @Nested
    inner class CommentTests {
        @Test
        fun `buildConversation - should nest replies and replies to replies under the root comment`() {
            // GIVEN
            val root = comment(id = 1, createdOn = "2026-09-29T09:00:00Z")
            val reply = comment(id = 2, parentId = 1, createdOn = "2026-09-29T10:00:00Z")
            val nestedReply = comment(id = 3, parentId = 2, createdOn = "2026-09-29T11:00:00Z")

            // WHEN
            val conversation = buildConversation(comments = listOf(nestedReply, root, reply))

            // THEN
            conversation.threads shouldBe listOf(
                TimelineEntry.CommentThread(root = root, replies = listOf(reply, nestedReply))
            )
        }

        @Test
        fun `buildConversation - should drop deleted comments and threads whose root is deleted, and drop pending comments`() {
            // GIVEN
            val deletedRoot = comment(id = 1, createdOn = "2026-09-29T09:00:00Z", isDeleted = true)
            val replyToDeletedRoot = comment(id = 2, parentId = 1, createdOn = "2026-09-29T10:00:00Z")
            val root = comment(id = 3, createdOn = "2026-09-29T11:00:00Z")
            val deletedReply = comment(id = 4, parentId = 3, createdOn = "2026-09-29T12:00:00Z", isDeleted = true)
            val liveReply = comment(id = 5, parentId = 3, createdOn = "2026-09-29T13:00:00Z")
            val pending = comment(id = 6, createdOn = "2026-09-29T14:00:00Z", isPending = true)

            // WHEN
            val conversation = buildConversation(
                comments = listOf(deletedRoot, replyToDeletedRoot, root, deletedReply, liveReply, pending)
            )

            // THEN
            conversation.threads shouldBe listOf(
                TimelineEntry.CommentThread(root = root, replies = listOf(liveReply))
            )
        }

        @Test
        fun `buildConversation - should return only comment threads`() {
            // GIVEN
            val root = comment(id = 1, createdOn = "2026-09-29T09:00:00Z")
            val activity = listOf(PullRequestActivityItem.Approval(date = at("2026-09-29T09:00:00Z"), user = reviewer))

            // WHEN
            val conversation = buildConversation(
                comments = listOf(root),
                currentUserUuid = null,
                currentUserAvatarUrl = null
            )

            // THEN
            assertSoftly(conversation) {
                threads.map { thread -> thread::class } shouldBe listOf(TimelineEntry.CommentThread::class)
                threads.single().root shouldBe root
            }
        }
    }

    @Nested
    inner class TaskAndBuildTests {
        @Test
        fun `buildActivityFeed - should add created and resolved entries for a resolved task`() {
            // GIVEN
            val task = PullRequestTask(
                id = 9,
                content = "Update the changelog",
                isResolved = true,
                createdOn = at("2026-09-29T09:00:00Z"),
                creator = author,
                resolvedOn = at("2026-09-29T12:00:00Z"),
                resolvedBy = reviewer
            )

            // WHEN
            val feed = buildActivityFeed(tasks = listOf(task))

            // THEN
            feed.entries.take(2) shouldBe listOf(
                TimelineEntry.TaskResolved(date = at("2026-09-29T12:00:00Z"), actor = reviewer, task = task),
                TimelineEntry.TaskCreated(date = at("2026-09-29T09:00:00Z"), actor = author, task = task)
            )
        }

        @Test
        fun `buildActivityFeed - should skip tasks and builds without a timestamp`() {
            // GIVEN
            val task = PullRequestTask(id = 9, content = "Task", isResolved = false)
            val undatedBuild = buildStatus(key = "a", updatedOn = null)
            val datedBuild = buildStatus(key = "b", updatedOn = "2026-09-29T09:00:00Z")

            // WHEN
            val feed = buildActivityFeed(tasks = listOf(task), builds = listOf(undatedBuild, datedBuild))

            // THEN
            feed.entries.map { entry -> entry::class } shouldBe listOf(TimelineEntry.BuildUpdated::class, TimelineEntry.Opened::class)
        }
    }

    @Nested
    inner class OrderingTests {
        @Test
        fun `buildActivityFeed - should sort entries newest first`() {
            // GIVEN
            val activity = listOf(PullRequestActivityItem.Approval(date = at("2026-09-29T09:00:00Z"), user = reviewer))
            val builds = listOf(buildStatus(key = "a", updatedOn = "2026-09-28T10:00:00Z"))

            // WHEN
            val feed = buildActivityFeed(activity = activity.paged(), builds = builds)

            // THEN
            feed.entries.map { entry -> entry::class } shouldBe listOf(
                TimelineEntry.Approved::class,
                TimelineEntry.BuildUpdated::class,
                TimelineEntry.Opened::class
            )
        }

        @Test
        fun `buildActivityFeed - should fold adjacent commits by the same author`() {
            // GIVEN
            val newest = commit(hash = "c3", date = "2026-09-30T12:00:00Z", user = author)
            val middle = commit(hash = "c2", date = "2026-09-30T11:00:00Z", user = author)
            val otherAuthor = commit(hash = "c1", date = "2026-09-30T10:00:00Z", user = reviewer)
            val oldest = commit(hash = "c0", date = "2026-09-29T10:00:00Z", user = author)
            val approval = PullRequestActivityItem.Approval(date = at("2026-09-29T12:00:00Z"), user = reviewer)

            // WHEN
            val feed =
                buildActivityFeed(
                    activity = listOf(approval).paged(),
                    commits = PagedItems(items = listOf(oldest, otherAuthor, middle, newest), isTruncated = false)
                )

            // THEN
            val pushes = feed.entries.filterIsInstance<TimelineEntry.CommitsPushed>()
            pushes.map { push -> push.commits.map { commit -> commit.hash } } shouldBe listOf(listOf("c3", "c2"), listOf("c1"), listOf("c0"))
        }
    }

    @Nested
    inner class ExclusionTests {
        @Test
        fun `buildActivityFeed - should exclude comment threads`() {
            // GIVEN
            val approval = PullRequestActivityItem.Approval(date = at("2026-09-29T09:00:00Z"), user = reviewer)

            // WHEN
            val feed = buildActivityFeed(activity = listOf(approval).paged())

            // THEN
            feed.entries.filterIsInstance<TimelineEntry.CommentThread>() shouldBe emptyList()
        }
    }

    @Nested
    inner class FlagTests {
        @Test
        fun `buildActivityFeed - should set hasPartialFailure when activity is null`() {
            // WHEN
            val feed = buildActivityFeed(activity = null)

            // THEN
            assertSoftly(feed) {
                hasPartialFailure shouldBe true
                entries.map { entry -> entry::class } shouldBe listOf(TimelineEntry.Opened::class)
            }
        }

        @Test
        fun `buildActivityFeed - should report partial failure when commits, tasks, or builds are missing`() {
            // WHEN
            val withoutCommits = buildActivityFeed(commits = null)
            val withoutTasks = buildActivityFeed(tasks = null)
            val withoutBuilds = buildActivityFeed(builds = null)
            val withActivityFailure = buildActivityFeed(activity = null)

            // THEN
            assertSoftly {
                withoutCommits.hasPartialFailure shouldBe true
                withoutTasks.hasPartialFailure shouldBe true
                withoutBuilds.hasPartialFailure shouldBe true
                withActivityFailure.hasPartialFailure shouldBe true
                buildActivityFeed().hasPartialFailure shouldBe false
            }
        }

        @Test
        fun `buildActivityFeed - should report truncation and keep the current user`() {
            // WHEN
            val conversation = buildConversation(
                comments = emptyList(),
                currentUserUuid = "{me}",
                currentUserAvatarUrl = null,
                isTruncated = true
            )
            val feed = buildActivityFeed(
                activity = PagedItems(items = emptyList(), isTruncated = true),
                commits = PagedItems(items = emptyList(), isTruncated = false)
            )

            // THEN
            assertSoftly {
                conversation.isTruncated shouldBe true
                conversation.currentUserUuid shouldBe "{me}"
                feed.isTruncated shouldBe true
                feed.entries.single().shouldBeInstanceOf<TimelineEntry.Opened>()
            }
        }
    }

    private fun buildActivityFeed(
        activity: PagedItems<PullRequestActivityItem>? = PagedItems(items = emptyList(), isTruncated = false),
        commits: PagedItems<PullRequestCommit>? = PagedItems(items = emptyList(), isTruncated = false),
        tasks: List<PullRequestTask>? = emptyList(),
        builds: List<PullRequestBuildStatus>? = emptyList()
    ): PullRequestActivityFeed {
        return PullRequestTimelineBuilder.buildActivityFeed(
            summary = summary,
            activity = activity,
            tasks = tasks,
            builds = builds,
            commits = commits
        )
    }

    private fun buildConversation(
        comments: List<PullRequestComment> = emptyList(),
        currentUserUuid: String? = null,
        currentUserAvatarUrl: String? = null,
        isTruncated: Boolean = false
    ): PullRequestConversation {
        return PullRequestTimelineBuilder.buildConversation(
            comments = PagedItems(items = comments, isTruncated = isTruncated),
            currentUserUuid = currentUserUuid,
            currentUserAvatarUrl = currentUserAvatarUrl
        )
    }

    private fun update(date: String, state: PullRequestState?, user: ActivityUser): PullRequestActivityItem.Update {
        return PullRequestActivityItem.Update(date = at(date), user = user, state = state)
    }

    private fun comment(id: Long, createdOn: String, parentId: Long? = null, isDeleted: Boolean = false, isPending: Boolean = false): PullRequestComment {
        return PullRequestComment(
            id = id,
            parentId = parentId,
            createdOn = at(createdOn),
            author = reviewer,
            html = "<p>Comment $id</p>",
            inlinePath = null,
            inlineLine = null,
            inlineSide = null,
            isResolved = false,
            isDeleted = isDeleted,
            isPending = isPending
        )
    }

    private fun commit(hash: String, date: String, user: ActivityUser): PullRequestCommit {
        return PullRequestCommit(hash = hash, message = "Commit $hash", date = at(date), author = user, url = null)
    }

    private fun buildStatus(key: String, updatedOn: String?): PullRequestBuildStatus {
        return PullRequestBuildStatus(
            key = key,
            name = "Build $key",
            state = BuildState.Successful,
            url = "https://ci/$key",
            description = null,
            updatedOn = updatedOn?.let { at(it) }
        )
    }

    private fun <T> List<T>.paged(isTruncated: Boolean = false): PagedItems<T> {
        return PagedItems(items = this, isTruncated = isTruncated)
    }

    private fun at(value: String): Instant {
        return Instant.parse(value)
    }
}