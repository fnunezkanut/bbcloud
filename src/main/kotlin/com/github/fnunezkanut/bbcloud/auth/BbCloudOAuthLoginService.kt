package com.github.fnunezkanut.bbcloud.auth

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Clock
import kotlin.time.Instant

/** The observable state of the single OAuth login that may run per IDE. */
sealed class OAuthLoginState {
    data object Idle : OAuthLoginState()

    data class InProgress(val browserLaunchFailedUrl: String?) : OAuthLoginState()

    data class Finished(val result: OAuthLoginResult, val finishedAt: Instant) : OAuthLoginState()
}

/**
 * Application-level service that owns the one OAuth login allowed per IDE. Every open project's Settings tab observes
 * [state] to show progress, and any project may [cancel] it (decision 13b). The login runs in this service's scope, so
 * it is cancelled on application shutdown and the callback server closes through the flow's `NonCancellable` cleanup.
 */
@Service(Service.Level.APP)
class BbCloudOAuthLoginService(private val coroutineScope: CoroutineScope) {
    private val mutableState = MutableStateFlow<OAuthLoginState>(OAuthLoginState.Idle)
    val state: StateFlow<OAuthLoginState> = mutableState.asStateFlow()

    private val activeLogin = AtomicReference<Deferred<OAuthLoginResult>?>(null)

    /** Creates the flow to run; replaceable by tests. */
    internal var flowFactory: () -> BbCloudOAuthLoginFlow = BbCloudOAuthLoginFlow::createDefault

    // getCompleted() is read inside invokeOnCompletion, where the deferred is guaranteed complete; that accessor is
    // still marked ExperimentalCoroutinesApi, so the opt-in is scoped to this function.

    /** Starts a login, or returns the running one when a login is already in progress. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(): Deferred<OAuthLoginResult> {
        activeLogin.get()?.let { running ->
            if (running.isActive) {
                LOG.info("OAuth login already in progress; reusing it")
                return running
            }
        }

        val flow = flowFactory()
        val deferred = coroutineScope.async {
            flow.login(onBrowserLaunchFailed = { url ->
                mutableState.value = OAuthLoginState.InProgress(browserLaunchFailedUrl = url)
            })
        }
        val existing = activeLogin.compareAndSet(null, deferred)
        if (!existing) {
            // Another thread won the race; cancel ours and return the winner.
            val winner = activeLogin.get()
            if (winner != null && winner.isActive) {
                deferred.cancel()
                return winner
            }
            activeLogin.set(deferred)
        }

        mutableState.value = OAuthLoginState.InProgress(browserLaunchFailedUrl = null)
        deferred.invokeOnCompletion { throwable ->
            activeLogin.compareAndSet(deferred, null)
            if (throwable == null) {
                val result = deferred.getCompleted()
                mutableState.value = OAuthLoginState.Finished(result = result, finishedAt = Clock.System.now())
            }
        }
        return deferred
    }

    /** Cancels the active login, if any, and returns to [OAuthLoginState.Idle]. Any project may call this. */
    fun cancel() {
        val running = activeLogin.getAndSet(null)
        if (running != null && running.isActive) {
            LOG.info("OAuth login cancelled")
            running.cancel()
        }
        mutableState.value = OAuthLoginState.Idle
    }

    /** Resets a [OAuthLoginState.Finished] state to [OAuthLoginState.Idle] after a view model consumes the result. */
    fun acknowledge() {
        if (mutableState.value is OAuthLoginState.Finished) {
            mutableState.value = OAuthLoginState.Idle
        }
    }

    companion object {
        private val LOG = logger<BbCloudOAuthLoginService>()
    }
}