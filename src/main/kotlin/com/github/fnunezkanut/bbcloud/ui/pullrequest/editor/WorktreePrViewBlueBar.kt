@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.review.WorktreeTodoBannerUiState
import com.github.fnunezkanut.bbcloud.ui.components.BRANCH_ARROW
import com.github.fnunezkanut.bbcloud.ui.components.BbCloudCompactOutlinedIconButton
import com.github.fnunezkanut.bbcloud.ui.components.BbCloudPluginIcons
import com.github.fnunezkanut.bbcloud.ui.components.BranchChip
import com.github.fnunezkanut.bbcloud.ui.components.HeaderLinesChangedIndicator
import com.github.fnunezkanut.bbcloud.ui.components.labelForeground
import com.github.fnunezkanut.bbcloud.ui.components.reviewBarPurple
import com.github.fnunezkanut.bbcloud.ui.main.ActionButtonOutlines
import com.github.fnunezkanut.bbcloud.ui.otherprojects.toComposeImageBitmap
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import javax.swing.Icon

private val ROW_HORIZONTAL_PADDING = 12.dp
private val ROW_VERTICAL_PADDING = 6.dp
private val BORDER_WIDTH = 1.dp
private val SEGMENT_GAP = 6.dp
private val REPO_ICON_SIZE = 16.dp

/** The gap drawn below the blue bordered box so the bar is separated from the review bar or editor below it. */
private val BANNER_BOTTOM_GAP = 5.dp

/** The placeholder a branch chip shows while the work tree binding has not resolved the branch names yet. */
private const val BRANCH_PLACEHOLDER = "\u2026"

/**
 * The pull-request view blue bar shown above the editor in plugin-managed pull request worktrees:
 *
 * `<repoIcon> <repoSlug> pr#<id> <sourceBranch> -> <destinationBranch>` with, right-aligned, the whole-pull-request
 * lines-changed indicator followed by a refresh button, inside a blue bordered box with a 5 dp gap below it.
 *
 * Renders nothing when [state] has `isWorktree = false`, which hides the bar on merged or declined pull requests and
 * while the Bitbucket state is still unknown (decisions 2b, 3a, 6a).
 *
 * To the left of the repository avatar sits a compact green-outlined changed-files button that opens the pull request
 * branch diff in the Version Control tool window; it is disabled until both branch names resolve. A second compact
 * purple-outlined button with the pull-request merge icon opens or refocuses the pull request overview editor page.
 *
 * @param state the bar content, shared across every editor's bar.
 * @param avatarIconFor resolves the 16 px repository avatar icon, falling back to a project placeholder while it loads.
 * @param onOpenChangedFiles opens the pull request changed files in the Version Control tool window.
 * @param onOpenPrPage opens or refocuses the pull request overview editor page.
 * @param onOpenMainProject opens the main local checkout of the repository (web fallback when no checkout exists).
 * @param onOpenPullRequest opens the pull request on Bitbucket.
 * @param onRefresh reloads the pull request page.
 */
@Composable
internal fun WorktreePrViewBlueBar(
    state: WorktreeTodoBannerUiState,
    avatarIconFor: (String?) -> Icon,
    onOpenChangedFiles: () -> Unit,
    onOpenPrPage: () -> Unit,
    onOpenMainProject: () -> Unit,
    onOpenPullRequest: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.isWorktree) {
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // The blue outline only runs along the top and bottom edges, so the bar reads as a horizontal band
                // across the editor rather than a boxed-in card with left and right sides.
                .topAndBottomBorder(width = BORDER_WIDTH, color = WorktreePrViewBlueBarColors.border())
                .background(color = WorktreePrViewBlueBarColors.background())
                .padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = ROW_VERTICAL_PADDING)
        ) {
            WorktreePrViewBlueBarHeader(
                state = state,
                avatarIconFor = avatarIconFor,
                onOpenChangedFiles = onOpenChangedFiles,
                onOpenPrPage = onOpenPrPage,
                onOpenMainProject = onOpenMainProject,
                onOpenPullRequest = onOpenPullRequest,
                onRefresh = onRefresh
            )
        }
        Spacer(modifier = Modifier.height(BANNER_BOTTOM_GAP))
    }
}

