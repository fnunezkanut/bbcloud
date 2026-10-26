@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** The avatar size of a reviewer row, shared by the Overview tab and the new pull request form. */
internal val REVIEWER_IDENTITY_AVATAR_SIZE = 20.dp

/**
 * One reviewer row: an optional leading status icon slot, a 20dp circular avatar, the display name, and an optional
 * remove affordance. It is the visual building block shared by the Overview tab's reviewer card and the new pull
 * request form's reviewer list, so the two screens render reviewers identically.
 */
@Composable
internal fun ReviewerIdentityRow(
    displayName: String,
    avatar: ImageBitmap,
    statusIcon: (@Composable () -> Unit)?,
    isRemovable: Boolean,
    isRemoveEnabled: Boolean,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (statusIcon != null) {
            statusIcon()
        }
        Image(
            bitmap = avatar,
            contentDescription = "Reviewer avatar",
            modifier = Modifier.size(REVIEWER_IDENTITY_AVATAR_SIZE),
            filterQuality = FilterQuality.High
        )
        Text(text = displayName, modifier = Modifier.weight(1f))
        if (isRemovable) {
            Tooltip(tooltip = { Text(text = BbCloudBundle.message("main.branch.reviewers.remove", displayName)) }) {
                val modifier = if (isRemoveEnabled) {
                    Modifier.clickable(onClick = onRemove).pointerHoverIcon(PointerIcon.Hand)
                } else {
                    Modifier
                }
                Icon(key = AllIconsKeys.Actions.Close, contentDescription = "Remove reviewer", modifier = modifier)
            }
        }
    }
}