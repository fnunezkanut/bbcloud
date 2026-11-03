@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestReviewer
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerStatus
import com.github.fnunezkanut.bbcloud.ui.otherprojects.toComposeImageBitmap
import com.github.fnunezkanut.bbcloud.ui.pullrequests.LISTING_AUTHOR_AVATAR_SIZE_DP
import com.github.fnunezkanut.bbcloud.ui.pullrequests.ReviewerStack
import com.github.fnunezkanut.bbcloud.ui.pullrequests.reviewerOverflowLabel
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * The reviewer avatar size in the listing stack. It tracks [LISTING_AUTHOR_AVATAR_SIZE_DP] so reviewers always match the
 * author on a listing row. It is separate from the shared [REVIEWER_IDENTITY_AVATAR_SIZE], which the Overview tab and the
 * new pull request form keep.
 */
private val REVIEWER_STACK_AVATAR_SIZE = LISTING_AUTHOR_AVATAR_SIZE_DP.dp
private val REVIEWER_STACK_OVERLAP = 6.dp
private val REVIEWER_STACK_RING_WIDTH = 1.5.dp
private val APPROVED_BADGE_SIZE = 10.dp

/** The reduction, in sp, of the "+N" overflow text relative to the default body text, so it fits the listing circle. */
private const val OVERFLOW_FONT_SIZE_REDUCTION = 1f

/**
 * A compact overlapping stack of at most two reviewer avatars plus an optional "+N" circle
 * for the remaining reviewers. Each avatar carries a status badge (green check for an approval, warning for a changes
 * request) and a hover tooltip; the "+N" circle lists the hidden reviewers in its tooltip. Renders nothing for an
 * empty stack.
 */
@Composable
internal fun ReviewerAvatarStack(
    stack: ReviewerStack,
    avatarRevision: Int,
    avatarIconFor: (String?, javax.swing.Icon) -> javax.swing.Icon,
    modifier: Modifier = Modifier
) {
    if (stack.isEmpty) {
        return
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(-REVIEWER_STACK_OVERLAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        stack.visible.forEachIndexed { index, reviewer ->
            // Draw earlier avatars on top of later ones, so the leftmost avatar overlaps the next (matching the design).
            Box(modifier = Modifier.zIndex((stack.visible.size - index).toFloat())) {
                ReviewerAvatar(
                    reviewer = reviewer,
                    avatarRevision = avatarRevision,
                    avatarIconFor = avatarIconFor
                )
            }
        }
        if (stack.hiddenCount > 0) {
            ReviewerOverflowCircle(stack = stack)
        }
    }
}

@Composable
private fun ReviewerAvatar(reviewer: PullRequestReviewer, avatarRevision: Int, avatarIconFor: (String?, javax.swing.Icon) -> javax.swing.Icon) {
    Tooltip(tooltip = { Text(text = reviewerTooltip(reviewer = reviewer)) }) {
        Box(modifier = Modifier.size(REVIEWER_STACK_AVATAR_SIZE)) {
            val icon = remember(reviewer.avatarUrl, avatarRevision) {
                avatarIconFor(reviewer.avatarUrl, AuthorAvatarCache.PLACEHOLDER_ICON).toComposeImageBitmap()
            }
            Image(
                bitmap = icon,
                contentDescription = "Reviewer avatar",
                modifier = Modifier
                    .fillMaxSize()
                    .border(width = REVIEWER_STACK_RING_WIDTH, color = JewelTheme.globalColors.panelBackground, shape = CircleShape)
                    .clip(CircleShape),
                filterQuality = FilterQuality.High
            )
            val badgeKey = reviewerStatusBadgeKey(status = reviewer.status)
            if (badgeKey != null) {
                Icon(
                    key = badgeKey,
                    contentDescription = reviewerStatusTooltip(status = reviewer.status),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-3).dp)
                        .size(APPROVED_BADGE_SIZE)
                        .background(color = JewelTheme.globalColors.panelBackground, shape = CircleShape)
                )
            }
        }
    }
}

@Composable
private fun ReviewerOverflowCircle(stack: ReviewerStack) {
    Tooltip(
        tooltip = {
            Column {
                stack.hidden.forEach { reviewer ->
                    Text(text = reviewerTooltip(reviewer = reviewer))
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .size(REVIEWER_STACK_AVATAR_SIZE)
                .background(color = JewelTheme.globalColors.borders.normal, shape = CircleShape)
                .border(width = REVIEWER_STACK_RING_WIDTH, color = JewelTheme.globalColors.panelBackground, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = reviewerOverflowLabel(hiddenCount = stack.hiddenCount),
                fontSize = (JewelTheme.defaultTextStyle.fontSize.value - OVERFLOW_FONT_SIZE_REDUCTION).sp,
                maxLines = 1
            )
        }
    }
}

private fun reviewerStatusBadgeKey(status: ReviewerStatus): IconKey? {
    return when (status) {
        ReviewerStatus.Approved -> AllIconsKeys.RunConfigurations.TestPassed
        ReviewerStatus.ChangesRequested -> AllIconsKeys.General.BalloonWarning
        ReviewerStatus.None -> null
    }
}

private fun reviewerTooltip(reviewer: PullRequestReviewer): String {
    return BbCloudBundle.message(
        "main.pullRequests.reviewers.tooltip",
        reviewer.displayName,
        reviewerStatusTooltip(status = reviewer.status)
    )
}