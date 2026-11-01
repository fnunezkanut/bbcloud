package com.github.fnunezkanut.bbcloud.ui.main

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestChangesTreeTest {
    @Test
    fun `selectedFileRow - should return the row for a single selected file id`() {
        // GIVEN
        val row = rowFor("src/main/App.kt")
        val rowsById = mapOf(fileId(row.relativePath) to row)

        // WHEN
        val resolved = selectedFileRow(selectedKeys = setOf(fileId(row.relativePath)), rowsById = rowsById)

        // THEN
        resolved shouldBe row
    }

    @Test
    fun `selectedFileRow - should return null for a directory id`() {
        // GIVEN
        val row = rowFor("src/main/App.kt")
        val rowsById = mapOf(fileId(row.relativePath) to row)

        // WHEN
        val resolved = selectedFileRow(selectedKeys = setOf(directoryId("src/main")), rowsById = rowsById)

        // THEN
        resolved.shouldBeNull()
    }

    @Test
    fun `selectedFileRow - should return null for zero or multiple selected keys`() {
        // GIVEN
        val row = rowFor("src/main/App.kt")
        val rowsById = mapOf(fileId(row.relativePath) to row)

        // WHEN
        val none = selectedFileRow(selectedKeys = emptySet(), rowsById = rowsById)
        val several = selectedFileRow(
            selectedKeys = setOf(fileId(row.relativePath), directoryId("src/main")),
            rowsById = rowsById
        )

        // THEN
        none.shouldBeNull()
        several.shouldBeNull()
    }

    @Test
    fun `fileRowsByDirectory - should list a file under every folder above it`() {
        // GIVEN
        val row = rowFor("src/main/App.kt")

        // WHEN
        val rowsByDirectory = fileRowsByDirectory(rows = listOf(row))

        // THEN
        assertSoftly {
            rowsByDirectory["src"] shouldBe listOf(row)
            rowsByDirectory["src/main"] shouldBe listOf(row)
            rowsByDirectory.containsKey("src/main/App.kt") shouldBe false
        }
    }

    @Test
    fun `directoryFullyViewed - should be true when every file below the folder is viewed`() {
        // GIVEN
        val rows = listOf(rowFor("src/a/One.kt"), rowFor("src/b/Two.kt"))
        val rowsByDirectory = fileRowsByDirectory(rows = rows)

        // WHEN
        val fullyViewed = directoryFullyViewed(
            descendantRows = rowsByDirectory.getValue("src"),
            isViewed = { true }
        )

        // THEN
        fullyViewed shouldBe true
    }

    @Test
    fun `directoryFullyViewed - should be false when one nested file is not viewed`() {
        // GIVEN
        val rows = listOf(rowFor("src/a/One.kt"), rowFor("src/b/Two.kt"))
        val rowsByDirectory = fileRowsByDirectory(rows = rows)
        val viewedPaths = setOf("src/a/One.kt")

        // WHEN
        val fullyViewed = directoryFullyViewed(
            descendantRows = rowsByDirectory.getValue("src"),
            isViewed = { row -> row.relativePath in viewedPaths }
        )

        // THEN
        fullyViewed shouldBe false
    }

    @Test
    fun `directoryFullyViewed - should be false for a folder with no file below it`() {
        // GIVEN
        val descendantRows = emptyList<PullRequestFileChangeRow>()

        // WHEN
        val fullyViewed = directoryFullyViewed(descendantRows = descendantRows, isViewed = { true })

        // THEN
        fullyViewed shouldBe false
    }

    @Test
    fun `directoryFullyViewed - should not count a sibling folder with a similar name`() {
        // GIVEN
        val rows = listOf(rowFor("src/a/One.kt"), rowFor("src/ab/Two.kt"))
        val rowsByDirectory = fileRowsByDirectory(rows = rows)
        val viewedPaths = setOf("src/a/One.kt")

        // WHEN
        val fullyViewed = directoryFullyViewed(
            descendantRows = rowsByDirectory.getValue("src/a"),
            isViewed = { row -> row.relativePath in viewedPaths }
        )

        // THEN
        fullyViewed shouldBe true
    }

    private fun rowFor(relativePath: String): PullRequestFileChangeRow {
        return PullRequestFileChangeRow(
            changeId = 0,
            relativePath = relativePath,
            fileName = relativePath.substringAfterLast('/'),
            commentCount = 0,
            statusColorArgb = null
        )
    }
}