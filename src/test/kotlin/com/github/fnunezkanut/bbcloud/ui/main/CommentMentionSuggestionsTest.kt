package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidateTier
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class CommentMentionSuggestionsTest {
    private class FakeSource(
        private val local: List<MentionCandidate>,
        private val workspace: WorkspaceMentionCandidates? = null,
        override val currentUserUuid: String? = null
    ) : CommentMentionSource {
        var loadCount = 0
            private set

        override fun localCandidates(): List<MentionCandidate> {
            return local
        }

        override suspend fun workspaceCandidates(): WorkspaceMentionCandidates? {
            loadCount += 1
            return workspace
        }
    }

    @Test
    fun `onEditorChanged - should open on an at sign with pull request people`() {
        // GIVEN
        val suggestions = suggestions(local = listOf(candidate(uuid = "{a}", name = "Anna")))

        runBlocking {
            // WHEN
            suggestions.onEditorChanged(text = "@", selectionStart = 1, selectionEnd = 1, coroutineScope = scope())

            // THEN
            suggestions.state.value.isOpen shouldBe true
            suggestions.state.value.items.map { it.displayName } shouldContainExactly listOf("Anna")
        }
    }

    @Test
    fun `onEditorChanged - should filter as the query grows`() {
        // GIVEN
        val suggestions = suggestions(local = listOf(candidate(uuid = "{a}", name = "Anna"), candidate(uuid = "{b}", name = "Ben")))

        runBlocking {
            // WHEN the query narrows to Anna
            suggestions.onEditorChanged(text = "@ann", selectionStart = 4, selectionEnd = 4, coroutineScope = scope())

            // THEN
            suggestions.state.value.items.map { it.displayName } shouldContainExactly listOf("Anna")
        }
    }

    @Test
    fun `onEditorChanged - should close when the caret leaves the query`() {
        // GIVEN an open list
        val suggestions = suggestions(local = listOf(candidate(uuid = "{a}", name = "Anna")))
        runBlocking {
            suggestions.onEditorChanged(text = "@ann", selectionStart = 4, selectionEnd = 4, coroutineScope = scope())

            // WHEN the text no longer has a query under the caret
            suggestions.onEditorChanged(text = "plain", selectionStart = 5, selectionEnd = 5, coroutineScope = scope())

            // THEN
            suggestions.state.value.isOpen shouldBe false
        }
    }

    @Test
    fun `dismiss - should stay closed for the same anchor and reopen for a new one`() {
        // GIVEN an open list
        val suggestions = suggestions(local = listOf(candidate(uuid = "{a}", name = "Anna")))
        runBlocking {
            suggestions.onEditorChanged(text = "@ann", selectionStart = 4, selectionEnd = 4, coroutineScope = scope())

            // WHEN the user dismisses it with Escape, then keeps typing in the same query
            suggestions.dismiss(byUser = true)
            suggestions.onEditorChanged(text = "@anna", selectionStart = 5, selectionEnd = 5, coroutineScope = scope())

            // THEN it stays closed for that anchor
            suggestions.state.value.isOpen shouldBe false

            // WHEN a brand-new at sign is typed after a space
            suggestions.onEditorChanged(text = "@anna @a", selectionStart = 8, selectionEnd = 8, coroutineScope = scope())

            // THEN it reopens for the new anchor
            suggestions.state.value.isOpen shouldBe true
        }
    }

    @Test
    fun `onEditorChanged - should load the workspace only when local results are short and the query is not blank`() {
        // GIVEN a source whose only match is in the workspace tier
        val source = FakeSource(
            local = emptyList(),
            workspace = WorkspaceMentionCandidates(
                candidates = listOf(candidate(uuid = "{w}", name = "Wanda", tier = MentionCandidateTier.Workspace)),
                isTruncated = false
            )
        )
        val suggestions = CommentMentionSuggestions(source = source, label = "comment")

        runBlocking {
            // WHEN a non-blank query is typed
            suggestions.onEditorChanged(text = "@wa", selectionStart = 3, selectionEnd = 3, coroutineScope = scope())

            // THEN the workspace loaded and its match shows
            source.loadCount shouldBe 1
            suggestions.state.value.items.map { it.displayName } shouldContainExactly listOf("Wanda")
        }
    }

    @Test
    fun `onEditorChanged - should not load the workspace for a blank query`() {
        // GIVEN
        val source = FakeSource(local = emptyList(), workspace = WorkspaceMentionCandidates(candidates = emptyList(), isTruncated = false))
        val suggestions = CommentMentionSuggestions(source = source, label = "comment")

        runBlocking {
            // WHEN only the at sign is typed
            suggestions.onEditorChanged(text = "@", selectionStart = 1, selectionEnd = 1, coroutineScope = scope())

            // THEN no workspace load happens
            source.loadCount shouldBe 0
        }
    }

    @Test
    fun `onEditorChanged - should set the failure flag when the workspace load fails`() {
        // GIVEN a source whose workspace load returns null
        val source = FakeSource(local = emptyList(), workspace = null)
        val suggestions = CommentMentionSuggestions(source = source, label = "comment")

        runBlocking {
            // WHEN a query triggers the load
            suggestions.onEditorChanged(text = "@wa", selectionStart = 3, selectionEnd = 3, coroutineScope = scope())

            // THEN the failure flag is set
            suggestions.state.value.workspaceLoadFailed shouldBe true
        }
    }

    @Test
    fun `onEditorChanged - should ignore a stale workspace result when the anchor changed`() {
        // GIVEN a source whose workspace load is held until released
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val source = object : CommentMentionSource {
            override val currentUserUuid: String? = null

            override fun localCandidates(): List<MentionCandidate> {
                return emptyList()
            }

            override suspend fun workspaceCandidates(): WorkspaceMentionCandidates {
                gate.await()
                return WorkspaceMentionCandidates(
                    candidates = listOf(candidate(uuid = "{w}", name = "Wanda", tier = MentionCandidateTier.Workspace)),
                    isTruncated = false
                )
            }
        }
        val suggestions = CommentMentionSuggestions(source = source, label = "comment")

        runBlocking {
            // WHEN a first query starts the (held) load, then the user moves to a new at sign
            suggestions.onEditorChanged(text = "@wa", selectionStart = 3, selectionEnd = 3, coroutineScope = CoroutineScope(coroutineContext))
            suggestions.onEditorChanged(text = "@wa @zz", selectionStart = 7, selectionEnd = 7, coroutineScope = CoroutineScope(coroutineContext))

            // WHEN the stale load finally completes
            gate.complete(Unit)

            // THEN the late Wanda result is not applied to the newer "zz" query
            suggestions.state.value.items.any { it.displayName == "Wanda" } shouldBe false
        }
    }

    @Test
    fun `moveSelection - should wrap around`() {
        // GIVEN two open items
        val suggestions = suggestions(local = listOf(candidate(uuid = "{a}", name = "Anna"), candidate(uuid = "{b}", name = "Ben")))
        runBlocking {
            suggestions.onEditorChanged(text = "@", selectionStart = 1, selectionEnd = 1, coroutineScope = scope())

            // WHEN moving up from the first item
            suggestions.moveSelection(delta = -1)

            // THEN it wraps to the last item
            suggestions.state.value.selectedIndex shouldBe 1
        }
    }

    @Test
    fun `onEditorChanged - should exclude the current user`() {
        // GIVEN the current user is one of the local people
        val source = FakeSource(
            local = listOf(candidate(uuid = "{me}", name = "Me"), candidate(uuid = "{other}", name = "Other")),
            currentUserUuid = "{me}"
        )
        val suggestions = CommentMentionSuggestions(source = source, label = "comment")

        runBlocking {
            // WHEN
            suggestions.onEditorChanged(text = "@", selectionStart = 1, selectionEnd = 1, coroutineScope = scope())

            // THEN the current user is not offered
            suggestions.state.value.items.map { it.displayName } shouldContainExactly listOf("Other")
        }
    }

    private fun suggestions(local: List<MentionCandidate>): CommentMentionSuggestions {
        return CommentMentionSuggestions(source = FakeSource(local = local), label = "comment")
    }

    private fun scope(): CoroutineScope {
        return CoroutineScope(Dispatchers.Unconfined)
    }

    private fun candidate(uuid: String, name: String, tier: MentionCandidateTier = MentionCandidateTier.PullRequest): MentionCandidate {
        return MentionCandidate(uuid = uuid, accountId = null, displayName = name, nickname = null, avatarUrl = null, tier = tier)
    }
}