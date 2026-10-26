package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.SolidColor
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.BadgeColors
import org.jetbrains.jewel.ui.component.styling.BadgeStyle
import org.jetbrains.jewel.ui.theme.badgeStyle

object PullRequestBadgeStyles {
    @Composable
    fun draft(): BadgeStyle {
        return JewelTheme.badgeStyle.purpleSecondary
    }

    @Composable
    fun merged(): BadgeStyle {
        return JewelTheme.badgeStyle.greenSecondary
    }

    @Composable
    fun declined(): BadgeStyle {
        val baseStyle = JewelTheme.badgeStyle.greenSecondary
        val declinedColor = PullRequestStateStyle.color(PullRequestState.Declined)
        val declinedBackground = SolidColor(declinedColor.copy(alpha = 0.16f))
        return BadgeStyle(
            colors = BadgeColors(
                background = declinedBackground,
                backgroundDisabled = baseStyle.colors.backgroundDisabled,
                backgroundFocused = declinedBackground,
                backgroundPressed = declinedBackground,
                backgroundHovered = declinedBackground,
                content = declinedColor,
                contentDisabled = baseStyle.colors.contentDisabled,
                contentFocused = declinedColor,
                contentPressed = declinedColor,
                contentHovered = declinedColor
            ),
            metrics = baseStyle.metrics
        )
    }

    /**
     * The state pill of the activity feed, tinted with the pull-request state color so it matches the state badges of
     * the information card.
     */
    @Composable
    fun state(state: PullRequestState): BadgeStyle {
        val baseStyle = JewelTheme.badgeStyle.blueSecondary
        val stateColor = PullRequestStateStyle.color(state)
        val stateBackground = SolidColor(stateColor.copy(alpha = 0.16f))
        return BadgeStyle(
            colors = BadgeColors(
                background = stateBackground,
                backgroundDisabled = baseStyle.colors.backgroundDisabled,
                backgroundFocused = stateBackground,
                backgroundPressed = stateBackground,
                backgroundHovered = stateBackground,
                content = stateColor,
                contentDisabled = baseStyle.colors.contentDisabled,
                contentFocused = stateColor,
                contentPressed = stateColor,
                contentHovered = stateColor
            ),
            metrics = baseStyle.metrics
        )
    }

    fun stateBadgeKind(state: PullRequestState, isDraft: Boolean): StateBadgeKind {
        if (isDraft && state == PullRequestState.Open) {
            return StateBadgeKind.None
        }
        return when (state) {
            PullRequestState.Open -> StateBadgeKind.None
            PullRequestState.Merged -> StateBadgeKind.Merged
            PullRequestState.Declined -> StateBadgeKind.Declined
        }
    }

    enum class StateBadgeKind {
        None,
        Merged,
        Declined
    }
}