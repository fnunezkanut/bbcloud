package com.github.fnunezkanut.bbcloud.auth

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Tag("unit")
class BbCloudOAuthLoginFlowTest {
    private val generator = PkceGenerator()

    private fun tokens(): OAuthTokens {
        return OAuthTokens(
            accessToken = "at",
            refreshToken = "rt",
            expiresAt = Instant.parse("2026-01-01T02:00:00Z")
        )
    }

    @Test
    fun `login - happy path returns Success, publishes success, and closes the server`(): Unit =
        runBlocking {
            // GIVEN
            val server = FakeServer()
            server.callbackDeferred.complete(OAuthCallbackResult.Code(code = "the-code"))
            server.resultPageServedDeferred.complete(Unit)
            val flow = flow(
                server = server,
                tokenClient = FakeTokenClient(exchangeResult = OAuthTokenResult.Success(tokens()))
            )

            // WHEN
            val result = flow.login()

            // THEN
            result.shouldBeInstanceOf<OAuthLoginResult.Success>()
            server.publishedOutcome?.success shouldBe true
            server.closed shouldBe true
        }

    @Test
    fun `login - port unavailable returns PortUnavailable and does not open the browser`(): Unit =
        runBlocking {
            // GIVEN a factory that always fails to bind
            var browserOpened = false
            val flow = BbCloudOAuthLoginFlow(
                pkceGenerator = generator,
                serverFactory = { throw OAuthCallbackServerUnavailableException(cause = RuntimeException("in use")) },
                tokenClient = FakeTokenClient(exchangeResult = OAuthTokenResult.Success(tokens())),
                openBrowser = { browserOpened = true },
                timeout = 1.seconds,
                resultPageGrace = 100.milliseconds
            )

            // WHEN
            val result = flow.login()

            // THEN
            result.shouldBeInstanceOf<OAuthLoginResult.PortUnavailable>()
            browserOpened shouldBe false
        }

    @Test
    fun `login - browser throws but a later callback still succeeds`(): Unit =
        runBlocking {
            // GIVEN the browser launch fails, then a code arrives
            val server = FakeServer()
            server.callbackDeferred.complete(OAuthCallbackResult.Code(code = "the-code"))
            server.resultPageServedDeferred.complete(Unit)
            var fallbackUrl: String? = null
            val flow = BbCloudOAuthLoginFlow(
                pkceGenerator = generator,
                serverFactory = { server },
                tokenClient = FakeTokenClient(exchangeResult = OAuthTokenResult.Success(tokens())),
                openBrowser = { throw RuntimeException("no browser") },
                timeout = 2.seconds,
                resultPageGrace = 100.milliseconds
            )

            // WHEN
            val result = flow.login(onBrowserLaunchFailed = { url -> fallbackUrl = url })

            // THEN
            result.shouldBeInstanceOf<OAuthLoginResult.Success>()
            (fallbackUrl != null) shouldBe true
        }

    @Test
    fun `login - timeout returns TimedOut and closes the server`(): Unit =
        runBlocking {
            // GIVEN a server whose callback never completes
            val server = FakeServer()
            val flow = flow(server = server, tokenClient = FakeTokenClient(exchangeResult = OAuthTokenResult.Success(tokens())))

            // WHEN
            val result = flow.login()

            // THEN
            result.shouldBeInstanceOf<OAuthLoginResult.TimedOut>()
            server.closed shouldBe true
        }

    @Test
    fun `login - denied returns Denied and publishes a failure outcome`(): Unit =
        runBlocking {
            // GIVEN
            val server = FakeServer()
            server.callbackDeferred.complete(OAuthCallbackResult.Denied(error = "access_denied", description = null))
            server.resultPageServedDeferred.complete(Unit)
            val flow = flow(server = server, tokenClient = FakeTokenClient(exchangeResult = OAuthTokenResult.Success(tokens())))

            // WHEN
            val result = flow.login()

            // THEN
            result.shouldBeInstanceOf<OAuthLoginResult.Denied>()
            server.publishedOutcome?.success shouldBe false
        }

    @Test
    fun `login - invalid client maps to ClientRejected`(): Unit =
        runBlocking {
            // GIVEN
            val server = FakeServer()
            server.callbackDeferred.complete(OAuthCallbackResult.Code(code = "c"))
            server.resultPageServedDeferred.complete(Unit)
            val flow = flow(
                server = server,
                tokenClient = FakeTokenClient(exchangeResult = OAuthTokenResult.InvalidClient(error = "invalid_client", description = null))
            )

            // WHEN
            val result = flow.login()

            // THEN
            result.shouldBeInstanceOf<OAuthLoginResult.ClientRejected>()
        }

    @Test
    fun `login - cancellation propagates and closes the server`(): Unit =
        runBlocking {
            // GIVEN a server whose callback never completes
            val server = FakeServer()
            val flow = BbCloudOAuthLoginFlow(
                pkceGenerator = generator,
                serverFactory = {
                    server.started = true
                    server
                },
                tokenClient = FakeTokenClient(exchangeResult = OAuthTokenResult.Success(tokens())),
                openBrowser = {},
                timeout = 10.seconds,
                resultPageGrace = 100.milliseconds
            )

            // WHEN the login job is cancelled while waiting
            val job: Deferred<OAuthLoginResult> = async { flow.login() }
            // Give the flow a moment to start the server and begin awaiting the callback before cancelling.
            var startWait = 0
            while (!server.started && startWait < 2000) {
                kotlinx.coroutines.delay(10)
                startWait += 10
            }
            job.cancel()
            val cancelled = runCatching { job.await() }

            // THEN the job did not complete normally, and the server is closed through the NonCancellable finally
            // block (which runs after the cancellation unwinds, so poll briefly for it).
            cancelled.isFailure shouldBe true
            var waited = 0
            while (!server.closed && waited < 2000) {
                kotlinx.coroutines.delay(10)
                waited += 10
            }
            server.closed shouldBe true
        }

    private fun flow(server: FakeServer, tokenClient: BitbucketOAuthTokenClient): BbCloudOAuthLoginFlow {
        return BbCloudOAuthLoginFlow(
            pkceGenerator = generator,
            serverFactory = { server },
            tokenClient = tokenClient,
            openBrowser = {},
            timeout = 1.seconds,
            resultPageGrace = 100.milliseconds
        )
    }

    private class FakeServer : OAuthCallbackServer {
        val callbackDeferred = CompletableDeferred<OAuthCallbackResult>()
        val resultPageServedDeferred = CompletableDeferred<Unit>()
        var publishedOutcome: OAuthResultPage? = null
        var closed = false

        @Volatile
        var started = false

        override val callback: Deferred<OAuthCallbackResult> get() = callbackDeferred
        override val resultPageServed: Deferred<Unit> get() = resultPageServedDeferred

        override fun publishOutcome(page: OAuthResultPage) {
            publishedOutcome = page
            if (!resultPageServedDeferred.isCompleted) {
                resultPageServedDeferred.complete(Unit)
            }
        }

        override fun close() {
            closed = true
            if (!callbackDeferred.isCompleted) {
                callbackDeferred.cancel()
            }
            if (!resultPageServedDeferred.isCompleted) {
                resultPageServedDeferred.cancel()
            }
        }
    }

    private class FakeTokenClient(private val exchangeResult: OAuthTokenResult) : BitbucketOAuthTokenClient {
        override suspend fun exchangeCode(code: String, codeVerifier: String): OAuthTokenResult {
            return exchangeResult
        }

        override suspend fun refresh(refreshToken: String): OAuthTokenResult {
            return exchangeResult
        }
    }
}