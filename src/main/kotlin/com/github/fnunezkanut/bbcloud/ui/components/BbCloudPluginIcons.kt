package com.github.fnunezkanut.bbcloud.ui.components

import com.intellij.openapi.util.IconLoader
import org.jetbrains.jewel.ui.icon.IntelliJIconKey
import javax.swing.Icon

/**
 * The plugin's green and purple pull-request merge icon. [prIcon] is the shared 16×16 Swing icon for the `.pr` file type
 * and classic tool-window registration; [pr] is the Jewel [IntelliJIconKey] for Compose bars (`icons/prToolWindow.svg` on
 * the classic UI and `icons/expui/toolwindow/prToolWindow.svg` on the new UI).
 */
internal object BbCloudPluginIcons {
    val prIcon: Icon
        get() = IconLoader.getIcon("/icons/prToolWindow.svg", BbCloudPluginIcons::class.java)

    val pr: IntelliJIconKey = IntelliJIconKey(
        oldUiPath = "icons/prToolWindow.svg",
        newUiPath = "icons/expui/toolwindow/prToolWindow.svg",
        iconClass = BbCloudPluginIcons::class.java
    )
}