package com.github.fnunezkanut.bbcloud.ui

import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.scale.JBUIScale
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException

/** Resolves the UI scale of the connected monitors, so images rendered at the highest scale stay sharp on every monitor. */
internal object ScreenScale {
    /**
     * The highest UI scale of the connected monitors, computed from each device's configuration, which honors the
     * IDE-managed HiDPI mode. Falls back to [JBUIScale.sysScale] when no device reports a usable scale or the screen
     * query fails (for example in headless runs).
     */
    fun maxOfConnectedScreens(): Float {
        return maxScreenScale(
            screenScales = { connectedScreenScales() },
            fallbackScale = { JBUIScale.sysScale() }
        )
    }

    /** Returns the largest finite, positive scale from [screenScales], or the result of [fallbackScale] when none is usable. */
    fun maxScreenScale(screenScales: () -> List<Float>, fallbackScale: () -> Float): Float {
        val usableScale = screenScales().filter { scale -> scale > 0f && scale.isFinite() }.maxOrNull()
        return usableScale ?: fallbackScale()
    }

    private fun connectedScreenScales(): List<Float> {
        return try {
            GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { device ->
                JBUIScale.sysScale(device.defaultConfiguration)
            }
        } catch (exception: HeadlessException) {
            LOG.debug("Screen devices are not available; using the system scale for images", exception)
            emptyList()
        }
    }

    private val LOG = logger<ScreenScale>()
}