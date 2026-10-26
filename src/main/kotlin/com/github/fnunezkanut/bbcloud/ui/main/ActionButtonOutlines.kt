package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.bridge.retrieveColor

/**
 * The outline colours of the action buttons of the plugin.
 *
 * They are looked up by name in the current LaF, so a theme can override them; the values below are the fallbacks the
 * plugin has always used.
 */
object ActionButtonOutlines {
    fun red(): Color {
        return retrieveColor(key = "Red", default = Color(0xFFDB3737))
    }

    fun orange(): Color {
        return retrieveColor(key = "Orange", default = Color(0xFFFA8C16))
    }

    fun blue(): Color {
        return retrieveColor(key = "Blue", default = Color(0xFF3574F0))
    }

    fun green(): Color {
        return retrieveColor(key = "Green", default = Color(0xFF59A869))
    }
}