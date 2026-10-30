package com.github.fnunezkanut.bbcloud.ui.otherprojects

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.intellij.openapi.diagnostic.logger

/**
 * The immutable state of the workspace header of one Workspace tab root: the workspace indicator, the open-PR count
 * pill, and the refresh action.
 *
 * A null [workspaceUrl] hides the green tick icon and the workspace link, and a null [openCountText] hides the count
 * pill. [workspaceName] is the link text shown after the tick (the workspace slug); it falls back to the last path
 * segment of [workspaceUrl] when null.
 */
data class WorkspaceHeaderState(
    val workspaceUrl: String? = null,
    val workspaceName: String? = null,
    val openCountText: String? = null,
    val isRefreshVisible: Boolean = false,
    val isRefreshEnabled: Boolean = true
)

/**
 * Owns the workspace header for one Workspace tab root: the green tick indicator, the open-PR count pill, and the
 * refresh button. Two
 * instances exist in [BbCloudOtherProjectsPanel], one for the My PRs root and one for the Assigned PRs root, so each tab
 * has its own header and its own refresh button. All call the same coordinator function when the user clicks refresh.
 */
class WorkspaceHeaderViewModel(private val onRefreshRequested: () -> Unit) {
    val state: MutableState<WorkspaceHeaderState> = mutableStateOf(WorkspaceHeaderState())

    fun setWorkspaceUrl(url: String?, name: String? = null) {
        state.value = state.value.copy(workspaceUrl = url, workspaceName = name)
    }

    /** Sets the open pull request count shown in the header pill, or hides it when not [visible]. */
    fun setOpenCount(text: String?, visible: Boolean) {
        state.value = state.value.copy(openCountText = if (visible) text else null)
    }

    fun setRefreshVisible(visible: Boolean) {
        state.value = state.value.copy(isRefreshVisible = visible)
    }

    fun setRefreshEnabled(enabled: Boolean) {
        state.value = state.value.copy(isRefreshEnabled = enabled)
    }

    /** Runs the refresh action the header offers. */
    fun onRefreshClicked() {
        LOG.info("User requested a reload of the Workspace tabs")
        onRefreshRequested()
    }

    companion object {
        private val LOG = logger<WorkspaceHeaderViewModel>()
    }
}