package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.BuildState
import com.github.fnunezkanut.bbcloud.bitbucket.MergeCheckStatus
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestReviewer
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerStatus

/** Compose state for the Jewel Overview tab: the description plus four collapsible cards (Builds, Checks, Reviewers, Tasks). */
data class PullRequestOverviewUiState(
    val description: DescriptionSectionState = DescriptionSectionState(),
    val builds: BuildsSectionState = BuildsSectionState(),
    val checks: ChecksSectionState = ChecksSectionState(),
    val reviewers: ReviewersSectionState = ReviewersSectionState(),
    val tasks: TasksSectionState = TasksSectionState(),
    val avatarRevision: Int = 0
)

/** The pull-request description rendered in the larger Overview column. */
data class DescriptionSectionState(
    val title: String = "",
    val markdown: String? = null,
    val emptyMessage: String? = null,
    /** Raw Markdown source of the current description, used to seed the editor; empty when none. */
    val rawMarkdown: String = "",
    /** True when the PR is Open and the current user may edit the description. */
    val isEditable: Boolean = false,
    /** True while the inline description editor is open. */
    val isEditing: Boolean = false
)

/** The status affordance shown on the right of a collapsible section header. */
enum class SectionStatusIcon {
    None,
    Loading,
    Error,
    Success,

    /** Reviewers are assigned but none have approved or requested changes yet (the old EmptyCircleIcon state). */
    NoVote
}

data class BuildsSectionState(val title: String = "", val isExpanded: Boolean = true, val message: String? = null, val rows: List<BuildRowState> = emptyList())

data class BuildRowState(val key: String, val name: String, val url: String, val state: BuildState, val details: String?)

data class ChecksSectionState(
    val title: String = "",
    val isExpanded: Boolean = true,
    val statusIcon: SectionStatusIcon = SectionStatusIcon.None,
    val statusTooltip: String? = null,
    val message: String? = null,
    val rows: List<CheckRowState> = emptyList()
)

data class CheckRowState(val labelHtml: String, val tooltip: String, val status: MergeCheckStatus, val isBlocking: Boolean)

data class ReviewersSectionState(
    val title: String = "",
    val isExpanded: Boolean = true,
    val statusIcon: SectionStatusIcon = SectionStatusIcon.None,
    val statusTooltip: String? = null,
    val isLoaded: Boolean = false,
    val reviewers: List<PullRequestReviewer> = emptyList(),
    val emptyMessage: String? = null,
    val isEditable: Boolean = false,
    val isUpdating: Boolean = false
)

/** Aggregated reviewer approval icon shown in the section header. */
enum class ReviewerAggregateIcon {
    None,
    Approved,
    ChangesRequested,
    NoVote
}

fun ReviewerStatus.toAggregate(): ReviewerAggregateIcon {
    return when (this) {
        ReviewerStatus.Approved -> ReviewerAggregateIcon.Approved
        ReviewerStatus.ChangesRequested -> ReviewerAggregateIcon.ChangesRequested
        ReviewerStatus.None -> ReviewerAggregateIcon.NoVote
    }
}

enum class TasksMode {
    View,
    Creating,
    Editing
}

data class TasksSectionState(
    val title: String = "",
    val isExpanded: Boolean = true,
    val isUpdating: Boolean = false,
    val isLoaded: Boolean = false,
    val isError: Boolean = false,
    val isEditable: Boolean = false,
    val mode: TasksMode = TasksMode.View,
    val headerCheckboxVisible: Boolean = false,
    val headerCheckboxSelected: Boolean = false,
    val headerCheckboxEnabled: Boolean = false,
    val headerCheckboxTooltip: String = "",
    val emptyMessage: String? = null,
    val viewRows: List<TaskViewRow> = emptyList(),
    val editRows: List<TaskEditRow> = emptyList(),
    val createFieldText: String = "",
    val createPlaceholder: String = "",
    val saveEnabled: Boolean = false
)

data class TaskViewRow(val id: Long, val content: String, val resolved: Boolean, val enabled: Boolean)

data class TaskEditRow(val id: Long, val content: String, val resolved: Boolean, val enabled: Boolean)