package com.github.fnunezkanut.bbcloud.ui.pullrequest.help

import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/** Guards the Gradle copy of docs/ into the plugin resources. The tests run against the processed build resources. */
@Tag("unit")
class WorktreesHelpMarkdownTest {
    @Test
    fun `load - should return the work trees help page starting with its title`() {
        // WHEN
        val markdown = WorktreesHelpMarkdown.load()

        // THEN
        markdown shouldStartWith "# Git Work Trees 101"
    }

    @Test
    fun `readResourceBytes - should package both help pictures`() {
        // WHEN
        val pictures = listOf("/help/worktrees.jpeg", "/help/worktrees_2.jpeg").map { path ->
            WorktreesHelpMarkdown.readResourceBytes(resourcePath = path)
        }

        // THEN
        pictures.forEach { bytes ->
            bytes.shouldNotBeNull().size shouldBeGreaterThan 0
        }
    }

    @Test
    fun `readResourceBytes - should return null for a resource that is not packaged`() {
        // WHEN
        val bytes = WorktreesHelpMarkdown.readResourceBytes(resourcePath = "/help/missing.jpeg")

        // THEN
        bytes.shouldBeNull()
    }
}