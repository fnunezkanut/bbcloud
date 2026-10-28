@file:OptIn(androidx.compose.ui.unit.ExperimentalUnitApi::class)

package com.github.fnunezkanut.bbcloud.ui.diff

import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.awt.UNSPECIFIED_DIMENSION_VALUE
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.scale.JBUIScale
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.ceil
import kotlin.math.max

/**
 * The Swing host of the inline diff comment thread.
 *
 * An editor inlay needs a `JComponent`, so the Compose content is hosted here. The host keeps the notification that
 * tells the editor the embedded height changed, which is what the Swing panel used to do.
 *
 * The host also tracks the editor viewport width so the thread spans the full editor text width (see
 * [editorInlayContentWidth]) instead of the narrower `editor.contentComponent.width`. It listens for viewport resizes
 * and for height changes of the embedded [ComposePanel], re-measuring and relaying the inlay on either.
 */
internal class DiffCommentThreadHost(private val editor: EditorEx) : JPanel(BorderLayout()) {
    /** The embedded Compose content; set by [attachComposePanel] once the content is built. */
    private var composePanel: ComposePanel? = null

    /** Set once a Compose measurement failed, so [logMeasureFailureOnce] logs only the first failure of this host. */
    private var measureFailureLogged: Boolean = false

    /** The height last reported by [getPreferredSize]; used when Compose has no measured height yet. */
    private var lastInlayHeight: Int = 0

    /** The latest Compose content height (physical pixels) waiting for [applyComposeContentHeight]. */
    private var pendingContentHeightPx: Int = 0

    /** Whether an [applyComposeContentHeight] run is already queued on the EDT. */
    private var contentHeightSyncScheduled: Boolean = false

    /** The scrollbar-flip math only depends on the scroll pane client property, so it is computed once. */
    private val verticalScrollbarFlipped: Boolean = run {
        val scrollbarFlip = editor.scrollPane.getClientProperty(JBScrollPane.Flip::class.java)
        scrollbarFlip == JBScrollPane.Flip.HORIZONTAL || scrollbarFlip == JBScrollPane.Flip.BOTH
    }

    private val viewportResizeListener: ComponentListener = object : ComponentAdapter() {
        override fun componentResized(event: ComponentEvent) {
            refreshWidth()
        }

        override fun componentShown(event: ComponentEvent) {
            refreshWidth()
        }
    }

    private val composePanelResizeListener: ComponentListener = object : ComponentAdapter() {
        override fun componentResized(event: ComponentEvent) {
            notifyEmbeddedHeightChanged()
        }
    }

    init {
        isOpaque = false
        isFocusable = true
        isRequestFocusEnabled = true
        editor.scrollPane.viewport.addComponentListener(viewportResizeListener)
    }

    /** Hosts [panel] at the center and keeps a reference so width and height updates can reach it. */
    fun attachComposePanel(panel: ComposePanel) {
        composePanel = panel
        panel.addComponentListener(composePanelResizeListener)
        add(panel, BorderLayout.CENTER)
        refreshWidth()
    }

    /** The current full editor-text width the inlay should occupy. */
    fun currentInlayWidth(): Int {
        return editorInlayContentWidth(
            viewportWidth = editor.scrollPane.viewport.width,
            verticalScrollbarWidth = verticalScrollbarAllowance(),
            gutterTextGap = gutterTextGap()
        )
    }

    /** Re-measures Compose at the current viewport width and relays the inlay (for deferred layout after insert). */
    fun remeasureInlayWidth() {
        refreshWidth()
    }

    /**
     * Drives the inlay width from the editor viewport instead of the preferred width of the Compose content, so the
     * thread spans the full editor text width. The height follows the embedded content preferred height.
     *
     * Must not call `super.getPreferredSize()`: that re-enters [ComposePanel.getPreferredSize], which measures Compose
     * during a Swing validation pass and fails with "layout state is not idle before measure starts".
     */
    override fun getPreferredSize(): Dimension {
        val width = currentInlayWidth()
        val childHeight = inlayHeightOrFallback(
            resolve = { resolveComposePanelHeightForInlay(panel = composePanel) },
            fallback = { lastInlayHeight },
            onFailure = { exception -> logMeasureFailureOnce(exception = exception) }
        )
        lastInlayHeight = childHeight
        return Dimension(width, childHeight)
    }

