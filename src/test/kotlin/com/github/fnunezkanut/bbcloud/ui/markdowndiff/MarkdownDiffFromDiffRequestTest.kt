package com.github.fnunezkanut.bbcloud.ui.markdowndiff

import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.contents.EmptyContent
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.openapi.vfs.VirtualFile
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class MarkdownDiffFromDiffRequestTest {
    private fun documentContent(fileName: String): DocumentContent {
        val file = mockk<VirtualFile>()
        every { file.name } returns fileName
        val content = mockk<DocumentContent>()
        every { content.highlightFile } returns file
        every { content.contentType } returns null
        return content
    }

    private fun requestOf(vararg contents: DiffContent): ContentDiffRequest {
        val request = mockk<ContentDiffRequest>()
        every { request.contents } returns contents.toList()
        return request
    }

    @Test
    fun `markdownContentsOf - should return contents for two Markdown documents`() {
        // GIVEN
        val request = requestOf(documentContent(fileName = "a.md"), documentContent(fileName = "a.md"))

        // WHEN
        val result = MarkdownDiffFromDiffRequest.markdownContentsOf(request = request)

        // THEN
        result.shouldNotBeNull().fileName shouldBe "a.md"
    }

    @Test
    fun `markdownContentsOf - should return null for non Markdown documents`() {
        // GIVEN
        val request = requestOf(documentContent(fileName = "a.kt"), documentContent(fileName = "a.kt"))

        // WHEN
        val result = MarkdownDiffFromDiffRequest.markdownContentsOf(request = request)

        // THEN
        result.shouldBeNull()
    }

    @Test
    fun `markdownContentsOf - should return null for a side that is not a document`() {
        // GIVEN
        val request = requestOf(documentContent(fileName = "a.md"), mockk<DiffContent>())

        // WHEN
        val result = MarkdownDiffFromDiffRequest.markdownContentsOf(request = request)

        // THEN
        result.shouldBeNull()
    }

    @Test
    fun `markdownContentsOf - should return null when the request does not have two sides`() {
        // GIVEN
        val request = requestOf(documentContent(fileName = "a.md"))

        // WHEN
        val result = MarkdownDiffFromDiffRequest.markdownContentsOf(request = request)

        // THEN
        result.shouldBeNull()
    }

    @Test
    fun `markdownContentsOf - should treat an empty side as empty text`() {
        // GIVEN
        val request = requestOf(EmptyContent(), documentContent(fileName = "new.md"))

        // WHEN
        val result = MarkdownDiffFromDiffRequest.markdownContentsOf(request = request)

        // THEN
        assertSoftly {
            result.shouldNotBeNull().fileName shouldBe "new.md"
            result.left.text() shouldBe ""
            result.left shouldBe MarkdownDiffFromDiffRequest.MarkdownSide.Empty
        }
    }

    @Test
    fun `markdownContentsOf - should return null for two empty sides`() {
        // GIVEN
        val request = requestOf(EmptyContent(), EmptyContent())

        // WHEN
        val result = MarkdownDiffFromDiffRequest.markdownContentsOf(request = request)

        // THEN
        result.shouldBeNull()
    }
}