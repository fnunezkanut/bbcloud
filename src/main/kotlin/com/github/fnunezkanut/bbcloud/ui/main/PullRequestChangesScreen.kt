@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.git.FileLineCount
import com.github.fnunezkanut.bbcloud.ui.components.LinesChangedBadge
import com.github.fnunezkanut.bbcloud.ui.components.RoundedBadge
import com.github.fnunezkanut.bbcloud.ui.components.actionButtonHoverBackground
import com.github.fnunezkanut.bbcloud.ui.components.viewedTickPurple
import com.github.fnunezkanut.bbcloud.ui.otherprojects.toComposeImageBitmap
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.ide.CopyPasteManager
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.lazy.tree.rememberTreeState
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.LazyTree
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import java.awt.datatransfer.StringSelection

private val FILE_ICON_SIZE = 16.dp
private val COMMENT_PILL_ICON_SIZE = 14.dp

/** The gap to the left of the comment balloon pill, so it does not touch the file name. */
private val COMMENT_BADGE_LEADING_GAP = 5.dp

/** The gap to the left of the line-stats block, so the `+12 −3` counts do not touch the preceding content. */
private val LINE_STATS_LEADING_GAP = 5.dp
private val LOG = logger<PullRequestChangesViewModel>()

/**
 * The file-changes view of the PR Changes tool window: the file-changes card fills the whole width and height of the
 * window. The Activity timeline is shown on the pull-request page, below the description.
 */
@Composable
internal fun PullRequestChangesScreen(filesViewModel: PullRequestChangesViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        TitledCardBox(title = BbCloudBundle.message("main.branch.changes.card.fileChanges")) {
            FileChangesBody(viewModel = filesViewModel)
        }
    }
}

@Composable
private fun FileChangesBody(viewModel: PullRequestChangesViewModel) {
    val state by viewModel.state
    when (state.card) {
        PullRequestFileChangesCard.Idle -> {
            CenteredMessage(message = BbCloudBundle.message("main.branch.filesChanged.idle"))
        }
        PullRequestFileChangesCard.Loading -> {
            CenteredMessage(message = BbCloudBundle.message("main.branch.filesChanged.loading"))
        }
        PullRequestFileChangesCard.Empty -> {
            CenteredMessage(message = BbCloudBundle.message("main.branch.filesChanged.empty"))
        }
        PullRequestFileChangesCard.Error -> {
            ErrorBody(message = state.errorMessage, onRefresh = viewModel::onRefresh)
        }
        PullRequestFileChangesCard.Listing -> {
            FileChangesTree(viewModel = viewModel, rows = state.rows)
        }
        PullRequestFileChangesCard.Resolved -> {
            CenteredMessage(message = resolvedMessage(state.resolvedPullRequestId))
        }
    }
}

/** The centered message shown by the `Resolved` card, naming the pull request when its number is known. */
private fun resolvedMessage(pullRequestId: Long?): String {
    return if (pullRequestId != null) {
        BbCloudBundle.message("main.branch.filesChanged.resolved", pullRequestId)
    } else {
        BbCloudBundle.message("main.branch.filesChanged.resolvedNoId")
    }
}

@Composable
private fun ErrorBody(message: String?, onRefresh: () -> Unit) {
    val errorColor = MainPanelStatusSupport.errorForeground()
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(key = AllIconsKeys.General.Error, contentDescription = "Error", tint = errorColor)
            Text(text = message.orEmpty(), color = errorColor)
        }
        Link(text = BbCloudBundle.message("main.branch.filesChanged.refresh"), onClick = onRefresh)
    }
}

/**
 * The directory tree of the changed files. A double click on a file opens the platform diff, the way the old changes
 * browser did.
 */
