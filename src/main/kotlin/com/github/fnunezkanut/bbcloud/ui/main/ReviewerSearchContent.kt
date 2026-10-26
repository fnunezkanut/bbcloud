@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerCandidate
import com.github.fnunezkanut.bbcloud.ui.otherprojects.toComposeImageBitmap
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** The size of the avatar of a candidate. */
private val REVIEWER_AVATAR_SIZE = 20.dp

/**
 * The content of the reviewer search: the search field, the list of matching candidates, and the status message under
 * the list.
 */
@Composable
internal fun ReviewerSearchContent(viewModel: ReviewerSearchViewModel) {
    val state by viewModel.state
    Column(modifier = Modifier.fillMaxSize()) {
        ReviewerSearchField(
            query = state.query,
            onQueryChanged = viewModel::onQueryChanged,
            onSubmit = { viewModel.selectFirstCandidate() }
        )
        if (state.candidates.isNotEmpty()) {
            VerticallyScrollableContainer(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    state.candidates.forEach { candidate ->
                        ReviewerCandidateRow(
                            candidate = candidate,
                            isDefaultReviewer = candidate.uuid in state.defaultReviewerUuids,
                            avatarRevision = state.avatarRevision,
                            avatarIconFor = viewModel.avatarCache::iconFor,
                            onClick = { viewModel.onCandidateSelected(candidate, source = "click") }
                        )
                    }
                }
            }
        }
        ReviewerSearchStatus(isLoading = state.isLoading, hasError = state.hasError, message = state.statusMessage)
    }
}

/** The search field of the reviewer search. */
@Composable
private fun ReviewerSearchField(query: String, onQueryChanged: (String) -> Unit, onSubmit: () -> Unit) {
    // Hold the TextFieldValue locally so the caret advances with typing; resync from the incoming query only when
    // the backing text actually differs (for example when the view model clears the search), so an external update
    // never snaps the caret back to the start mid-edit.
    var fieldValue by remember { mutableStateOf(TextFieldValue(query)) }
    LaunchedEffect(query) {
        if (fieldValue.text != query) {
            fieldValue = TextFieldValue(query)
        }
    }
    TextField(
        value = fieldValue,
        onValueChange = { updated ->
            fieldValue = updated
            onQueryChanged(updated.text)
        },
        modifier = Modifier.fillMaxWidth().padding(4.dp),
        placeholder = { Text(text = BbCloudBundle.message("main.branch.reviewers.search.placeholder")) },
        leadingIcon = { Icon(key = AllIconsKeys.Actions.Search, contentDescription = null) },
        keyboardActions = KeyboardActions(onDone = { onSubmit() })
    )
}

/** One candidate of the reviewer search: its avatar, its name, and the default-reviewer marker. */
@Composable
private fun ReviewerCandidateRow(
    candidate: ReviewerCandidate,
    isDefaultReviewer: Boolean,
    avatarRevision: Int,
    avatarIconFor: (String?) -> javax.swing.Icon,
    onClick: () -> Unit
) {
    val bitmap = remember(candidate.avatarUrl, avatarRevision) {
        avatarIconFor(candidate.avatarUrl).toComposeImageBitmap()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(REVIEWER_AVATAR_SIZE))
        Text(text = candidate.displayName)
        if (isDefaultReviewer) {
            Text(text = BbCloudBundle.message("main.branch.reviewers.search.defaultReviewer"))
        }
    }
}

/** The status message under the list of candidates. */
@Composable
private fun ReviewerSearchStatus(isLoading: Boolean, hasError: Boolean, message: String?) {
    val text = when {
        isLoading -> BbCloudBundle.message("main.branch.reviewers.search.loading")
        hasError -> BbCloudBundle.message("main.branch.reviewers.search.error")
        else -> message
    }
    if (text == null) {
        return
    }
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isLoading) {
                Icon(key = AllIconsKeys.Process.Step_1, contentDescription = null)
            }
            Text(text = text)
        }
    }
}