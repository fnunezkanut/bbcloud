package com.github.fnunezkanut.bbcloud.ui.pullrequest.help

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.ui.components.BbCloudPluginIcons
import com.intellij.openapi.fileTypes.FileType
import javax.swing.Icon

/**
 * A dedicated file type for the `help.pr` Work Trees help tab. The platform routes a [BbCloudWorktreesHelpVirtualFile] to
 * [BbCloudWorktreesHelpFileEditorProvider] through this type. Without it the default text editor opens an empty tab.
 *
 * The default extension is distinct from the review and create-form types, and no file name association uses it.
 */
object BbCloudWorktreesHelpFileType : FileType {
    override fun getName(): String = "Bitbucket Cloud Work Trees Help"

    override fun getDescription(): String = BbCloudBundle.message("fileType.worktreesHelp.description")

    override fun getDefaultExtension(): String = "helppr"

    override fun getIcon(): Icon {
        return BbCloudPluginIcons.prIcon
    }

    override fun isBinary(): Boolean = false

    override fun isReadOnly(): Boolean = true
}