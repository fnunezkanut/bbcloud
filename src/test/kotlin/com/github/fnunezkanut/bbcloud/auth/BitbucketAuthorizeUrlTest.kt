package com.github.fnunezkanut.bbcloud.auth

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BitbucketAuthorizeUrlTest {
    @Test
    fun `build - should include host, path and every required query parameter`() {
        // GIVEN
        val request = OAuthLoginRequest(
            pkce = PkceChallenge(codeVerifier = "verifier", codeChallenge = "challenge-value"),
            state = "state-value"
        )

        // WHEN
        val url = BitbucketAuthorizeUrl.build(request = request).toHttpUrl()

        // THEN
        assertSoftly {
            url.host shouldBe "bitbucket.org"
            url.encodedPath shouldBe "/site/oauth2/authorize"
            url.queryParameter(BbCloudConsumerMaterial.primaryField()) shouldBe BbCloudConsumerMaterial.primary()
            url.queryParameter("response_type") shouldBe "code"
            url.queryParameter("redirect_uri") shouldBe "http://127.0.0.1:42424/bbcloud"
            url.queryParameter("state") shouldBe "state-value"
            url.queryParameter("code_challenge") shouldBe "challenge-value"
            url.queryParameter("code_challenge_method") shouldBe "S256"
            url.toString().contains(BbCloudConsumerMaterial.secondary()) shouldBe false
        }
    }
}