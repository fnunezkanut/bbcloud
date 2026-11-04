@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.pullrequests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.settings.PullRequestListFilter
import com.github.fnunezkanut.bbcloud.settings.PullRequestUserScope
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.ListComboBox

/**
 * The Exclude checkboxes and the user scope dropdown, emitted inline into the header row of a merged tab.
 *
 * Layout, left to right: Exclude Resolved (Current Project only, because [PullRequestsViewModel.scope] offers it),
 * Exclude Approved by me, Exclude Drafts, then the user scope dropdown. The dropdown offers Created by me and Assigned
 * to me only once the current user is known ([isCurrentUserKnown]); until then it lists just Created by any user.
 */
@Composable
internal fun RowScope.PullRequestExcludeCheckboxes(filter: PullRequestListFilter, viewModel: PullRequestsViewModel, isCurrentUserKnown: Boolean) {
    // The 10dp start padding separates the group from the left-hand header section (the repo link and the open-count pill).
    Row(
        modifier = Modifier.padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (viewModel.scope.offersResolved) {
            CheckboxRow(
                text = BbCloudBundle.message("pullRequests.exclude.resolved"),
                checked = filter.excludeResolved,
                onCheckedChange = viewModel::onExcludeResolvedChanged
            )
        }
        CheckboxRow(
            text = BbCloudBundle.message("pullRequests.exclude.approvedByMe"),
            checked = filter.excludeApprovedByMe,
            onCheckedChange = viewModel::onExcludeApprovedByMeChanged
        )
        CheckboxRow(
            text = BbCloudBundle.message("pullRequests.exclude.drafts"),
            checked = filter.excludeDrafts,
            onCheckedChange = viewModel::onExcludeDraftsChanged
        )
        UserScopeComboBox(
            userScope = filter.userScope,
            isCurrentUserKnown = isCurrentUserKnown,
            onUserScopeChanged = viewModel::onUserScopeChanged
        )
    }
}

/**
 * The user scope dropdown. Its items are Created by any user, plus Created by me and Assigned to me when
 * [isCurrentUserKnown] is true, in [PullRequestUserScope] declaration order. The Jewel combo box cannot disable a single
 * item, so the two user-dependent items are left out while the current user is unknown. A saved
 * [PullRequestUserScope.Me] or [PullRequestUserScope.AssignedToMe] therefore shows as Created by any user until the uuid
 * arrives; the saved value itself is not changed.
 */
@Composable
private fun UserScopeComboBox(userScope: PullRequestUserScope, isCurrentUserKnown: Boolean, onUserScopeChanged: (PullRequestUserScope) -> Unit) {
    val choices = if (isCurrentUserKnown) PullRequestUserScope.entries.toList() else listOf(PullRequestUserScope.AnyUser)
    val options = choices.map { choice -> BbCloudBundle.message(labelKeyOf(userScope = choice)) }
    val selectedIndex = choices.indexOf(userScope).coerceAtLeast(0)
    ListComboBox(
        items = options,
        selectedIndex = selectedIndex,
        onSelectedItemChange = { index ->
            onUserScopeChanged(choices.getOrElse(index) { PullRequestUserScope.AnyUser })
        },
        modifier = Modifier.width(200.dp)
    )
}

/** The message key of the label shown for [userScope] in the user scope dropdown. */
private fun labelKeyOf(userScope: PullRequestUserScope): String {
    return when (userScope) {
        PullRequestUserScope.AnyUser -> "pullRequests.userScope.anyUser"
        PullRequestUserScope.Me -> "pullRequests.userScope.me"
        PullRequestUserScope.AssignedToMe -> "pullRequests.userScope.assignedToMe"
    }
}