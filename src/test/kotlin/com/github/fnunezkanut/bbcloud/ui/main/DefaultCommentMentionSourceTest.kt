package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidateTier
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.WorkspaceMembers
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@Tag("unit")
class DefaultCommentMentionSourceTest {
    @Test
    fun `localCandidates - should list pull request people before recently seen`() {
        // GIVEN
        val source = DefaultCommentMentionSource(
            pullRequestPeople = { listOf(candidate(uuid = "{a}", name = "Anna")) },
            recentlySeen = { listOf(candidate(uuid = "{b}", name = "Ben", tier = MentionCandidateTier.RecentlySeen)) },
            loadWorkspaceMembers = { null },
            currentUser = { null }
        )

        // WHEN
        val local = source.localCandidates()

        // THEN
        local.map { it.displayName } shouldContainExactly listOf("Anna", "Ben")
    }

    @Test
    fun `workspaceCandidates - should map members to the Workspace tier keeping account ids`(): Unit =
        runBlocking {
            // GIVEN
            val member = ReviewerCandidate(uuid = "{c}", displayName = "Carla", nickname = "carla", avatarUrl = null, accountId = "712020:aaaa-bbbb")
            val source = source(loadWorkspaceMembers = { WorkspaceMembers(members = listOf(member), isTruncated = true) })

            // WHEN
            val result = source.workspaceCandidates()

            // THEN
            result?.isTruncated shouldBe true
            result?.candidates?.single()?.let { candidate ->
                candidate.tier shouldBe MentionCandidateTier.Workspace
                candidate.accountId shouldBe "712020:aaaa-bbbb"
            }
        }

    @Test
    fun `workspaceCandidates - should load once for two calls`(): Unit =
        runBlocking {
            // GIVEN
            val loads = AtomicInteger(0)
            val source = source(loadWorkspaceMembers = {
                loads.incrementAndGet()
                WorkspaceMembers(members = emptyList(), isTruncated = false)
            })

            // WHEN
            source.workspaceCandidates()
            source.workspaceCandidates()

            // THEN
            loads.get() shouldBe 1
        }

    @Test
    fun `workspaceCandidates - should not cache a failure`(): Unit =
        runBlocking {
            // GIVEN a loader that fails once then succeeds
            val loads = AtomicInteger(0)
            val source = source(loadWorkspaceMembers = {
                if (loads.getAndIncrement() == 0) null else WorkspaceMembers(members = emptyList(), isTruncated = false)
            })

            // WHEN
            val first = source.workspaceCandidates()
            val second = source.workspaceCandidates()

            // THEN the first null is not cached, so the second call loads again
            first.shouldBeNull()
            second shouldBe WorkspaceMentionCandidates(candidates = emptyList(), isTruncated = false)
            loads.get() shouldBe 2
        }

    @Test
    fun `workspaceCandidates - should share one load between concurrent callers`(): Unit =
        runBlocking {
            // GIVEN a slow loader
            val loads = AtomicInteger(0)
            val source = source(loadWorkspaceMembers = {
                loads.incrementAndGet()
                delay(50)
                WorkspaceMembers(members = emptyList(), isTruncated = false)
            })

            // WHEN two callers request concurrently
            awaitAll(
                async { source.workspaceCandidates() },
                async { source.workspaceCandidates() }
            )

            // THEN the loader ran once
            loads.get() shouldBe 1
        }

    private fun source(loadWorkspaceMembers: suspend () -> WorkspaceMembers?): DefaultCommentMentionSource {
        return DefaultCommentMentionSource(
            pullRequestPeople = { emptyList() },
            recentlySeen = { emptyList() },
            loadWorkspaceMembers = loadWorkspaceMembers,
            currentUser = { null }
        )
    }

    private fun candidate(uuid: String, name: String, tier: MentionCandidateTier = MentionCandidateTier.PullRequest): MentionCandidate {
        return MentionCandidate(uuid = uuid, accountId = null, displayName = name, nickname = null, avatarUrl = null, tier = tier)
    }
}