    /**
     * Logs a failed Compose measurement once per host, so a layout pass that keeps failing does not flood `idea.log`.
     */
    private fun logMeasureFailureOnce(exception: IllegalStateException) {
        if (measureFailureLogged) {
            return
        }
        measureFailureLogged = true
        LOG.warn("Inline comment inlay could not measure the Compose content; using the Swing height", exception)
    }

    /**
     * Updates the Compose preferred height after layout (Reply/Edit expansion). Width stays viewport-derived; height is
     * set only when [contentHeightPx] is positive. [contentHeightPx] is in Compose physical pixels and is converted to
     * Swing logical pixels before being stored on the panel preferred size. The update runs on a later EDT event.
     */
    fun syncComposeContentHeight(contentHeightPx: Int) {
        if (contentHeightPx <= 0) {
            return
        }
        // Called from `onSizeChanged`, i.e. inside the Compose measure pass. Revalidating and dispatching the resize
        // event synchronously re-enters Compose measurement ("layout state is not idle before measure starts") and
        // leaves the Compose layout state stuck, which then fails again when the panel is detached. Defer and
        // coalesce the update so it runs after the measure pass completes.
        pendingContentHeightPx = contentHeightPx
        if (contentHeightSyncScheduled) {
            return
        }
        contentHeightSyncScheduled = true
        SwingUtilities.invokeLater {
            contentHeightSyncScheduled = false
            if (isDisplayable) {
                applyComposeContentHeight(contentHeightPx = pendingContentHeightPx)
            }
        }
    }

    private fun applyComposeContentHeight(contentHeightPx: Int) {
        val panel = composePanel ?: return
        val width = currentInlayWidth().takeIf { inlayWidth -> inlayWidth > 0 }
            ?: panel.preferredSize?.width?.takeIf { panelWidth -> panelWidth > 0 }
            ?: return
        val swingHeight = composePixelsToSwingPixels(pixels = contentHeightPx, scale = JBUIScale.sysScale())
        panel.preferredSize = Dimension(width, swingHeight)
        notifyEmbeddedHeightChanged()
    }

    override fun addNotify() {
        super.addNotify()
        val parent = parent
        if (parent is Container && liftInlayWrapperWidthCap(wrapper = parent)) {
            parent.revalidate()
        } else if (parent != null) {
            LOG.debug("Inline comment inlay parent is not a width-capped platform wrapper; full-width relies on host preferred size")
        }
    }

    /** Re-measures the Compose content at the current viewport width and relays the inlay. */
    private fun refreshWidth() {
        val width = currentInlayWidth()
        if (width <= 0) {
            // The editor is not laid out yet; a later viewport resize/shown event remeasures. No fallback width.
            LOG.debug("Inline comment inlay width not available yet; deferring remeasure")
            return
        }
        composePanel?.let { panel ->
            panel.preferredSize = preferredComposeDimensionForWidthUpdate(
                width = width,
                existingPreferredHeight = panel.preferredSize?.height ?: UNSPECIFIED_DIMENSION_VALUE
            )
            panel.invalidate()
            panel.revalidate()
        }
        notifyEmbeddedHeightChanged()
    }

    /** Tells the editor that the embedded height of the thread changed, so the inlay is laid out again. */
    fun notifyEmbeddedHeightChanged() {
        invalidate()
        revalidate()
        repaint()
        dispatchEvent(ComponentEvent(this as Component, ComponentEvent.COMPONENT_RESIZED))
    }

    override fun removeNotify() {
        editor.scrollPane.viewport.removeComponentListener(viewportResizeListener)
        composePanel?.removeComponentListener(composePanelResizeListener)
        super.removeNotify()
    }

