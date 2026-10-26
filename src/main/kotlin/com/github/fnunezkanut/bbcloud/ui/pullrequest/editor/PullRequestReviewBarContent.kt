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
import com.github.fnunezkanut.bbcloud.ui.components.reviewBarPurple
import com.github.fnunezkanut.bbcloud.ui.main.ActionButtonOutlines
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** The gap between the surface action button and the line-change badge. */
private val REVIEW_BAR_LINES_GAP = 8.dp

/** The gap between the "Viewed" pill and the "Add Comment" button. */
private val REVIEW_BAR_CONTROL_GAP = 8.dp

/** The padding around the content of the bar. */
private val REVIEW_BAR_PADDING = 4.dp

/** The content of the review bar. Comment threads and composers render in editor inlays, not in this bar. */
@Composable
internal fun PullRequestReviewBarContent(
    viewed: Boolean,
    lineCount: FileLineCount?,
    surface: ReviewBarSurface,
    action: ReviewBarAction?,
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
            RoundedBadge(
                text = BbCloudBundle.message("editor.reviewBar.viewed"),
                fillColor = reviewBarPurple(),
                modifier = Modifier.clickable { onViewedToggled(!viewed) }.pointerHoverIcon(PointerIcon.Hand),
                leadingContent = { Checkbox(checked = viewed, onCheckedChange = onViewedToggled) }
            )
            BbCloudOutlinedActionButton(
                text = BbCloudBundle.message("editor.reviewBar.addComment"),
                icon = AllIconsKeys.General.Add,
                outlineColor = ActionButtonOutlines.green(),
                enabled = lineCount?.isBinary != true,
                onClick = onAddComment,
                modifier = Modifier.padding(start = REVIEW_BAR_CONTROL_GAP)
            )
        }
    }
}