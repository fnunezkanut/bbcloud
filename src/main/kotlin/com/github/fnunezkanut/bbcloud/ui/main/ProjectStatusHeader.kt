@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.ui.components.BbCloudOutlinedActionButton
import com.github.fnunezkanut.bbcloud.ui.components.RoundedBadge
import com.github.fnunezkanut.bbcloud.ui.components.actionButtonHoverBackground
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * The project status row of the Current Project - PRs tab: when the repository is resolved it reads
 * <green tick icon> <repo_slug link> <num_open pill>, then the inline [excludeContent] slot (the Exclude: checkboxes),
 * then a flexible spacer, then the Create new PR and Refresh buttons. When the repository is not resolved it instead
 * shows the status icon and message (for example "Checking..."). The former "Project:" prefix has been removed.
 */
@Composable
internal fun ProjectStatusHeader(
    state: ProjectStatusHeaderState,
    onRefreshRequested: () -> Unit,
    onCreatePullRequestRequested: () -> Unit,
    onRepositoryLinkClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
    excludeContent: @Composable RowScope.() -> Unit = {}
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
            // The link text is the repository slug; when the slug is blank, fall back to the last path segment of the URL.
            val linkText = state.repositorySlug ?: repositoryWebUrl.trimEnd('/').substringAfterLast('/')
            Link(text = linkText, onClick = { onRepositoryLinkClicked(repositoryWebUrl) })
        }
        val openCountText = state.openCountText
        if (openCountText != null) {
            RoundedBadge(text = openCountText, fillColor = actionButtonHoverBackground())
        }
        excludeContent()
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