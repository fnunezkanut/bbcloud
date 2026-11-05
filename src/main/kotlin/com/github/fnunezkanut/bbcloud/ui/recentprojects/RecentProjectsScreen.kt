@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.recentprojects

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.ui.components.BbCloudCompactOutlinedIconButton
import com.github.fnunezkanut.bbcloud.ui.components.REPOSITORY_ICON_SIZE
import com.github.fnunezkanut.bbcloud.ui.components.actionButtonHoverBackground
import com.github.fnunezkanut.bbcloud.ui.main.ActionButtonOutlines
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private val BRANCH_ICON_SIZE = 14.dp
private val GROUP_CHILD_INDENT = 20.dp

@Composable
fun RecentProjectsScreen(viewModel: RecentProjectsViewModel) {
    val state by viewModel.state
    val visible = remember(state.groups, state.ungrouped, state.searchQuery) {
        filterRecentProjects(
            groups = state.groups,
            ungrouped = state.ungrouped,
            query = state.searchQuery
        )
    }
    Column(modifier = Modifier.fillMaxSize()) {
        RecentProjectsSearchField(
            query = state.searchQuery,
            onQueryChange = viewModel::onSearchQueryChanged,
            onClear = viewModel::onSearchCleared
        )
        Divider(
            orientation = Orientation.Horizontal,
            modifier = Modifier.fillMaxWidth()
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading && state.isEmpty -> CenteredMessage(text = BbCloudBundle.message("recentProjects.loading"))
                state.loadFailed -> LoadFailed(onRetry = viewModel::reload)
                state.isEmpty -> CenteredMessage(text = BbCloudBundle.message("recentProjects.empty"))
                visible.first.isEmpty() && visible.second.isEmpty() -> CenteredMessage(
                    text = BbCloudBundle.message("recentProjects.search.noResults", state.searchQuery.trim())
                )
                else -> RecentProjectsList(
                    groups = visible.first,
                    ungrouped = visible.second,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LoadFailed(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = BbCloudBundle.message("recentProjects.loadFailed"))
            Link(
                text = BbCloudBundle.message("recentProjects.retry"),
                onClick = onRetry
            )
        }
    }
}

/** The "Search projects" field. Its text is the session-only [RecentProjectsUiState.searchQuery]. */
@Composable
private fun RecentProjectsSearchField(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    // Keep the TextFieldValue here so the caret survives recomposition; resync only when the backing text differs.
    var fieldValue by remember { mutableStateOf(TextFieldValue(text = query)) }
    LaunchedEffect(query) {
        if (fieldValue.text != query) {
            fieldValue = TextFieldValue(text = query)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TextField(
            value = fieldValue,
            onValueChange = { updated ->
                fieldValue = updated
                onQueryChange(updated.text)
            },
            modifier = Modifier.weight(1f),
            placeholder = { Text(text = BbCloudBundle.message("recentProjects.search.placeholder")) },
            leadingIcon = {
                Icon(
                    key = AllIconsKeys.Actions.Search,
                    contentDescription = BbCloudBundle.message("recentProjects.search.icon.description")
                )
            }
        )
        if (fieldValue.text.isNotEmpty()) {
            Tooltip(tooltip = { Text(text = BbCloudBundle.message("recentProjects.search.clear.tooltip")) }) {
                Icon(
                    key = AllIconsKeys.Actions.Close,
                    contentDescription = BbCloudBundle.message("recentProjects.search.clear.tooltip"),
                    modifier = Modifier.clickable(onClick = onClear).pointerHoverIcon(PointerIcon.Hand)
                )
            }
        }
    }
}

@Composable
private fun RecentProjectsList(groups: List<RecentProjectGroupRow>, ungrouped: List<RecentProjectRow>, viewModel: RecentProjectsViewModel) {
    VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            groups.forEach { group ->
                RecentProjectGroupHeader(
                    group = group,
                    onToggle = { viewModel.onGroupToggled(groupName = group.name) }
                )
                if (group.isExpanded) {
                    group.projects.forEach { row ->
                        RecentProjectRowItem(
                            row = row,
                            viewModel = viewModel,
                            startPadding = GROUP_CHILD_INDENT
                        )
                    }
                }
            }
            ungrouped.forEach { row ->
                RecentProjectRowItem(
                    row = row,
                    viewModel = viewModel,
                    startPadding = 0.dp
                )
            }
        }
    }
}

