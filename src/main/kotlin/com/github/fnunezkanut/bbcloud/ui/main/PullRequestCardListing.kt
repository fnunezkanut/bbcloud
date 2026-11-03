@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewingPullRequest
import com.github.fnunezkanut.bbcloud.ui.components.BRANCH_ARROW
import com.github.fnunezkanut.bbcloud.ui.components.BranchChip
import com.github.fnunezkanut.bbcloud.ui.otherprojects.WorkspacePullRequestRow
import com.github.fnunezkanut.bbcloud.ui.otherprojects.toComposeImageBitmap
import com.github.fnunezkanut.bbcloud.ui.pullrequests.LISTING_ACTIVITY_COLUMN_WIDTH
import com.github.fnunezkanut.bbcloud.ui.pullrequests.LISTING_AUTHOR_AVATAR_SIZE_DP
import com.github.fnunezkanut.bbcloud.ui.pullrequests.LISTING_BRANCHES_COLUMN_WIDTH
import com.github.fnunezkanut.bbcloud.ui.pullrequests.LISTING_REVIEWERS_COLUMN_WIDTH
import com.github.fnunezkanut.bbcloud.ui.pullrequests.LISTING_SUBTITLE_SEPARATOR
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Badge
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** The author avatar on the pull-request detail card. */
private val DETAIL_AVATAR_SIZE = 48.dp

/** The author avatar on a listing row, sized by [LISTING_AUTHOR_AVATAR_SIZE_DP]. */
private val LISTING_AVATAR_SIZE = LISTING_AUTHOR_AVATAR_SIZE_DP.dp

/** The padding around every table cell, so the first line of each cell lines up with the title line. */
private val CELL_PADDING = 8.dp

/** The bump, in sp, of the title over the default body text, so the title of a card reads as the headline of its row. */
private const val PR_TITLE_FONT_SIZE_BUMP = 1f

@Composable
internal fun CenteredMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message)
    }
}

@Composable
internal fun CenteredError(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                key = AllIconsKeys.General.Error,
                contentDescription = "Error",
                tint = MainPanelStatusSupport.errorForeground()
            )
            Text(
                text = message,
                color = MainPanelStatusSupport.errorForeground()
            )
        }
    }
}

internal data class PullRequestCardActions(
    val onOpenTitle: (ReviewingPullRequest) -> Unit,
    val onOpenPrOnWeb: (ReviewingPullRequest) -> Unit,
    val onOpenRepository: (ReviewingPullRequest) -> Unit
)

/**
 * The scrollable pull-request listing. Each row is a table row without a header: the primary cell (author avatar, title,
 * and subtitle) takes the free width, and the Branches, Activity, and Reviewers cells keep fixed widths.
 */
@Composable
internal fun PullRequestCardListing(
    rows: List<WorkspacePullRequestRow>,
    avatarRevision: Int,
    showStateBadge: Boolean,
    avatarIconFor: (String?, javax.swing.Icon) -> javax.swing.Icon,
    actions: PullRequestCardActions
) {
    VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { rowIndex, row ->
                stripedRow(rowIndex = rowIndex) {
                    PullRequestListingTableRow(
                        row = row,
                        avatarRevision = avatarRevision,
                        showStateBadge = showStateBadge,
                        avatarIconFor = avatarIconFor,
                        actions = actions
                    )
                }
            }
        }
    }
}

/**
 * The pull-request detail info card. Unlike the listing rows, the author subtitle here shows the updated/created
 * timestamps with an absolute-date tooltip, and it does not show a comment count under the author.
 */
@Composable
internal fun PullRequestInfoCard(
    row: WorkspacePullRequestRow,
    avatarRevision: Int,
    avatarIconFor: (String?, javax.swing.Icon) -> javax.swing.Icon,
    actions: PullRequestCardActions,
    trailingContent: (@Composable () -> Unit)? = null
) {
    PullRequestCard(
        row = row,
        avatarRevision = avatarRevision,
        showStateBadge = true,
        avatarIconFor = avatarIconFor,
        actions = actions,
        trailingContent = trailingContent
    )
}

