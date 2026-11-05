package com.github.fnunezkanut.bbcloud.ui.markdowndiff

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileType
import javax.swing.Icon

/**
 * A dedicated file type for the Markdown diff tab. The platform routes a [BbCloudMarkdownDiffVirtualFile] to
 * [BbCloudMarkdownDiffFileEditorProvider] through this type. The default extension is not associated with any file name.
 */
object BbCloudMarkdownDiffFileType : FileType {
    override fun getName(): String = "Bitbucket Cloud Markdown Diff"

    override fun getDescription(): String = BbCloudBundle.message("fileType.markdownDiff.description")

    override fun getDefaultExtension(): String = "mddiffpr"

    override fun getIcon(): Icon {
        return AllIcons.Actions.Diff
    }

    override fun isBinary(): Boolean = false

    override fun isReadOnly(): Boolean = true
}