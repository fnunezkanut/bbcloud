package com.github.fnunezkanut.bbcloud.auth

import com.github.fnunezkanut.bbcloud.credentials.BbCloudCredentialStore
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Tag("unit")
class BbCloudCredentialValidatorTest {
    private val base = Instant.parse("2026-01-01T00:00:00Z")

    private class MutableClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private class FakeStore(var apiToken: String? = null, var oauth: OAuthTokens? = null) : BbCloudCredentialStore {
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
        }

        override suspend fun clearOAuthTokens() {
            oauth = null
        }
    }

    /** A provider stub whose OAuth token and refresh behavior are set per test. */
    private class FakeProvider(var oauthToken: String?, var refreshedToken: String? = null, var markedNotWorking: Boolean = false) :
        BbCloudAccessTokenProvider {
        override suspend fun bearerTokenOrNull() = oauthToken

        override suspend fun oauthAccessTokenOrNull() = oauthToken

        override suspend fun refreshAfterUnauthorized(rejectedToken: String) = refreshedToken

        override fun markOAuthNotWorking() {
            markedNotWorking = true
        }

        override fun onOAuthTokensChanged() {}
    }

    private fun tokens(access: String): OAuthTokens {
        return OAuthTokens(accessToken = access, refreshToken = "rt", expiresAt = base + 2.hours)
    }

    private fun validator(store: FakeStore, provider: FakeProvider, check: BitbucketCredentialCheckClient, clock: MutableClock): BbCloudCredentialValidator {
        return BbCloudCredentialValidator(
            credentialStore = store,
            accessTokenProvider = provider,
            checkClient = check,
            clock = clock
        )
    }

    private fun checkClient(results: Map<String, CredentialCheckResult>): BitbucketCredentialCheckClient {
        return object : BitbucketCredentialCheckClient {
            override suspend fun check(bearerToken: String): CredentialCheckResult {
                return results[bearerToken] ?: CredentialCheckResult.Unauthorized
            }
        }
    }

    @Test
    fun `workingMethod - valid OAuth maps to OAuth`(): Unit =
        runBlocking {
            val store = FakeStore(oauth = tokens(access = "at"))
            val provider = FakeProvider(oauthToken = "at")
            val validator = validator(
                store = store,
                provider = provider,
                check = checkClient(mapOf("at" to CredentialCheckResult.Valid)),
                clock = MutableClock(base)
            )

            validator.workingMethod() shouldBe BbCloudAuthenticationMethod.OAuth
        }

    @Test
    fun `workingMethod - OAuth 401 then a successful refresh maps to OAuth`(): Unit =
        runBlocking {
            val store = FakeStore(oauth = tokens(access = "refreshed"))
            val provider = FakeProvider(oauthToken = "stale", refreshedToken = "refreshed")
            val validator = validator(
                store = store,
                provider = provider,
                check = checkClient(
                    mapOf(
                        "stale" to CredentialCheckResult.Unauthorized,
                        "refreshed" to CredentialCheckResult.Valid
                    )
                ),
                clock = MutableClock(base)
            )

            validator.workingMethod() shouldBe BbCloudAuthenticationMethod.OAuth
        }

    @Test
    fun `workingMethod - OAuth failing twice falls back to a valid API token`(): Unit =
        runBlocking {
            // GIVEN OAuth that stays unauthorized, and a valid API token
            val store = FakeStore(apiToken = "api", oauth = tokens(access = "bad"))
            val provider = FakeProvider(oauthToken = "bad", refreshedToken = "bad")
            val validator = validator(
                store = store,
                provider = provider,
                check = checkClient(
                    mapOf(
                        "bad" to CredentialCheckResult.Unauthorized,
                        "api" to CredentialCheckResult.Valid
                    )
                ),
                clock = MutableClock(base)
            )

            validator.workingMethod() shouldBe BbCloudAuthenticationMethod.ApiToken
            provider.markedNotWorking shouldBe true
        }

    @Test
    fun `workingMethod - OAuth Forbidden falls back to the API token`(): Unit =
        runBlocking {
            val store = FakeStore(apiToken = "api", oauth = tokens(access = "at"))
            val provider = FakeProvider(oauthToken = "at")
            val validator = validator(
                store = store,
                provider = provider,
                check = checkClient(
                    mapOf(
                        "at" to CredentialCheckResult.Forbidden,
                        "api" to CredentialCheckResult.Valid
                    )
                ),
                clock = MutableClock(base)
            )

            validator.workingMethod() shouldBe BbCloudAuthenticationMethod.ApiToken
        }

    @Test
    fun `workingMethod - both invalid maps to None`(): Unit =
        runBlocking {
            val store = FakeStore(apiToken = "api", oauth = tokens(access = "at"))
            val provider = FakeProvider(oauthToken = "at", refreshedToken = "at")
            val validator = validator(
                store = store,
                provider = provider,
                check = checkClient(
                    mapOf(
                        "at" to CredentialCheckResult.Unauthorized,
                        "api" to CredentialCheckResult.Unauthorized
                    )
                ),
                clock = MutableClock(base)
            )

            validator.workingMethod() shouldBe BbCloudAuthenticationMethod.None
        }

    @Test
    fun `workingMethod - unavailable with no last-known treats the stored OAuth as working`(): Unit =
        runBlocking {
            val store = FakeStore(oauth = tokens(access = "at"))
            val provider = FakeProvider(oauthToken = "at")
            val validator = validator(
                store = store,
                provider = provider,
                check = checkClient(mapOf("at" to CredentialCheckResult.Unavailable())),
                clock = MutableClock(base)
            )

            validator.workingMethod() shouldBe BbCloudAuthenticationMethod.OAuth
        }

    @Test
    fun `workingMethod - a cache hit within the TTL makes no second check`(): Unit =
        runBlocking {
            val store = FakeStore(oauth = tokens(access = "at"))
            val provider = FakeProvider(oauthToken = "at")
            val checks = AtomicInteger(0)
            val countingCheck = object : BitbucketCredentialCheckClient {
                override suspend fun check(bearerToken: String): CredentialCheckResult {
                    checks.incrementAndGet()
                    return CredentialCheckResult.Valid
                }
            }
            val clock = MutableClock(base)
            val validator = validator(store = store, provider = provider, check = countingCheck, clock = clock)

            validator.workingMethod()
            validator.workingMethod()
            checks.get() shouldBe 1

            // After the TTL it re-checks.
            clock.now = clock.now + 6.minutes
            validator.workingMethod()
            checks.get() shouldBe 2
        }

    @Test
    fun `invalidate - forces a re-check`(): Unit =
        runBlocking {
            val store = FakeStore(oauth = tokens(access = "at"))
            val provider = FakeProvider(oauthToken = "at")
            val checks = AtomicInteger(0)
            val countingCheck = object : BitbucketCredentialCheckClient {
                override suspend fun check(bearerToken: String): CredentialCheckResult {
                    checks.incrementAndGet()
                    return CredentialCheckResult.Valid
                }
            }
            val validator = validator(store = store, provider = provider, check = countingCheck, clock = MutableClock(base))

            validator.workingMethod()
            validator.invalidate()
            validator.workingMethod()
            checks.get() shouldBe 2
        }

    @Test
    fun `workingMethod - concurrent callers cause one check`(): Unit =
        runBlocking {
            val store = FakeStore(oauth = tokens(access = "at"))
            val provider = FakeProvider(oauthToken = "at")
            val checks = AtomicInteger(0)
            val countingCheck = object : BitbucketCredentialCheckClient {
                override suspend fun check(bearerToken: String): CredentialCheckResult {
                    checks.incrementAndGet()
                    return CredentialCheckResult.Valid
                }
            }
            val validator = validator(store = store, provider = provider, check = countingCheck, clock = MutableClock(base))

            (1..10).map { async { validator.workingMethod() } }.awaitAll()
            checks.get() shouldBe 1
        }
}