@Composable
private fun RecentProjectGroupHeader(group: RecentProjectGroupRow, onToggle: () -> Unit) {
    val description = BbCloudBundle.message(
        if (group.isExpanded) "recentProjects.group.collapse" else "recentProjects.group.expand"
    )
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onToggle)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            key = if (group.isExpanded) AllIconsKeys.General.ChevronDown else AllIconsKeys.General.ChevronRight,
            contentDescription = description
        )
        Text(
            text = group.name,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RecentProjectRowItem(row: RecentProjectRow, viewModel: RecentProjectsViewModel, startPadding: androidx.compose.ui.unit.Dp) {
    val avatarRevision = viewModel.state.value.avatarRevision
    val canRemoveWorktree = row.isPullRequestWorktree && !row.isCurrentProject
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val secondaryColor = JewelTheme.globalColors.text.info
    ContextMenuArea(
        items = {
            val items = mutableListOf(
                ContextMenuItem(label = BbCloudBundle.message("recentProjects.row.remove")) {
                    viewModel.onRemoveRequested(row = row)
                }
            )
            if (canRemoveWorktree) {
                items += ContextMenuItem(label = BbCloudBundle.message("recentProjects.row.removeWorktree")) {
                    viewModel.onRemoveWorktreeRequested(row = row)
                }
            }
            items
        }
    ) {
        Tooltip(
            tooltip = { Text(text = BbCloudBundle.message("recentProjects.row.missing.tooltip")) },
            enabled = !row.exists
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .hoverable(interactionSource = interactionSource)
                    .background(color = if (hovered) actionButtonHoverBackground() else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { viewModel.onProjectClicked(row = row) }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(start = 8.dp + startPadding, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecentProjectIcon(
                    row = row,
                    viewModel = viewModel,
                    avatarRevision = avatarRevision,
                    modifier = Modifier.alpha(if (row.exists) 1f else 0.5f)
                )
                Column(modifier = Modifier.weight(1f).alpha(if (row.exists) 1f else 0.5f)) {
                    Text(
                        text = row.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Tooltip(tooltip = { Text(text = row.path) }) {
                        Text(
                            text = row.displayPath,
                            color = secondaryColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (row.branch != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                key = AllIconsKeys.Vcs.Branch,
                                contentDescription = BbCloudBundle.message("recentProjects.row.branch.description"),
                                modifier = Modifier.size(BRANCH_ICON_SIZE),
                                tint = secondaryColor
                            )
                            Text(
                                text = row.branch,
                                color = secondaryColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                RecentProjectRowActions(
                    row = row,
                    canRemoveWorktree = canRemoveWorktree,
                    viewModel = viewModel
                )
            }
        }
    }
}

/**
 * The buttons at the far right of a row. They stay at full opacity and enabled, also for a missing folder, so a dead
 * entry can still be removed. A button consumes its own click, so the row is not opened.
 */
@Composable
private fun RecentProjectRowActions(row: RecentProjectRow, canRemoveWorktree: Boolean, viewModel: RecentProjectsViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tooltip(tooltip = { Text(text = BbCloudBundle.message("recentProjects.row.remove.tooltip")) }) {
            BbCloudCompactOutlinedIconButton(
                icon = AllIconsKeys.Actions.Close,
                contentDescription = BbCloudBundle.message("recentProjects.row.remove.contentDescription", row.name),
                outlineColor = ActionButtonOutlines.neutral(),
                enabled = true,
                onClick = { viewModel.onRemoveRequested(row = row) }
            )
        }
        if (canRemoveWorktree) {
            Tooltip(tooltip = { Text(text = BbCloudBundle.message("recentProjects.row.removeWorktree.tooltip")) }) {
                BbCloudCompactOutlinedIconButton(
                    icon = AllIconsKeys.Actions.GC,
                    contentDescription = BbCloudBundle.message("recentProjects.row.removeWorktree.contentDescription", row.name),
                    outlineColor = ActionButtonOutlines.red(),
                    enabled = true,
                    onClick = { viewModel.onRemoveWorktreeRequested(row = row) }
                )
            }
        }
    }
}

/** Draws the repository avatar when it is loaded, otherwise the default repository icon (same as Watched Projects). */
@Composable
private fun RecentProjectIcon(row: RecentProjectRow, viewModel: RecentProjectsViewModel, avatarRevision: Int, modifier: Modifier) {
    // Reading the revision subscribes this composable to avatars that finish loading.
    val bitmap = remember(row, avatarRevision) {
        viewModel.repoAvatarFor(row = row) ?: viewModel.defaultRepoIcon()
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = modifier.size(REPOSITORY_ICON_SIZE),
        filterQuality = FilterQuality.High
    )
}