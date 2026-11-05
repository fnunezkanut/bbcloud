package com.github.fnunezkanut.bbcloud.toolwindow

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import java.awt.Component
import java.awt.Window
import javax.swing.JComponent

/**
 * Hides the tool window name the platform draws to the left of the header tabs, so the Bitbucket Cloud PR Review header shows only the
 * Current Project, Workspace, Watched Projects, Recent Projects and Settings tabs. The PR Changes tool window uses the same helper, so its header shows only the
 * `source > destination` content tab.
 *
 * The platform evaluates this only while rebuilding the header, and it walks up from the tool window content component
 * rather than down into the content we contribute, so the property has to be in place before the first content is added
 * to the tool window. Applying it later leaves the name in the header until some unrelated event rebuilds it.
 */
object BbCloudToolWindowIdLabel {
    /**
     * Marks [component] and each of its ancestors so the platform treats the tool window name as hidden.
     *
     * The value must be the string `"true"`: the platform matches it with `"true".equals(...)`, so a `Boolean` would be
     * ignored. The walk starts at [component] itself, because the platform includes its start component when it looks
     * the property up.
     */
    fun hide(component: JComponent) {
        LOG.debug("Hiding the tool window name in the header of tool window component $component")
        var current: Component? = component
        while (current != null && current !is Window) {
            (current as? JComponent)?.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")
            current = current.parent
        }
    }

    private val LOG = logger<BbCloudToolWindowIdLabel>()
}