package com.github.fnunezkanut.bbcloud.ui.pullrequest.help

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposePanel
import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.compose
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

/** The editor behind the `help.pr` Work Trees help tab. It hosts the Compose help screen and disposes it with the tab. */
class BbCloudWorktreesHelpFileEditor(private val file: BbCloudWorktreesHelpVirtualFile) : FileEditorBase() {
    private val markdown = WorktreesHelpMarkdown.load()

    private var composePanel: ComposePanel? = null

    // The ComposePanel must never be asked to size against unbounded height, or the scrolling container inside blows up.
    // The empty preferred and minimum sizes force BorderLayout to give it the available area instead.
    private val composeComponent: JComponent = compose(config = { composePanel = this }) {
        WorktreesHelpScreen(markdown = markdown)
    }

    private val composeHost = object : JPanel(BorderLayout()) {
        override fun getPreferredSize(): Dimension {
            return JBUI.emptySize()
        }

        override fun getMinimumSize(): Dimension {
            return JBUI.emptySize()
        }
    }.apply {
        isOpaque = false
        add(composeComponent, BorderLayout.CENTER)
    }

    private val editorComponent: JPanel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(JBUI.scale(8))
        add(composeHost, BorderLayout.CENTER)
    }

    override fun getComponent(): JComponent {
        return editorComponent
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return composeComponent
    }

    override fun getName(): String {
        return file.name
    }

    override fun getFile(): VirtualFile {
        return file
    }

    override fun isValid(): Boolean {
        return file.isValid
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun dispose() {
        composePanel?.dispose()
        super.dispose()
    }
}