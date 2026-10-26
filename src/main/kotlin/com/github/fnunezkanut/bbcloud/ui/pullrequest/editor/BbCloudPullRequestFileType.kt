package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.ui.components.BbCloudPluginIcons
import com.intellij.openapi.fileTypes.FileType
import javax.swing.Icon

object BbCloudPullRequestFileType : FileType {
    override fun getName(): String = "Bitbucket Cloud PR Review Overview"

    override fun getDescription(): String = BbCloudBundle.message("fileType.pullRequest.description")

    override fun getDefaultExtension(): String = "pr"

    override fun getIcon(): Icon {
        return BbCloudPluginIcons.prIcon
    }

    override fun isBinary(): Boolean = false

    override fun isReadOnly(): Boolean = true
}