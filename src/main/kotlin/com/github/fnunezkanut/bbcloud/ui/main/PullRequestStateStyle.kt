package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.ui.graphics.Color
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.intellij.ui.JBColor
import org.jetbrains.jewel.bridge.toComposeColor

/**
 * The colours and labels of the pull-request states.
 *
 * The colours are read from the current theme on every call, so a theme switch is picked up by the next
 * recomposition.
 */
object PullRequestStateStyle {
    /** The colour of a draft pull request, which has no state of its own. */
    fun draftColor(): Color {
        return JBColor(0xE56D17, 0xE08855).toComposeColor()
    }

    fun color(state: PullRequestState): Color {
        return when (state) {
            PullRequestState.Open -> JBColor(0x3574F0, 0x548AF7).toComposeColor()
            PullRequestState.Merged -> JBColor(0x208A3C, 0x5FB865).toComposeColor()
            PullRequestState.Declined -> JBColor(0xDB3B4B, 0xDB5C5C).toComposeColor()
        }
    }

    fun label(state: PullRequestState): String {
        return when (state) {
            PullRequestState.Open -> BbCloudBundle.message("main.pullRequests.state.open")
            PullRequestState.Merged -> BbCloudBundle.message("main.pullRequests.state.merged")
            PullRequestState.Declined -> BbCloudBundle.message("main.pullRequests.state.declined")
        }
    }
}