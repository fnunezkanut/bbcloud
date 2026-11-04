package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Rect
import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidate
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.compose
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener
import kotlin.math.ceil
import kotlin.math.roundToInt

/** The fixed width of the mention popup, in logical pixels. */
private const val POPUP_WIDTH = 320

/** The height of one suggestion row, in logical pixels. */
private const val ROW_HEIGHT = 28

/**
 * A plugin-owned, non-focusable [JBPopup] with Compose content, anchored under the `@` caret. It is non-focusable so
 * typing keeps going to the text area; rows pick on pointer press (see [CommentMentionSuggestionList]). Editor inlays
 * host a bare ComposePanel whose Jewel wrapper is not showing, so the owner is supplied explicitly.
 */
internal class CommentMentionPopup(
    private val owner: Component,
    suggestions: CommentMentionSuggestions,
    avatarCache: AuthorAvatarCache,
    avatarRevision: State<Int>,
    onPick: (MentionCandidate) -> Unit,
    private val onClosedExternally: () -> Unit
) {
    private var currentHeight = ROW_HEIGHT

    private var shown = false

    private var disposed = false

    private val content = object : JPanel(BorderLayout()) {
        override fun getPreferredSize(): Dimension {
            return JBUI.size(POPUP_WIDTH, currentHeight)
        }
    }.apply {
        isOpaque = false
        add(
            compose(config = { isFocusable = false }) {
                CommentMentionSuggestionList(
                    state = suggestions.state.value,
                    avatarRevision = avatarRevision.value,
                    avatarIconFor = avatarCache::iconFor,
                    onPick = onPick
                )
            },
            BorderLayout.CENTER
        )
    }

    private val popup: JBPopup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(content, null)
        .setFocusable(false)
        .setRequestFocus(false)
        .setCancelOnClickOutside(true)
        .setCancelOnWindowDeactivation(true)
        .setCancelKeyEnabled(false)
        .setResizable(false)
        .setMovable(false)
        .setShowShadow(true)
        .addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                onClosedExternally()
            }
        })
        .createPopup()

    private val ancestorListener = object : AncestorListener {
        override fun ancestorAdded(event: AncestorEvent) = Unit

        override fun ancestorMoved(event: AncestorEvent) {
            close()
        }

        override fun ancestorRemoved(event: AncestorEvent) {
            close()
        }
    }

    /** Recomputes the popup height from the row count and relays it to the popup when it changed. */
    fun update(rowCount: Int, hasStatusRow: Boolean) {
        val height = mentionPopupHeight(rowCount = rowCount, hasStatusRow = hasStatusRow)
        if (height == currentHeight) {
            return
        }
        currentHeight = height
        if (shown && !disposed) {
            popup.size = JBUI.size(POPUP_WIDTH, currentHeight)
        }
    }

    /** Shows the popup at [point], or moves it there on later calls. */
    fun showAt(point: RelativePoint) {
        if (disposed) {
            return
        }
        if (!shown) {
            shown = true
            (owner as? JComponent)?.addAncestorListener(ancestorListener)
            LOG.debug("Showing the mention suggestion popup")
            popup.show(point)
        } else {
            popup.setLocation(point.screenPoint)
        }
    }

    /** Cancels the popup when it has not been disposed. */
    fun close() {
        if (disposed) {
            return
        }
        disposed = true
        (owner as? JComponent)?.removeAncestorListener(ancestorListener)
        LOG.debug("Closing the mention suggestion popup")
        popup.cancel()
    }

    companion object {
        private val LOG = logger<CommentMentionPopup>()
    }
}

/**
 * The popup height for [rowCount] suggestion rows plus the status row when [hasStatusRow], capped at
 * [MentionCandidateRanker.MAX_SUGGESTIONS] rows, in logical pixels.
 */
internal fun mentionPopupHeight(rowCount: Int, hasStatusRow: Boolean): Int {
    val cappedRows = rowCount.coerceAtMost(MentionCandidateRanker.MAX_SUGGESTIONS)
    val statusRows = if (hasStatusRow) 1 else 0
    val totalRows = (cappedRows + statusRows).coerceAtLeast(1)
    return totalRows * ROW_HEIGHT
}

/**
 * Where the suggestion popup goes, in Swing logical pixels relative to the popup owner: under the `@` caret line,
 * clamped vertically to the text area so a wrong caret offset never places it far away.
 */
internal fun mentionPopupLocation(
    textAreaBoundsInRoot: Rect,
    cursorRect: Rect?,
    contentPaddingLeftPx: Float,
    contentPaddingTopPx: Float,
    scrollOffsetPx: Float,
    density: Float
): Point {
    val safeDensity = if (density <= 0f) 1f else density
    val composeX = textAreaBoundsInRoot.left + contentPaddingLeftPx + (cursorRect?.left ?: 0f)
    val cursorBottom = cursorRect?.bottom ?: textAreaBoundsInRoot.height
    val composeYRaw = textAreaBoundsInRoot.top + contentPaddingTopPx + cursorBottom - scrollOffsetPx
    val composeY = composeYRaw.coerceIn(textAreaBoundsInRoot.top, textAreaBoundsInRoot.bottom)
    val x = (composeX / safeDensity).roundToInt()
    val y = ceil(composeY / safeDensity).roundToInt()
    return Point(x, y)
}