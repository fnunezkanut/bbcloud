@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.otherprojects

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.project.WatchedProjectMapping
import com.github.fnunezkanut.bbcloud.project.WatchedProjectWorktreeRow
import com.github.fnunezkanut.bbcloud.ui.main.MainPanelStatusSupport
import com.intellij.ide.BrowserUtil
import org.jetbrains.jewel.foundation.layout.BasicTableLayout
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.TriStateCheckbox
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
fun WatchedProjectsScreen(viewModel: WatchedProjectsViewModel) {
    val state by viewModel.state
    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp)
    ) {
        if (state.rescanFailed) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    key = AllIconsKeys.General.Warning,
                    contentDescription = "Warning",
                    tint = JewelTheme.globalColors.text.warning
                )
                Text(
                    text = BbCloudBundle.message("workspace.watchedProjects.rescan.failed"),
                    color = JewelTheme.globalColors.text.warning
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state.card) {
                WatchedProjectsCard.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = BbCloudBundle.message("workspace.watchedProjects.loading"))
                }

                WatchedProjectsCard.Empty -> Unit
                WatchedProjectsCard.Listing -> WatchedProjectsTable(viewModel = viewModel)
            }
        }
        WatchedProjectsFooter(state = state, onSync = viewModel::onSyncClicked)
    }
}

private enum class WatchedProjectsColumn {
    Watched,
    Remote,
    Local,
    Worktrees,
    Prune
}

private val TABLE_COLUMNS = WatchedProjectsColumn.entries
private const val COLUMN_COUNT = 5
private val CHECKBOX_WIDTH = 36.dp
private val PRUNE_WIDTH = 210.dp
private val AVATAR_SIZE = 32.dp
private val CELL_BORDER_WIDTH = 8.dp

// Floors for the three flexible columns (Remote, Local, Worktrees). BasicTableLayout sizes columns to their intrinsic
// width and only scales down when the content overflows the available width, so without a floor a narrow tool window
// would collapse these columns to their content width. The floors keep the columns legible before the proportional
// distribution computed in WatchedProjectsTable kicks in.
private val REMOTE_MIN_WIDTH = 120.dp
private val LOCAL_MIN_WIDTH = 120.dp
private val WORKTREES_MIN_WIDTH = 240.dp

// Flexible column weights in the historical 1:1:2 ratio (Remote:Local:Worktrees). The leftover width after the fixed
// Watched/Prune columns and inter-cell padding is split across the three flexible columns in this ratio.
private const val REMOTE_WEIGHT = 1
private const val LOCAL_WEIGHT = 1
private const val WORKTREES_WEIGHT = 2
private const val FLEXIBLE_WEIGHT_TOTAL = REMOTE_WEIGHT + LOCAL_WEIGHT + WORKTREES_WEIGHT

/** Resolved pixel-independent widths for the three flexible columns, computed from the table's available width. */
private data class FlexibleColumnWidths(
    val remote: androidx.compose.ui.unit.Dp,
    val local: androidx.compose.ui.unit.Dp,
    val worktrees: androidx.compose.ui.unit.Dp
)

