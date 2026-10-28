@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.jewel.ui.component.Text

/**
 * Plain text the user can select and copy, such as a task content or a commit hash. Use it only for user-authored or
 * copyable values. Do not wrap text inside buttons, tabs, badges or tooltips, because the selection handling would
 * consume the clicks meant for those controls.
 */
@Composable
internal fun SelectableText(text: String, color: Color = Color.Unspecified, fontFamily: FontFamily? = null) {
    SelectionContainer {
        Text(text = text, color = color, fontFamily = fontFamily)
    }
}