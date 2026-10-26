package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.git.FileLineCount

/**
 * The state of the file-changes card of the Changes tab. It mirrors the old Swing `ViewCard` of
 * [PullRequestFilesChangedPanel], so the same five states are rendered in the same order.
 */
enum class PullRequestFileChangesCard {
    Idle,
    Loading,
    Error,
    Empty,
    Listing
}

/**
 * One changed file, rendered as a leaf of the Jewel file tree.
 *
 * The row deliberately keeps no reference to the platform `Change`: the UI state stays a plain data model and the
 * view model resolves a row back to its change through [changeId].
 *
 * @param changeId The index of the backing change in the list held by the view model.
 * @param relativePath The full project-relative path of the file.
 * @param fileName The last path segment, shown as the leaf label.
 * @param commentCount The number of inline comments on the file; zero means no badge.
 * @param statusColorArgb The ARGB color of the file status, or null to use the default text color.
 * @param lineCount The added and removed line counts of the file, or null when they are unknown or not loaded. A
 * binary file may be non-null with [FileLineCount.isBinary] set to true.
 */
data class PullRequestFileChangeRow(
    val changeId: Int,
    val relativePath: String,
    val fileName: String,
    val commentCount: Int,
    val statusColorArgb: Int?,
    val lineCount: FileLineCount? = null
)

/** The Compose state of the Changes tab: the file-changes card plus the rows the file tree is built from. */
data class PullRequestChangesUiState(
    val card: PullRequestFileChangesCard = PullRequestFileChangesCard.Idle,
    val rows: List<PullRequestFileChangeRow> = emptyList(),
    val errorMessage: String? = null
)