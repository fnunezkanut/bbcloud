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
import com.github.fnunezkanut.bbcloud.settings.PullRequestAuthorScope
import com.github.fnunezkanut.bbcloud.settings.PullRequestListFilter
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.ListComboBox

/**
 * The Exclude checkboxes and the "Created by" dropdown, emitted inline into the header row of a merged tab.
 *
 * Layout, left to right: Exclude Resolved (Current Project only, because [PullRequestsViewModel.scope] offers it),
 * Exclude Approved by me, Exclude Drafts, then the Created by dropdown. The dropdown offers Created by me only once
 * the current user is known ([isCurrentUserKnown]); until then it lists just Created by any user.
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
        CreatedByComboBox(
            authorScope = filter.authorScope,
            isCurrentUserKnown = isCurrentUserKnown,
            onAuthorScopeChanged = viewModel::onAuthorScopeChanged
        )
    }
}

/**
 * The "Created by" dropdown. Its items are Created by any user, plus Created by me when [isCurrentUserKnown] is true.
 * The Jewel combo box cannot disable a single item, so the Created by me item is left out while the current user is
 * unknown. A saved [PullRequestAuthorScope.Me] therefore shows as Created by any user until the uuid arrives; the
 * saved value itself is not changed.
 */
@Composable
private fun CreatedByComboBox(authorScope: PullRequestAuthorScope, isCurrentUserKnown: Boolean, onAuthorScopeChanged: (PullRequestAuthorScope) -> Unit) {
    val anyUserText = BbCloudBundle.message("pullRequests.createdBy.anyUser")
    val options = if (isCurrentUserKnown) {
        listOf(anyUserText, BbCloudBundle.message("pullRequests.createdBy.me"))
    } else {
        listOf(anyUserText)
    }
    val selectedIndex = if (isCurrentUserKnown && authorScope == PullRequestAuthorScope.Me) 1 else 0
    ListComboBox(
        items = options,
        selectedIndex = selectedIndex,
        onSelectedItemChange = { index ->
            onAuthorScopeChanged(if (index == 1) PullRequestAuthorScope.Me else PullRequestAuthorScope.AnyUser)
        },
        modifier = Modifier.width(200.dp)
    )
}