@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.ui.BbCloudToolWindowPanel
import com.github.fnunezkanut.bbcloud.ui.components.CenteredContent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import javax.swing.JComponent

/**
 * The body of the Settings > Tools page: a description and a link that opens the plugin's own settings tab.
 *
 * The page itself stays a platform [Configurable], so the body is a Compose tree hosted by the platform.
 */
@Composable
private fun ToolsConfigurableContent(onOpenSettings: () -> Unit) {
    // The description sits above the link, and the whole stack is centered instead of stretching a button across the
    // whole area.
    CenteredContent(modifier = Modifier.padding(8.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = BbCloudBundle.message("configurable.tools.description"))
            Link(text = BbCloudBundle.message("configurable.tools.open"), onClick = onOpenSettings)
        }
    }
}

/** Provides a launcher from Settings > Tools to the plugin's project settings tab. */
class BbCloudToolsConfigurable(private val project: Project) : Configurable {
    /** The Compose host of the page, used to find the Settings dialog that has to be closed after the link is used. */
    private var componentHost: JComponent? = null

    override fun getDisplayName(): String {
        return BbCloudBundle.message("configurable.tools.displayName")
    }

    override fun createComponent(): JComponent {
        val host = compose {
            ToolsConfigurableContent(onOpenSettings = { openPluginSettings() })
        }
        componentHost = host
        return host
    }

    override fun isModified(): Boolean {
        return false
    }

    override fun apply() {
        // Settings are persisted by the Bitbucket Cloud PR Review tool window's Settings tab.
    }

    private fun openPluginSettings() {
        LOG.info("User opened Bitbucket Cloud PR Review settings from the Tools settings page")
        openToolWindowOnSettings()
        closeSettingsDialog()
    }

    private fun openToolWindowOnSettings() {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
        if (toolWindow == null) {
            LOG.info("Cannot open Bitbucket Cloud PR Review settings: tool window is unavailable")
            return
        }
        val panel = project.service<BbCloudToolWindowPanel>()
        toolWindow.show {
            if (!panel.showSettings()) {
                LOG.info("Cannot open Bitbucket Cloud PR Review settings: Settings content is unavailable")
            }
        }
    }

    private fun closeSettingsDialog() {
        val component = componentHost
        val dialog = if (component == null) null else DialogWrapper.findInstance(component)
        if (dialog == null) {
            LOG.debug("Settings dialog was not found after opening Bitbucket Cloud PR Review settings")
            return
        }
        dialog.doCancelAction()
        LOG.debug("Closed the Settings dialog after opening Bitbucket Cloud PR Review settings")
    }

    companion object {
        private val LOG = logger<BbCloudToolsConfigurable>()
        private const val TOOL_WINDOW_ID = "Bitbucket Cloud PR Review"
    }
}