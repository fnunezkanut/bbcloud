package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BitbucketApiUrlPolicyTest {
    @Test
    fun `isTrustedApiUrl - accepts HTTPS on the Bitbucket API host`() {
        // GIVEN
        val urls = listOf(
            "https://api.bitbucket.org/2.0/repositories/workspace/repo/pullrequests?page=2",
            "https://api.bitbucket.org:443/2.0/user",
            "https://API.BITBUCKET.ORG/2.0/user"
        )

        // WHEN / THEN
        assertSoftly {
            urls.forEach { url ->
                withClue(url) { BitbucketApiUrlPolicy.isTrustedApiUrl(url) shouldBe true }
            }
        }
    }

    @Test
    fun `isTrustedApiUrl - rejects other schemes, hosts, ports, and malformed URLs`() {
        // GIVEN
        val urls = listOf(
            "http://api.bitbucket.org/2.0/user",
            "https://evil.example/2.0/user",
            "https://api.bitbucket.org.evil.example/2.0/user",
            "https://evil.example/?h=api.bitbucket.org",
            "https://api.bitbucket.org@evil.example/2.0/user",
            "https://bitbucket.org/2.0/user",
            "https://api.bitbucket.org:8443/2.0/user",
            "ftp://api.bitbucket.org/2.0/user",
            "not a url",
            ""
        )

        // WHEN / THEN
        assertSoftly {
            urls.forEach { url ->
                withClue(url) { BitbucketApiUrlPolicy.isTrustedApiUrl(url) shouldBe false }
            }
        }
    }

    @Test
    fun `isTrustedOAuthUrl - accepts the OAuth token endpoint on bitbucket org`() {
        // GIVEN
        val urls = listOf(
            "https://bitbucket.org/site/oauth2/access_token",
            "https://bitbucket.org:443/site/oauth2/authorize",
            "https://BITBUCKET.ORG/site/oauth2/access_token"
        )

        // WHEN / THEN
        assertSoftly {
            urls.forEach { url ->
                withClue(url) { BitbucketApiUrlPolicy.isTrustedOAuthUrl(url) shouldBe true }
            }
        }
    }

    @Test
    fun `isTrustedOAuthUrl - rejects wrong scheme, host, port, and path`() {
        // GIVEN
        val urls = listOf(
            "http://bitbucket.org/site/oauth2/access_token",
            "https://bitbucket.org.evil.com/site/oauth2/access_token",
            "https://api.bitbucket.org/site/oauth2/access_token",
            "https://bitbucket.org:8443/site/oauth2/access_token",
            "https://bitbucket.org/2.0/user",
            "not a url",
            ""
        )

        // WHEN / THEN
        assertSoftly {
            urls.forEach { url ->
                withClue(url) { BitbucketApiUrlPolicy.isTrustedOAuthUrl(url) shouldBe false }
            }
        }
    }
}