package com.github.fnunezkanut.bbcloud.ui.setup

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.github.fnunezkanut.bbcloud.BbCloudBuildInfo
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Holds Settings state and coordinates persistence without depending on Swing or Compose UI components.
 *
 * Dialog callbacks are supplied by the hosting UI so the workspace chooser and reset confirmation remain platform
 * dialogs while all save/reset decisions stay testable and independent of the rendered screen.
 */
class BbCloudSettingsViewModel(
    private val configurationService: BbCloudConfigurationService,
    private val coroutineScope: CoroutineScope,
    private val onSaveSuccess: () -> Unit,
    private val onTokenReset: () -> Unit,
    private val onProjectsDirectoryChosenCallback: (String) -> Unit,
    private val chooseWorkspace: (slugs: List<String>, preselectedSlug: String) -> String?,
    private val confirmReset: () -> Boolean
) {
    val state: MutableState<SettingsUiState> = mutableStateOf(
        SettingsUiState(pluginVersionFooter = SetupFooterText.build(resolvePluginVersion()))
    )

    /**
     * Re-reads persisted Settings values whenever the Settings tab is selected.
     *
     * A successful Save re-binds the already-selected Settings tab (see refreshAfterSave in the tool window panel),
     * so the "Valid API Token..." success affordance set by [persist] must survive this re-bind; clearing it here
     * wiped the message before the user could see it. The message is retained and only drops when the user edits the
     * token field (see [setTokenFieldText]) or a later Save fails.
     */
    fun bindSettingsState(hasStoredToken: Boolean) {
        coroutineScope.launch {
            val projectsDirectory = configurationService.projectsDirectoryOrNull()
            state.value = state.value.copy(
                hasStoredToken = hasStoredToken,
                projectsDirectory = projectsDirectory,
                tokenFieldText = "",
                errorMessage = null,
                isBusy = false
            )
        }
    }

    /** Mirrors the token field into the observable screen state. */
    fun setTokenFieldText(text: String) {
        if (state.value.tokenFieldText != text) {
            // The user edited the token, so a prior "Valid API Token..." message is now stale.
            state.value = state.value.copy(tokenFieldText = text, successMessage = null)
        }
    }

    fun onSaveClicked() {
        LOG.info("User clicked setup Save")
        val currentState = state.value
        val rawToken = currentState.tokenFieldText
        val typedToken = rawToken.trim()
        state.value = currentState.copy(
            tokenFieldText = typedToken,
            errorMessage = null,
            successMessage = null
        )
        if (!currentState.hasStoredToken && typedToken.isEmpty()) {
            LOG.info("Setup save aborted: no API token entered and none stored")
            showError(BbCloudBundle.message("setup.error.tokenRequired"))
            return
        }
        setBusy(true)
        coroutineScope.launch {
            // A blank field with the mask showing means "keep the stored token", so the workspace can be switched
            // without retyping it.
            val effectiveToken = typedToken.ifEmpty { configurationService.storedTokenOrNull() }
            if (effectiveToken == null) {
                LOG.info("Setup save aborted: no API token entered and none stored")
                state.value = state.value.copy(
                    isBusy = false,
                    errorMessage = BbCloudBundle.message("setup.error.tokenRequired")
                )
                return@launch
            }
            val resolution = WorkspaceResolutionPolicy.resolve(
                result = configurationService.listWorkspaces(apiToken = effectiveToken),
                preferredSlug = configurationService.workspaceSlugOrNull()
            )
            applyResolution(resolution = resolution, typedToken = typedToken)
        }
    }

    /** Maps a resolve outcome to the matching bundle message, or delegates to the workspace chooser. */
    private fun applyResolution(resolution: WorkspaceResolution, typedToken: String) {
        when (resolution) {
            is WorkspaceResolution.Resolved -> {
                LOG.info(
                    "Setup save auto-selected the only accessible workspace: " +
                        "workspaceSlug=${resolution.slug}, tokenUpdated=${typedToken.isNotEmpty()}"
                )
                coroutineScope.launch {
                    persist(workspaceSlug = resolution.slug, apiTokenInput = typedToken)
                }
            }

            is WorkspaceResolution.NeedsConfirmation -> {
                LOG.debug("Offering ${resolution.slugs.size} Bitbucket workspaces")
                val chosenSlug = chooseWorkspace(resolution.slugs, resolution.preselectedSlug)
                if (chosenSlug == null) {
                    LOG.info("Setup save cancelled at the workspace chooser")
                    setBusy(false)
                    return
                }
                LOG.info("Setup save confirmed: workspaceSlug=$chosenSlug, tokenUpdated=${typedToken.isNotEmpty()}")
                coroutineScope.launch {
                    persist(workspaceSlug = chosenSlug, apiTokenInput = typedToken)
                }
            }

            WorkspaceResolution.NoneAccessible -> {
                LOG.info("Setup save aborted: the token has no accessible workspaces")
                showError(BbCloudBundle.message("setup.workspaces.empty"))
            }

            WorkspaceResolution.Unauthorized -> {
                LOG.info("Setup save aborted: the entered token was rejected by Bitbucket")
                showError(BbCloudBundle.message("setup.workspaces.error.invalidToken"))
            }

            is WorkspaceResolution.Failed -> {
                LOG.warn("Failed to list Bitbucket workspaces", resolution.cause)
                showError(BbCloudBundle.message("setup.workspaces.error.failed"))
            }
        }
    }

    private suspend fun persist(workspaceSlug: String, apiTokenInput: String) {
        // A blank input keeps the stored token, which is what makes the placeholder-only Save path work.
        val result = configurationService.saveSetup(workspaceSlug = workspaceSlug, apiTokenInput = apiTokenInput)
        when {
            result.isSuccess -> {
                LOG.info("Setup save succeeded: workspaceSlug=$workspaceSlug")
                state.value = state.value.copy(
                    hasStoredToken = true,
                    tokenFieldText = "",
                    errorMessage = null,
                    successMessage = BbCloudBundle.message("setup.save.success", workspaceSlug),
                    isBusy = false
                )
                onSaveSuccess()
            }

            result.exceptionOrNull() is BbCloudConfigurationService.SaveException.TokenRequired -> {
                LOG.info("Setup save failed: API token required")
                showError(BbCloudBundle.message("setup.error.tokenRequired"))
            }

            else -> {
                LOG.warn("Failed to save Bitbucket Cloud PR Review setup", result.exceptionOrNull())
                showError(BbCloudBundle.message("setup.error.saveFailed"))
            }
        }
    }

    fun onResetClicked() {
        LOG.info("User clicked setup Reset")
        if (!confirmReset()) {
            return
        }
        setBusy(true)
        // resetAll wipes every plugin setting: the API token, workspace slug, projects directory, and watched projects.
        coroutineScope.launch {
            configurationService.resetAll()
            state.value = state.value.copy(
                hasStoredToken = false,
                projectsDirectory = null,
                tokenFieldText = "",
                errorMessage = null,
                successMessage = null,
                isBusy = false
            )
            LOG.info("Bitbucket Cloud PR Review plugin settings reset")
            onTokenReset()
        }
    }

    /** Persists and displays a directory selected by the platform folder chooser. */
    fun onProjectsDirectoryChosen(path: String) {
        onProjectsDirectoryChosenCallback(path)
        state.value = state.value.copy(projectsDirectory = configurationService.projectsDirectoryOrNull())
    }

    private fun setBusy(isBusy: Boolean) {
        state.value = state.value.copy(isBusy = isBusy)
    }

    private fun showError(message: String) {
        // Error and success are mutually exclusive on the row, so an error replaces any prior success.
        state.value = state.value.copy(errorMessage = message, successMessage = null, isBusy = false)
    }

    private fun resolvePluginVersion(): String? {
        return BbCloudBuildInfo.version() ?: BbCloudBundle.message("about.versionUnknown")
    }

    companion object {
        private val LOG = logger<BbCloudSettingsViewModel>()
    }
}