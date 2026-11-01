package com.github.fnunezkanut.bbcloud.auth

import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketApiUrlPolicy
import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketHttpErrors
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

interface BitbucketOAuthTokenClient {
    suspend fun exchangeCode(code: String, codeVerifier: String): OAuthTokenResult

    suspend fun refresh(refreshToken: String): OAuthTokenResult
}

class OkHttpBitbucketOAuthTokenClient(private val httpClient: OkHttpClient = sharedHttpClient, private val clock: Clock = Clock.System) :
    BitbucketOAuthTokenClient {
    override suspend fun exchangeCode(code: String, codeVerifier: String): OAuthTokenResult {
        val form = clientAuthenticatedForm(
            "grant_type" to "authorization_code",
            "code" to code,
            "code_verifier" to codeVerifier,
            "redirect_uri" to BbCloudOAuthConfig.REDIRECT_URI
        )
        return post(grantType = "authorization_code", form = form, previousRefreshToken = null)
    }

    override suspend fun refresh(refreshToken: String): OAuthTokenResult {
        val form = clientAuthenticatedForm(
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken
        )
        return post(grantType = "refresh_token", form = form, previousRefreshToken = refreshToken)
    }

    private fun clientAuthenticatedForm(vararg fields: Pair<String, String>): FormBody {
        val builder = FormBody.Builder()
        fields.forEach { (name, value) -> builder.add(name, value) }
        builder.add(BbCloudConsumerMaterial.primaryField(), BbCloudConsumerMaterial.primary())
        return builder.build()
    }

    private fun consumerAuthorization(): String {
        return Credentials.basic(
            username = BbCloudConsumerMaterial.primary(),
            password = BbCloudConsumerMaterial.secondary(),
            charset = Charsets.UTF_8
        )
    }

    private suspend fun post(grantType: String, form: FormBody, previousRefreshToken: String?): OAuthTokenResult {
        return withContext(Dispatchers.IO) {
            val tokenUrl = BbCloudOAuthConfig.TOKEN_URL
            if (!BitbucketApiUrlPolicy.isTrustedOAuthUrl(tokenUrl)) {
                LOG.warn("Refusing to post to an untrusted OAuth token URL (${BitbucketApiUrlPolicy.describeRejected(tokenUrl)})")
                return@withContext OAuthTokenResult.Failed()
            }
            val request = Request.Builder()
                .url(tokenUrl)
                .header("Accept", "application/json")
                .header("Authorization", consumerAuthorization())
                .post(form)
                .build()
            LOG.debug("POST $tokenUrl (grant_type=$grantType)")
            val startNanos = System.nanoTime()
            try {
                httpClient.newCall(request).execute().use { response ->
                    LOG.debug("POST $tokenUrl -> ${response.code} in ${elapsedMillis(startNanos = startNanos)} ms")
                    mapResponse(
                        statusCode = response.code,
                        body = response.body.string(),
                        requestIdHeader = response.header("X-Request-Id"),
                        httpErrorDescription = BitbucketHttpErrors.describe(response),
                        previousRefreshToken = previousRefreshToken
                    )
                }
            } catch (exception: IOException) {
                LOG.debug("POST $tokenUrl failed after ${elapsedMillis(startNanos = startNanos)} ms", exception)
                OAuthTokenResult.Failed(cause = exception)
            }
        }
    }

    private fun mapResponse(
        statusCode: Int,
        body: String,
        requestIdHeader: String?,
        httpErrorDescription: String,
        previousRefreshToken: String?
    ): OAuthTokenResult {
        if (statusCode == 200) {
            val tokens = OAuthTokenResponseParser.parseSuccess(
                body = body,
                now = clock.now(),
                previousRefreshToken = previousRefreshToken
            )
            val rotated = tokens != null && tokens.refreshToken != previousRefreshToken
            LOG.debug("Token response parsed: success=${tokens != null}, refreshTokenRotated=$rotated")
            return if (tokens == null) {
                LOG.warn("Token endpoint returned 200 but the body could not be parsed into tokens")
                OAuthTokenResult.Failed()
            } else {
                OAuthTokenResult.Success(tokens = tokens)
            }
        }

        val errorBody = OAuthTokenResponseParser.parseError(body)
        val error = errorBody?.error
        val description = errorBody?.description
        return when (error) {
            "invalid_grant" -> {
                OAuthTokenResult.InvalidGrant(description = description)
            }
            "invalid_client", "unauthorized_client" -> {
                OAuthTokenResult.InvalidClient(error = error, description = description)
            }
            else -> {
                LOG.warn(
                    "Unexpected token endpoint status $statusCode (error=$error, error_description=$description, " +
                        "X-Request-Id=$requestIdHeader$httpErrorDescription)"
                )
                OAuthTokenResult.Failed(error = error, description = description)
            }
        }
    }

    companion object {
        private val LOG = logger<OkHttpBitbucketOAuthTokenClient>()

        private val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        private fun elapsedMillis(startNanos: Long): Long {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        }
    }
}