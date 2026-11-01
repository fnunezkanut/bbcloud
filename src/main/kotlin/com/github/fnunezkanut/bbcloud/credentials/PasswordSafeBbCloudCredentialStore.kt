package com.github.fnunezkanut.bbcloud.credentials

import com.github.fnunezkanut.bbcloud.auth.OAuthTokens
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class PasswordSafeBbCloudCredentialStore : BbCloudCredentialStore {
    private val credentialAttributes = CredentialAttributes(generateServiceName(SERVICE_NAMESPACE, CREDENTIAL_KEY))
    private val oauthAttributes = CredentialAttributes(generateServiceName(SERVICE_NAMESPACE, OAUTH_CREDENTIAL_KEY))

    override suspend fun hasToken(): Boolean {
        return withContext(Dispatchers.IO) {
            val hasToken = !PasswordSafe.instance.getPassword(credentialAttributes).isNullOrBlank()
            LOG.debug("Password safe token presence check: hasToken=$hasToken")
            hasToken
        }
    }

    override suspend fun getToken(): String? {
        return withContext(Dispatchers.IO) {
            val token = PasswordSafe.instance.getPassword(credentialAttributes)?.takeIf { it.isNotBlank() }
            LOG.debug("Password safe token read: found=${token != null}")
            token
        }
    }

    override suspend fun saveToken(token: String) {
        withContext(Dispatchers.IO) {
            PasswordSafe.instance.setPassword(credentialAttributes, token)
            LOG.debug("Password safe token saved")
        }
    }

    override suspend fun clearToken() {
        withContext(Dispatchers.IO) {
            PasswordSafe.instance.set(credentialAttributes, null)
            LOG.debug("Password safe token cleared")
        }
    }

    override suspend fun getOAuthTokens(): OAuthTokens? {
        return withContext(Dispatchers.IO) {
            val json = PasswordSafe.instance.getPassword(oauthAttributes)?.takeIf { it.isNotBlank() }
            if (json == null) {
                LOG.debug("Password safe OAuth tokens read: found=false")
                return@withContext null
            }
            val decoded = runCatching { JSON.decodeFromString<OAuthTokens>(json) }.getOrNull()
            if (decoded == null) {
                // A corrupt entry is unusable; clear it without logging its content.
                LOG.warn("Stored OAuth tokens could not be decoded; clearing the corrupt entry")
                PasswordSafe.instance.set(oauthAttributes, null)
                return@withContext null
            }
            LOG.debug("Password safe OAuth tokens read: found=true")
            decoded
        }
    }

    override suspend fun saveOAuthTokens(tokens: OAuthTokens) {
        withContext(Dispatchers.IO) {
            val json = JSON.encodeToString(tokens)
            PasswordSafe.instance.set(oauthAttributes, Credentials("bitbucket-oauth", json))
            LOG.debug("Password safe OAuth tokens saved")
        }
    }

    override suspend fun clearOAuthTokens() {
        withContext(Dispatchers.IO) {
            PasswordSafe.instance.set(oauthAttributes, null)
            LOG.debug("Password safe OAuth tokens cleared")
        }
    }

    companion object {
        private val LOG = logger<PasswordSafeBbCloudCredentialStore>()

        private val JSON = Json { ignoreUnknownKeys = true }

        private const val SERVICE_NAMESPACE = "Bitbucket Cloud PR Review"
        private const val CREDENTIAL_KEY = "ApiToken"
        private const val OAUTH_CREDENTIAL_KEY = "OAuthTokens"
    }
}