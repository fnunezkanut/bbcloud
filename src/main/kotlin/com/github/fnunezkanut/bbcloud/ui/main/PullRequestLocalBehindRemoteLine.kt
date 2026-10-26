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
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text

/** The vertical separation the local-behind-remote line keeps from the views above and below it. */
private val LOCAL_BEHIND_REMOTE_VERTICAL_PADDING = 8.dp

/**
 * The red line that tells the local pull-request branch is behind its remote, with a link that fetches and updates the
 * current branch.
 *
 * It mirrors [PullRequestCommitsBehindLine]: the content is centered in the width it is given, so it reads the same
 * wherever the line sits. The error text colour follows the IDE theme through [MainPanelStatusSupport.errorForeground].
 * The update link is disabled while a pull or a page reload is in flight (decision 7a).
 */
@Composable
internal fun PullRequestLocalBehindRemoteLine(viewModel: PullRequestHeaderViewModel, onUpdateLocalBranch: () -> Unit) {
    val state by viewModel.state
    val localBehindRemote = state.localBehindRemote ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = LOCAL_BEHIND_REMOTE_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
    ) {
        Text(text = localBehindRemote.prefix, color = MainPanelStatusSupport.errorForeground())
        Link(
            text = localBehindRemote.updateLinkText,
            onClick = onUpdateLocalBranch,
            enabled = !localBehindRemote.updateInProgress
        )
    }
}