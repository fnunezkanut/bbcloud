package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import androidx.compose.ui.graphics.Color
import com.intellij.ui.JBColor
import org.jetbrains.jewel.bridge.toComposeColor

/** Theme-aware colors for the pull-request view blue bar under editor tabs. */
internal object WorktreePrViewBlueBarColors {
    fun border(): Color {
        return JBColor(0x99C2FF, 0x4A6FA5).toComposeColor()
    }

    fun background(): Color {
        return JBColor(0xF0F7FF, 0x1E2838).toComposeColor()
    }
}