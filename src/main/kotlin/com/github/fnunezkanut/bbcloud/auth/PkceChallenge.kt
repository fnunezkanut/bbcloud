package com.github.fnunezkanut.bbcloud.auth

/**
 * A PKCE pair (RFC 7636). [codeVerifier] stays in the plugin; [codeChallenge] goes to the authorize endpoint. Both
 * values are secrets for the duration of a login, so [toString] redacts them.
 */
data class PkceChallenge(val codeVerifier: String, val codeChallenge: String) {
    override fun toString(): String {
        return "PkceChallenge(codeVerifier=***, codeChallenge=***)"
    }
}

/**
 * One OAuth login attempt: its PKCE pair and the random anti-forgery [state]. [toString] redacts the values so the
 * verifier and state never reach a log.
 */
data class OAuthLoginRequest(val pkce: PkceChallenge, val state: String) {
    override fun toString(): String {
        return "OAuthLoginRequest(pkce=$pkce, state=***)"
    }
}