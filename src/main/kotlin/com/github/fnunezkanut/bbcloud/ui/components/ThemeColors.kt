package com.github.fnunezkanut.bbcloud.ui.components

import androidx.compose.ui.graphics.Color
import com.intellij.ui.JBColor
import org.jetbrains.jewel.bridge.retrieveColor
import org.jetbrains.jewel.bridge.toComposeColor

/**
 * The tint the conversation thread strip mixes the panel colour towards [reviewBarPurple] by on a dark theme, so the
 * purple header and footer stay legible against light text instead of flooding the strip with saturated purple.
 */
private const val CONVERSATION_STRIP_DARK_TINT_RATIO = 0.2f

/*
 * The theme-aware colours the converted Swing components used to read from the platform UI defaults.
 *
 * The colours are resolved from the current LaF every time they are read, so a theme switch is picked up on the next
 * recomposition, exactly like the Swing components resolved theirs on every paint.
 */

/** The platform `Label.foreground` colour: the default text colour of a label. */
internal fun labelForeground(): Color {
    return retrieveColor(key = "Label.foreground", default = Color(0xFF000000))
}

/** The platform `Link.activeForeground` colour, used by the selected tab of the pull-request tab strip. */
internal fun linkForeground(): Color {
    return retrieveColor(
        key = "Link.activeForeground",
        default = retrieveColor(key = "link.foreground", default = Color(0xFF589DF6))
    )
}

/** The platform `ActionButton.hoverBackground` colour, used to fill the counters of the pull-request tab strip. */
internal fun actionButtonHoverBackground(): Color {
    return retrieveColor(key = "ActionButton.hoverBackground", default = Color(0xFFDFDFDF))
}

/** The platform `Label.infoForeground` colour, used by the secondary text of the inline diff comment thread. */
internal fun contextHelpForeground(): Color {
    return retrieveColor(key = "Label.infoForeground", default = Color(0xFF787878))
}

/** The platform `Panel.background` colour. */
internal fun panelBackground(): Color {
    return retrieveColor(key = "Panel.background", default = Color(0xFFFFFFFF))
}

/**
 * The platform `Editor.background` colour: the flat editor canvas fill.
 *
 * Non-purple thread and comment surfaces use this colour so editor inlays stay opaque over diff line highlights. The
 * colour is resolved on every call, so a theme switch is picked up on the next recomposition.
 */
internal fun editorBackground(): Color {
    return retrieveColor(key = "Editor.background", default = panelBackground())
}

/**
 * The fill of thread and comment content areas outside the purple location and footer strips.
 *
 * Alias of [editorBackground] so call sites stay readable if the policy changes later.
 */
internal fun commentThreadContentBackground(): Color {
    return editorBackground()
}

/**
 * Mixes [first] towards [second] by [ratio], the way the platform mixes a panel colour towards the label colour to
 * build a tinted card.
 */
internal fun mixColors(first: Color, second: Color, ratio: Float): Color {
    return Color(
        red = first.red + (second.red - first.red) * ratio,
        green = first.green + (second.green - first.green) * ratio,
        blue = first.blue + (second.blue - first.blue) * ratio,
        alpha = first.alpha + (second.alpha - first.alpha) * ratio
    )
}

/**
 * The platform `Label.disabledForeground` colour.
 *
 * Disabled buttons share one theme-aware light gray across the border, the label, and the icon, so the dotted outline
 * stays visible in dark themes where a dimmer colour would wash out.
 */
internal fun labelDisabledForeground(): Color {
    return retrieveColor(key = "Label.disabledForeground", default = Color(0xFF808080))
}

/**
 * The purple of the "Requires review" pill of the editor review bar.
 *
 * The two shades follow the IDE theme: a light lavender for the light themes, where the label of the pill is dark, and
 * a deeper purple for the dark themes, where the label of the pill is light. The colour is resolved through the IDE
 * theme on every call, so a theme switch is picked up on the next recomposition.
 */
internal fun reviewBarPurple(): Color {
    return JBColor(0xFFE3D6F5.toInt(), 0xFF6B4E9E.toInt()).toComposeColor()
}

/**
 * The fill of the purple header and footer strips of an open conversation thread card.
 *
 * On a light theme the strip takes the full lavender of [reviewBarPurple], where the dark strip text reads well. On a
 * dark theme the saturated purple would overwhelm the light strip text, so the strip takes only a tint of the purple
 * mixed into the panel colour. The strip borders stay the full [reviewBarPurple] in both themes. The colours are
 * resolved on every call, so a theme switch is picked up on the next recomposition.
 */
internal fun conversationThreadStripBackground(): Color {
    return if (JBColor.isBright()) {
        reviewBarPurple()
    } else {
        mixColors(first = panelBackground(), second = reviewBarPurple(), ratio = CONVERSATION_STRIP_DARK_TINT_RATIO)
    }
}

/**
 * The fill of the meta header strip of a comment or reply meta block, inside an open conversation thread card.
 *
 * Matches [commentThreadContentBackground] so the author row sits on the same editor canvas fill as the comment body.
 * The colour is resolved on every call, so a theme switch is picked up on the next recomposition.
 */
internal fun conversationMetaStripBackground(): Color {
    return commentThreadContentBackground()
}