package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class ReviewBarFileNavigationTest {
    @Nested
    inner class Next {
        @Test
        fun `next - should skip viewed files and return the first unviewed file after the current one`() {
            // GIVEN five files where files 1 and 2 are viewed
            val viewed = viewedIndexes(1, 2)

            // WHEN searching forward from file 0
            val result = targetIndex(fileCount = 5, currentIndex = 0, direction = ReviewBarFileNavigationDirection.Next, viewed = viewed)

            // THEN file 3 is the target
            result shouldBe 3
        }

        @Test
        fun `next - should return null when every other file is viewed`() {
            // GIVEN four files where every file except the current one is viewed
            val viewed = viewedIndexes(0, 2, 3)

            // WHEN searching forward from file 1
            val result = targetIndex(fileCount = 4, currentIndex = 1, direction = ReviewBarFileNavigationDirection.Next, viewed = viewed)

            // THEN there is no target
            result.shouldBeNull()
        }

        @Test
        fun `next - should wrap around from the last file to the first unviewed file`() {
            // GIVEN four files where file 0 is unviewed and files 1 and 2 are viewed
            val viewed = viewedIndexes(1, 2)

            // WHEN searching forward from the last file
            val result = targetIndex(fileCount = 4, currentIndex = 3, direction = ReviewBarFileNavigationDirection.Next, viewed = viewed)

            // THEN the search wraps to file 0
            result shouldBe 0
        }

        @Test
        fun `next - should wrap around when only earlier files are unviewed`() {
            // GIVEN five files where files 4 and 0 are viewed and files 1 and 2 are unviewed
            val viewed = viewedIndexes(0, 4)

            // WHEN searching forward from file 3
            val result = targetIndex(fileCount = 5, currentIndex = 3, direction = ReviewBarFileNavigationDirection.Next, viewed = viewed)

            // THEN the search wraps past files 4 and 0 and returns file 1
            result shouldBe 1
        }
    }

    @Nested
    inner class Previous {
        @Test
        fun `previous - should skip viewed files and return the first unviewed file before the current one`() {
            // GIVEN five files where files 3 and 2 are viewed
            val viewed = viewedIndexes(2, 3)

            // WHEN searching backward from file 4
            val result = targetIndex(fileCount = 5, currentIndex = 4, direction = ReviewBarFileNavigationDirection.Previous, viewed = viewed)

            // THEN file 1 is the target
            result shouldBe 1
        }

        @Test
        fun `previous - should return null when every other file is viewed`() {
            // GIVEN three files where every file except the current one is viewed
            val viewed = viewedIndexes(0, 2)

            // WHEN searching backward from file 1
            val result = targetIndex(fileCount = 3, currentIndex = 1, direction = ReviewBarFileNavigationDirection.Previous, viewed = viewed)

            // THEN there is no target
            result.shouldBeNull()
        }

        @Test
        fun `previous - should wrap around from the first file to the last unviewed file`() {
            // GIVEN four files where file 3 is unviewed and files 1 and 2 are viewed
            val viewed = viewedIndexes(1, 2)

            // WHEN searching backward from the first file
            val result = targetIndex(fileCount = 4, currentIndex = 0, direction = ReviewBarFileNavigationDirection.Previous, viewed = viewed)

            // THEN the search wraps to file 3
            result shouldBe 3
        }

        @Test
        fun `previous - should wrap around when only later files are unviewed`() {
            // GIVEN five files where files 0, 3 and 4 are viewed and file 2 is unviewed
            val viewed = viewedIndexes(0, 3, 4)

            // WHEN searching backward from file 1
            val result = targetIndex(fileCount = 5, currentIndex = 1, direction = ReviewBarFileNavigationDirection.Previous, viewed = viewed)

            // THEN the search wraps past file 0 and 4 and 3 and returns file 2
            result shouldBe 2
        }
    }

    @Nested
    inner class CurrentFile {
        @Test
        fun `target - should never return the current file even when the current file is unviewed`() {
            // GIVEN three files where only the current file 1 is unviewed
            val viewed = viewedIndexes(0, 2)

            // WHEN searching in both directions
            val next = targetIndex(fileCount = 3, currentIndex = 1, direction = ReviewBarFileNavigationDirection.Next, viewed = viewed)
            val previous = targetIndex(fileCount = 3, currentIndex = 1, direction = ReviewBarFileNavigationDirection.Previous, viewed = viewed)

            // THEN neither search returns the current file
            assertSoftly {
                next.shouldBeNull()
                previous.shouldBeNull()
            }
        }

        @Test
        fun `target - should return the same file when only one other file is unviewed`() {
            // GIVEN three files where only file 0 is unviewed besides the current file 2
            val viewed = viewedIndexes(1)

            // WHEN searching in both directions
            val next = targetIndex(fileCount = 3, currentIndex = 2, direction = ReviewBarFileNavigationDirection.Next, viewed = viewed)
            val previous = targetIndex(fileCount = 3, currentIndex = 2, direction = ReviewBarFileNavigationDirection.Previous, viewed = viewed)

            // THEN both directions reach file 0
            assertSoftly {
                next shouldBe 0
                previous shouldBe 0
            }
        }
    }

    @Nested
    inner class InvalidInput {
        @Test
        fun `target - should return null for an out-of-range current index`() {
            // GIVEN three files with none viewed
            val viewed = viewedIndexes()

            // WHEN searching from an index before the first file and from the index of the count
            val before = targetIndex(fileCount = 3, currentIndex = -1, direction = ReviewBarFileNavigationDirection.Next, viewed = viewed)
            val after = targetIndex(fileCount = 3, currentIndex = 3, direction = ReviewBarFileNavigationDirection.Previous, viewed = viewed)

            // THEN there is no target
            assertSoftly {
                before.shouldBeNull()
                after.shouldBeNull()
            }
        }

        @Test
        fun `target - should return null when there are no files or only the current file`() {
            // GIVEN no viewed files
            val viewed = viewedIndexes()

            // WHEN searching in a chain of zero files and in a chain of one file
            val empty = targetIndex(fileCount = 0, currentIndex = 0, direction = ReviewBarFileNavigationDirection.Next, viewed = viewed)
            val single = targetIndex(fileCount = 1, currentIndex = 0, direction = ReviewBarFileNavigationDirection.Previous, viewed = viewed)

            // THEN there is no target
            assertSoftly {
                empty.shouldBeNull()
                single.shouldBeNull()
            }
        }
    }

    private fun viewedIndexes(vararg indexes: Int): (Int) -> Boolean {
        val viewed = indexes.toSet()
        return { index -> index in viewed }
    }

    private fun targetIndex(fileCount: Int, currentIndex: Int, direction: ReviewBarFileNavigationDirection, viewed: (Int) -> Boolean): Int? {
        return ReviewBarFileNavigation.targetIndex(
            fileCount = fileCount,
            currentIndex = currentIndex,
            direction = direction,
            isViewed = viewed
        )
    }
}