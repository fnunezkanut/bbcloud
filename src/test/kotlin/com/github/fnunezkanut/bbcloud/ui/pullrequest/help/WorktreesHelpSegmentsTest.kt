package com.github.fnunezkanut.bbcloud.ui.pullrequest.help

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class WorktreesHelpSegmentsTest {
    @Test
    fun `splitWorktreesHelpSegments - should turn the help page images into picture segments in order`() {
        // GIVEN
        val markdown = WorktreesHelpMarkdown.load()

        // WHEN
        val pictures = splitWorktreesHelpSegments(markdown = markdown).filterIsInstance<WorktreesHelpSegment.Picture>()

        // THEN
        pictures.map { picture -> picture.resourcePath } shouldBe listOf("/help/worktrees.jpeg", "/help/worktrees_2.jpeg")
    }

    @Test
    fun `splitWorktreesHelpSegments - should keep the title and text before the first picture as prose`() {
        // GIVEN
        val markdown = WorktreesHelpMarkdown.load()

        // WHEN
        val segments = splitWorktreesHelpSegments(markdown = markdown)

        // THEN
        val firstProse = segments.first() as WorktreesHelpSegment.Prose
        firstProse.markdown shouldStartWith "# Git Work Trees 101"
    }

    @Test
    fun `splitWorktreesHelpSegments - should return one prose segment when the markdown has no picture`() {
        // GIVEN
        val markdown = "# Title\n\nSome text.\n"

        // WHEN
        val segments = splitWorktreesHelpSegments(markdown = markdown)

        // THEN
        segments shouldBe listOf(WorktreesHelpSegment.Prose(markdown = markdown))
    }

    @Test
    fun `splitWorktreesHelpSegments - should return no segments for empty markdown`() {
        // WHEN
        val segments = splitWorktreesHelpSegments(markdown = "")

        // THEN
        segments shouldBe emptyList()
    }

    @Test
    fun `splitWorktreesHelpSegments - should keep picture syntax inside a fenced code block as prose`() {
        // GIVEN
        val markdown = "Intro\n\n```\n![example](docs/worktrees.jpeg)\n```\n"

        // WHEN
        val segments = splitWorktreesHelpSegments(markdown = markdown)

        // THEN
        segments shouldBe listOf(WorktreesHelpSegment.Prose(markdown = markdown))
    }

    @Test
    fun `splitWorktreesHelpSegments - should keep an image inside a sentence as prose`() {
        // GIVEN
        val markdown = "See ![example](docs/worktrees.jpeg) for details.\n"

        // WHEN
        val segments = splitWorktreesHelpSegments(markdown = markdown)

        // THEN
        segments shouldBe listOf(WorktreesHelpSegment.Prose(markdown = markdown))
    }

    @Test
    fun `splitWorktreesHelpSegments - should keep an image that is not in the docs folder as prose`() {
        // GIVEN
        val markdown = "![remote](https://example.com/picture.png)\n"

        // WHEN
        val segments = splitWorktreesHelpSegments(markdown = markdown)

        // THEN
        segments shouldBe listOf(WorktreesHelpSegment.Prose(markdown = markdown))
    }
}