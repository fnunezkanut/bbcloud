package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerCandidates
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.jewel.bridge.compose
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A popup that searches workspace members locally and reports the chosen user through [onSelected].
 *
 * The popup shell stays a platform [JBPopup], because it provides the cancel-on-click-outside, the focus handling, and
 * the window-deactivation behaviour the search relies on; its content is a Compose tree.
 */
class ReviewerSearchPopup(excludedUuids: Set<String>, avatarCache: AuthorAvatarCache, coroutineScope: CoroutineScope, onSelected: (ReviewerCandidate) -> Unit) {
    private val viewModel = ReviewerSearchViewModel(
        excludedUuids = excludedUuids,
        avatarCache = avatarCache,
        coroutineScope = coroutineScope,
        onSelected = onSelected
    )

    /**
     * The Compose host is wrapped in a panel with a fixed preferred size, so the popup keeps the size it always had and
     * the Compose content is laid out inside a bounded area.
     */
    private val content = object : JPanel(BorderLayout()) {
        override fun getPreferredSize() = JBUI.size(POPUP_WIDTH, POPUP_HEIGHT)

        override fun getMinimumSize() = JBUI.size(POPUP_WIDTH, POPUP_HEIGHT)
    }.apply {
        isOpaque = false
        add(
            compose {
                ReviewerSearchContent(viewModel = viewModel)
            },
            BorderLayout.CENTER
        )
    }

    private val popup: JBPopup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(content, null)
        .setRequestFocus(true)
        .setFocusable(true)
        .setCancelOnClickOutside(true)
        .setCancelOnWindowDeactivation(true)
        .setResizable(true)
        .setMovable(false)
        .createPopup()

    init {
        viewModel.onDismiss = { popup.closeOk(null) }
    }

    fun show(anchor: JComponent) {
        popup.showUnderneathOf(anchor)
    }

    /** A null [result] means the workspace members could not be loaded. */
    fun showCandidates(result: ReviewerCandidates?) {
        if (popup.isDisposed) {
            LOG.debug("Ignoring reviewer candidates; the reviewer search popup is already closed")
            return
        }
        viewModel.showCandidates(result)
    }

    companion object {
        private val LOG = logger<ReviewerSearchPopup>()

        private const val POPUP_WIDTH = 320
        private const val POPUP_HEIGHT = 300
    }
}