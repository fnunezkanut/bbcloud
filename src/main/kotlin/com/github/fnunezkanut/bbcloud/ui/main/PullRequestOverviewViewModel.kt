package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestBuildStatus
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestDetails
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestMergeCheck
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestOverview
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestReviewer
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestTask
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerStatus
import com.github.fnunezkanut.bbcloud.bitbucket.TaskEdits
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope

/**
 * Drives the Jewel Overview tab. The editor session pushes data through the imperative `showOverview`/`showReviewers`/
 * `setUpdating` methods (the same contract the old Swing [PullRequestOverviewPanel] exposed); the composable reads
 * [state] and routes user intent back through the `on*` callbacks.
 *
 * [reviewerEditor], [taskEditor], and [descriptionEditor] perform the actual Bitbucket mutations; this view model only tracks optimistic
 * state (pending resolution, drafts, editing modes) so the UI stays responsive while a request runs.
 */
class PullRequestOverviewViewModel(
    private val reviewerEditor: ReviewerEditor,
    private val taskEditor: TaskEditor,
    private val descriptionEditor: DescriptionEditor,
    private val reviewerAvatarCache: AuthorAvatarCache = AuthorAvatarCache(avatarSize = REVIEWER_AVATAR_SIZE),
    private val popupAvatarCache: AuthorAvatarCache = AuthorAvatarCache(),
    private val openSearchPopup: (ReviewerSearchRequest) -> Unit
) {
    val state: MutableState<PullRequestOverviewUiState> = mutableStateOf(initialState())

    private var displayedPullRequestId: Long? = null
    private var coroutineScope: CoroutineScope? = null

    // Reviewer backing data.
    private var reviewers: List<PullRequestReviewer> = emptyList()
    private var authorUuid: String? = null

    // Tasks backing data; null tasks means the request failed.
    private var tasks: List<PullRequestTask>? = emptyList()
    private var isTasksLoaded = false
    private var isTaskDraftSubmitted = false
    private val pendingResolved = mutableMapOf<Long, Boolean>()
    private val draftContents = mutableMapOf<Long, String>()
    private val draftDeletedIds = mutableSetOf<Long>()
    private var tasksMode = TasksMode.View
    private var createFieldText = ""

    fun reset() {
        displayedPullRequestId = null
        reviewers = emptyList()
        authorUuid = null
        tasks = emptyList()
        isTasksLoaded = false
        isTaskDraftSubmitted = false
        pendingResolved.clear()
        clearDrafts()
        state.value = initialState()
    }

    fun showOverview(pullRequestId: Long, overview: PullRequestOverview) {
        displayedPullRequestId = pullRequestId
        renderBuilds(overview.builds)
        renderChecks(overview.mergeChecks)
        applyTasks(overview.tasks)
    }

    fun showReviewers(details: PullRequestDetails, coroutineScope: CoroutineScope) {
        this.coroutineScope = coroutineScope
        displayedPullRequestId = details.summary.id
        reviewers = details.reviewers
        authorUuid = details.authorUuid
        val editable = details.summary.state == PullRequestState.Open
        renderReviewers(isEditable = editable, isUpdating = false)
        state.value = state.value.copy(tasks = state.value.tasks.copy(isEditable = editable))
        renderTasks()
        renderDescription(details.description)
        state.value = state.value.copy(description = state.value.description.copy(isEditable = editable))
        reviewerAvatarCache.prefetch(
            urls = reviewers.mapNotNull { reviewer -> reviewer.avatarUrl },
            coroutineScope = coroutineScope,
            onLoaded = { bumpAvatarRevision() }
        )
    }

    fun setReviewersUpdating(updating: Boolean) {
        renderReviewers(isEditable = state.value.reviewers.isEditable, isUpdating = updating)
    }

    fun setTasksUpdating(updating: Boolean) {
        if (!updating) {
            isTaskDraftSubmitted = false
            pendingResolved.clear()
        }
        state.value = state.value.copy(tasks = state.value.tasks.copy(isUpdating = updating))
        renderTasks()
    }

    fun onEditDescription() {
        LOG.info("User opened the description editor for pull request #$displayedPullRequestId")
        state.value = state.value.copy(description = state.value.description.copy(isEditing = true))
    }

    fun onCancelDescriptionEdit() {
        LOG.info("User cancelled the description editor for pull request #$displayedPullRequestId")
        state.value = state.value.copy(description = state.value.description.copy(isEditing = false))
    }

    /** Builds the shared Markdown composer input; blank drafts keep the composer's standard rejection behavior. */
    fun descriptionInput(): CommentInput {
        return CommentInput(
            label = "description edit",
            placeholder = BbCloudBundle.message("main.branch.overview.description.editor.placeholder"),
            submitLabel = BbCloudBundle.message("main.branch.overview.description.editor.submit"),
            submitHint = BbCloudBundle.message("main.branch.overview.description.editor.submitHint", SUBMIT_SHORTCUT_HINT),
            failureMessage = BbCloudBundle.message("main.branch.overview.description.editor.failed.inline"),
            initialText = state.value.description.rawMarkdown,
            submit = { content, onResult ->
                descriptionEditor.updateDescription(content) { accepted ->
                    if (accepted) {
                        // Close the editor; renderDescription will refresh Markdown from the details reload.
                        state.value = state.value.copy(description = state.value.description.copy(isEditing = false))
                    }
                    onResult(accepted)
                }
            }
        )
    }

    fun avatarIconFor(url: String?): javax.swing.Icon {
        return reviewerAvatarCache.iconFor(url)
    }

    private fun renderDescription(description: String?) {
        val markdown = description?.takeIf { value -> value.isNotBlank() }
        state.value = state.value.copy(
            description = state.value.description.copy(
                markdown = markdown,
                emptyMessage = if (markdown == null) {
                    BbCloudBundle.message("main.branch.overview.description.empty")
                } else {
                    null
                },
                rawMarkdown = description.orEmpty()
            )
        )
        LOG.debug("Rendered description for pull request #$displayedPullRequestId: present=${markdown != null}")
    }

    // region Builds

    private fun renderBuilds(builds: List<PullRequestBuildStatus>?) {
        val section = when {
            builds == null -> BuildsSectionState(
                title = BbCloudBundle.message("main.branch.builds.title"),
                isExpanded = state.value.builds.isExpanded,
                message = BbCloudBundle.message("main.branch.builds.error")
            )
            builds.isEmpty() -> BuildsSectionState(
                title = BbCloudBundle.message("main.branch.builds.title"),
                isExpanded = state.value.builds.isExpanded,
                message = BbCloudBundle.message("main.branch.builds.empty")
            )
            else -> BuildsSectionState(
                title = buildsSummaryText(BuildStatusSummary.of(builds)),
                isExpanded = state.value.builds.isExpanded,
                rows = builds.map { build -> toBuildRow(build) }
            )
        }
        state.value = state.value.copy(builds = section)
    }

    private fun buildsSummaryText(summary: BuildStatusSummary): String {
        return when (summary.kind) {
            BuildSummaryKind.Empty -> BbCloudBundle.message("main.branch.builds.title")
            BuildSummaryKind.Failed -> BbCloudBundle.message("main.branch.builds.summary.failed", summary.count, summary.total)
            BuildSummaryKind.InProgress -> BbCloudBundle.message("main.branch.builds.summary.inProgress", summary.count, summary.total)
            BuildSummaryKind.Passed -> BbCloudBundle.message("main.branch.builds.summary.passed", summary.total)
        }
    }

    private fun toBuildRow(build: PullRequestBuildStatus): BuildRowState {
        val details = listOfNotNull(
            build.description,
            build.updatedOn?.let { PullRequestRelativeTime.format(it) }
        ).joinToString(separator = DETAILS_SEPARATOR)
        return BuildRowState(
            key = build.key,
            name = build.name,
            url = build.url,
            state = build.state,
            details = details.ifEmpty { null }
        )
    }

    fun onBuildOpened(key: String) {
        LOG.info("User opened build $key for pull request #$displayedPullRequestId in browser")
    }

    fun onToggleBuilds() {
        logToggle(sectionName = "builds", expanding = !state.value.builds.isExpanded)
        state.value = state.value.copy(builds = state.value.builds.copy(isExpanded = !state.value.builds.isExpanded))
    }

    // endregion

    // region Checks

    private fun renderChecks(checks: List<PullRequestMergeCheck>?) {
        val expanded = state.value.checks.isExpanded
        val section = when {
            checks == null -> {
                ChecksSectionState(
                    title = BbCloudBundle.message("main.branch.checks.title"),
                    isExpanded = expanded,
                    message = BbCloudBundle.message("main.branch.checks.error")
                )
            }
            checks.isEmpty() -> {
                ChecksSectionState(
                    title = BbCloudBundle.message("main.branch.checks.title"),
                    isExpanded = expanded,
                    message = BbCloudBundle.message("main.branch.checks.empty")
                )
            }
            else -> {
                val summary = MergeCheckSummary.of(checks)
                ChecksSectionState(
                    title = BbCloudBundle.message("main.branch.checks.summary", summary.passed, summary.total),
                    isExpanded = expanded,
                    statusIcon = if (summary.isBlocking) SectionStatusIcon.Error else SectionStatusIcon.Success,
                    statusTooltip = if (summary.isBlocking) {
                        BbCloudBundle.message("main.branch.checks.header.blocking")
                    } else {
                        BbCloudBundle.message("main.branch.checks.header.notBlocking")
                    },
                    rows = checks.map { check -> toCheckRow(check) }
                )
            }
        }
        state.value = state.value.copy(checks = section)
    }

    private fun toCheckRow(check: PullRequestMergeCheck): CheckRowState {
        return CheckRowState(
            labelHtml = MergeCheckLabels.toHtml(MergeCheckLabels.labelFor(check)),
            tooltip = checkTooltip(check),
            status = check.status,
            isBlocking = check.isBlocking
        )
    }

    private fun checkTooltip(check: PullRequestMergeCheck): String {
        val key = when (check.status) {
            com.github.fnunezkanut.bbcloud.bitbucket.MergeCheckStatus.Passed -> {
                "main.branch.checks.tooltip.passed"
            }
            com.github.fnunezkanut.bbcloud.bitbucket.MergeCheckStatus.Failed -> {
                if (check.isBlocking) "main.branch.checks.tooltip.failedBlocking" else "main.branch.checks.tooltip.failedNotBlocking"
            }
            com.github.fnunezkanut.bbcloud.bitbucket.MergeCheckStatus.Pending -> {
                "main.branch.checks.tooltip.pending"
            }
            com.github.fnunezkanut.bbcloud.bitbucket.MergeCheckStatus.Skipped -> {
                "main.branch.checks.tooltip.skipped"
            }
            com.github.fnunezkanut.bbcloud.bitbucket.MergeCheckStatus.Unknown -> {
                "main.branch.checks.tooltip.unknown"
            }
        }
        return BbCloudBundle.message(key)
    }

    fun onToggleChecks() {
        logToggle(sectionName = "merge checks", expanding = !state.value.checks.isExpanded)
        state.value = state.value.copy(checks = state.value.checks.copy(isExpanded = !state.value.checks.isExpanded))
    }

    // endregion

    // region Reviewers

    private fun renderReviewers(isEditable: Boolean, isUpdating: Boolean) {
        val statusIcon = when {
            isUpdating -> SectionStatusIcon.Loading
            reviewers.isEmpty() -> SectionStatusIcon.None
            reviewers.any { it.status == ReviewerStatus.ChangesRequested } -> SectionStatusIcon.Error
            reviewers.any { it.status == ReviewerStatus.Approved } -> SectionStatusIcon.Success
            else -> SectionStatusIcon.NoVote
        }
        val approved = reviewers.count { it.status == ReviewerStatus.Approved }
        val tooltip = if (reviewers.isEmpty()) {
            null
        } else {
            BbCloudBundle.message("main.branch.reviewers.header.approved", approved, reviewers.size)
        }
        state.value = state.value.copy(
            reviewers = ReviewersSectionState(
                title = BbCloudBundle.message("main.branch.reviewers.summary", reviewers.size),
                isExpanded = state.value.reviewers.isExpanded,
                statusIcon = statusIcon,
                statusTooltip = if (isUpdating) null else tooltip,
                isLoaded = true,
                reviewers = reviewers,
                emptyMessage = if (reviewers.isEmpty()) BbCloudBundle.message("main.branch.reviewers.empty") else null,
                isEditable = isEditable,
                isUpdating = isUpdating
            )
        )
    }

    fun onToggleReviewers() {
        logToggle(sectionName = "reviewers", expanding = !state.value.reviewers.isExpanded)
        state.value = state.value.copy(reviewers = state.value.reviewers.copy(isExpanded = !state.value.reviewers.isExpanded))
    }

    fun onRemoveReviewer(reviewer: PullRequestReviewer) {
        if (state.value.reviewers.isUpdating) {
            LOG.debug("Ignoring remove reviewer click on pull request #$displayedPullRequestId; a reviewer update is in progress")
            return
        }
        LOG.info("User clicked remove reviewer on pull request #$displayedPullRequestId")
        setReviewersUpdating(true)
        reviewerEditor.removeReviewer(reviewer)
    }

    fun onAddReviewer() {
        val scope = coroutineScope ?: return
        if (state.value.reviewers.isUpdating) {
            LOG.debug("Ignoring add reviewer click on pull request #$displayedPullRequestId; a reviewer update is in progress")
            return
        }
        LOG.info("User opened the reviewer search for pull request #$displayedPullRequestId")
        val excludedUuids = reviewers.map { it.uuid }.toSet() + listOfNotNull(authorUuid)
        openSearchPopup(
            ReviewerSearchRequest(
                excludedUuids = excludedUuids,
                avatarCache = popupAvatarCache,
                coroutineScope = scope,
                loadCandidates = { onLoaded -> reviewerEditor.loadCandidates(onLoaded) },
                onSelected = { candidate ->
                    LOG.info("User selected a reviewer to add to pull request #$displayedPullRequestId")
                    setReviewersUpdating(true)
                    reviewerEditor.addReviewer(candidate)
                }
            )
        )
    }

    // endregion

    // region Tasks

    private fun applyTasks(tasks: List<PullRequestTask>?) {
        this.tasks = tasks
        isTasksLoaded = true
        if (isTaskDraftSubmitted || tasks == null) {
            clearDrafts()
        }
        isTaskDraftSubmitted = false
        pendingResolved.clear()
        state.value = state.value.copy(tasks = state.value.tasks.copy(isUpdating = false))
        renderTasks()
    }

    private fun clearDrafts() {
        tasksMode = TasksMode.View
        createFieldText = ""
        draftContents.clear()
        draftDeletedIds.clear()
    }

    private fun renderTasks() {
        val current = tasks.orEmpty()
        val isEditable = state.value.tasks.isEditable
        val isUpdating = state.value.tasks.isUpdating
        val resolvedCount = current.count { isDisplayedResolved(it) }
        val isAllResolved = current.isNotEmpty() && resolvedCount == current.size
        val title = if (current.isEmpty()) {
            BbCloudBundle.message("main.branch.tasks.title")
        } else {
            BbCloudBundle.message("main.branch.tasks.summary", resolvedCount, current.size)
        }

        val section = TasksSectionState(
            title = title,
            isExpanded = state.value.tasks.isExpanded,
            isUpdating = isUpdating,
            isLoaded = isTasksLoaded,
            isError = tasks == null,
            isEditable = isEditable,
            mode = tasksMode,
            headerCheckboxVisible = current.isNotEmpty(),
            headerCheckboxSelected = isAllResolved,
            headerCheckboxEnabled = isEditable && !isUpdating && tasksMode != TasksMode.Editing,
            headerCheckboxTooltip = BbCloudBundle.message(
                if (isAllResolved) "main.branch.tasks.reopenAll" else "main.branch.tasks.resolveAll"
            ),
            emptyMessage = if (isTasksLoaded && tasks != null && current.isEmpty()) {
                BbCloudBundle.message("main.branch.tasks.empty")
            } else {
                null
            },
            viewRows = if (tasksMode == TasksMode.Editing) emptyList() else current.map { toTaskViewRow(it, isEditable, isUpdating) },
            editRows = if (tasksMode == TasksMode.Editing) {
                current.filterNot { it.id in draftDeletedIds }.map { toTaskEditRow(it, isUpdating) }
            } else {
                emptyList()
            },
            createFieldText = createFieldText,
            createPlaceholder = BbCloudBundle.message("main.branch.tasks.create.placeholder"),
            saveEnabled = computeSaveEnabled(isUpdating)
        )
        state.value = state.value.copy(tasks = section)
    }

    private fun toTaskViewRow(task: PullRequestTask, isEditable: Boolean, isUpdating: Boolean): TaskViewRow {
        return TaskViewRow(
            id = task.id,
            content = task.content,
            resolved = isDisplayedResolved(task),
            enabled = isEditable && !isUpdating
        )
    }

    private fun toTaskEditRow(task: PullRequestTask, isUpdating: Boolean): TaskEditRow {
        return TaskEditRow(
            id = task.id,
            content = draftContents[task.id] ?: task.content,
            resolved = task.isResolved,
            enabled = !task.isResolved && !isUpdating
        )
    }

    private fun currentEdits(): TaskEdits {
        return TaskEdits.of(original = tasks.orEmpty(), editedContents = draftContents, deletedIds = draftDeletedIds)
    }

    private fun computeSaveEnabled(isUpdating: Boolean): Boolean {
        if (tasksMode != TasksMode.Editing) {
            return false
        }
        val edits = currentEdits()
        return !isUpdating && !edits.isEmpty && !edits.hasBlankContent
    }

    private fun isDisplayedResolved(task: PullRequestTask): Boolean {
        return pendingResolved[task.id] ?: task.isResolved
    }

    fun onToggleTasks() {
        logToggle(sectionName = "tasks", expanding = !state.value.tasks.isExpanded)
        state.value = state.value.copy(tasks = state.value.tasks.copy(isExpanded = !state.value.tasks.isExpanded))
    }

    fun onTaskResolvedChanged(taskId: Long, resolved: Boolean) {
        LOG.info("User ${if (resolved) "resolved" else "reopened"} task $taskId on pull request #$displayedPullRequestId")
        requestResolved(taskIds = listOf(taskId), resolved = resolved)
    }

    fun onHeaderCheckboxClicked() {
        val current = tasks.orEmpty()
        if (current.isEmpty() || state.value.tasks.isUpdating || !state.value.tasks.isEditable) {
            renderTasks()
            return
        }
        val isAllResolved = current.all { isDisplayedResolved(it) }
        val resolved = !isAllResolved
        val taskIds = current.filter { isDisplayedResolved(it) != resolved }.map { it.id }
        LOG.info("User ${if (resolved) "resolved" else "reopened"} all ${taskIds.size} tasks from the header of pull request #$displayedPullRequestId")
        requestResolved(taskIds = taskIds, resolved = resolved)
    }

    private fun requestResolved(taskIds: List<Long>, resolved: Boolean) {
        if (state.value.tasks.isUpdating) {
            LOG.debug("Ignoring task resolution click on pull request #$displayedPullRequestId; a task update is in progress")
            renderTasks()
            return
        }
        taskIds.forEach { pendingResolved[it] = resolved }
        setTasksUpdating(true)
        taskEditor.setTasksResolved(taskIds = taskIds, resolved = resolved)
    }

    fun onStartCreating() {
        if (state.value.tasks.isUpdating) {
            return
        }
        LOG.info("User opened the create task field on pull request #$displayedPullRequestId")
        tasksMode = TasksMode.Creating
        renderTasks()
    }

    fun onStopCreating() {
        if (tasksMode != TasksMode.Creating || state.value.tasks.isUpdating) {
            return
        }
        LOG.info("User cancelled creating a task on pull request #$displayedPullRequestId")
        tasksMode = TasksMode.View
        createFieldText = ""
        renderTasks()
    }

    fun onCreateFieldChanged(text: String) {
        createFieldText = text
        state.value = state.value.copy(tasks = state.value.tasks.copy(createFieldText = text))
    }

    fun onSubmitCreate() {
        val content = createFieldText.trim()
        if (content.isEmpty() || state.value.tasks.isUpdating) {
            return
        }
        LOG.info("User created a task on pull request #$displayedPullRequestId")
        isTaskDraftSubmitted = true
        setTasksUpdating(true)
        taskEditor.createTask(content)
    }

    fun onStartEditing() {
        if (state.value.tasks.isUpdating) {
            return
        }
        LOG.info("User opened the tasks editor of pull request #$displayedPullRequestId")
        draftContents.clear()
        draftDeletedIds.clear()
        tasksMode = TasksMode.Editing
        renderTasks()
    }

    fun onStopEditing() {
        if (state.value.tasks.isUpdating) {
            return
        }
        LOG.info("User cancelled editing tasks of pull request #$displayedPullRequestId")
        clearDrafts()
        renderTasks()
    }

    fun onEditRowChanged(taskId: Long, text: String) {
        draftContents[taskId] = text
        renderTasks()
    }

    fun onDeleteEditRow(taskId: Long) {
        if (state.value.tasks.isUpdating) {
            return
        }
        LOG.info("User deleted task $taskId in the tasks editor of pull request #$displayedPullRequestId")
        draftDeletedIds += taskId
        renderTasks()
    }

    fun onSubmitEdits() {
        val edits = currentEdits()
        if (state.value.tasks.isUpdating || edits.isEmpty || edits.hasBlankContent) {
            return
        }
        LOG.info(
            "User saved task edits of pull request #$displayedPullRequestId: " +
                "updated=${edits.contentUpdates.size}, deleted=${edits.deletedIds.size}"
        )
        isTaskDraftSubmitted = true
        setTasksUpdating(true)
        taskEditor.saveTaskEdits(edits)
    }

    // endregion

    private fun logToggle(sectionName: String, expanding: Boolean) {
        LOG.info("User ${if (expanding) "expanded" else "collapsed"} the $sectionName section for pull request #$displayedPullRequestId")
    }

    private fun bumpAvatarRevision() {
        state.value = state.value.copy(avatarRevision = state.value.avatarRevision + 1)
    }

    private fun initialState(): PullRequestOverviewUiState {
        return PullRequestOverviewUiState(
            description = DescriptionSectionState(
                title = BbCloudBundle.message("main.branch.overview.card.description"),
                emptyMessage = BbCloudBundle.message("main.branch.overview.description.empty")
            ),
            builds = BuildsSectionState(title = BbCloudBundle.message("main.branch.builds.title")),
            checks = ChecksSectionState(title = BbCloudBundle.message("main.branch.checks.title")),
            reviewers = ReviewersSectionState(title = BbCloudBundle.message("main.branch.reviewers.title")),
            tasks = TasksSectionState(
                title = BbCloudBundle.message("main.branch.tasks.title"),
                createPlaceholder = BbCloudBundle.message("main.branch.tasks.create.placeholder")
            )
        )
    }

    companion object {
        private val LOG = logger<PullRequestOverviewViewModel>()

        private const val REVIEWER_AVATAR_SIZE = 20
        private const val DETAILS_SEPARATOR = " \u00b7 "

        /** The platform shortcut of the collaboration comment editor; the Jewel composer wires the same key itself. */
        private val SUBMIT_SHORTCUT_HINT: String = CommentInputActionsComponentFactory.submitShortcutText
    }
}

/** Parameters the view model hands to the host so it can open the Swing reviewer-search popup. */
data class ReviewerSearchRequest(
    val excludedUuids: Set<String>,
    val avatarCache: AuthorAvatarCache,
    val coroutineScope: CoroutineScope,
    val loadCandidates: ((com.github.fnunezkanut.bbcloud.bitbucket.ReviewerCandidates?) -> Unit) -> Unit,
    val onSelected: (com.github.fnunezkanut.bbcloud.bitbucket.ReviewerCandidate) -> Unit
)