/** Renders the blue-bar controls, including the purple-outlined pull-request merge icon button between the diff button and repository avatar. */
@Composable
private fun WorktreePrViewBlueBarHeader(
    state: WorktreeTodoBannerUiState,
    avatarIconFor: (String?) -> Icon,
    onOpenChangedFiles: () -> Unit,
    onOpenPrPage: () -> Unit,
    onOpenMainProject: () -> Unit,
    onOpenPullRequest: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP)
    ) {
        Tooltip(tooltip = { Text(text = BbCloudBundle.message("worktree.todoRow.openChangedFiles.tooltip")) }) {
            BbCloudCompactOutlinedIconButton(
                icon = AllIconsKeys.Actions.ListChanges,
                contentDescription = BbCloudBundle.message("worktree.todoRow.openChangedFiles"),
                outlineColor = ActionButtonOutlines.green(),
                enabled = state.sourceBranch != null && state.destinationBranch != null,
                onClick = onOpenChangedFiles
            )
        }
        Tooltip(tooltip = { Text(text = BbCloudBundle.message("worktree.todoRow.openPrPage.tooltip")) }) {
            BbCloudCompactOutlinedIconButton(
                icon = BbCloudPluginIcons.pr,
                contentDescription = BbCloudBundle.message("worktree.todoRow.openPrPage"),
                outlineColor = reviewBarPurple(),
                enabled = true,
                onClick = onOpenPrPage,
                showOutline = true
            )
        }
        val icon = remember(state.repositoryAvatarUrl, state.avatarRevision) {
            avatarIconFor(state.repositoryAvatarUrl).toComposeImageBitmap()
        }
        Image(
            bitmap = icon,
            contentDescription = BbCloudBundle.message("worktree.todoRow.repositoryIcon"),
            modifier = Modifier.size(REPO_ICON_SIZE),
            filterQuality = FilterQuality.High
        )
        Link(
            text = state.repoSlug,
            onClick = onOpenMainProject,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = BbCloudBundle.message("worktree.todoRow.pullRequestPrefix"),
            color = labelForeground(),
            maxLines = 1
        )
        Link(
            text = state.pullRequestId.toString(),
            onClick = onOpenPullRequest
        )
        BranchChip(text = state.sourceBranch ?: BRANCH_PLACEHOLDER)
        Text(text = BRANCH_ARROW, color = labelForeground(), maxLines = 1)
        BranchChip(text = state.destinationBranch ?: BRANCH_PLACEHOLDER)
        Spacer(modifier = Modifier.weight(1f))
        HeaderLinesChangedIndicator(lines = state.linesChanged)
        Tooltip(tooltip = { Text(text = BbCloudBundle.message("main.branch.header.refresh.tooltip")) }) {
            BbCloudCompactOutlinedIconButton(
                icon = AllIconsKeys.Actions.Refresh,
                contentDescription = BbCloudBundle.message("main.branch.header.refresh"),
                outlineColor = ActionButtonOutlines.blue(),
                enabled = state.refreshEnabled,
                onClick = onRefresh
            )
        }
    }
}

/**
 * Draws a [width]-thick [color] line along the top and bottom edges of the element, leaving the left and right edges
 * open, so the bar reads as a horizontal band rather than a four-sided boxed card. The line is drawn centered on each
 * edge, mirroring how [androidx.compose.foundation.border] strokes a full box.
 */
private fun Modifier.topAndBottomBorder(width: Dp, color: Color): Modifier {
    return drawBehind {
        val strokeWidth = width.toPx()
        val halfStroke = strokeWidth / 2
        drawLine(
            color = color,
            start = Offset(x = 0f, y = halfStroke),
            end = Offset(x = size.width, y = halfStroke),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = color,
            start = Offset(x = 0f, y = size.height - halfStroke),
            end = Offset(x = size.width, y = size.height - halfStroke),
            strokeWidth = strokeWidth
        )
    }
}