package com.github.fnunezkanut.bbcloud.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Generates PKCE login requests (RFC 7636, S256). Not a registered service, so a default [random] argument is allowed;
 * tests pass a seeded [SecureRandom] for determinism where needed.
 */
class PkceGenerator(private val random: SecureRandom = SecureRandom()) {
    /** A fresh login request: a 43-character base64url verifier, its S256 challenge, and an independent state. */
    fun newRequest(): OAuthLoginRequest {
        val codeVerifier = randomBase64Url(byteCount = VERIFIER_BYTE_COUNT)
        val codeChallenge = challengeFor(codeVerifier = codeVerifier)
        val state = randomBase64Url(byteCount = STATE_BYTE_COUNT)
        return OAuthLoginRequest(
            pkce = PkceChallenge(codeVerifier = codeVerifier, codeChallenge = codeChallenge),
            state = state
        )
    }

    /** The base64url (no padding) of the SHA-256 digest of [codeVerifier], the S256 code challenge. */
    fun challengeFor(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return URL_ENCODER.encodeToString(digest)
    }

    private fun randomBase64Url(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return URL_ENCODER.encodeToString(bytes)
    }

    companion object {
        /** 32 bytes encode to 43 base64url characters, satisfying the RFC 7636 verifier length requirement. */
        private const val VERIFIER_BYTE_COUNT = 32
        private const val STATE_BYTE_COUNT = 32

        private val URL_ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}