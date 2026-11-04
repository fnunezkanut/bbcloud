@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.MentionCandidateTier
import com.github.fnunezkanut.bbcloud.ui.otherprojects.toComposeImageBitmap
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import javax.swing.Icon as SwingIcon

/** The size of the avatar shown for each mention candidate. */
private val MENTION_AVATAR_SIZE = 20.dp

/** The stateless suggestion list rendered inside the mention popup. */
@Composable
internal fun CommentMentionSuggestionList(
    state: CommentMentionSuggestionsUiState,
    avatarRevision: Int,
    avatarIconFor: (String?) -> SwingIcon,
    onPick: (MentionCandidate) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        var previousTier: MentionCandidateTier? = null
        state.items.forEachIndexed { index, candidate ->
            val isFirstOfTier = candidate.tier != previousTier
            previousTier = candidate.tier
            MentionRow(
                candidate = candidate,
                isSelected = index == state.selectedIndex,
                isFirstOfTier = isFirstOfTier,
                avatarRevision = avatarRevision,
                avatarIconFor = avatarIconFor,
                onPick = onPick
            )
        }
        MentionStatusRow(state = state)
    }
}

@Composable
private fun MentionRow(
    candidate: MentionCandidate,
    isSelected: Boolean,
    isFirstOfTier: Boolean,
    avatarRevision: Int,
    avatarIconFor: (String?) -> SwingIcon,
    onPick: (MentionCandidate) -> Unit
) {
    val bitmap = remember(candidate.avatarUrl, avatarRevision) {
        avatarIconFor(candidate.avatarUrl).toComposeImageBitmap()
    }
    val background = if (isSelected) selectionBackground() else Color.Unspecified
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = background)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(candidate) {
                detectTapGestures(onPress = { onPick(candidate) })
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(MENTION_AVATAR_SIZE))
        Text(text = candidate.displayName)
        val nickname = candidate.nickname
        if (nickname != null && nickname != candidate.displayName) {
            Text(text = nickname, color = JewelTheme.globalColors.text.info)
        }
        if (isFirstOfTier) {
            Text(text = tierLabel(tier = candidate.tier), color = JewelTheme.globalColors.text.info)
        }
    }
}

@Composable
private fun MentionStatusRow(state: CommentMentionSuggestionsUiState) {
    val text = when {
        state.isLoadingWorkspace -> BbCloudBundle.message("main.branch.comments.mentions.searching")
        state.items.isEmpty() -> BbCloudBundle.message("main.branch.comments.mentions.noMatches")
        state.workspaceLoadFailed -> BbCloudBundle.message("main.branch.comments.mentions.loadFailed")
        state.isWorkspaceTruncated -> BbCloudBundle.message("main.branch.comments.mentions.truncated")
        else -> null
    } ?: return
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.isLoadingWorkspace) {
                Icon(key = AllIconsKeys.Process.Step_1, contentDescription = null)
            }
            Text(text = text, color = JewelTheme.globalColors.text.info)
        }
    }
}

private fun tierLabel(tier: MentionCandidateTier): String {
    val key = when (tier) {
        MentionCandidateTier.PullRequest -> "main.branch.comments.mentions.tier.pullRequest"
        MentionCandidateTier.RecentlySeen -> "main.branch.comments.mentions.tier.recentlySeen"
        MentionCandidateTier.Workspace -> "main.branch.comments.mentions.tier.workspace"
    }
    return BbCloudBundle.message(key)
}

/** The theme-aware selection background, following the established JBColor-to-Compose idiom. */
private fun selectionBackground(): Color {
    return Color(JBUI.CurrentTheme.List.Selection.background(true).rgb)
}