package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestFileCommentCounts
import com.github.fnunezkanut.bbcloud.git.FileLineCount
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class PullRequestFileChangeRowsTest {
    @BeforeEach
    fun mockChangesUtil() {
        mockkStatic(ChangesUtil::class)
    }

    @AfterEach
    fun unmockChangesUtil() {
        unmockkStatic(ChangesUtil::class)
    }

    @Test
    fun `toFileChangeRows - should resolve the line count by normalized relative path`() {
        // GIVEN
        val basePath = "/home/dev/project"
        val change = changeForPath("$basePath/src/main/App.kt")
        val lineCounts = mapOf(
            "src/main/App.kt" to FileLineCount(linesAdded = 12, linesRemoved = 3, isBinary = false)
        )

        // WHEN
        val rows = toFileChangeRows(
            changes = listOf(change),
            commentCounts = null,
            projectBasePath = basePath,
            fileLineCounts = lineCounts
        )

        // THEN
        assertSoftly(rows.single()) {
            relativePath shouldBe "src/main/App.kt"
            fileName shouldBe "App.kt"
            commentCount shouldBe 0
            lineCount shouldBe FileLineCount(linesAdded = 12, linesRemoved = 3, isBinary = false)
        }
    }

    @Test
    fun `toFileChangeRows - should leave the line count null when the map has no entry for the file`() {
        // GIVEN
        val basePath = "/home/dev/project"
        val change = changeForPath("$basePath/src/main/App.kt")
        val lineCounts = mapOf(
            "src/main/Other.kt" to FileLineCount(linesAdded = 1, linesRemoved = 1, isBinary = false)
        )

        // WHEN
        val rows = toFileChangeRows(
            changes = listOf(change),
            commentCounts = null,
            projectBasePath = basePath,
            fileLineCounts = lineCounts
        )

        // THEN
        rows.single().lineCount.shouldBeNull()
    }

    @Test
    fun `toFileChangeRows - should leave the line count null when no map is supplied`() {
        // GIVEN
        val basePath = "/home/dev/project"
        val change = changeForPath("$basePath/src/main/App.kt")

        // WHEN
        val rows = toFileChangeRows(
            changes = listOf(change),
            commentCounts = null,
            projectBasePath = basePath,
            fileLineCounts = null
        )

        // THEN
        rows.single().lineCount.shouldBeNull()
    }

    @Test
    fun `toFileChangeRows - should carry both the comment count and the line count`() {
        // GIVEN
        val basePath = "/home/dev/project"
        val change = changeForPath("$basePath/src/main/App.kt")
        val commentCounts = PullRequestFileCommentCounts.from(
            comments = listOf(
                commentOnPath("src/main/App.kt"),
                commentOnPath("src/main/App.kt")
            )
        )
        val lineCounts = mapOf(
            "src/main/App.kt" to FileLineCount(linesAdded = 5, linesRemoved = 0, isBinary = false)
        )

        // WHEN
        val rows = toFileChangeRows(
            changes = listOf(change),
            commentCounts = commentCounts,
            projectBasePath = basePath,
            fileLineCounts = lineCounts
        )

        // THEN
        assertSoftly(rows.single()) {
            commentCount shouldBe 2
            lineCount shouldBe FileLineCount(linesAdded = 5, linesRemoved = 0, isBinary = false)
        }
    }

    /** A [Change] whose file path (via the mocked [ChangesUtil]) is [absolutePath], the path the row mapping reads. */
    private fun changeForPath(absolutePath: String): Change {
        val filePath = mockk<FilePath> {
            every { path } returns absolutePath
        }
        val fileStatus = mockk<FileStatus> {
            every { color } returns null
        }
        val change = mockk<Change> {
            every { this@mockk.fileStatus } returns fileStatus
        }
        every { ChangesUtil.getFilePath(change) } returns filePath
        return change
    }

    private fun commentOnPath(inlinePath: String): PullRequestComment {
        return PullRequestComment(
            id = 1L,
            parentId = null,
            createdOn = Instant.parse("2024-01-01T00:00:00Z"),
            author = null,
            html = "comment",
            inlinePath = inlinePath,
            inlineLine = 1,
            inlineSide = null,
            isResolved = false,
            isDeleted = false,
            isPending = false
        )
    }
}