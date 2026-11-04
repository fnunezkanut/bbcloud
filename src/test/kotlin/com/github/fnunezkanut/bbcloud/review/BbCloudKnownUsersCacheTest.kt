package com.github.fnunezkanut.bbcloud.review

import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidateTier
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BbCloudKnownUsersCacheTest {
    @Test
    fun `record then candidates - should return the recorded user for the workspace`() {
        // GIVEN
        val state = BbCloudKnownUsersState.State()
        val cache = BbCloudKnownUsersCache(store = { state })

        // WHEN
        cache.record(workspace = "workspace", candidates = listOf(candidate(uuid = "{a}", name = "Anna")))

        // THEN
        cache.candidates(workspace = "workspace").map { it.displayName } shouldContainExactly listOf("Anna")
    }

    @Test
    fun `candidates - should filter by workspace case-insensitively`() {
        // GIVEN a user recorded under a mixed-case workspace
        val state = BbCloudKnownUsersState.State()
        val cache = BbCloudKnownUsersCache(store = { state })
        cache.record(workspace = "WorkSpace", candidates = listOf(candidate(uuid = "{a}", name = "Anna")))

        // THEN the lowercased query still matches, and a different workspace does not
        cache.candidates(workspace = "workspace").map { it.displayName } shouldContainExactly listOf("Anna")
        cache.candidates(workspace = "other").shouldBeEmpty()
    }

    @Test
    fun `record - should keep a known account id when a later record lacks it`() {
        // GIVEN a user first recorded with an account id, then again without
        val state = BbCloudKnownUsersState.State()
        val cache = BbCloudKnownUsersCache(store = { state })
        cache.record(workspace = "workspace", candidates = listOf(candidate(uuid = "{a}", name = "Anna", accountId = "712020:aaaa-bbbb")))
        cache.record(workspace = "workspace", candidates = listOf(candidate(uuid = "{a}", name = "Anna", accountId = null)))

        // THEN the earlier account id survives the merge
        cache.candidates(workspace = "workspace").single().accountId shouldBe "712020:aaaa-bbbb"
    }

    @Test
    fun `candidates - should order by recency`() {
        // GIVEN two users recorded, then the first re-recorded so it becomes the most recent
        val state = BbCloudKnownUsersState.State()
        val cache = BbCloudKnownUsersCache(store = { state })
        cache.record(workspace = "workspace", candidates = listOf(candidate(uuid = "{a}", name = "Anna")))
        cache.record(workspace = "workspace", candidates = listOf(candidate(uuid = "{b}", name = "Ben")))
        cache.record(workspace = "workspace", candidates = listOf(candidate(uuid = "{a}", name = "Anna")))

        // THEN Anna, seen most recently, is first
        cache.candidates(workspace = "workspace").map { it.displayName } shouldContainExactly listOf("Anna", "Ben")
    }

    @Test
    fun `record - should evict the least recently seen past the cap`() {
        // GIVEN a cache with a cap of two
        val state = BbCloudKnownUsersState.State()
        val cache = BbCloudKnownUsersCache(store = { state }, maxEntries = 2)
        cache.record(workspace = "workspace", candidates = listOf(candidate(uuid = "{a}", name = "Anna")))
        cache.record(workspace = "workspace", candidates = listOf(candidate(uuid = "{b}", name = "Ben")))

        // WHEN a third user is recorded
        cache.record(workspace = "workspace", candidates = listOf(candidate(uuid = "{c}", name = "Carol")))

        // THEN the oldest (Anna) is evicted
        cache.candidates(workspace = "workspace").map { it.displayName } shouldContainExactly listOf("Carol", "Ben")
    }

    @Test
    fun `record - should persist the rows so a new cache over the same state reads them back`() {
        // GIVEN a user recorded through one cache
        val state = BbCloudKnownUsersState.State()
        BbCloudKnownUsersCache(store = { state }).record(workspace = "workspace", candidates = listOf(candidate(uuid = "{a}", name = "Anna")))

        // THEN the rows were written through
        state.rows.isEmpty() shouldBe false

        // WHEN a second cache is built over the same state (a simulated restart)
        val restarted = BbCloudKnownUsersCache(store = { state })

        // THEN it reads the persisted user back
        restarted.candidates(workspace = "workspace").map { it.displayName } shouldContainExactly listOf("Anna")
    }

    @Test
    fun `clear - should empty both the reads and the persisted rows`() {
        // GIVEN a cache with one recorded user
        val state = BbCloudKnownUsersState.State()
        val cache = BbCloudKnownUsersCache(store = { state })
        cache.record(workspace = "workspace", candidates = listOf(candidate(uuid = "{a}", name = "Anna")))

        // WHEN
        cache.clear()

        // THEN
        cache.candidates(workspace = "workspace").shouldBeEmpty()
        state.rows.shouldBeEmpty()
    }

    private fun candidate(uuid: String, name: String, accountId: String? = null): MentionCandidate {
        return MentionCandidate(
            uuid = uuid,
            accountId = accountId,
            displayName = name,
            nickname = null,
            avatarUrl = null,
            tier = MentionCandidateTier.PullRequest
        )
    }
}