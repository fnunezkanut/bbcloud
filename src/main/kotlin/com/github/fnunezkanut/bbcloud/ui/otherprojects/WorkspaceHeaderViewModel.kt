package com.github.fnunezkanut.bbcloud.ui.otherprojects

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.intellij.openapi.diagnostic.logger

/**
 * The immutable state of the workspace header of one Workspace tab root: the workspace link and the refresh action.
 *
 * A null [workspaceUrl] hides the link.
 */
data class WorkspaceHeaderState(val workspaceUrl: String? = null, val isRefreshVisible: Boolean = false, val isRefreshEnabled: Boolean = true)

/**
 * Owns the workspace header for one Workspace tab root: the workspace prefix and link, and the refresh button. Two
 * instances exist in [BbCloudOtherProjectsPanel], one for the My PRs root and one for the Assigned PRs root, so each tab
 * has its own header and its own refresh button. All call the same coordinator function when the user clicks refresh.
 */
class WorkspaceHeaderViewModel(private val onRefreshRequested: () -> Unit) {
    val state: MutableState<WorkspaceHeaderState> = mutableStateOf(WorkspaceHeaderState())

    fun setWorkspaceUrl(url: String?) {
        state.value = state.value.copy(workspaceUrl = url)
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