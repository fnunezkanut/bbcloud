package com.github.fnunezkanut.bbcloud.ui.markdowndiff

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposePanel
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.jewel.bridge.compose
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The editor behind a Markdown diff tab. It hosts the Compose diff screen, owns the [MarkdownDiffViewModel], and
 * reloads the diff each time the tab is selected so it follows edits made in other tabs.
 */
class BbCloudMarkdownDiffFileEditor(private val file: BbCloudMarkdownDiffVirtualFile) : FileEditorBase() {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val viewModel = MarkdownDiffViewModel(
        source = file.source,
        coroutineScope = coroutineScope,
        runOnEdt = { block -> ApplicationManager.getApplication().invokeLater(block) },
        failureMessage = { exception -> BbCloudBundle.message("markdownDiff.error.loadFailed", exception.message.orEmpty()) }
    )

    private var composePanel: ComposePanel? = null

    private val composeComponent: JComponent = compose(config = { composePanel = this }) {
        MarkdownDiffScreen(viewModel = viewModel)
    }

    // An empty preferred and minimum size makes BorderLayout give the Compose panel the available area, so the lazy
    // list inside never measures against unbounded height.
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

    init {
        viewModel.reload()
    }

    fun reload() {
        viewModel.reload()
    }

    override fun selectNotify() {
        viewModel.reload()
    }

    override fun getComponent(): JComponent {
        return composeHost
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
        coroutineScope.cancel()
        composePanel?.dispose()
        super.dispose()
    }
}