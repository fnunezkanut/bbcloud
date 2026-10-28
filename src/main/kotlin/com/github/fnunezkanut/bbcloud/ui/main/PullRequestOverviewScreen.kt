@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.MergeCheckStatus
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestReviewer
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerStatus
import com.github.fnunezkanut.bbcloud.ui.components.BbCloudOutlinedActionButton
import com.github.fnunezkanut.bbcloud.ui.otherprojects.toComposeImageBitmap
import com.intellij.ide.BrowserUtil
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private val CARD_GAP = 8.dp

/**
 * The Jewel Overview tab: the description with the history feed beneath it on the left, and four existing cards on the
 * right, in a golden-ratio split. The left column scrolls as one, so a long description and a long history do not
 * fight over the height.
 */
@Composable
fun PullRequestOverviewScreen(viewModel: PullRequestOverviewViewModel, historyViewModel: PullRequestHistoryViewModel) {
    val state by viewModel.state
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(CARD_GAP)
    ) {
        VerticallyScrollableContainer(
            modifier = Modifier
                .weight(GOLDEN_RATIO_LEFT)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CARD_GAP)
            ) {
                TitledCardBox(title = null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.description.title,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            if (state.description.isEditable && !state.description.isEditing) {
                                EditDescriptionButton(onClick = viewModel::onEditDescription)
                            }
                        }
                        DescriptionBody(viewModel = viewModel)
                    }
                }
                // The feed renders its own bold "History" title, so the card carries no title of its own.
                TitledCardBox {
                    PullRequestHistoryFeedScreen(viewModel = historyViewModel)
                }
            }
        }
        VerticallyScrollableContainer(
            modifier = Modifier
                .weight(GOLDEN_RATIO_RIGHT)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CARD_GAP)
            ) {
                TitledCardBox {
                    BuildsCard(viewModel = viewModel)
                }
                TitledCardBox {
                    ChecksCard(viewModel = viewModel)
                }
                TitledCardBox {
                    ReviewersCard(viewModel = viewModel)
                }
                TitledCardBox {
                    TasksCard(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun EditDescriptionButton(onClick: () -> Unit) {
    val label = BbCloudBundle.message("main.branch.overview.description.edit")
    Tooltip(tooltip = { Text(text = label) }) {
        BbCloudOutlinedActionButton(
            text = label,
            icon = AllIconsKeys.Actions.Edit,
            outlineColor = ActionButtonOutlines.blue(),
            enabled = true,
            onClick = onClick
        )
    }
}

@Composable
private fun DescriptionBody(viewModel: PullRequestOverviewViewModel) {
    val state by viewModel.state
    if (!state.description.isEditing) {
        val markdown = state.description.markdown
        if (markdown.isNullOrBlank()) {
            state.description.emptyMessage?.let { message -> MessageText(text = message) }
        } else {
            // The left column of the Overview tab scrolls, so the description sizes to its content here.
            MarkdownDescription(markdown = markdown)
        }
        return
    }

    val input = remember(state.description.rawMarkdown, state.description.isEditing) { viewModel.descriptionInput() }
    val composerViewModel = remember(input) { CommentComposerViewModel(input = input, startsExpanded = true) }
    CommentComposerScreen(
        viewModel = composerViewModel,
        avatarIcon = viewModel.avatarIconFor(null),
        showsCollapsedField = false,
        showsDottedBorder = true,
        onCollapsed = viewModel::onCancelDescriptionEdit
    )
}

/**
 * The shared collapsible section chrome: a clickable chevron + title header with an optional status icon,
 * over the expandable body. Mirrors the old Swing CollapsibleSidebarSection.
 */
@Composable
private fun CollapsibleSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    titleIcon: IconKey? = null,
    isTitleBold: Boolean = true,
    statusIcon: SectionStatusIcon = SectionStatusIcon.None,
    statusTooltip: String? = null,
    leading: (@Composable () -> Unit)? = null,
    body: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                key = if (isExpanded) AllIconsKeys.General.ChevronDown else AllIconsKeys.General.ChevronRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.clickable(onClick = onToggle).pointerHoverIcon(PointerIcon.Hand)
            )
            leading?.invoke()
            titleIcon?.let { key ->
                Icon(key = key, contentDescription = null)
            }
            Text(
                text = htmlToPlain(title),
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggle)
                    .pointerHoverIcon(PointerIcon.Hand),
                fontWeight = if (isTitleBold) FontWeight.Bold else null,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
            SectionStatus(statusIcon = statusIcon, tooltip = statusTooltip)
        }
        if (isExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                body()
            }
        }
    }
}

