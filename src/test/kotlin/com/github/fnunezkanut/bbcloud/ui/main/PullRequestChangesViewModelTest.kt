package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.git.BranchDiffResult
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestChangesViewModelTest {
    @BeforeEach
    fun mockChangesUtil() {
        mockkStatic(ChangesUtil::class)
    }

    @AfterEach
    fun unmockChangesUtil() {
        unmockkStatic(ChangesUtil::class)
    }

    @Nested
    inner class OnFileOpened {
        @Test
        fun `onFileOpened - should call onOpenChangeInList with all changes and the index when provided`() {
            // GIVEN
            val first = changeForPath("$BASE_PATH/src/A.kt")
            val second = changeForPath("$BASE_PATH/src/B.kt")
            var openedChanges: List<Change>? = null
            var openedIndex: Int? = null
            val viewModel = viewModel(
                onOpenChange = { error("single-change open must not be used when a list opener is provided") },
                onOpenChangeInList = { changes, index ->
                    openedChanges = changes
                    openedIndex = index
                }
            )
            viewModel.showResult(successOf(first, second))

            // WHEN
            viewModel.onFileOpened(changeId = 1)

            // THEN
            assertSoftly {
                openedChanges.shouldContainExactly(first, second)
                openedIndex shouldBe 1
            }
        }

        @Test
        fun `onFileOpened - should call onOpenChange when onOpenChangeInList is null`() {
            // GIVEN
            val change = changeForPath("$BASE_PATH/src/A.kt")
            var openedChange: Change? = null
            val viewModel = viewModel(onOpenChange = { opened -> openedChange = opened }, onOpenChangeInList = null)
            viewModel.showResult(successOf(change))

            // WHEN
            viewModel.onFileOpened(changeId = 0)

            // THEN
            openedChange shouldBe change
        }

        @Test
        fun `onFileOpened - should do nothing for an unknown change id`() {
            // GIVEN
            val change = changeForPath("$BASE_PATH/src/A.kt")
            var listOpenerCalled = false
            var singleOpenerCalled = false
            val viewModel = viewModel(
                onOpenChange = { singleOpenerCalled = true },
                onOpenChangeInList = { _, _ -> listOpenerCalled = true }
            )
            viewModel.showResult(successOf(change))

            // WHEN
            viewModel.onFileOpened(changeId = 5)

            // THEN
            assertSoftly {
                listOpenerCalled shouldBe false
                singleOpenerCalled shouldBe false
            }
        }
    }

    @Nested
    inner class ShowResolved {
        @Test
        fun `showResolved - should switch to the Resolved card with the pull request id and no rows`() {
            // GIVEN
            val viewModel = viewModel()

            // WHEN
            viewModel.showResolved(pullRequestId = 275L)

            // THEN
            assertSoftly(viewModel.state.value) {
                card shouldBe PullRequestFileChangesCard.Resolved
                resolvedPullRequestId shouldBe 275L
                rows.shouldContainExactly()
            }
        }

        @Test
        fun `showResolved - should drop the previous changes so onFileOpened does nothing afterwards`() {
            // GIVEN
            val change = changeForPath("$BASE_PATH/src/A.kt")
            var singleOpenerCalled = false
            val viewModel = viewModel(onOpenChange = { singleOpenerCalled = true }, onOpenChangeInList = null)
            viewModel.showResult(successOf(change))

            // WHEN
            viewModel.showResolved(pullRequestId = null)
            viewModel.onFileOpened(changeId = 0)

            // THEN
            assertSoftly(viewModel.state.value) {
                card shouldBe PullRequestFileChangesCard.Resolved
                resolvedPullRequestId.shouldBeNull()
                singleOpenerCalled shouldBe false
            }
        }
    }

    @Nested
    inner class IsViewed {
        @Test
        fun `isViewed - should return false when no pull request is shown`() {
            // GIVEN
            var storeCalled = false
            val viewModel = PullRequestChangesViewModel(
                pullRequestId = { null },
                projectBasePath = { BASE_PATH },
                onRefreshRequested = {},
                onOpenChange = {},
                isFileViewed = { _, _, _ ->
                    storeCalled = true
                    true
                }
            )
            val row = rowFor(relativePath = "src/A.kt", revision = "rev1")

            // WHEN
            val viewed = viewModel.isViewed(row)

            // THEN
            assertSoftly {
                viewed shouldBe false
                storeCalled shouldBe false
            }
        }

        @Test
        fun `isViewed - should ask the store with the pull request id, the row path and the row revision`() {
            // GIVEN
            var askedId: Long? = null
            var askedPath: String? = null
            var askedRevision: String? = null
            val viewModel = PullRequestChangesViewModel(
                pullRequestId = { 275L },
                projectBasePath = { BASE_PATH },
                onRefreshRequested = {},
                onOpenChange = {},
                isFileViewed = { pullRequestId, relativePath, revision ->
                    askedId = pullRequestId
                    askedPath = relativePath
                    askedRevision = revision
                    true
                }
            )
            val row = rowFor(relativePath = "src/A.kt", revision = "rev1")

            // WHEN
            val viewed = viewModel.isViewed(row)

            // THEN
            assertSoftly {
                viewed shouldBe true
                askedId shouldBe 275L
                askedPath shouldBe "src/A.kt"
                askedRevision shouldBe "rev1"
            }
        }

        @Test
        fun `isViewed - should reflect a changed store answer on the next call`() {
            // GIVEN
            var viewedAnswer = false
            val viewModel = PullRequestChangesViewModel(
                pullRequestId = { 275L },
                projectBasePath = { BASE_PATH },
                onRefreshRequested = {},
                onOpenChange = {},
                isFileViewed = { _, _, _ -> viewedAnswer }
            )
            val row = rowFor(relativePath = "src/A.kt", revision = "rev1")

            // WHEN
            val before = viewModel.isViewed(row)
            viewedAnswer = true
            val after = viewModel.isViewed(row)

            // THEN
            assertSoftly {
                before shouldBe false
                after shouldBe true
            }
        }
    }

    @Nested
    inner class AbsolutePathFor {
        @Test
        fun `absolutePathFor - should return the absolute path of the backing change`() {
            // GIVEN
            val first = changeForPath("$BASE_PATH/src/A.kt")
            val second = changeForPath("$BASE_PATH/src/B.kt")
            val viewModel = viewModel()
            viewModel.showResult(successOf(first, second))

            // WHEN
            val path = viewModel.absolutePathFor(changeId = 1)

            // THEN
            path shouldBe "$BASE_PATH/src/B.kt"
        }

        @Test
        fun `absolutePathFor - should return null for an unknown change id`() {
            // GIVEN
            val change = changeForPath("$BASE_PATH/src/A.kt")
            val viewModel = viewModel()
            viewModel.showResult(successOf(change))

            // WHEN
            val path = viewModel.absolutePathFor(changeId = 5)

            // THEN
            path.shouldBeNull()
        }
    }

    @Nested
    inner class DirectoryAbsolutePathFor {
        @Test
        fun `directoryAbsolutePathFor - should join the base path and the relative directory path`() {
            // GIVEN
            val viewModel = viewModel()

            // WHEN
            val path = viewModel.directoryAbsolutePathFor("src/main")

            // THEN
            path shouldBe "$BASE_PATH/src/main"
        }

        @Test
        fun `directoryAbsolutePathFor - should return the base path for an empty relative path`() {
            // GIVEN
            val viewModel = viewModel()

            // WHEN
            val path = viewModel.directoryAbsolutePathFor("")

            // THEN
            path shouldBe BASE_PATH
        }

        @Test
        fun `directoryAbsolutePathFor - should return null when the base path is unknown`() {
            // GIVEN
            val viewModel = PullRequestChangesViewModel(
                pullRequestId = { 275L },
                projectBasePath = { null },
                onRefreshRequested = {},
                onOpenChange = {}
            )

            // WHEN
            val path = viewModel.directoryAbsolutePathFor("src/main")

            // THEN
            path.shouldBeNull()
        }
    }

    private fun rowFor(relativePath: String, revision: String?): PullRequestFileChangeRow {
        return PullRequestFileChangeRow(
            changeId = 0,
            relativePath = relativePath,
            fileName = relativePath.substringAfterLast('/'),
            commentCount = 0,
            statusColorArgb = null,
            revision = revision
        )
    }

    private fun viewModel(
        onOpenChange: (Change) -> Unit = {},
        onOpenChangeInList: ((changes: List<Change>, index: Int) -> Unit)? = null
    ): PullRequestChangesViewModel {
        return PullRequestChangesViewModel(
            pullRequestId = { 275L },
            projectBasePath = { BASE_PATH },
            onRefreshRequested = {},
            onOpenChange = onOpenChange,
            onOpenChangeInList = onOpenChangeInList
        )
    }

    private fun successOf(vararg changes: Change): BranchDiffResult.Success {
        return BranchDiffResult.Success(
            changes = changes.toList(),
            destinationRef = "origin/main",
            sourceRef = "origin/feature"
        )
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

    companion object {
        private const val BASE_PATH = "/home/dev/project"
    }
}