package com.github.fnunezkanut.bbcloud.credentials

interface BbCloudCredentialStore {
    suspend fun hasToken(): Boolean

    suspend fun getToken(): String?

    suspend fun saveToken(token: String)

    suspend fun clearToken()
}