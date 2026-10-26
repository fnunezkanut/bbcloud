package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * The immutable state of the project status row of one Project tab root: the status icon and message, the "Project:"
 * prefix and repository link, the open pull request count, and the refresh action.
 *
 * A null [statusMessage] hides the message, a null [repositoryWebUrl] hides the prefix and the link, and a null
 * [openCountText] hides the count.
 */
data class ProjectStatusHeaderState(
    val statusIcon: IconKey? = null,
    val statusMessage: String? = null,
    val repositoryWebUrl: String? = null,
    val openCountText: String? = null,
    val isRefreshVisible: Boolean = false,
    val isRefreshEnabled: Boolean = true,
    /** Whether the "Create new PR" button is shown; it shares the refresh button's lifecycle (visible once Found). */
    val isCreatePullRequestVisible: Boolean = false
)

/**
 * Owns the project status row for one Project tab root: the status icon and message, the "Project:" prefix and
 * repository link, the open pull request count, and the refresh button. Two instances exist in [BbCloudMainPanel], one
 * for the Open PRs root and one for the Completed PRs root, so each tab has its own header and its own refresh button.
 * The open pull request count is shown only while the Open PRs listing is visible; it is hidden on every other card.
 * The header also offers a "Create new PR" button next to refresh, which shares the refresh button's lifecycle.
 */
class ProjectStatusHeaderViewModel(private val onRefreshRequested: () -> Unit, private val onCreatePullRequestRequested: () -> Unit = {}) {
    val state: MutableState<ProjectStatusHeaderState> = mutableStateOf(ProjectStatusHeaderState())

    /** Clears the status row so the next status starts from a blank row. */
    fun reset() {
        state.value = ProjectStatusHeaderState(statusMessage = "")
    }

    /**
     * Sets the status icon and message in the header, hiding the "Project:" prefix and the repository link. Used by
     * statuses that leave the listing in place, so the header shows what went wrong.
     */
    fun showStatus(icon: IconKey?, message: String) {
        state.value = state.value.copy(
            statusIcon = icon,
            statusMessage = message,
            repositoryWebUrl = null
        )
    }

    /** Shows the "Checking..." message while the project repository is being resolved. */
    fun showChecking() {
        showStatus(icon = null, message = BbCloudBundle.message("main.checking"))
    }

    /** Shows the resolved repository instead of a status message. */
    fun showProjectFound(repositoryWebUrl: String) {
        state.value = state.value.copy(
            statusIcon = AllIconsKeys.General.InspectionsOK,
            statusMessage = null,
            repositoryWebUrl = repositoryWebUrl
        )
    }

    fun setRefreshVisible(visible: Boolean) {
        state.value = state.value.copy(isRefreshVisible = visible)
    }

    fun setRefreshEnabled(enabled: Boolean) {
        state.value = state.value.copy(isRefreshEnabled = enabled)
    }

    fun setCreatePullRequestVisible(visible: Boolean) {
        state.value = state.value.copy(isCreatePullRequestVisible = visible)
    }

    fun onCreatePullRequestClicked() {
        LOG.info("User clicked Create new PR in the Project PR header")
        onCreatePullRequestRequested()
    }

    /** Sets the open pull request count and whether it is shown. */
    fun setOpenCount(text: String?, visible: Boolean) {
        state.value = state.value.copy(openCountText = if (visible) text else null)
    }

    /** Opens the Bitbucket project repository in the browser. */
    fun onRepositoryLinkClicked(repositoryWebUrl: String) {
        LOG.info("User opened Bitbucket project repository in browser")
        BrowserUtil.browse(repositoryWebUrl)
    }

    /** Runs the refresh action the header offers. */
    fun onRefreshClicked() {
        onRefreshRequested()
    }

    companion object {
        private val LOG = logger<ProjectStatusHeaderViewModel>()
    }
}