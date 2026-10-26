@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.pullrequest.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.ui.main.MainPanelStatusSupport
import com.github.fnunezkanut.bbcloud.ui.main.MarkdownDraftEditor
import com.github.fnunezkanut.bbcloud.ui.main.ReviewerIdentityRow
import com.github.fnunezkanut.bbcloud.ui.main.TitledCardBox
import com.github.fnunezkanut.bbcloud.ui.otherprojects.toComposeImageBitmap
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private val MAX_FORM_WIDTH = 900.dp
private val DESCRIPTION_EDITOR_MAX_HEIGHT = 320.dp
private val SECTION_GAP = 8.dp

/** The create-pull-request form shown in the `new.pr` editor tab. */
@Composable
internal fun NewPullRequestScreen(viewModel: NewPullRequestViewModel, repositorySlug: String) {
    val state by viewModel.state
    Column(modifier = Modifier.fillMaxSize()) {
        VerticallyScrollableContainer(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = MAX_FORM_WIDTH),
                verticalArrangement = Arrangement.spacedBy(SECTION_GAP)
            ) {
                HeadingSection(repositorySlug = repositorySlug)
                TitleCard(title = state.title, isSubmitting = state.isSubmitting, onTitleChanged = viewModel::onTitleChanged)
                BranchesCard(state = state, viewModel = viewModel)
                ExistingPullRequestWarning(state = state, viewModel = viewModel)
                WorkingTreeWarning(state = state, viewModel = viewModel)
                TitledCardBox(title = BbCloudBundle.message("newPr.description.label")) {
                    MarkdownDraftEditor(
                        viewModel = viewModel.descriptionComposer,
                        editorMaxHeight = DESCRIPTION_EDITOR_MAX_HEIGHT
                    )
                }
                ReviewersCard(state = state, viewModel = viewModel)
                CheckboxRow(
                    text = BbCloudBundle.message("newPr.closeSourceBranch"),
                    checked = state.closeSourceBranch,
                    onCheckedChange = viewModel::onCloseSourceBranchChanged,
                    enabled = !state.isSubmitting
                )
                state.failureMessage?.let { message ->
                    Text(text = message, color = MainPanelStatusSupport.errorForeground())
                }
                ButtonRow(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun HeadingSection(repositorySlug: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = BbCloudBundle.message("newPr.heading"), fontWeight = FontWeight.Bold)
        if (repositorySlug.isNotBlank()) {
            Text(text = repositorySlug, color = JewelTheme.globalColors.text.info)
        }
    }
}

@Composable
private fun TitleCard(title: String, isSubmitting: Boolean, onTitleChanged: (String) -> Unit) {
    TitledCardBox(title = BbCloudBundle.message("newPr.title.label")) {
        // Hold the TextFieldValue locally so the caret advances with typing; resync from the incoming title only when
        // the backing text actually differs (for example a programmatic clear), so an external update never snaps the
        // caret back to the start mid-edit.
        var fieldValue by remember { mutableStateOf(TextFieldValue(text = title)) }
        LaunchedEffect(title) {
            if (fieldValue.text != title) {
                fieldValue = TextFieldValue(text = title)
            }
        }
        TextField(
            value = fieldValue,
            onValueChange = { newValue ->
                fieldValue = newValue
                onTitleChanged(newValue.text)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSubmitting,
            placeholder = { Text(text = BbCloudBundle.message("newPr.title.placeholder")) }
        )
    }
}

@Composable
private fun BranchesCard(state: NewPullRequestUiState, viewModel: NewPullRequestViewModel) {
    TitledCardBox(title = BbCloudBundle.message("newPr.branches.label")) {
        when (state.branchesLoad) {
            BranchesLoad.Loading -> Text(text = BbCloudBundle.message("newPr.branches.loading"))
            BranchesLoad.Failed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = BbCloudBundle.message("newPr.branches.failed"), color = MainPanelStatusSupport.errorForeground())
                Link(text = BbCloudBundle.message("newPr.branches.retry"), onClick = viewModel::onRetryBranchesClicked)
            }
            BranchesLoad.Loaded -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BranchSelectorRow(state = state, viewModel = viewModel)
                if (state.sourceOptions.isEmpty()) {
                    Text(text = BbCloudBundle.message("newPr.branches.noSource"), color = MainPanelStatusSupport.warningForeground())
                }
                if (state.isBranchListTruncated) {
                    Text(text = BbCloudBundle.message("newPr.branches.truncated"), color = JewelTheme.globalColors.text.info)
                }
            }
        }
    }
}

