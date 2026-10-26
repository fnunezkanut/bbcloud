package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class TaskEditsTest {
    private val open = PullRequestTask(id = 1, content = "test task", isResolved = false)
    private val resolved = PullRequestTask(id = 2, content = "test task 2", isResolved = true)

    @Test
    fun `of - should be empty when nothing changed`() {
        // WHEN
        val edits = TaskEdits.of(original = listOf(open, resolved), editedContents = mapOf(1L to "test task "), deletedIds = emptySet())

        // THEN
        edits.isEmpty shouldBe true
    }

    @Test
    fun `of - should keep trimmed text of unresolved tasks only`() {
        // WHEN
        val edits = TaskEdits.of(
            original = listOf(open, resolved),
            editedContents = mapOf(1L to "  renamed  ", 2L to "ignored"),
            deletedIds = emptySet()
        )

        // THEN
        edits shouldBe TaskEdits(contentUpdates = mapOf(1L to "renamed"), deletedIds = emptySet())
    }

    @Test
    fun `of - should drop text of deleted tasks and unknown deleted ids`() {
        // WHEN
        val edits = TaskEdits.of(original = listOf(open, resolved), editedContents = mapOf(1L to "renamed"), deletedIds = setOf(1L, 2L, 99L))

        // THEN
        assertSoftly(edits) {
            contentUpdates shouldBe emptyMap()
            deletedIds shouldBe setOf(1L, 2L)
            isEmpty shouldBe false
        }
    }

    @Test
    fun `of - should report blank text`() {
        // WHEN
        val edits = TaskEdits.of(original = listOf(open), editedContents = mapOf(1L to "   "), deletedIds = emptySet())

        // THEN
        edits.hasBlankContent shouldBe true
    }
}