@Composable
private fun FileChangesTree(viewModel: PullRequestChangesViewModel, rows: List<PullRequestFileChangeRow>) {
    // The tree is rebuilt only when the rows change, and the tree state is kept, so an identical refresh does not
    // collapse folders the user had expanded. A genuinely new set of rows is expanded by default.
    val tree = remember(rows) { buildChangesTree(rows) }
    val directoryIds = remember(rows) { changesTreeDirectoryIds(rows) }
    val rowsById = remember(rows) { rows.associateBy { row -> fileId(row.relativePath) } }
    val rowsByDirectory = remember(rows) { fileRowsByDirectory(rows) }
    val treeState = rememberTreeState()
    LaunchedEffect(rows) {
        treeState.openNodes(directoryIds)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        ChangesTreeToolbar(
            onExpandAll = {
                LOG.info("User expanded all folders of the PR Changes file tree")
                treeState.openNodes(directoryIds)
            },
            onCollapseAll = {
                LOG.info("User collapsed all folders of the PR Changes file tree")
                treeState.openNodes = emptySet()
            }
        )
        LazyTree(
            tree = tree,
            treeState = treeState,
            modifier = Modifier
                // Jewel's tree has no Enter action; Enter opens the diff of the selected file like a double click,
                // and toggles a selected folder.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || (event.key != Key.Enter && event.key != Key.NumPadEnter)) {
                        return@onPreviewKeyEvent false
                    }
                    val row = selectedFileRow(selectedKeys = treeState.selectedKeys, rowsById = rowsById)
                    if (row != null) {
                        LOG.info("User pressed Enter on a file of the PR Changes file tree")
                        viewModel.onFileOpened(row.changeId)
                        return@onPreviewKeyEvent true
                    }
                    val key = treeState.selectedKeys.singleOrNull()
                    if (key is String && key.startsWith("dir:")) {
                        treeState.toggleNode(key)
                        return@onPreviewKeyEvent true
                    }
                    false
                }
                .weight(1f)
                .fillMaxSize(),
            onElementDoubleClick = { element -> openDiff(element = element, viewModel = viewModel) }
        ) { element ->
            ChangesTreeNodeContent(
                node = element.data,
                viewModel = viewModel,
                rowsByDirectory = rowsByDirectory
            )
        }
    }
}

@Composable
private fun ChangesTreeToolbar(onExpandAll: () -> Unit, onCollapseAll: () -> Unit) {
    Row(
        modifier = Modifier.padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tooltip(tooltip = { Text(text = BbCloudBundle.message("main.branch.filesChanged.expandAll")) }) {
            IconButton(onClick = onExpandAll) {
                Icon(
                    key = AllIconsKeys.Actions.Expandall,
                    contentDescription = BbCloudBundle.message("main.branch.filesChanged.expandAll")
                )
            }
        }
        Tooltip(tooltip = { Text(text = BbCloudBundle.message("main.branch.filesChanged.collapseAll")) }) {
            IconButton(onClick = onCollapseAll) {
                Icon(
                    key = AllIconsKeys.Actions.Collapseall,
                    contentDescription = BbCloudBundle.message("main.branch.filesChanged.collapseAll")
                )
            }
        }
    }
}

private fun openDiff(element: Tree.Element<PullRequestChangesTreeNode>, viewModel: PullRequestChangesViewModel) {
    val file = element.data as? PullRequestChangesTreeNode.File ?: return
    viewModel.onFileOpened(file.row.changeId)
}

@Composable
private fun ChangesTreeNodeContent(
    node: PullRequestChangesTreeNode,
    viewModel: PullRequestChangesViewModel,
    rowsByDirectory: Map<String, List<PullRequestFileChangeRow>>
) {
    when (node) {
        is PullRequestChangesTreeNode.Directory -> {
            DirectoryRowContent(
                directory = node,
                descendantRows = rowsByDirectory[node.path].orEmpty(),
                viewModel = viewModel
            )
        }
        is PullRequestChangesTreeNode.File -> {
            FileChangeRowContent(row = node.row, viewModel = viewModel)
        }
    }
}

/**
 * A folder row. The folder shows the Viewed tick at the end of the row when every changed file below it is viewed.
 * Folders have no line counts, so the tick sits where the file badge would be.
 */
@Composable
private fun DirectoryRowContent(
    directory: PullRequestChangesTreeNode.Directory,
    descendantRows: List<PullRequestFileChangeRow>,
    viewModel: PullRequestChangesViewModel
) {
    val allViewed = directoryFullyViewed(descendantRows = descendantRows, isViewed = viewModel::isViewed)
    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem(label = BbCloudBundle.message("main.branch.filesChanged.copyAbsolutePath")) {
                    val absolutePath = viewModel.directoryAbsolutePathFor(directory.path)
                    if (absolutePath != null) {
                        copyPathToClipboard(path = absolutePath)
                    }
                }
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(key = AllIconsKeys.Nodes.Folder, contentDescription = null)
            Text(
                text = directory.name,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .weight(1f, fill = true),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
            if (allViewed) {
                ViewedTick(
                    tooltip = BbCloudBundle.message("main.branch.filesChanged.viewed.tooltip"),
                    modifier = Modifier.padding(start = LINE_STATS_LEADING_GAP)
                )
            }
        }
    }
}

/**
 * The purple tick that replaces the line counts of a Viewed file, or the Viewed mark of a folder whose files are all
 * viewed. The tooltip doubles as the content description.
 */
@Composable
private fun ViewedTick(tooltip: String, modifier: Modifier = Modifier) {
    Tooltip(tooltip = { Text(text = tooltip) }) {
        Icon(
            key = AllIconsKeys.Actions.Checked,
            contentDescription = tooltip,
            modifier = modifier.size(FILE_ICON_SIZE),
            tint = viewedTickPurple()
        )
    }
}

