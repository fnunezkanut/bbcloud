package com.github.fnunezkanut.bbcloud.ui.components

import androidx.compose.ui.graphics.Color

/*
 * The pure colour and state rules of the outlined action buttons.
 *
 * They are separated from the composable so they can be unit tested without a Compose runtime, and so the button
 * itself stays a thin drawing of what these rules decide.
 */

/** The alpha of the hover fill the outlined action buttons paint behind their label. */
internal const val OUTLINED_ACTION_BUTTON_HOVER_FILL_ALPHA = 0.15f

/**
 * The colour of the outline: the caller's colour while the button is enabled, and the theme-aware disabled colour
 * otherwise. Disabled buttons share one light gray across the border, the label, and the icon so the dotted outline
 * stays visible in dark themes.
 */
internal fun outlinedActionButtonOutlineColor(enabled: Boolean, outlineColor: Color, disabledColor: Color): Color {
    return if (enabled) outlineColor else disabledColor
}

/** The colour of the label and the icon: the ordinary label colour while enabled, the disabled colour otherwise. */
internal fun outlinedActionButtonContentColor(enabled: Boolean, contentColor: Color, disabledColor: Color): Color {
    return if (enabled) contentColor else disabledColor
}

/** The fill painted behind the button while the pointer hovers it; disabled buttons never fill. */
internal fun outlinedActionButtonFillColor(enabled: Boolean, hovered: Boolean, outlineColor: Color): Color {
    return if (enabled && hovered) {
        outlineColor.copy(alpha = OUTLINED_ACTION_BUTTON_HOVER_FILL_ALPHA)
    } else {
        Color.Transparent
    }
}