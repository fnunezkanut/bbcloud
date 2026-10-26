@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/** The vertical separation the warning line keeps from the views above and below it. */
private val COMMITS_BEHIND_VERTICAL_PADDING = 8.dp

/**
 * The line that tells the pull request is behind its destination branch.
 *
 * The message is centered in the width it is given, so it reads the same wherever the line sits in the view.
 */
@Composable
internal fun PullRequestCommitsBehindLine(viewModel: PullRequestHeaderViewModel) {
    val state by viewModel.state
    val commitsBehind = state.commitsBehind ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = COMMITS_BEHIND_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
    ) {
        Text(text = commitsBehind.text, color = JewelTheme.globalColors.text.warning)
    }
}