@Composable
private fun SectionStatus(statusIcon: SectionStatusIcon, tooltip: String?) {
    val key = when (statusIcon) {
        SectionStatusIcon.None -> return
        SectionStatusIcon.Loading -> AllIconsKeys.Process.Step_1
        SectionStatusIcon.Error -> AllIconsKeys.General.Error
        SectionStatusIcon.Success -> AllIconsKeys.Status.Success
        SectionStatusIcon.NoVote -> AllIconsKeys.RunConfigurations.TestNotRan
    }
    if (tooltip != null) {
        Tooltip(tooltip = { Text(text = tooltip) }) {
            Icon(key = key, contentDescription = tooltip)
        }
    } else {
        Icon(key = key, contentDescription = null)
    }
}

/** An inline icon-and-text link rendered as a clickable Row, since Jewel's Link has no icon slot. */
@Composable
private fun IconLink(text: String, icon: IconKey, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(key = icon, contentDescription = null)
        Link(text = text, onClick = onClick, enabled = enabled)
    }
}

/** A standalone clickable icon used for remove/delete affordances (Jewel has no inline icon button). */
@Composable
private fun ClickableIcon(icon: IconKey, description: String, enabled: Boolean, onClick: () -> Unit) {
    val modifier = if (enabled) {
        Modifier.clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
    } else {
        Modifier
    }
    Icon(key = icon, contentDescription = description, modifier = modifier)
}

@Composable
private fun MessageText(text: String) {
    Text(text = text, color = JewelTheme.globalColors.text.info)
}

// region Builds

@Composable
private fun BuildsCard(viewModel: PullRequestOverviewViewModel) {
    val state by viewModel.state
    val builds = state.builds
    CollapsibleSection(
        title = builds.title,
        isExpanded = builds.isExpanded,
        onToggle = viewModel::onToggleBuilds
    ) {
        builds.message?.let { MessageText(text = it) }
        builds.rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                Icon(key = buildStateIconKey(row.state), contentDescription = null)
                Column {
                    Link(
                        text = row.name,
                        onClick = {
                            viewModel.onBuildOpened(row.key)
                            BrowserUtil.browse(row.url)
                        }
                    )
                    row.details?.let { MessageText(text = it) }
                }
            }
        }
    }
}

// endregion

// endregion
// region Checks

@Composable
private fun ChecksCard(viewModel: PullRequestOverviewViewModel) {
    val state by viewModel.state
    val checks = state.checks
    CollapsibleSection(
        title = checks.title,
        isExpanded = checks.isExpanded,
        onToggle = viewModel::onToggleChecks,
        titleIcon = AllIconsKeys.Actions.ListChanges,
        isTitleBold = false,
        statusIcon = checks.statusIcon,
        statusTooltip = checks.statusTooltip
    ) {
        checks.message?.let { MessageText(text = it) }
        checks.rows.forEach { row ->
            Tooltip(tooltip = { Text(text = row.tooltip) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                    Icon(key = checkStatusIconKey(row.status, row.isBlocking), contentDescription = null)
                    Text(text = htmlToPlain(row.labelHtml))
                }
            }
        }
    }
}

private fun checkStatusIconKey(status: MergeCheckStatus, isBlocking: Boolean): IconKey {
    return when (status) {
        MergeCheckStatus.Passed -> AllIconsKeys.RunConfigurations.TestPassed
        MergeCheckStatus.Failed -> if (isBlocking) AllIconsKeys.RunConfigurations.TestFailed else AllIconsKeys.General.BalloonWarning
        MergeCheckStatus.Pending -> AllIconsKeys.Process.Step_1
        MergeCheckStatus.Skipped, MergeCheckStatus.Unknown -> AllIconsKeys.RunConfigurations.TestIgnored
    }
}

// endregion

// region Reviewers