@Composable
private fun WatchedProjectsTable(viewModel: WatchedProjectsViewModel) {
    val state by viewModel.state
    Column(modifier = Modifier.fillMaxSize()) {
        VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
            // BoxWithConstraints exposes the available width so the flexible columns can be sized to consume the whole
            // tool window. BasicTableLayout never grows columns past their intrinsic width, so the fill-to-width split
            // has to be computed here and handed to the cells as explicit widths.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val flexibleWidths = computeFlexibleColumnWidths(availableWidth = maxWidth)
                val rows: List<List<@Composable () -> Unit>> = listOf(
                    watchedProjectsHeaderCells(
                        state = state,
                        flexibleWidths = flexibleWidths,
                        onMasterToggle = viewModel::onMasterToggleClicked
                    )
                ) + state.rows.mapIndexed { index, row ->
                    watchedProjectRowCells(
                        row = row,
                        rowIndex = index,
                        flexibleWidths = flexibleWidths,
                        viewModel = viewModel
                    )
                }

                BasicTableLayout(
                    rowCount = state.rows.size + 1,
                    columnCount = COLUMN_COUNT,
                    cellBorderColor = Color.Unspecified,
                    cellBorderWidth = CELL_BORDER_WIDTH,
                    rows = rows,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Splits the width left after the fixed Watched/Prune columns and the inter-cell padding across the three flexible
 * columns in the 1:1:2 ratio, so the table fills the whole available width. Falls back to the per-column floors when
 * the window is too narrow to honor the ratio; BasicTableLayout then scales everything down proportionally.
 */
private fun computeFlexibleColumnWidths(availableWidth: androidx.compose.ui.unit.Dp): FlexibleColumnWidths {
    // BasicTableLayout reserves cellBorderWidth on both edges and between every column (columnCount + 1 gaps).
    val totalPadding = CELL_BORDER_WIDTH * (COLUMN_COUNT + 1)
    val flexibleWidth = availableWidth - CHECKBOX_WIDTH - PRUNE_WIDTH - totalPadding
    val minimumFlexibleWidth = REMOTE_MIN_WIDTH + LOCAL_MIN_WIDTH + WORKTREES_MIN_WIDTH
    if (flexibleWidth <= minimumFlexibleWidth) {
        return FlexibleColumnWidths(
            remote = REMOTE_MIN_WIDTH,
            local = LOCAL_MIN_WIDTH,
            worktrees = WORKTREES_MIN_WIDTH
        )
    }
    val unit = flexibleWidth / FLEXIBLE_WEIGHT_TOTAL
    return FlexibleColumnWidths(
        remote = unit * REMOTE_WEIGHT,
        local = unit * LOCAL_WEIGHT,
        worktrees = unit * WORKTREES_WEIGHT
    )
}

private fun watchedProjectsHeaderCells(
    state: WatchedProjectsUiState,
    flexibleWidths: FlexibleColumnWidths,
    onMasterToggle: () -> Unit
): List<@Composable () -> Unit> {
    return TABLE_COLUMNS.map { column ->
        when (column) {
            WatchedProjectsColumn.Watched -> {
                {
                    Box(
                        modifier = Modifier.width(CHECKBOX_WIDTH).fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Tooltip(
                            tooltip = {
                                Text(
                                    text = BbCloudBundle.message(
                                        "workspace.watchedProjects.column.watchedAll.tooltip"
                                    )
                                )
                            }
                        ) {
                            TriStateCheckbox(
                                state = state.masterToggle,
                                onClick = onMasterToggle
                            )
                        }
                    }
                }
            }

            WatchedProjectsColumn.Remote -> {
                headerCell(
                    text = BbCloudBundle.message("workspace.watchedProjects.column.remote"),
                    width = flexibleWidths.remote
                )
            }

            WatchedProjectsColumn.Local -> {
                headerCell(
                    text = BbCloudBundle.message("workspace.watchedProjects.column.local"),
                    width = flexibleWidths.local
                )
            }

            WatchedProjectsColumn.Worktrees -> {
                headerCell(
                    text = BbCloudBundle.message("workspace.watchedProjects.column.worktrees"),
                    width = flexibleWidths.worktrees
                )
            }

            WatchedProjectsColumn.Prune -> {
                {
                    Box(modifier = Modifier.width(PRUNE_WIDTH))
                }
            }
        }
    }
}

private fun headerCell(text: String, width: androidx.compose.ui.unit.Dp): @Composable () -> Unit {
    return {
        Box(
            modifier = Modifier.width(width),
            contentAlignment = Alignment.TopStart
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun watchedProjectRowCells(
    row: WatchedProjectRow,
    rowIndex: Int,
    flexibleWidths: FlexibleColumnWidths,
    viewModel: WatchedProjectsViewModel
): List<@Composable () -> Unit> {
    return TABLE_COLUMNS.map { column ->
        when (column) {
            WatchedProjectsColumn.Watched -> {
                stripedCell(rowIndex) {
                    Box(
                        modifier = Modifier.width(CHECKBOX_WIDTH).fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Checkbox(
                            checked = row.mapping.watched,
                            onCheckedChange = { watched ->
                                viewModel.onWatchedChanged(row.mapping, watched)
                            }
                        )
                    }
                }
            }

            WatchedProjectsColumn.Remote -> {
                stripedCell(rowIndex) {
                    RemoteCell(
                        row = row,
                        viewModel = viewModel,
                        modifier = Modifier.width(flexibleWidths.remote)
                    )
                }
            }

            WatchedProjectsColumn.Local -> {
                stripedCell(rowIndex) {
                    LocalCell(
                        row = row,
                        viewModel = viewModel,
                        modifier = Modifier.width(flexibleWidths.local)
                    )
                }
            }

            WatchedProjectsColumn.Worktrees -> {
                stripedCell(rowIndex) {
                    WorktreesCell(
                        row = row,
                        viewModel = viewModel,
                        modifier = Modifier.width(flexibleWidths.worktrees)
                    )
                }
            }

            WatchedProjectsColumn.Prune -> {
                stripedCell(rowIndex) {
                    PruneCell(
                        row = row,
                        viewModel = viewModel,
                        modifier = Modifier.width(PRUNE_WIDTH)
                    )
                }
            }
        }
    }
}

private fun stripedCell(rowIndex: Int, content: @Composable () -> Unit): @Composable () -> Unit {
    return {
        val stripeColor = JewelTheme.globalColors.borders.normal.copy(alpha = 0.12f)
        Box(
            modifier = Modifier.fillMaxSize().then(
                if (rowIndex % 2 == 1) {
                    Modifier.background(color = stripeColor)
                } else {
                    Modifier
                }
            ),
            contentAlignment = Alignment.TopStart
        ) {
            content()
        }
    }
}

@Composable
private fun RemoteCell(row: WatchedProjectRow, viewModel: WatchedProjectsViewModel, modifier: Modifier) {
    val icon = remember(row.avatarUrl, viewModel.state.value.avatarRevision) {
        viewModel.avatarIconFor(row.avatarUrl).toComposeImageBitmap()
    }
    val remoteModifier = if (row.remoteUrl != null) {
        modifier.clickable {
            BrowserUtil.browse(row.remoteUrl)
        }.pointerHoverIcon(PointerIcon.Hand)
    } else {
        modifier
    }
    Tooltip(
        tooltip = { if (row.remoteUrl != null) Text(text = row.remoteUrl) },
        enabled = row.remoteUrl != null
    ) {
        Row(
            modifier = remoteModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Image(
                bitmap = icon,
                contentDescription = "Repository avatar",
                modifier = Modifier.size(AVATAR_SIZE),
                filterQuality = FilterQuality.High
            )
            Text(
                text = row.mapping.remoteRepoSlug,
                modifier = Modifier.weight(1f),
                color = if (row.remoteUrl != null) {
                    MainPanelStatusSupport.projectFoundForeground()
                } else {
                    JewelTheme.globalColors.text.disabled
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LocalCell(row: WatchedProjectRow, viewModel: WatchedProjectsViewModel, modifier: Modifier) {
    val localModifier = if (row.isLocalOpenable) {
        modifier.clickable { viewModel.onOpenLocalProject(row.mapping) }
            .pointerHoverIcon(PointerIcon.Hand)
    } else {
        modifier
    }
    Tooltip(
        tooltip = { Text(text = row.mapping.localFolderPath) },
        enabled = row.mapping.localFolderPath.isNotBlank()
    ) {
        Text(
            text = row.localSlug,
            modifier = localModifier,
            color = if (row.isLocalOpenable) {
                JewelTheme.globalColors.text.normal
            } else {
                JewelTheme.globalColors.text.disabled
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WorktreesCell(row: WatchedProjectRow, viewModel: WatchedProjectsViewModel, modifier: Modifier) {
    Column(modifier = modifier) {
        row.worktrees.forEach { worktree ->
            WorktreeEntry(
                mapping = row.mapping,
                worktree = worktree,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun WorktreeEntry(mapping: WatchedProjectMapping, worktree: WatchedProjectWorktreeRow, viewModel: WatchedProjectsViewModel) {
    val label = if (worktree.branchName.isNullOrBlank()) {
        "#${worktree.prNumber}"
    } else {
        "#${worktree.prNumber}  ${worktree.branchName}"
    }
    Tooltip(
        tooltip = {
            if (!worktree.branchName.isNullOrBlank()) {
                Text(text = worktree.branchName)
            }
        },
        enabled = !worktree.branchName.isNullOrBlank()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                key = AllIconsKeys.Vcs.Branch,
                contentDescription = "Worktree branch"
            )
            Link(
                text = label,
                onClick = { viewModel.onOpenWorktree(mapping, worktree) },
                modifier = Modifier.weight(1f),
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PruneCell(row: WatchedProjectRow, viewModel: WatchedProjectsViewModel, modifier: Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End
    ) {
        if (row.worktrees.isNotEmpty()) {
            Tooltip(
                tooltip = {
                    Text(text = BbCloudBundle.message("workspace.watchedProjects.column.prune.tooltip"))
                }
            ) {
                OutlinedButton(
                    onClick = { viewModel.onPruneWorktrees(row.mapping) },
                    enabled = !row.isPruneInProgress
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            key = AllIconsKeys.Actions.GC,
                            contentDescription = "Prune worktrees"
                        )
                        Text(text = BbCloudBundle.message("workspace.watchedProjects.column.prune.button"))
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchedProjectsFooter(state: WatchedProjectsUiState, onSync: () -> Unit) {
    Divider(
        orientation = Orientation.Horizontal,
        modifier = Modifier.fillMaxWidth()
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = BbCloudBundle.message("workspace.watchedProjects.footerNote"),
            color = JewelTheme.globalColors.text.info,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.weight(1f))
        if (state.syncVisible) {
            OutlinedButton(onClick = onSync, enabled = state.syncEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        key = AllIconsKeys.Actions.Refresh,
                        contentDescription = "Sync"
                    )
                    Text(text = BbCloudBundle.message("workspace.watchedProjects.sync"))
                }
            }
        }
    }
}