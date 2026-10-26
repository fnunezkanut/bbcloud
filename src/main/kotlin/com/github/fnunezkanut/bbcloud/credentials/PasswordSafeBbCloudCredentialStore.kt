package com.github.fnunezkanut.bbcloud.credentials

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PasswordSafeBbCloudCredentialStore : BbCloudCredentialStore {
    private val credentialAttributes = CredentialAttributes(generateServiceName(SERVICE_NAMESPACE, CREDENTIAL_KEY))

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

    companion object {
        private val LOG = logger<PasswordSafeBbCloudCredentialStore>()

        private const val SERVICE_NAMESPACE = "Bitbucket Cloud PR Review"
        private const val CREDENTIAL_KEY = "ApiToken"
    }
}