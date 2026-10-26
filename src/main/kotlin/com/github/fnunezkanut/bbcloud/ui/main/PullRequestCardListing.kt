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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewingPullRequest
import com.github.fnunezkanut.bbcloud.ui.components.BRANCH_ARROW
import com.github.fnunezkanut.bbcloud.ui.components.BranchChip
import com.github.fnunezkanut.bbcloud.ui.otherprojects.WorkspacePullRequestRow
import com.github.fnunezkanut.bbcloud.ui.otherprojects.toComposeImageBitmap
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Badge
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private val AVATAR_SIZE = 48.dp

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

@Composable
internal fun PullRequestCardListing(
    rows: List<WorkspacePullRequestRow>,
    avatarRevision: Int,
    showRepositoryColumn: Boolean,
    showStateBadge: Boolean,
    avatarIconFor: (String?, javax.swing.Icon) -> javax.swing.Icon,
    actions: PullRequestCardActions
) {
    VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { rowIndex, row ->
                PullRequestCard(
                    row = row,
                    rowIndex = rowIndex,
                    avatarRevision = avatarRevision,
                    showRepositoryColumn = showRepositoryColumn,
                    showStateBadge = showStateBadge,
                    avatarIconFor = avatarIconFor,
                    actions = actions
                )
            }
        }
    }
}

/**
 * The pull-request detail info card. Unlike the listing card (`showRepositoryColumn = true`), the author subtitle here
 * shows the updated/created timestamps with an absolute-date tooltip; conversation volume lives on the Conversation tab
 * badge instead of a comment count under the author.
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
        rowIndex = 0,
        avatarRevision = avatarRevision,
        showRepositoryColumn = false,
        showStateBadge = true,
        avatarIconFor = avatarIconFor,
        actions = actions,
        trailingContent = trailingContent
    )
}

@Composable
private fun PullRequestCard(
    row: WorkspacePullRequestRow,
    rowIndex: Int,
    avatarRevision: Int,
    showRepositoryColumn: Boolean,
    showStateBadge: Boolean,
    avatarIconFor: (String?, javax.swing.Icon) -> javax.swing.Icon,
    actions: PullRequestCardActions,
    trailingContent: (@Composable () -> Unit)? = null
) {
    stripedRow(rowIndex = rowIndex) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // The detail card renders a trailing actions column, so the author column takes a weight instead of the
            // full width; listing tables keep their golden-ratio split and their own full-width single column.
            val authorColumnModifier = when {
                showRepositoryColumn -> Modifier.weight(GOLDEN_RATIO_LEFT)
                trailingContent != null -> Modifier.weight(1f)
                else -> Modifier.fillMaxWidth()
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
                        modifier = Modifier.size(AVATAR_SIZE),
                        filterQuality = FilterQuality.High
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Tooltip(tooltip = { Text(text = row.title) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Link(
                                    text = row.title,
                                    onClick = { actions.onOpenTitle(row.pullRequest) },
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
                            }
                        }
                        Text(
                            text = row.authorDisplayName,
                            color = JewelTheme.globalColors.text.info,
                            fontStyle = FontStyle.Italic,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (showRepositoryColumn) {
                            // Listings keep the balloon comment count under the author; the Conversation tab badge on
                            // the detail card already carries conversation volume, so the detail card shows dates here.
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
                        } else {
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
            }
            if (showRepositoryColumn) {
                Box(
                    modifier = Modifier.weight(GOLDEN_RATIO_RIGHT).padding(8.dp)
                ) {
                    val icon = remember(row.repoAvatarUrl, avatarRevision) {
                        avatarIconFor(
                            row.repoAvatarUrl,
                            AuthorAvatarCache.REPOSITORY_PLACEHOLDER_ICON
                        ).toComposeImageBitmap()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Image(
                            bitmap = icon,
                            contentDescription = "Repository avatar",
                            modifier = Modifier.size(AVATAR_SIZE),
                            filterQuality = FilterQuality.High
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Link(
                                    text = row.prId,
                                    onClick = { actions.onOpenPrOnWeb(row.pullRequest) },
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = " ${BbCloudBundle.message("main.pullRequests.inRepoConnector")} ",
                                    color = JewelTheme.globalColors.text.info,
                                    maxLines = 1
                                )
                                if (row.repositoryWebUrl.isNullOrBlank()) {
                                    Text(
                                        text = row.repoSlug,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Link(
                                        text = row.repoSlug,
                                        onClick = { actions.onOpenRepository(row.pullRequest) },
                                        modifier = Modifier.weight(1f),
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
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
                            Text(
                                text = BbCloudBundle.message(
                                    "main.pullRequests.updatedCreated",
                                    row.updatedRelative,
                                    row.createdRelative
                                ),
                                color = JewelTheme.globalColors.text.info,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else if (trailingContent != null) {
                Box(
                    modifier = Modifier.padding(8.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    trailingContent()
                }
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