@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * The shared Jewel card chrome: a 1dp border with rounded corners and 8dp of padding around an optional bold title.
 * It is the Jewel equivalent of the Swing `TitledCard`, and it is shared by the Overview and the Changes tabs.
 */
@Composable
internal fun TitledCardBox(title: String? = null, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = JewelTheme.globalColors.borders.normal, shape = RoundedCornerShape(2.dp))
            .padding(8.dp)
    ) {
        if (title != null) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        content()
    }
}