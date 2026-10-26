@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.ui.components.BbCloudOutlinedActionButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * The project status row of one Project tab root: the status icon and message, the "Project:" prefix and repository
 * link, the open pull request count, and the refresh action.
 */
@Composable
internal fun ProjectStatusHeader(
    state: ProjectStatusHeaderState,
    onRefreshRequested: () -> Unit,
    onCreatePullRequestRequested: () -> Unit,
    onRepositoryLinkClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val statusIcon = state.statusIcon
        if (statusIcon != null) {
            Icon(key = statusIcon, contentDescription = null)
        }
        val statusMessage = state.statusMessage
        if (statusMessage != null) {
            Text(text = statusMessage)
        }
        val repositoryWebUrl = state.repositoryWebUrl
        if (repositoryWebUrl != null) {
            Text(text = BbCloudBundle.message("main.branch.project"), color = MainPanelStatusSupport.projectFoundForeground())
            Link(text = repositoryWebUrl, onClick = { onRepositoryLinkClicked(repositoryWebUrl) })
        }
        val openCountText = state.openCountText
        if (openCountText != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = openCountText)
        }
        Spacer(modifier = Modifier.weight(1f))
        if (state.isCreatePullRequestVisible) {
            BbCloudOutlinedActionButton(
                text = BbCloudBundle.message("main.pullRequests.create"),
                icon = AllIconsKeys.General.Add,
                outlineColor = ActionButtonOutlines.green(),
                enabled = true,
                onClick = onCreatePullRequestRequested
            )
        }
        if (state.isRefreshVisible) {
            OutlinedButton(onClick = onRefreshRequested, enabled = state.isRefreshEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(key = AllIconsKeys.Actions.Refresh, contentDescription = null)
                    Text(text = BbCloudBundle.message("main.pullRequests.refresh"))
                }
            }
        }
    }
}