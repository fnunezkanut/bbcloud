package com.github.fnunezkanut.bbcloud.ui.pullrequest.create

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.ui.components.BbCloudPluginIcons
import com.intellij.openapi.fileTypes.FileType
import javax.swing.Icon

/**
 * A dedicated file type for the `new.pr` create-pull-request tab, kept separate from the `<id>.pr` review file type.
 *
 * Giving the create form its own file type removes any ambiguity about which editor opens it: the platform routes a
 * [BbCloudNewPullRequestVirtualFile] to [BbCloudNewPullRequestFileEditorProvider] via this type, rather than falling
 * back to the default text editor and showing an empty tab. It reuses the shared pull-request icon.
 */
object BbCloudNewPullRequestFileType : FileType {
    override fun getName(): String = "Bitbucket Cloud New PR"

    override fun getDescription(): String = BbCloudBundle.message("fileType.newPullRequest.description")

    override fun getDefaultExtension(): String = "newpr"

    override fun getIcon(): Icon {
        return BbCloudPluginIcons.prIcon
    }

    override fun isBinary(): Boolean = false

    override fun isReadOnly(): Boolean = true
}