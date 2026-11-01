package com.github.fnunezkanut.bbcloud.auth

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@Tag("unit")
class BbCloudOAuthLoginServiceTest {
    @Test
    fun `start - two calls return the same deferred and run the flow once`(): Unit =
        runBlocking {
            // GIVEN a flow that blocks until released, counting its invocations
            val release = CompletableDeferred<OAuthLoginResult>()
            val invocations = AtomicInteger(0)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val service = BbCloudOAuthLoginService(coroutineScope = scope)
            service.flowFactory = {
                invocations.incrementAndGet()
                FlowStub(result = release)
            }

            // WHEN
            val first = service.start()
            val second = service.start()

            // THEN
            (first === second) shouldBe true
            release.complete(OAuthLoginResult.TimedOut)
            first.await()
            invocations.get() shouldBe 1
        }

    @Test
    fun `start - state moves through InProgress and Finished`(): Unit =
        runBlocking {
            // GIVEN
            val release = CompletableDeferred<OAuthLoginResult>()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val service = BbCloudOAuthLoginService(coroutineScope = scope)
            service.flowFactory = { FlowStub(result = release) }

            // WHEN
            service.start()
            // Let the launched coroutine set InProgress.
            waitFor { service.state.value is OAuthLoginState.InProgress }
            service.state.value.shouldBeInstanceOf<OAuthLoginState.InProgress>()

            release.complete(OAuthLoginResult.Success(tokens()))
            waitFor { service.state.value is OAuthLoginState.Finished }

            // THEN
            val finished = service.state.value.shouldBeInstanceOf<OAuthLoginState.Finished>()
            finished.result.shouldBeInstanceOf<OAuthLoginResult.Success>()
        }

    @Test
    fun `cancel - returns to Idle and cancels the flow`(): Unit =
        runBlocking {
            // GIVEN a flow that never completes
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val service = BbCloudOAuthLoginService(coroutineScope = scope)
            service.flowFactory = { FlowStub(result = CompletableDeferred()) }
            service.start()
            waitFor { service.state.value is OAuthLoginState.InProgress }

            // WHEN
            service.cancel()

            // THEN
            service.state.value.shouldBeInstanceOf<OAuthLoginState.Idle>()
        }

    @Test
    fun `acknowledge - resets a Finished state to Idle`(): Unit =
        runBlocking {
            // GIVEN
            val release = CompletableDeferred<OAuthLoginResult>()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val service = BbCloudOAuthLoginService(coroutineScope = scope)
            service.flowFactory = { FlowStub(result = release) }
            service.start()
            release.complete(OAuthLoginResult.TimedOut)
            waitFor { service.state.value is OAuthLoginState.Finished }

            // WHEN
            service.acknowledge()

            // THEN
            service.state.value.shouldBeInstanceOf<OAuthLoginState.Idle>()
        }

    private fun tokens(): OAuthTokens {
        return OAuthTokens(
            accessToken = "at",
            refreshToken = "rt",
            expiresAt = kotlin.time.Instant.parse("2026-01-01T02:00:00Z")
        )
    }

    private suspend fun waitFor(predicate: () -> Boolean) {
        var waited = 0
        while (!predicate() && waited < 2000) {
            delay(10)
            waited += 10
        }
    }

    /** A flow whose login suspends until [result] completes, so the service state can be observed mid-flight. */
    private class FlowStub(private val result: CompletableDeferred<OAuthLoginResult>) :
        BbCloudOAuthLoginFlow(
            pkceGenerator = PkceGenerator(),
            serverFactory = { throw UnsupportedOperationException() },
            tokenClient = object : BitbucketOAuthTokenClient {
                override suspend fun exchangeCode(code: String, codeVerifier: String) = OAuthTokenResult.Failed()

                override suspend fun refresh(refreshToken: String) = OAuthTokenResult.Failed()
            },
            openBrowser = {}
        ) {
        override suspend fun login(onBrowserLaunchFailed: (String) -> Unit): OAuthLoginResult {
            return result.await()
        }
    }
}