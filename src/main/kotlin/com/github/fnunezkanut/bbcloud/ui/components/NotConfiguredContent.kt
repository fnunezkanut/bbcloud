@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.jewel.ui.component.Link

private val LOG = Logger.getInstance("com.github.fnunezkanut.bbcloud.ui.components.NotConfiguredContent")

/**
 * Shown by the Project and Workspace tabs while the plugin is not configured. The whole message is the link, so
 * first-time setup is one click away.
 */
@Composable
internal fun NotConfiguredContent(onGoToSettings: () -> Unit, modifier: Modifier = Modifier) {
    CenteredContent(modifier = modifier.padding(8.dp)) {
        Link(
            text = BbCloudBundle.message("toolwindow.notConfigured.goToSettings"),
            onClick = {
                LOG.info("User requested Bitbucket Cloud PR Review settings from an unconfigured panel")
                onGoToSettings()
            }
        )
    }
}