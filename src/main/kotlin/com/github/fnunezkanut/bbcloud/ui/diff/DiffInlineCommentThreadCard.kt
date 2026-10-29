@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.github.fnunezkanut.bbcloud.ui.diff

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry
import com.github.fnunezkanut.bbcloud.ui.main.ConversationThreadLayout
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestCommentThreadCard

/** Inline diff thread card with Conversation spacing; reports its natural height so the inlay can grow and shrink. */
@Composable
internal fun DiffInlineCommentThreadCard(thread: TimelineEntry.CommentThread, host: DiffCommentThreadHost, controller: EditorCommentThreadController) {
    val density = LocalDensity.current.density
    PullRequestCommentThreadCard(
        thread = thread,
        host = controller,
        // The size must be measured unbounded and outside the padding: a size measured inside the pinned inlay height
        // can never grow, and one that excludes the padding shrinks the inlay on every layout pass.
        modifier = Modifier
            .wrapContentHeight(align = Alignment.Top, unbounded = true)
            .onSizeChanged { size ->
                host.syncComposeContentHeight(contentHeightPx = size.height, density = density)
            }
            .padding(bottom = ConversationThreadLayout.verticalGapBetweenCards)
    )
}