@Composable
private fun PullRequestListingTableRow(
    row: WorkspacePullRequestRow,
    avatarRevision: Int,
    showStateBadge: Boolean,
    avatarIconFor: (String?, javax.swing.Icon) -> javax.swing.Icon,
    actions: PullRequestCardActions
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(modifier = Modifier.weight(1f).padding(CELL_PADDING)) {
            ListingPrimaryCell(
                row = row,
                avatarRevision = avatarRevision,
                showStateBadge = showStateBadge,
                avatarIconFor = avatarIconFor,
                actions = actions
            )
        }
        Box(modifier = Modifier.width(LISTING_BRANCHES_COLUMN_WIDTH).padding(CELL_PADDING)) {
            ListingBranchesCell(row = row)
        }
        Box(modifier = Modifier.width(LISTING_ACTIVITY_COLUMN_WIDTH).padding(CELL_PADDING)) {
            ListingActivityCell(row = row)
        }
        Box(modifier = Modifier.width(LISTING_REVIEWERS_COLUMN_WIDTH).padding(CELL_PADDING)) {
            ReviewerAvatarStack(
                stack = row.reviewerStack,
                avatarRevision = avatarRevision,
                avatarIconFor = avatarIconFor
            )
        }
    }
}

@Composable
private fun ListingPrimaryCell(
    row: WorkspacePullRequestRow,
    avatarRevision: Int,
    showStateBadge: Boolean,
    avatarIconFor: (String?, javax.swing.Icon) -> javax.swing.Icon,
    actions: PullRequestCardActions
) {
    val icon = remember(row.authorAvatarUrl, avatarRevision) {
        avatarIconFor(row.authorAvatarUrl, AuthorAvatarCache.PLACEHOLDER_ICON).toComposeImageBitmap()
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            bitmap = icon,
            contentDescription = "Author avatar",
            modifier = Modifier.size(LISTING_AVATAR_SIZE),
            filterQuality = FilterQuality.High
        )
        Column(modifier = Modifier.weight(1f)) {
            PullRequestTitleRow(
                row = row,
                showStateBadge = showStateBadge,
                onOpenTitle = { actions.onOpenTitle(row.pullRequest) }
            )
            ListingSubtitleRow(row = row, actions = actions)
        }
    }
}

/**
 * The listing subtitle reads "author · #id · updated · repository". The id opens the pull request in the browser, and
 * the repository opens through [PullRequestCardActions.onOpenRepository] when its web URL is known.
 */
