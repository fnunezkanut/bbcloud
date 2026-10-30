@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.pullrequests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Text

/**
 * The "Exclude:" label and its checkboxes, emitted inline into the header row of a merged tab (not a row of its own).
 * The Resolved checkbox is rendered only when the tab's scope offers it, so Workspace - PRs shows Mine, Approved by me,
 * Drafts; Current Project - PRs shows Resolved as well.
 */
@Composable
internal fun RowScope.PullRequestExcludeCheckboxes(filter: com.github.fnunezkanut.bbcloud.settings.PullRequestListFilter, viewModel: PullRequestsViewModel) {
    // A 10px gap separates the Exclude: label from the preceding left-hand header section (the workspace/repo link and
    // the open-count pill), on both the Workspace - PRs and Current Project - PRs headers.
    Text(text = BbCloudBundle.message("pullRequests.exclude.label"), modifier = Modifier.padding(start = 10.dp))
    // A nested Row keeps the group directly after the left-hand header section (no weighted spacer here).
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (viewModel.scope.offersResolved) {
            CheckboxRow(
                text = BbCloudBundle.message("pullRequests.exclude.resolved"),
                checked = filter.excludeResolved,
                onCheckedChange = viewModel::onExcludeResolvedChanged
            )
        }
        CheckboxRow(
            text = BbCloudBundle.message("pullRequests.exclude.mine"),
            checked = filter.excludeMine,
            onCheckedChange = viewModel::onExcludeMineChanged
        )
        CheckboxRow(
            text = BbCloudBundle.message("pullRequests.exclude.approvedByMe"),
            checked = filter.excludeApprovedByMe,
            onCheckedChange = viewModel::onExcludeApprovedByMeChanged
        )
        CheckboxRow(
            text = BbCloudBundle.message("pullRequests.exclude.drafts"),
            checked = filter.excludeDrafts,
            onCheckedChange = viewModel::onExcludeDraftsChanged
        )
    }
}