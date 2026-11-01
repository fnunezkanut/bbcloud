package com.github.fnunezkanut.bbcloud.ui.setup

import com.github.fnunezkanut.bbcloud.auth.BbCloudAuthenticationMethod

/** Immutable state rendered by the Compose Settings screen. */
data class SettingsUiState(
    val hasStoredToken: Boolean = false,
    // Whether an OAuth login is stored; independent of [hasStoredToken] since both credentials may coexist.
    val hasOAuthTokens: Boolean = false,
    // The working authentication method as determined by the online credential check; None when nothing verifies.
    val workingMethod: BbCloudAuthenticationMethod = BbCloudAuthenticationMethod.None,
    val projectsDirectory: String? = null,
    // Stored Bitbucket workspace slug shown in the authentication status label; null when none is stored.
    val workspaceSlug: String? = null,
    val tokenFieldText: String = "",
    val errorMessage: String? = null,
    // "Save succeeded" affordance shown next to the Save button; null hides it.
    val successMessage: String? = null,
    val isBusy: Boolean = false,
    // Whether an OAuth login is currently running (possibly started from another project's Settings tab).
    val isOAuthLoginInProgress: Boolean = false,
    // When the browser could not be opened, the authorize URL to show for manual paste; null otherwise.
    val oauthBrowserFallbackUrl: String? = null,
    // The OAuth status message (success or error) shown under the OAuth button; null hides it.
    val oauthMessage: String? = null,
    val oauthMessageIsError: Boolean = false,
    val pluginVersionFooter: String
) {
    /**
     * The active authentication method. It mirrors [workingMethod], the result of the online credential check, so the
     * status label reflects what actually works (OAuth preferred, API token fallback) rather than merely what is
     * stored.
     */
    val authenticationMethod: BbCloudAuthenticationMethod
        get() = workingMethod

    /**
     * Whether some credential is stored but none is verified working, so the status label can warn the user to
     * authenticate again (for example after an expired OAuth session with no API token fallback).
     */
    val hasStoredButNotWorkingCredentials: Boolean
        get() = (hasStoredToken || hasOAuthTokens) && workingMethod == BbCloudAuthenticationMethod.None
}