@Composable
private fun ListingSubtitleRow(row: WorkspacePullRequestRow, actions: PullRequestCardActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.authorDisplayName,
            color = JewelTheme.globalColors.text.info,
            fontStyle = FontStyle.Italic,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        ListingSubtitleSeparator()
        Link(
            text = row.prId,
            onClick = { actions.onOpenPrOnWeb(row.pullRequest) },
            overflow = TextOverflow.Ellipsis
        )
        ListingSubtitleSeparator()
        Text(
            text = row.updatedRelative,
            color = JewelTheme.globalColors.text.info,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        ListingSubtitleSeparator()
        if (row.repositoryWebUrl.isNullOrBlank()) {
            Text(
                text = row.repoSlug,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Link(
                text = row.repoSlug,
                onClick = { actions.onOpenRepository(row.pullRequest) },
                modifier = Modifier.weight(1f, fill = false),
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ListingSubtitleSeparator() {
    Text(
        text = LISTING_SUBTITLE_SEPARATOR,
        color = JewelTheme.globalColors.text.info,
        maxLines = 1
    )
}

/**
 * The Branches column: the source and destination branch chips over the relative created time. The branch row carries a
 * tooltip of the full "source -> destination" text, and the created time a tooltip of the absolute created/updated
 * times.
 */
@Composable
private fun ListingBranchesCell(row: WorkspacePullRequestRow) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ListingBranchesRow(row = row)
        ListingCreatedCell(row = row)
    }
}

@Composable
private fun ListingBranchesRow(row: WorkspacePullRequestRow) {
    Tooltip(tooltip = { Text(text = row.branches) }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BranchChip(
                text = row.sourceBranch,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = BRANCH_ARROW,
                color = JewelTheme.globalColors.text.info,
                maxLines = 1
            )
            BranchChip(
                text = row.destinationBranch,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

/**
 * The created time, relative, below the branch chips. It is muted like the subtitle, and its tooltip shows the absolute
 * created and updated times.
 */
@Composable
private fun ListingCreatedCell(row: WorkspacePullRequestRow) {
    Tooltip(tooltip = { Text(text = pullRequestAbsoluteDatesTooltip(summary = row.pullRequest.summary)) }) {
        Text(
            text = row.createdRelative,
            color = JewelTheme.globalColors.text.info,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The balloon comment count. The count is the only activity figure a listing row shows. */
@Composable
private fun ListingActivityCell(row: WorkspacePullRequestRow) {
    Tooltip(
        tooltip = {
            Text(
                text = BbCloudBundle.message(
                    "main.pullRequests.commentCount",
                    row.commentCount
                )
            )
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                key = AllIconsKeys.General.Balloon,
                contentDescription = "Comments"
            )
            Text(text = row.commentCount.toString())
        }
    }
}

/** The title link with its draft, state, and approved badges. Shared by the listing rows and the detail card. */
@Composable
private fun PullRequestTitleRow(row: WorkspacePullRequestRow, showStateBadge: Boolean, onOpenTitle: () -> Unit) {
    Tooltip(tooltip = { Text(text = row.title) }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Link(
                text = row.title,
                onClick = onOpenTitle,
                modifier = Modifier.weight(1f, fill = false),
                textStyle = JewelTheme.defaultTextStyle.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (JewelTheme.defaultTextStyle.fontSize.value + PR_TITLE_FONT_SIZE_BUMP).sp
                ),
                overflow = TextOverflow.Ellipsis
            )
            if (row.isDraft) {
                Badge(style = PullRequestBadgeStyles.draft()) {
                    Text(text = BbCloudBundle.message("main.pullRequests.draft"))
                }
            }
            if (showStateBadge) {
                when (PullRequestBadgeStyles.stateBadgeKind(row.state, row.isDraft)) {
                    PullRequestBadgeStyles.StateBadgeKind.None -> Unit
                    PullRequestBadgeStyles.StateBadgeKind.Merged -> Badge(
                        style = PullRequestBadgeStyles.merged()
                    ) {
                        Text(text = row.stateLabel)
                    }

                    PullRequestBadgeStyles.StateBadgeKind.Declined -> Badge(
                        style = PullRequestBadgeStyles.declined()
                    ) {
                        Text(text = row.stateLabel)
                    }
                }
            }
            if (row.isApprovedByMe && row.state == PullRequestState.Open) {
                Badge(style = PullRequestBadgeStyles.approved()) {
                    Text(text = BbCloudBundle.message("main.pullRequests.approved"))
                }
            }
        }
    }
}

/** The detail info card: the author, title, and dates, with an optional trailing action column. */
@Composable
private fun PullRequestCard(
    row: WorkspacePullRequestRow,
    avatarRevision: Int,
    showStateBadge: Boolean,
    avatarIconFor: (String?, javax.swing.Icon) -> javax.swing.Icon,
    actions: PullRequestCardActions,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // The card renders a trailing actions column, so the author column takes a weight instead of the full width.
        val authorColumnModifier = if (trailingContent != null) {
            Modifier.weight(1f)
        } else {
            Modifier.fillMaxWidth()
        }
        Box(modifier = authorColumnModifier.padding(8.dp)) {
            val icon = remember(row.authorAvatarUrl, avatarRevision) {
                avatarIconFor(row.authorAvatarUrl, AuthorAvatarCache.PLACEHOLDER_ICON).toComposeImageBitmap()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Image(
                    bitmap = icon,
                    contentDescription = "Author avatar",
                    modifier = Modifier.size(DETAIL_AVATAR_SIZE),
                    filterQuality = FilterQuality.High
                )
                Column(modifier = Modifier.weight(1f)) {
                    PullRequestTitleRow(
                        row = row,
                        showStateBadge = showStateBadge,
                        onOpenTitle = { actions.onOpenTitle(row.pullRequest) }
                    )
                    Text(
                        text = row.authorDisplayName,
                        color = JewelTheme.globalColors.text.info,
                        fontStyle = FontStyle.Italic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val authorDates = pullRequestAuthorDates(
                        summary = row.pullRequest.summary,
                        updatedRelative = row.updatedRelative,
                        createdRelative = row.createdRelative
                    )
                    Tooltip(tooltip = { Text(text = authorDates.tooltipText) }) {
                        Text(
                            text = authorDates.visibleText,
                            color = JewelTheme.globalColors.text.info,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        if (trailingContent != null) {
            Box(
                modifier = Modifier.padding(8.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                trailingContent()
            }
        }
    }
}

@Composable
private fun stripedRow(rowIndex: Int, content: @Composable () -> Unit) {
    val stripeColor = JewelTheme.globalColors.borders.normal.copy(alpha = 0.12f)
    Box(
        modifier = Modifier.fillMaxWidth().then(
            if (rowIndex % 2 == 1) {
                Modifier.background(color = stripeColor)
            } else {
                Modifier
            }
        )
    ) {
        content()
    }
}