@Composable
private fun ReviewersCard(viewModel: PullRequestOverviewViewModel) {
    val state by viewModel.state
    val reviewers = state.reviewers
    CollapsibleSection(
        title = reviewers.title,
        isExpanded = reviewers.isExpanded,
        onToggle = viewModel::onToggleReviewers,
        titleIcon = AllIconsKeys.General.User,
        isTitleBold = false,
        statusIcon = reviewers.statusIcon,
        statusTooltip = reviewers.statusTooltip
    ) {
        reviewers.emptyMessage?.let { MessageText(text = it) }
        reviewers.reviewers.forEach { reviewer ->
            ReviewerRow(
                reviewer = reviewer,
                isEditable = reviewers.isEditable,
                isUpdating = reviewers.isUpdating,
                avatarRevision = state.avatarRevision,
                viewModel = viewModel
            )
        }
        if (reviewers.isEditable) {
            IconLink(
                text = BbCloudBundle.message("main.branch.reviewers.add"),
                icon = AllIconsKeys.General.Add,
                enabled = !reviewers.isUpdating,
                onClick = viewModel::onAddReviewer
            )
        }
    }
}

@Composable
private fun ReviewerRow(reviewer: PullRequestReviewer, isEditable: Boolean, isUpdating: Boolean, avatarRevision: Int, viewModel: PullRequestOverviewViewModel) {
    val bitmap = remember(reviewer.avatarUrl, avatarRevision) {
        viewModel.avatarIconFor(reviewer.avatarUrl).toComposeImageBitmap()
    }
    // Reuses the shared reviewer row so the Overview output stays pixel-identical to the new pull request form: the
    // status tooltip and icon slot, then the 20dp avatar, the name, and the remove affordance.
    ReviewerIdentityRow(
        displayName = reviewer.displayName,
        avatar = bitmap,
        statusIcon = {
            Tooltip(tooltip = { Text(text = reviewerStatusTooltip(reviewer.status)) }) {
                Icon(key = reviewerStatusIconKey(reviewer.status), contentDescription = null)
            }
        },
        isRemovable = isEditable,
        isRemoveEnabled = !isUpdating,
        onRemove = { viewModel.onRemoveReviewer(reviewer) }
    )
}

private fun reviewerStatusIconKey(status: ReviewerStatus): IconKey {
    return when (status) {
        ReviewerStatus.Approved -> AllIconsKeys.RunConfigurations.TestPassed
        ReviewerStatus.ChangesRequested -> AllIconsKeys.General.BalloonWarning
        ReviewerStatus.None -> AllIconsKeys.RunConfigurations.TestNotRan
    }
}

private fun reviewerStatusTooltip(status: ReviewerStatus): String {
    return when (status) {
        ReviewerStatus.Approved -> BbCloudBundle.message("main.branch.reviewers.status.approved")
        ReviewerStatus.ChangesRequested -> BbCloudBundle.message("main.branch.reviewers.status.changesRequested")
        ReviewerStatus.None -> BbCloudBundle.message("main.branch.reviewers.status.none")
    }
}

// endregion

// region Tasks

@Composable
private fun TasksCard(viewModel: PullRequestOverviewViewModel) {
    val state by viewModel.state
    val tasks = state.tasks
    CollapsibleSection(
        title = tasks.title,
        isExpanded = tasks.isExpanded,
        onToggle = viewModel::onToggleTasks,
        isTitleBold = false,
        statusIcon = if (tasks.isUpdating) SectionStatusIcon.Loading else SectionStatusIcon.None,
        leading = if (tasks.headerCheckboxVisible) {
            {
                Tooltip(tooltip = { Text(text = tasks.headerCheckboxTooltip) }) {
                    Checkbox(
                        checked = tasks.headerCheckboxSelected,
                        onCheckedChange = { viewModel.onHeaderCheckboxClicked() },
                        enabled = tasks.headerCheckboxEnabled
                    )
                }
            }
        } else {
            null
        }
    ) {
        when {
            tasks.isError -> MessageText(text = BbCloudBundle.message("main.branch.tasks.error"))
            tasks.mode == TasksMode.Editing -> TasksEditContent(tasks = tasks, viewModel = viewModel)
            else -> TasksViewContent(tasks = tasks, viewModel = viewModel)
        }
    }
}

