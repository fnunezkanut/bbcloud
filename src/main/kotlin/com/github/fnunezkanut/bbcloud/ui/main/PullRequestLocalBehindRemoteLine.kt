@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text

/** The vertical separation the local-behind-remote warning keeps from the views above and below it. */
private val LOCAL_BEHIND_REMOTE_VERTICAL_PADDING = 8.dp

/**
 * The red warning that the local source and/or destination branch is behind Bitbucket, with one link that updates
 * every branch that is behind.
 *
 * It mirrors [PullRequestCommitsBehindLine]: the content is centered in the width it is given, so it reads the same
 * wherever the warning sits. The error text color follows the IDE theme through [MainPanelStatusSupport.errorForeground].
 * The update link is disabled while an update or a page reload is in flight.
 */
@Composable
internal fun PullRequestLocalBehindRemoteLine(viewModel: PullRequestHeaderViewModel, onUpdateLocalBranch: () -> Unit) {
    val state by viewModel.state
    val localBehindRemote = state.localBehindRemote ?: return
    val errorColor = MainPanelStatusSupport.errorForeground()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = LOCAL_BEHIND_REMOTE_VERTICAL_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = localBehindRemote.title,
            color = errorColor,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        localBehindRemote.lines.forEach { line ->
            Text(text = "• ${line.text}", color = errorColor, textAlign = TextAlign.Center)
        }
        Text(text = localBehindRemote.explanation, color = errorColor, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.Center) {
            Link(
                text = localBehindRemote.updateLinkText,
                onClick = onUpdateLocalBranch,
                enabled = !localBehindRemote.updateInProgress
            )
        }
    }
}