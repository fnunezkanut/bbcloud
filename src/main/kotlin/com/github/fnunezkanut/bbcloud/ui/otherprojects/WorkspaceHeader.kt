@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.ui.otherprojects

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
import com.github.fnunezkanut.bbcloud.ui.components.RoundedBadge
import com.github.fnunezkanut.bbcloud.ui.components.actionButtonHoverBackground
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private object WorkspaceHeaderLogCategory

private val LOG = logger<WorkspaceHeaderLogCategory>()

/**
 * The workspace header of the Workspace - PRs tab: a green tick icon, the workspace-name link to the Bitbucket
 * workspace overview, and the open-PR count pill (mirroring the Current Project - PRs header), then the inline
 * [excludeContent] slot (the Exclude: checkboxes), then a flexible spacer and the refresh action. The Watched Projects
 * tab passes no [excludeContent], so it shows no checkboxes.
 */
@Composable
internal fun WorkspaceHeader(
    state: WorkspaceHeaderState,
    onRefreshRequested: () -> Unit,
    modifier: Modifier = Modifier,
    excludeContent: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // A green tick (the bundled platform InspectionsOK icon) marks the resolved workspace, followed by the
        // workspace-name link to its Bitbucket overview, mirroring the Current Project - PRs header's tick + repo link.
        val workspaceUrl = state.workspaceUrl
        if (workspaceUrl != null) {
            Icon(key = AllIconsKeys.General.InspectionsOK, contentDescription = null)
            // The link text is the workspace slug; when it is blank, fall back to the last path segment of the URL.
            val linkText = state.workspaceName ?: workspaceUrl.trimEnd('/').substringBeforeLast("/workspace/overview").substringAfterLast('/')
            Link(
                text = linkText,
                onClick = {
                    LOG.info("User opened the Bitbucket workspace overview in browser")
                    BrowserUtil.browse(workspaceUrl)
                }
            )
        }
        val openCountText = state.openCountText
        if (openCountText != null) {
            RoundedBadge(text = openCountText, fillColor = actionButtonHoverBackground())
        }
        excludeContent()
        Spacer(modifier = Modifier.weight(1f))
        if (state.isRefreshVisible) {
            OutlinedButton(onClick = onRefreshRequested, enabled = state.isRefreshEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(key = AllIconsKeys.Actions.Refresh, contentDescription = null)
                    Text(text = BbCloudBundle.message("otherProjects.refresh"))
                }
            }
        }
    }
}