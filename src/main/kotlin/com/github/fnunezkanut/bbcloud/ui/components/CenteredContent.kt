@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Places [content] in the middle of the available space, both vertically and horizontally.
 *
 * This is the Compose counterpart of the Swing `GridBagConstraints` wrapper the panels used before: filling the whole
 * container is what makes the cell claim all the space, and the center alignment keeps the child itself at its natural
 * size inside that cell. Borders are left to the caller.
 */
@Composable
internal fun CenteredContent(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
        content = { content() }
    )
}