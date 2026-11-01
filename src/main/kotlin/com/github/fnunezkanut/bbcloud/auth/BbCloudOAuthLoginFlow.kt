package com.github.fnunezkanut.bbcloud.auth

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * The testable core of the OAuth login. It wires the PKCE request, the local callback server, the token exchange, and
 * the result page, with every production dependency injected so a test can drive it with fakes and short durations. The
 * flow persists nothing; the caller stores the tokens on [OAuthLoginResult.Success].
 */
open class BbCloudOAuthLoginFlow(
    private val pkceGenerator: PkceGenerator,
    private val serverFactory: OAuthCallbackServerFactory,
    private val tokenClient: BitbucketOAuthTokenClient,
    private val openBrowser: (String) -> Unit,
    private val timeout: Duration = BbCloudOAuthConfig.LOGIN_TIMEOUT,
    private val resultPageGrace: Duration = BbCloudOAuthConfig.RESULT_PAGE_GRACE
) {
    /**
     * Runs one login. [onBrowserLaunchFailed] is called with the authorize URL when the browser cannot be opened, so
     * the UI can show the URL for the user to paste; the flow keeps waiting for a callback either way.
     */
    open suspend fun login(onBrowserLaunchFailed: (String) -> Unit = {}): OAuthLoginResult {
        val request = pkceGenerator.newRequest()
        val authorizeUrl = BitbucketAuthorizeUrl.build(request = request)

        val server = try {
            withContext(Dispatchers.IO) { serverFactory.start(expectedState = request.state) }
        } catch (exception: OAuthCallbackServerUnavailableException) {
            LOG.info("OAuth login could not start the local callback listener", exception)
            return OAuthLoginResult.PortUnavailable
        }

        var browserLaunched = true
        try {
            try {
                openBrowser(authorizeUrl)
            } catch (exception: Exception) {
                // Keep waiting: the user can paste the URL into a browser manually (decision 16a).
                browserLaunched = false
                LOG.info("OAuth login could not open the browser; waiting for a manual callback", exception)
                onBrowserLaunchFailed(authorizeUrl)
            }

            val callback = withTimeoutOrNull(timeout) { server.callback.await() }
            if (callback == null) {
                return if (browserLaunched) {
                    logOutcome(outcome = "timed out")
                    OAuthLoginResult.TimedOut
                } else {
                    logOutcome(outcome = "browser launch failed, then timed out")
                    OAuthLoginResult.BrowserLaunchFailed(authorizeUrl = authorizeUrl)
                }
            }

            val result = handleCallback(callback = callback, request = request, server = server)
            publishOutcomeFor(result = result, server = server)
            return result
        } finally {
            withContext(NonCancellable) {
                withTimeoutOrNull(resultPageGrace) { server.resultPageServed.await() }
                server.close()
            }
        }
    }

    private suspend fun handleCallback(callback: OAuthCallbackResult, request: OAuthLoginRequest, server: OAuthCallbackServer): OAuthLoginResult {
        return when (callback) {
            is OAuthCallbackResult.Denied -> {
                logOutcome(outcome = "denied")
                OAuthLoginResult.Denied(description = callback.description)
            }
            is OAuthCallbackResult.Invalid -> {
                logOutcome(outcome = "invalid callback")
                OAuthLoginResult.InvalidCallback
            }
            is OAuthCallbackResult.Code -> {
                exchange(code = callback.code, request = request)
            }
        }
    }

    private suspend fun exchange(code: String, request: OAuthLoginRequest): OAuthLoginResult {
        return when (val result = tokenClient.exchangeCode(code = code, codeVerifier = request.pkce.codeVerifier)) {
            is OAuthTokenResult.Success -> {
                logOutcome(outcome = "success")
                OAuthLoginResult.Success(tokens = result.tokens)
            }
            is OAuthTokenResult.InvalidClient -> {
                logOutcome(outcome = "client rejected")
                OAuthLoginResult.ClientRejected(description = result.description)
            }
            is OAuthTokenResult.InvalidGrant -> {
                logOutcome(outcome = "exchange failed (invalid_grant)")
                OAuthLoginResult.ExchangeFailed()
            }
            is OAuthTokenResult.Failed -> {
                logOutcome(outcome = "exchange failed")
                OAuthLoginResult.ExchangeFailed(cause = result.cause)
            }
        }
    }

    /** Publishes the terminal outcome to the result page for any result that came through a real callback. */
    private fun publishOutcomeFor(result: OAuthLoginResult, server: OAuthCallbackServer) {
        val page = when (result) {
            is OAuthLoginResult.Success -> OAuthResultPage(success = true, message = "")
            is OAuthLoginResult.Denied -> OAuthResultPage(
                success = false,
                message = BbCloudBundle.message("setup.authentication.oauth.error.denied")
            )
            is OAuthLoginResult.InvalidCallback -> OAuthResultPage(
                success = false,
                message = BbCloudBundle.message("setup.authentication.oauth.error.invalidCallback")
            )
            is OAuthLoginResult.ClientRejected -> OAuthResultPage(
                success = false,
                message = BbCloudBundle.message("setup.authentication.oauth.error.clientRejected")
            )
            is OAuthLoginResult.ExchangeFailed -> OAuthResultPage(
                success = false,
                message = BbCloudBundle.message("setup.authentication.oauth.error.exchangeFailed")
            )
            // These did not come through a callback, so the result page is never loaded.
            is OAuthLoginResult.PortUnavailable,
            is OAuthLoginResult.BrowserLaunchFailed,
            is OAuthLoginResult.TimedOut -> return
        }
        server.publishOutcome(page = page)
    }

    private fun logOutcome(outcome: String) {
        LOG.info("OAuth login finished: $outcome")
    }

    companion object {
        private val LOG = logger<BbCloudOAuthLoginFlow>()

        /** Wires the production implementations and the system browser. */
        fun createDefault(): BbCloudOAuthLoginFlow {
            return BbCloudOAuthLoginFlow(
                pkceGenerator = PkceGenerator(),
                serverFactory = JdkOAuthCallbackServerFactory(),
                tokenClient = OkHttpBitbucketOAuthTokenClient(),
                openBrowser = { url -> BrowserUtil.browse(url) }
            )
        }
    }
}