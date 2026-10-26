@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.ui.otherprojects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.ui.main.MainPanelStatusSupport
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
 * The workspace header of one Workspace tab root: the workspace prefix and link, and the refresh action.
 */
@Composable
internal fun WorkspaceHeader(state: WorkspaceHeaderState, onRefreshRequested: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val workspaceUrl = state.workspaceUrl
        if (workspaceUrl != null) {
            Text(text = BbCloudBundle.message("otherProjects.workspace.prefix"), color = MainPanelStatusSupport.projectFoundForeground())
            Link(
                text = workspaceUrl,
                onClick = {
                    LOG.info("User opened the Bitbucket workspace overview in browser")
                    BrowserUtil.browse(workspaceUrl)
                }
            )
        }
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