@Composable
private fun FileChangeRowContent(row: PullRequestFileChangeRow, viewModel: PullRequestChangesViewModel) {
    // Read in composition, so toggling the Viewed checkbox in the diff review bar updates this row without a reload.
    val viewed = viewModel.isViewed(row)
    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem(label = BbCloudBundle.message("main.branch.filesChanged.openDiff")) {
                    viewModel.onFileOpened(row.changeId)
                },
                ContextMenuItem(label = BbCloudBundle.message("main.branch.filesChanged.copyPath")) {
                    viewModel.onCopyPath(row.relativePath)
                    copyPathToClipboard(path = row.relativePath)
                },
                ContextMenuItem(label = BbCloudBundle.message("main.branch.filesChanged.copyAbsolutePath")) {
                    val absolutePath = viewModel.absolutePathFor(row.changeId)
                    if (absolutePath != null) {
                        copyPathToClipboard(path = absolutePath)
                    }
                }
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val fileIcon = remember(row.fileName) { fileIconFor(fileName = row.fileName) }
            if (fileIcon != null) {
                Image(
                    bitmap = fileIcon,
                    contentDescription = null,
                    modifier = Modifier.size(FILE_ICON_SIZE),
                    filterQuality = FilterQuality.High
                )
            } else {
                Icon(key = AllIconsKeys.FileTypes.Any_type, contentDescription = null)
            }
            Text(
                text = row.fileName,
                color = row.statusColorArgb?.let { argb -> Color(argb) } ?: Color.Unspecified,
                modifier = Modifier
                    .padding(start = 4.dp)
                    // The name fills the free width of the row so the comment pill and line stats cluster on the
                    // right; a long name is ellipsized rather than pushing the trailing widgets off the row.
                    .weight(1f, fill = true),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
            if (row.commentCount > 0) {
                Tooltip(tooltip = { Text(text = BbCloudBundle.message("main.branch.filesChanged.commentCount.tooltip", row.commentCount)) }) {
                    RoundedBadge(
                        text = row.commentCount.toString(),
                        fillColor = actionButtonHoverBackground(),
                        // 5 dp between the file name and the comment balloon icon.
                        modifier = Modifier.padding(start = COMMENT_BADGE_LEADING_GAP),
                        leadingContent = {
                            Icon(
                                key = AllIconsKeys.General.Balloon,
                                contentDescription = null,
                                modifier = Modifier.size(COMMENT_PILL_ICON_SIZE)
                            )
                        }
                    )
                }
            }
            val lineCount = row.lineCount
            // A Viewed file shows the purple tick instead of the line counts, so the tick is the only trailing widget
            // for it. The 5 dp leading gap sits between the comment pill (or the file name) and the tick or counts.
            if (viewed) {
                ViewedTick(
                    tooltip = viewedTooltip(lineCount = lineCount),
                    modifier = Modifier.padding(start = LINE_STATS_LEADING_GAP)
                )
            } else if (lineCount != null && shouldShowLinesBadge(lineCount = lineCount, viewed = viewed)) {
                LinesChangedBadge(
                    linesAdded = lineCount.linesAdded,
                    linesRemoved = lineCount.linesRemoved,
                    showEmoji = false,
                    modifier = Modifier.padding(start = LINE_STATS_LEADING_GAP)
                )
            }
        }
    }
}

/**
 * Tells whether the compact `+N -N` line badge shows for a file. The badge shows only for a file that is not viewed,
 * whose counts are known, that is not binary, and where at least one line changed.
 */
internal fun shouldShowLinesBadge(lineCount: FileLineCount?, viewed: Boolean): Boolean {
    if (viewed || lineCount == null || lineCount.isBinary) {
        return false
    }
    return lineCount.linesAdded != 0 || lineCount.linesRemoved != 0
}

/**
 * The tooltip of the Viewed tick. It names the line counts when the file has changed lines, for example
 * `Viewed (+40 -0)`, and is plain `Viewed` when the counts are unknown, binary, or both zero.
 */
internal fun viewedTooltip(lineCount: FileLineCount?): String {
    if (lineCount == null || lineCount.isBinary || (lineCount.linesAdded == 0 && lineCount.linesRemoved == 0)) {
        return BbCloudBundle.message("main.branch.filesChanged.viewed.tooltip")
    }
    return BbCloudBundle.message(
        "main.branch.filesChanged.viewed.tooltipWithCounts",
        lineCount.linesAdded,
        lineCount.linesRemoved
    )
}

/** The icon of the file type of [fileName], or null when the file type has no icon. */
private fun fileIconFor(fileName: String): ImageBitmap? {
    val icon = FileTypeManager.getInstance().getFileTypeByFileName(fileName).icon ?: return null
    return icon.toComposeImageBitmap()
}

private fun copyPathToClipboard(path: String) {
    CopyPasteManager.getInstance().setContents(StringSelection(path))
}