@Composable
private fun TasksViewContent(tasks: TasksSectionState, viewModel: PullRequestOverviewViewModel) {
    tasks.emptyMessage?.let { MessageText(text = it) }
    tasks.viewRows.forEach { row ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Checkbox(
                checked = row.resolved,
                onCheckedChange = { resolved -> viewModel.onTaskResolvedChanged(row.id, resolved) },
                enabled = row.enabled
            )
            SelectableText(text = row.content)
        }
    }
    if (!tasks.isEditable) {
        return
    }
    if (tasks.mode == TasksMode.Creating) {
        CreateTaskField(tasks = tasks, viewModel = viewModel)
    } else {
        IconLink(
            text = BbCloudBundle.message("main.branch.tasks.create"),
            icon = AllIconsKeys.General.Add,
            enabled = !tasks.isUpdating,
            onClick = viewModel::onStartCreating
        )
    }
    if (tasks.viewRows.isNotEmpty() && tasks.mode == TasksMode.View) {
        Link(
            text = BbCloudBundle.message("main.branch.tasks.edit"),
            onClick = viewModel::onStartEditing,
            enabled = !tasks.isUpdating
        )
    }
}

@Composable
private fun CreateTaskField(tasks: TasksSectionState, viewModel: PullRequestOverviewViewModel) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(tasks.createFieldText)) }
    // Resync when the view model clears the field (after submit or cancel) without fighting local edits.
    LaunchedEffect(tasks.createFieldText) {
        if (tasks.createFieldText != fieldValue.text) {
            fieldValue = TextFieldValue(tasks.createFieldText)
        }
    }
    TextField(
        value = fieldValue,
        onValueChange = { value ->
            fieldValue = value
            viewModel.onCreateFieldChanged(value.text)
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !tasks.isUpdating,
        placeholder = { Text(text = tasks.createPlaceholder) },
        keyboardOptions = KeyboardOptions.Default
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Link(
            text = BbCloudBundle.message("main.branch.tasks.save"),
            onClick = viewModel::onSubmitCreate,
            enabled = !tasks.isUpdating && fieldValue.text.isNotBlank()
        )
        Link(
            text = BbCloudBundle.message("main.branch.tasks.cancel"),
            onClick = viewModel::onStopCreating,
            enabled = !tasks.isUpdating
        )
    }
}

@Composable
private fun TasksEditContent(tasks: TasksSectionState, viewModel: PullRequestOverviewViewModel) {
    tasks.editRows.forEach { row ->
        TaskEditRowView(row = row, isUpdating = tasks.isUpdating, viewModel = viewModel)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Link(
            text = BbCloudBundle.message("main.branch.tasks.cancel"),
            onClick = viewModel::onStopEditing,
            enabled = !tasks.isUpdating
        )
        Spacer(modifier = Modifier.weight(1f))
        Link(
            text = BbCloudBundle.message("main.branch.tasks.save"),
            onClick = viewModel::onSubmitEdits,
            enabled = tasks.saveEnabled
        )
    }
}

@Composable
private fun TaskEditRowView(row: TaskEditRow, isUpdating: Boolean, viewModel: PullRequestOverviewViewModel) {
    var fieldValue by remember(row.id) { mutableStateOf(TextFieldValue(row.content)) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(checked = row.resolved, onCheckedChange = {}, enabled = false)
        TextArea(
            value = fieldValue,
            onValueChange = { value ->
                fieldValue = value
                viewModel.onEditRowChanged(row.id, value.text)
            },
            modifier = Modifier.weight(1f),
            enabled = row.enabled
        )
        Tooltip(tooltip = { Text(text = BbCloudBundle.message("main.branch.tasks.delete")) }) {
            ClickableIcon(
                icon = AllIconsKeys.Actions.Close,
                description = "Delete task",
                enabled = !isUpdating,
                onClick = { viewModel.onDeleteEditRow(row.id) }
            )
        }
    }
}

// endregion

/**
 * The section titles reuse the Swing bundle keys that embed `<b>` tags (for example
 * `main.branch.checks.summary`). Jewel's [Text] renders plain strings, so the inline markup is stripped here.
 */
private fun htmlToPlain(text: String): String {
    return com.intellij.openapi.util.text.StringUtil.stripHtml(text, " ").trim()
}