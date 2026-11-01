package com.github.fnunezkanut.bbcloud.auth

import com.github.fnunezkanut.bbcloud.credentials.BbCloudCredentialStore
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Tag("unit")
class BbCloudAccessTokenResolverTest {
    private val base = Instant.parse("2026-01-01T00:00:00Z")

    private class MutableClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private class FakeStore(var apiToken: String? = null, var oauth: OAuthTokens? = null) : BbCloudCredentialStore {
        var savedOAuth: OAuthTokens? = oauth
        var clearedOAuth = false

        override suspend fun hasToken() = apiToken != null

        override suspend fun getToken() = apiToken

        override suspend fun saveToken(token: String) {
            apiToken = token
        }

        override suspend fun clearToken() {
            apiToken = null
        }

        override suspend fun getOAuthTokens() = oauth

        override suspend fun saveOAuthTokens(tokens: OAuthTokens) {
            oauth = tokens
            savedOAuth = tokens
        }

        override suspend fun clearOAuthTokens() {
            oauth = null
            clearedOAuth = true
        }
    }

    private fun tokens(access: String, refresh: String = "rt", expiresInHours: Long = 2): OAuthTokens {
        return OAuthTokens(accessToken = access, refreshToken = refresh, expiresAt = base + expiresInHours.hours)
    }

    private fun resolver(
        store: FakeStore,
        tokenClient: BitbucketOAuthTokenClient,
        clock: MutableClock,
        onExpired: () -> Unit = {},
        onChanged: () -> Unit = {}
    ): BbCloudAccessTokenResolver {
        return BbCloudAccessTokenResolver(
            credentialStore = store,
            tokenClient = tokenClient,
            clock = clock,
            onOAuthSessionExpired = onExpired,
            onCredentialsChanged = onChanged
        )
    }

    @Test
    fun `oauthAccessTokenOrNull - returns a usable token without refreshing`(): Unit =
        runBlocking {
            val store = FakeStore(oauth = tokens(access = "at"))
            val tokenClient = mockk<BitbucketOAuthTokenClient>()
            val resolver = resolver(store = store, tokenClient = tokenClient, clock = MutableClock(base))

            resolver.oauthAccessTokenOrNull() shouldBe "at"
            coVerify(exactly = 0) { tokenClient.refresh(any()) }
        }

    @Test
    fun `oauthAccessTokenOrNull - refreshes an expiring token and saves the new one`(): Unit =
        runBlocking {
            // GIVEN a token expiring within the margin
            val store = FakeStore(oauth = tokens(access = "old", expiresInHours = 0))
            val tokenClient = mockk<BitbucketOAuthTokenClient> {
                coEvery { refresh("rt") } returns OAuthTokenResult.Success(tokens(access = "new", refresh = "rt2"))
            }
            val resolver = resolver(store = store, tokenClient = tokenClient, clock = MutableClock(base))

            resolver.oauthAccessTokenOrNull() shouldBe "new"
            store.savedOAuth?.accessToken shouldBe "new"
        }

    @Test
    fun `oauthAccessTokenOrNull - ten concurrent callers cause a single refresh`(): Unit =
        runBlocking {
            val store = FakeStore(oauth = tokens(access = "old", expiresInHours = 0))
            val refreshes = AtomicInteger(0)
            val tokenClient = object : BitbucketOAuthTokenClient {
                override suspend fun exchangeCode(code: String, codeVerifier: String) = OAuthTokenResult.Failed()

                override suspend fun refresh(refreshToken: String): OAuthTokenResult {
                    refreshes.incrementAndGet()
                    return OAuthTokenResult.Success(tokens(access = "new"))
                }
            }
            val resolver = resolver(store = store, tokenClient = tokenClient, clock = MutableClock(base))

            // WHEN
            val results = (1..10).map { async { resolver.oauthAccessTokenOrNull() } }.awaitAll()

            // THEN one refresh, all callers see the new token
            refreshes.get() shouldBe 1
            results.all { it == "new" } shouldBe true
        }

    @Test
    fun `oauthAccessTokenOrNull - permanent failure clears tokens and falls back to the api token`(): Unit =
        runBlocking {
            val store = FakeStore(apiToken = "api", oauth = tokens(access = "old", expiresInHours = 0))
            val tokenClient = mockk<BitbucketOAuthTokenClient> {
                coEvery { refresh("rt") } returns OAuthTokenResult.InvalidGrant(description = "revoked")
            }
            var expiredCalls = 0
            var changedCalls = 0
            val resolver = resolver(
                store = store,
                tokenClient = tokenClient,
                clock = MutableClock(base),
                onExpired = { expiredCalls++ },
                onChanged = { changedCalls++ }
            )

            resolver.oauthAccessTokenOrNull() shouldBe null
            store.clearedOAuth shouldBe true
            expiredCalls shouldBe 1
            changedCalls shouldBe 1
            // Bearer now falls back to the API token.
            resolver.bearerTokenOrNull() shouldBe "api"
        }