@Composable
private fun BranchSelectorRow(state: NewPullRequestUiState, viewModel: NewPullRequestViewModel) {
    val controlsEnabled = state.branchesLoad == BranchesLoad.Loaded && !state.isSubmitting
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LabeledBranchComboBox(
            label = BbCloudBundle.message("newPr.branches.source"),
            options = state.sourceOptions,
            selected = state.selectedSource,
            enabled = controlsEnabled,
            onSelected = viewModel::onSourceSelected,
            modifier = Modifier.weight(1f)
        )
        Icon(key = AllIconsKeys.Actions.Forward, contentDescription = null)
        LabeledBranchComboBox(
            label = BbCloudBundle.message("newPr.branches.destination"),
            options = state.destinationOptions,
            selected = state.selectedDestination,
            enabled = controlsEnabled,
            onSelected = viewModel::onDestinationSelected,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LabeledBranchComboBox(label: String, options: List<String>, selected: String?, enabled: Boolean, onSelected: (Int) -> Unit, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, color = JewelTheme.globalColors.text.info)
        val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
        ListComboBox(
            items = options,
            selectedIndex = selectedIndex,
            onSelectedItemChange = onSelected,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && options.isNotEmpty()
        )
    }
}

@Composable
private fun ExistingPullRequestWarning(state: NewPullRequestUiState, viewModel: NewPullRequestViewModel) {
    when (val existing = state.existingPullRequest) {
        is ExistingPullRequestCheck.Found -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(key = AllIconsKeys.General.BalloonWarning, contentDescription = null)
            Text(text = BbCloudBundle.message("newPr.existing.message"), color = MainPanelStatusSupport.warningForeground())
            Link(
                text = BbCloudBundle.message("newPr.existing.link", existing.pullRequestId),
                onClick = viewModel::onExistingPullRequestLinkClicked
            )
        }
        ExistingPullRequestCheck.Checking -> Text(
            text = BbCloudBundle.message("newPr.existing.checking"),
            color = JewelTheme.globalColors.text.info
        )
        else -> Unit
    }
}

@Composable
private fun WorkingTreeWarning(state: NewPullRequestUiState, viewModel: NewPullRequestViewModel) {
    when (val workingTree = state.workingTree) {
        is WorkingTreeCheck.BlockedUncommittedChanges -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(key = AllIconsKeys.General.BalloonWarning, contentDescription = null)
            val branch = workingTree.currentBranch ?: BbCloudBundle.message("newPr.workingTree.detached")
            Text(text = BbCloudBundle.message("newPr.workingTree.blocked", branch), color = MainPanelStatusSupport.warningForeground())
            Link(text = BbCloudBundle.message("newPr.workingTree.recheck"), onClick = viewModel::onRecheckWorkingTreeClicked)
        }
        WorkingTreeCheck.Checking -> Text(
            text = BbCloudBundle.message("newPr.workingTree.checking"),
            color = JewelTheme.globalColors.text.info
        )
        else -> Unit
    }
}

@Composable
private fun ReviewersCard(state: NewPullRequestUiState, viewModel: NewPullRequestViewModel) {
    TitledCardBox(title = BbCloudBundle.message("newPr.reviewers.label")) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.reviewers.isEmpty()) {
                Text(text = BbCloudBundle.message("main.branch.reviewers.empty"), color = JewelTheme.globalColors.text.info)
            }
            state.reviewers.forEach { reviewer ->
                val avatar = remember(reviewer.avatarUrl, state.avatarRevision) {
                    viewModel.avatarIconFor(reviewer.avatarUrl).toComposeImageBitmap()
                }
                ReviewerIdentityRow(
                    displayName = reviewer.displayName,
                    avatar = avatar,
                    statusIcon = null,
                    isRemovable = true,
                    isRemoveEnabled = !state.isSubmitting,
                    onRemove = { viewModel.onRemoveReviewerClicked(reviewer.uuid) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(key = AllIconsKeys.General.Add, contentDescription = null)
                Link(
                    text = BbCloudBundle.message("main.branch.reviewers.add"),
                    onClick = viewModel::onAddReviewerClicked,
                    enabled = !state.isSubmitting
                )
            }
        }
    }
}

@Composable
private fun ButtonRow(state: NewPullRequestUiState, viewModel: NewPullRequestViewModel) {
    val createEnabled = NewPullRequestSubmitRules.areCreateButtonsEnabled(state)
    val createVisible = NewPullRequestSubmitRules.areCreateButtonsVisible(state.existingPullRequest)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.isSubmitting) {
            Text(text = BbCloudBundle.message("newPr.creating"), color = JewelTheme.globalColors.text.info)
        }
        OutlinedButton(onClick = viewModel::onCancelClicked, modifier = handModifier()) {
            Text(text = BbCloudBundle.message("newPr.cancel"))
        }
        if (createVisible) {
            OutlinedButton(onClick = viewModel::onCreateDraftClicked, enabled = createEnabled, modifier = handModifier()) {
                Text(text = BbCloudBundle.message("newPr.createDraft"))
            }
            DefaultButton(onClick = viewModel::onCreatePullRequestClicked, enabled = createEnabled, modifier = handModifier()) {
                Text(text = BbCloudBundle.message("newPr.create"))
            }
        }
    }
}

private fun handModifier(): Modifier {
    return Modifier.pointerHoverIcon(PointerIcon.Hand)
}