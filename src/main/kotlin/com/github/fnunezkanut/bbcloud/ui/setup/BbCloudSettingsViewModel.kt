package com.github.fnunezkanut.bbcloud.ui.setup

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.github.fnunezkanut.bbcloud.BbCloudBuildInfo
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.auth.BbCloudAuthenticationMethod
import com.github.fnunezkanut.bbcloud.auth.BbCloudOAuthLoginService
import com.github.fnunezkanut.bbcloud.auth.OAuthLoginResult
import com.github.fnunezkanut.bbcloud.auth.OAuthLoginState
import com.github.fnunezkanut.bbcloud.auth.OAuthTokens
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
    private val oauthLoginService: BbCloudOAuthLoginService,
    private val onSaveSuccess: () -> Unit,
    private val onTokenReset: () -> Unit,
    private val onExternalAuthChanged: () -> Unit,
    private val onProjectsDirectoryChosenCallback: (String) -> Unit,
    private val chooseWorkspace: (slugs: List<String>, preselectedSlug: String) -> String?,
    private val confirmReset: () -> Boolean
) {
    val state: MutableState<SettingsUiState> = mutableStateOf(
        SettingsUiState(pluginVersionFooter = SetupFooterText.build(resolvePluginVersion()))
    )

    // Set by the API token dialog while it is open so a successful Save can close it; null otherwise.
    private var apiTokenSaveListener: (() -> Unit)? = null

    // True while this view model owns the active OAuth login, so it does not react to its own finish as "external".
    private var ownsLogin = false

    init {
        // Mirror the application-level login state so every project's Settings tab shows the same progress and can
        // cancel it (decision 13b). A finish or cancel started elsewhere refreshes this tab's status.
        coroutineScope.launch {
            oauthLoginService.state.collect { loginState ->
                mirrorLoginState(loginState = loginState)
            }
        }
    }

    private fun mirrorLoginState(loginState: OAuthLoginState) {
        when (loginState) {
            is OAuthLoginState.InProgress -> {
                state.value = state.value.copy(
                    isOAuthLoginInProgress = true,
                    oauthBrowserFallbackUrl = loginState.browserLaunchFailedUrl
                )
            }
            is OAuthLoginState.Idle -> {
                state.value = state.value.copy(isOAuthLoginInProgress = false, oauthBrowserFallbackUrl = null)
                if (!ownsLogin) {
                    onExternalAuthChanged()
                }
            }
            is OAuthLoginState.Finished -> {
                state.value = state.value.copy(isOAuthLoginInProgress = false, oauthBrowserFallbackUrl = null)
                if (!ownsLogin) {
                    onExternalAuthChanged()
                }
            }
        }
    }

    /**
     * Re-reads persisted Settings values whenever the Settings tab is selected.
     *
     * A successful Save re-binds the already-selected Settings tab (see refreshAfterSave in the tool window panel),
     * so the "Valid API Token..." success affordance set by [persist] must survive this re-bind; clearing it here
     * wiped the message before the user could see it. The message is retained and only drops when the user edits the
     * token field (see [setTokenFieldText]) or a later Save fails. The success message is now shown inside the API
     * token dialog rather than on the main Settings page. The OAuth message is likewise retained across a re-bind.
     */
    fun bindSettingsState(hasStoredToken: Boolean, hasOAuthTokens: Boolean, workingMethod: BbCloudAuthenticationMethod) {
        coroutineScope.launch {
            val projectsDirectory = configurationService.projectsDirectoryOrNull()
            val workspaceSlug = configurationService.workspaceSlugOrNull()
            state.value = state.value.copy(
                hasStoredToken = hasStoredToken,
                hasOAuthTokens = hasOAuthTokens,
                workingMethod = workingMethod,
                projectsDirectory = projectsDirectory,
                workspaceSlug = workspaceSlug,
                tokenFieldText = "",
                errorMessage = null,
                isBusy = false
            )
        }
    }

    /** Registers (or clears with null) the one-shot close hook the API token dialog subscribes to while it is open. */
    fun setApiTokenSaveListener(listener: (() -> Unit)?) {
        apiTokenSaveListener = listener
    }

    /** Resets the transient token/error/success fields so the API token dialog opens clean (decision 9a). */
    fun onApiTokenDialogOpened() {
        LOG.info("User clicked Authenticate with a Bitbucket API Token on Settings")
        state.value = state.value.copy(tokenFieldText = "", errorMessage = null, successMessage = null)
    }

    /** Clears the save listener and any half-typed token so it is not kept in memory after the dialog closes. */
    fun onApiTokenDialogClosed() {
        LOG.debug("API token dialog closed")
        apiTokenSaveListener = null
        state.value = state.value.copy(tokenFieldText = "", errorMessage = null, successMessage = null)
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
            applyResolution(
                resolution = resolution,
                onResolved = { slug -> persist(workspaceSlug = slug, apiTokenInput = typedToken) },
                onError = { message -> showError(message) },
                unauthorizedMessage = BbCloudBundle.message("setup.workspaces.error.invalidToken")
            )
        }
    }

    /**
     * Maps a resolve outcome to the matching bundle message, or delegates to the workspace chooser. [onResolved] runs
     * with the chosen workspace slug; [onError] shows a message; [unauthorizedMessage] is used for an Unauthorized
     * outcome (the OAuth path uses the missing-scopes message, since its token is not saved; decision 18a).
     */
    private suspend fun applyResolution(
        resolution: WorkspaceResolution,
        onResolved: suspend (String) -> Unit,
        onError: (String) -> Unit,
        unauthorizedMessage: String
    ) {
        when (resolution) {
            is WorkspaceResolution.Resolved -> {
                LOG.info("Setup auto-selected the only accessible workspace: workspaceSlug=${resolution.slug}")
                onResolved(resolution.slug)
            }

            is WorkspaceResolution.NeedsConfirmation -> {
                LOG.debug("Offering ${resolution.slugs.size} Bitbucket workspaces")
                val chosenSlug = chooseWorkspace(resolution.slugs, resolution.preselectedSlug)
                if (chosenSlug == null) {
                    LOG.info("Setup cancelled at the workspace chooser")
                    setBusy(false)
                    return
                }
                LOG.info("Setup confirmed: workspaceSlug=$chosenSlug")
                onResolved(chosenSlug)
            }

            WorkspaceResolution.NoneAccessible -> {
                LOG.info("Setup aborted: the token has no accessible workspaces")
                onError(BbCloudBundle.message("setup.workspaces.empty"))
            }

            WorkspaceResolution.Unauthorized -> {
                LOG.info("Setup aborted: the token was rejected by Bitbucket")
                onError(unauthorizedMessage)
            }

            is WorkspaceResolution.Failed -> {
                LOG.warn("Failed to list Bitbucket workspaces", resolution.cause)
                onError(BbCloudBundle.message("setup.workspaces.error.failed"))
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
                    workingMethod = BbCloudAuthenticationMethod.ApiToken,
                    workspaceSlug = workspaceSlug,
                    tokenFieldText = "",
                    errorMessage = null,
                    successMessage = BbCloudBundle.message("setup.save.success", workspaceSlug),
                    isBusy = false
                )
                onSaveSuccess()
                // persist() runs on Dispatchers.Default; the dialog marshals the close to the EDT itself.
                apiTokenSaveListener?.invoke()
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

    /** Starts the OAuth login flow; progress and the result are mirrored from the application login service. */
    fun onOAuthLoginClicked() {
        LOG.info("User clicked Authenticate with Bitbucket oauth login on Settings")
        if (state.value.isOAuthLoginInProgress || state.value.isBusy) {
            LOG.info("Ignoring OAuth login click: a login or save is already in progress")
            return
        }
        state.value = state.value.copy(
            isBusy = true,
            oauthMessage = BbCloudBundle.message("setup.authentication.oauth.waiting"),
            oauthMessageIsError = false
        )
        ownsLogin = true
        val deferred = oauthLoginService.start()
        coroutineScope.launch {
            try {
                when (val result = deferred.await()) {
                    is OAuthLoginResult.Success -> onOAuthLoginSucceeded(tokens = result.tokens)
                    is OAuthLoginResult.PortUnavailable -> showOAuthError(
                        BbCloudBundle.message("setup.authentication.oauth.error.portUnavailable", 42424)
                    )
                    is OAuthLoginResult.BrowserLaunchFailed -> showOAuthError(
                        BbCloudBundle.message("setup.authentication.oauth.error.browser", result.authorizeUrl)
                    )
                    is OAuthLoginResult.TimedOut -> showOAuthError(
                        BbCloudBundle.message("setup.authentication.oauth.error.timedOut")
                    )
                    is OAuthLoginResult.Denied -> showOAuthError(
                        BbCloudBundle.message("setup.authentication.oauth.error.denied")
                    )
                    is OAuthLoginResult.InvalidCallback -> showOAuthError(
                        BbCloudBundle.message("setup.authentication.oauth.error.invalidCallback")
                    )
                    is OAuthLoginResult.ClientRejected -> showOAuthError(
                        BbCloudBundle.message("setup.authentication.oauth.error.clientRejected")
                    )
                    is OAuthLoginResult.ExchangeFailed -> showOAuthError(
                        BbCloudBundle.message("setup.authentication.oauth.error.exchangeFailed")
                    )
                }
            } finally {
                oauthLoginService.acknowledge()
                ownsLogin = false
                setBusy(false)
            }
        }
    }

    private suspend fun onOAuthLoginSucceeded(tokens: OAuthTokens) {
        val resolution = WorkspaceResolutionPolicy.resolve(
            result = configurationService.listWorkspaces(apiToken = tokens.accessToken),
            preferredSlug = configurationService.workspaceSlugOrNull()
        )
        applyResolution(
            resolution = resolution,
            onResolved = { slug -> persistOAuth(workspaceSlug = slug, tokens = tokens) },
            onError = { message -> showOAuthError(message) },
            // The OAuth login succeeded but the consumer lacks the scopes to list workspaces; tokens are not saved.
            unauthorizedMessage = BbCloudBundle.message("setup.authentication.oauth.error.scopes")
        )
    }

    private suspend fun persistOAuth(workspaceSlug: String, tokens: OAuthTokens) {
        val result = configurationService.saveOAuthSetup(workspaceSlug = workspaceSlug, tokens = tokens)
        if (result.isSuccess) {
            LOG.info("OAuth setup save succeeded: workspaceSlug=$workspaceSlug")
            state.value = state.value.copy(
                hasOAuthTokens = true,
                workingMethod = BbCloudAuthenticationMethod.OAuth,
                workspaceSlug = workspaceSlug,
                // The authenticated state is reported by the persistent AuthenticationStatus line, so the transient
                // OAuth success message is cleared here to avoid showing a duplicate.
                oauthMessage = null,
                oauthMessageIsError = false
            )
            onSaveSuccess()
        } else {
            LOG.warn("Failed to save OAuth setup", result.exceptionOrNull())
            showOAuthError(BbCloudBundle.message("setup.error.saveFailed"))
        }
    }

    /** Cancels the running OAuth login; any project may cancel the shared login (decision 13b). */
    fun onOAuthLoginCancelClicked() {
        LOG.info("User clicked Cancel on the Bitbucket oauth login")
        oauthLoginService.cancel()
    }

    fun onResetClicked() {
        LOG.info("User clicked setup Reset")
        if (!confirmReset()) {
            return
        }
        // Cancel any in-flight OAuth login first; the reset removes both credentials.
        oauthLoginService.cancel()
        setBusy(true)
        // resetAll wipes every plugin setting: the API token, OAuth login, workspace slug, projects directory, and
        // watched projects.
        coroutineScope.launch {
            configurationService.resetAll()
            state.value = state.value.copy(
                hasStoredToken = false,
                hasOAuthTokens = false,
                workingMethod = BbCloudAuthenticationMethod.None,
                projectsDirectory = null,
                workspaceSlug = null,
                tokenFieldText = "",
                errorMessage = null,
                successMessage = null,
                oauthMessage = null,
                oauthMessageIsError = false,
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

    private fun showOAuthError(message: String) {
        state.value = state.value.copy(oauthMessage = message, oauthMessageIsError = true)
    }

    private fun resolvePluginVersion(): String? {
        return BbCloudBuildInfo.version() ?: BbCloudBundle.message("about.versionUnknown")
    }

    companion object {
        private val LOG = logger<BbCloudSettingsViewModel>()
    }
}