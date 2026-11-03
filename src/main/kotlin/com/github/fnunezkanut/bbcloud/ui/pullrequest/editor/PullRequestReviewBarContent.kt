@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.git.FileLineCount
import com.github.fnunezkanut.bbcloud.ui.components.BbCloudOutlinedActionButton
import com.github.fnunezkanut.bbcloud.ui.components.LinesChangedBadge
import com.github.fnunezkanut.bbcloud.ui.components.RoundedBadge
import com.github.fnunezkanut.bbcloud.ui.components.labelDisabledForeground
import com.github.fnunezkanut.bbcloud.ui.components.reviewBarPurple
import com.github.fnunezkanut.bbcloud.ui.main.ActionButtonOutlines
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** The gap between the surface action button and the line-change badge. */
private val REVIEW_BAR_LINES_GAP = 8.dp

/** The gap between the "Viewed" pill and the "Add Comment" button. */
private val REVIEW_BAR_CONTROL_GAP = 8.dp

/** The gap between the inline hint text and the Previous button. */
private val REVIEW_BAR_HINT_GAP = 8.dp

/** The padding around the content of the bar. */
private val REVIEW_BAR_PADDING = 4.dp

/**
 * The content of the review bar. Comment threads and composers render in editor inlays, not in this bar.
 *
 * The previous and next unviewed-file buttons show only on the [ReviewBarSurface.Diff] surface and only when a
 * [navigation] is given. [navigation] is null when the chain of the diff cannot be found, or when the chain has one file.
 */
@Composable
internal fun PullRequestReviewBarContent(
    viewed: Boolean,
    lineCount: FileLineCount?,
    surface: ReviewBarSurface,
    action: ReviewBarAction?,
    navigation: ReviewBarFileNavigationState?,
    onViewedToggled: (Boolean) -> Unit,
    onAddComment: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(REVIEW_BAR_PADDING)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (action != null) {
                BbCloudOutlinedActionButton(
                    text = when (surface) {
                        ReviewBarSurface.NormalFile -> BbCloudBundle.message("editor.reviewBar.showDiff")
                        ReviewBarSurface.Diff -> BbCloudBundle.message("editor.reviewBar.openFile")
                    },
                    icon = when (surface) {
                        ReviewBarSurface.NormalFile -> AllIconsKeys.Actions.Diff
                        ReviewBarSurface.Diff -> AllIconsKeys.Actions.Edit
                    },
                    outlineColor = ActionButtonOutlines.blue(),
                    enabled = true,
                    onClick = action.onClick
                )
            }
            if (lineCount != null && !lineCount.isBinary) {
                LinesChangedBadge(
                    linesAdded = lineCount.linesAdded,
                    linesRemoved = lineCount.linesRemoved,
                    modifier = Modifier.padding(start = REVIEW_BAR_LINES_GAP)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (surface == ReviewBarSurface.Diff && navigation != null) {
                ReviewBarNavigationButtons(navigation = navigation)
            }
            RoundedBadge(
                text = BbCloudBundle.message("editor.reviewBar.viewed"),
                fillColor = reviewBarPurple(),
                modifier = Modifier.clickable { onViewedToggled(!viewed) }.pointerHoverIcon(PointerIcon.Hand),
                leadingContent = { Checkbox(checked = viewed, onCheckedChange = onViewedToggled) }
            )
            AddCommentButton(
                enabled = lineCount?.isBinary != true,
                onAddComment = onAddComment
            )
        }
    }
}

/** The "Add Comment" button. */
@Composable
private fun AddCommentButton(enabled: Boolean, onAddComment: () -> Unit) {
    BbCloudOutlinedActionButton(
        text = BbCloudBundle.message("editor.reviewBar.addComment"),
        icon = AllIconsKeys.General.Add,
        outlineColor = ActionButtonOutlines.green(),
        enabled = enabled,
        onClick = onAddComment,
        modifier = Modifier.padding(start = REVIEW_BAR_CONTROL_GAP)
    )
}

/**
 * The Previous and Next buttons that move the diff to the closest unviewed file before or after the current one.
 *
 * Both buttons stay visible. They are enabled or disabled together, because the search wraps around the chain.
 * When both are disabled, an inline hint says that every other file is marked as viewed.
 */
@Composable
private fun ReviewBarNavigationButtons(navigation: ReviewBarFileNavigationState) {
    val previousIndex = navigation.unviewedTargetIndex(direction = ReviewBarFileNavigationDirection.Previous)
    val nextIndex = navigation.unviewedTargetIndex(direction = ReviewBarFileNavigationDirection.Next)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (previousIndex == null && nextIndex == null) {
            Text(
                text = BbCloudBundle.message("editor.reviewBar.noUnviewedFile"),
                color = labelDisabledForeground(),
                modifier = Modifier.padding(end = REVIEW_BAR_HINT_GAP)
            )
        }
        ReviewBarNavigationButton(
            text = BbCloudBundle.message("editor.reviewBar.previousUnviewed"),
            icon = AllIconsKeys.Actions.Back,
            enabled = previousIndex != null,
            onClick = {
                previousIndex?.let { index -> navigation.onNavigate(index, ReviewBarFileNavigationDirection.Previous) }
            }
        )
        ReviewBarNavigationButton(
            text = BbCloudBundle.message("editor.reviewBar.nextUnviewed"),
            icon = AllIconsKeys.Actions.Forward,
            enabled = nextIndex != null,
            onClick = {
                nextIndex?.let { index -> navigation.onNavigate(index, ReviewBarFileNavigationDirection.Next) }
            },
            modifier = Modifier.padding(start = REVIEW_BAR_CONTROL_GAP, end = REVIEW_BAR_CONTROL_GAP)
        )
    }
}

/** One navigation button. It has no hover tooltip, because the tooltip popup covered the buttons and did not close. */
@Composable
private fun ReviewBarNavigationButton(text: String, icon: IconKey, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BbCloudOutlinedActionButton(
        text = text,
        icon = icon,
        outlineColor = ActionButtonOutlines.blue(),
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
    )
}