    /** The width the vertical scrollbar reserves next to the editor text, matching the platform watcher math. */
    private fun verticalScrollbarAllowance(): Int {
        val width = editor.scrollPane.verticalScrollBar.width
        return if (!verticalScrollbarFlipped) width * 2 else width
    }

    /** The gutter text gap the platform watcher deducts only when the vertical scrollbar is flipped. */
    private fun gutterTextGap(): Int {
        if (!verticalScrollbarFlipped) {
            return 0
        }
        val gutter = editor.gutterComponentEx
        return gutter.width - gutter.whitespaceSeparatorOffset
    }

    companion object {
        private val LOG = logger<DiffCommentThreadHost>()
    }
}

/**
 * Removes the platform 42 em width cap that [com.intellij.collaboration.ui.codereview.editor.insertComponentAfter]
 * applies via [SizeRestrictedSingleComponentLayout]. Returns whether the wrapper was recognized and updated.
 */
internal fun liftInlayWrapperWidthCap(wrapper: Container): Boolean {
    if (wrapper !is JPanel) {
        return false
    }
    val layout = wrapper.layout
    if (layout !is SizeRestrictedSingleComponentLayout) {
        return false
    }
    layout.prefSize = DimensionRestrictions.None
    layout.maxSize = DimensionRestrictions.None
    return true
}

/** Converts Compose layout size (physical pixels) to Swing preferred size (logical pixels) for the given UI scale. */
internal fun composePixelsToSwingPixels(pixels: Int, scale: Float): Int {
    if (scale <= 0f) {
        return pixels
    }
    return ceil(pixels / scale).toInt()
}

/**
 * The width the inline comment thread is measured at, ported from the platform `EditorTextWidthWatcher` math:
 * the editor viewport width minus the vertical scrollbar allowance minus the gutter text gap (deducted only when the
 * scrollbar is flipped). Unlike the platform, bbcloud does **not** clamp the result to a 42 em cap; the thread spans
 * the full editor text width (clarification 1b). Narrow editors clamp to `0` only (clarification 9a); a `0` result
 * means the editor is not laid out yet and the host defers the remeasure to a later viewport event.
 */
internal fun editorInlayContentWidth(viewportWidth: Int, verticalScrollbarWidth: Int, gutterTextGap: Int): Int {
    val visibleEditorTextWidth = viewportWidth - verticalScrollbarWidth - gutterTextGap
    return max(visibleEditorTextWidth, 0)
}

/**
 * Preferred size for a width-only Compose remeasure. Never pins height to zero before Compose has laid out; keeps an
 * existing positive height when widening the inlay.
 */
internal fun preferredComposeDimensionForWidthUpdate(width: Int, existingPreferredHeight: Int): Dimension {
    val height = if (existingPreferredHeight > 0) {
        existingPreferredHeight
    } else {
        UNSPECIFIED_DIMENSION_VALUE
    }
    return Dimension(width, height)
}

/**
 * Returns the height from [resolve], or the height from [fallback] when [resolve] yields no value. An
 * [IllegalStateException] thrown while Compose sizes its content is reported to [onFailure] and replaced by [fallback],
 * so the EDT layout pass does not fail.
 */
internal fun inlayHeightOrFallback(resolve: () -> Int?, fallback: () -> Int, onFailure: (IllegalStateException) -> Unit): Int {
    return try {
        resolve() ?: fallback()
    } catch (exception: IllegalStateException) {
        onFailure(exception)
        fallback()
    }
}

/** Height reported to the editor inlay when Compose preferred height is not ready yet. */
internal fun resolveComposePanelHeightForInlay(panel: ComposePanel?): Int? {
    if (panel == null) {
        return null
    }
    val preferredHeight = panel.preferredSize?.height ?: UNSPECIFIED_DIMENSION_VALUE
    if (preferredHeight > 0) {
        return preferredHeight
    }
    if (panel.height > 0) {
        return panel.height
    }
    if (panel.size.height > 0) {
        return panel.size.height
    }
    val minimumHeight = panel.minimumSize?.height ?: 0
    if (minimumHeight > 0) {
        return minimumHeight
    }
    return null
}