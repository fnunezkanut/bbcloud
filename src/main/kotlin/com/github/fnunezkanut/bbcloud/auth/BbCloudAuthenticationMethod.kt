package com.github.fnunezkanut.bbcloud.auth

/**
 * How the plugin is currently authenticated against Bitbucket Cloud, as determined by an online credential check.
 *
 * Both an OAuth login and an API token may be stored at the same time. OAuth wins when it is working, and the API token
 * is the fallback; the plugin counts as authenticated when either method is verified working online. This value is
 * therefore the *working* method, not merely the stored one.
 */
enum class BbCloudAuthenticationMethod {
    None,
    ApiToken,

    /** A Bitbucket OAuth (PKCE) login whose access token is working. */
    OAuth
}