    @Test
    fun `oauthAccessTokenOrNull - transient failure before expiry keeps the old token`(): Unit =
        runBlocking {
            // GIVEN a token within the refresh margin but not yet expired
            val store = FakeStore(oauth = tokens(access = "old", expiresInHours = 0))
            val clock = MutableClock(base - 2.minutes) // still before expiresAt but inside the 5-minute margin
            val tokenClient = mockk<BitbucketOAuthTokenClient> {
                coEvery { refresh("rt") } returns OAuthTokenResult.Failed()
            }
            val resolver = resolver(store = store, tokenClient = tokenClient, clock = clock)

            resolver.oauthAccessTokenOrNull() shouldBe "old"
            store.clearedOAuth shouldBe false
        }

    @Test
    fun `oauthAccessTokenOrNull - backoff suppresses a second refresh within one minute`(): Unit =
        runBlocking {
            val store = FakeStore(oauth = tokens(access = "old", expiresInHours = 0))
            val clock = MutableClock(base - 1.minutes)
            val refreshes = AtomicInteger(0)
            val tokenClient = object : BitbucketOAuthTokenClient {
                override suspend fun exchangeCode(code: String, codeVerifier: String) = OAuthTokenResult.Failed()

                override suspend fun refresh(refreshToken: String): OAuthTokenResult {
                    refreshes.incrementAndGet()
                    return OAuthTokenResult.Failed()
                }
            }
            val resolver = resolver(store = store, tokenClient = tokenClient, clock = clock)

            // First call refreshes and fails, setting the backoff.
            resolver.oauthAccessTokenOrNull()
            // A second call within the backoff window must not refresh.
            clock.now = clock.now + 30.seconds
            resolver.oauthAccessTokenOrNull()
            refreshes.get() shouldBe 1

            // After the backoff elapses, it retries.
            clock.now = clock.now + 2.minutes
            resolver.oauthAccessTokenOrNull()
            refreshes.get() shouldBe 2
        }

    @Test
    fun `refreshAfterUnauthorized - returns the stored token when it already differs from the rejected one`(): Unit =
        runBlocking {
            val store = FakeStore(oauth = tokens(access = "fresh"))
            val tokenClient = mockk<BitbucketOAuthTokenClient>()
            val resolver = resolver(store = store, tokenClient = tokenClient, clock = MutableClock(base))

            resolver.refreshAfterUnauthorized(rejectedToken = "stale") shouldBe "fresh"
            coVerify(exactly = 0) { tokenClient.refresh(any()) }
        }

    @Test
    fun `refreshAfterUnauthorized - forces a refresh when the rejected token is the stored one`(): Unit =
        runBlocking {
            val store = FakeStore(oauth = tokens(access = "rejected"))
            val tokenClient = mockk<BitbucketOAuthTokenClient> {
                coEvery { refresh("rt") } returns OAuthTokenResult.Success(tokens(access = "new"))
            }
            val resolver = resolver(store = store, tokenClient = tokenClient, clock = MutableClock(base))

            resolver.refreshAfterUnauthorized(rejectedToken = "rejected") shouldBe "new"
        }

    @Test
    fun `refreshAfterUnauthorized - the rejected api token yields null`(): Unit =
        runBlocking {
            val store = FakeStore(apiToken = "api")
            val tokenClient = mockk<BitbucketOAuthTokenClient>()
            val resolver = resolver(store = store, tokenClient = tokenClient, clock = MutableClock(base))

            resolver.refreshAfterUnauthorized(rejectedToken = "api") shouldBe null
        }

    @Test
    fun `markOAuthNotWorking - bearer falls back to api until onOAuthTokensChanged`(): Unit =
        runBlocking {
            val store = FakeStore(apiToken = "api", oauth = tokens(access = "at"))
            val tokenClient = mockk<BitbucketOAuthTokenClient>()
            val resolver = resolver(store = store, tokenClient = tokenClient, clock = MutableClock(base))

            resolver.markOAuthNotWorking()
            resolver.bearerTokenOrNull() shouldBe "api"

            resolver.onOAuthTokensChanged()
            resolver.bearerTokenOrNull() shouldBe "at"
        }
}