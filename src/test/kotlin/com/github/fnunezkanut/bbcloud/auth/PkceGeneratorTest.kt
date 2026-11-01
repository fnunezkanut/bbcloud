package com.github.fnunezkanut.bbcloud.auth

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PkceGeneratorTest {
    private val generator = PkceGenerator()

    @Test
    fun `challengeFor - should match the RFC 7636 appendix B vector`() {
        // GIVEN the verifier from RFC 7636 Appendix B
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

        // WHEN
        val challenge = generator.challengeFor(codeVerifier = verifier)

        // THEN
        challenge shouldBe "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
    }

    @Test
    fun `newRequest - should produce a 43 character base64url verifier`() {
        // WHEN
        val request = generator.newRequest()

        // THEN
        request.pkce.codeVerifier shouldMatch Regex("^[A-Za-z0-9_-]{43}$")
    }

    @Test
    fun `newRequest - state should differ from the verifier and between requests`() {
        // WHEN
        val first = generator.newRequest()
        val second = generator.newRequest()

        // THEN
        assertSoftly {
            (first.state != first.pkce.codeVerifier) shouldBe true
            (first.pkce.codeVerifier != second.pkce.codeVerifier) shouldBe true
            (first.state != second.state) shouldBe true
        }
    }

    @Test
    fun `toString - should redact the secret values`() {
        // GIVEN
        val request = generator.newRequest()

        // THEN neither the verifier, challenge, nor state appears in the string form
        assertSoftly {
            request.toString() shouldContain "state=***"
            request.pkce.toString() shouldContain "codeVerifier=***"
            request.pkce.toString() shouldContain "codeChallenge=***"
            (request.toString().contains(request.pkce.codeVerifier)) shouldBe false
            (request.toString().contains(request.state)) shouldBe false
        }
    }
}