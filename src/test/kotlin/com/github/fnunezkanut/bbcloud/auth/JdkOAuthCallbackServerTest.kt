package com.github.fnunezkanut.bbcloud.auth

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URI
import kotlin.time.Duration.Companion.seconds

@Tag("unit")
class JdkOAuthCallbackServerTest {
    private val state = "expected-state"

    @Test
    fun `callback with a valid code - should 302 to the result path and complete the callback`(): Unit =
        runBlocking {
            // GIVEN
            val factory = JdkOAuthCallbackServerFactory(port = 0)
            val server = factory.start(expectedState = state)
            try {
                // WHEN
                val connection = get(port = factory.boundPort, path = "/bbcloud?code=the-code&state=$state")

                // THEN
                assertSoftly {
                    connection.responseCode shouldBe 302
                    connection.getHeaderField("Location") shouldBe "/bbcloud/done"
                    connection.getHeaderField("Cache-Control") shouldBe "no-store"
                    connection.getHeaderField("Referrer-Policy") shouldBe "no-referrer"
                }
                val result = withTimeout(2.seconds) { server.callback.await() }
                val code = result.shouldBeInstanceOf<OAuthCallbackResult.Code>()
                code.code shouldBe "the-code"
            } finally {
                server.close()
            }
        }

    @Test
    fun `result page after publishOutcome - should return 200 with the success text`(): Unit =
        runBlocking {
            // GIVEN a completed callback and a published success outcome
            val factory = JdkOAuthCallbackServerFactory(port = 0)
            val server = factory.start(expectedState = state)
            try {
                get(port = factory.boundPort, path = "/bbcloud?code=c&state=$state")
                withTimeout(2.seconds) { server.callback.await() }
                server.publishOutcome(page = OAuthResultPage(success = true, message = ""))

                // WHEN
                val connection = get(port = factory.boundPort, path = "/bbcloud/done")

                // THEN
                connection.responseCode shouldBe 200
                connection.inputStream.bufferedReader().readText() shouldContain "succeeded"
                withTimeout(2.seconds) { server.resultPageServed.await() }
            } finally {
                server.close()
            }
        }

    @Test
    fun `result page without an outcome - should show the pending page`(): Unit =
        runBlocking {
            // GIVEN a completed callback but no outcome, with a very short wait
            val factory = JdkOAuthCallbackServerFactory(port = 0, resultPageWait = 1.seconds)
            val server = factory.start(expectedState = state)
            try {
                get(port = factory.boundPort, path = "/bbcloud?code=c&state=$state")
                withTimeout(2.seconds) { server.callback.await() }

                // WHEN
                val connection = get(port = factory.boundPort, path = "/bbcloud/done")

                // THEN
                connection.responseCode shouldBe 200
                connection.inputStream.bufferedReader().readText() shouldContain "Return to the IDE"
            } finally {
                server.close()
            }
        }

    @Test
    fun `result page before a valid callback - should be 404`() {
        // GIVEN
        val factory = JdkOAuthCallbackServerFactory(port = 0)
        val server = factory.start(expectedState = state)
        try {
            // WHEN
            val connection = get(port = factory.boundPort, path = "/bbcloud/done")

            // THEN
            connection.responseCode shouldBe 404
        } finally {
            server.close()
        }
    }

    @Test
    fun `wrong state - should be 400 and leave the callback incomplete`() {
        // GIVEN
        val factory = JdkOAuthCallbackServerFactory(port = 0)
        val server = factory.start(expectedState = state)
        try {
            // WHEN
            val connection = get(port = factory.boundPort, path = "/bbcloud?code=c&state=wrong")

            // THEN
            assertSoftly {
                connection.responseCode shouldBe 400
                server.callback.isCompleted shouldBe false
            }
        } finally {
            server.close()
        }
    }

    @Test
    fun `access denied - should complete the callback with Denied`(): Unit =
        runBlocking {
            // GIVEN
            val factory = JdkOAuthCallbackServerFactory(port = 0)
            val server = factory.start(expectedState = state)
            try {
                // WHEN
                get(port = factory.boundPort, path = "/bbcloud?error=access_denied&state=$state")

                // THEN
                val result = withTimeout(2.seconds) { server.callback.await() }
                result.shouldBeInstanceOf<OAuthCallbackResult.Denied>()
            } finally {
                server.close()
            }
        }

    @Test
    fun `unknown paths are 404 and POST is 405`() {
        // GIVEN
        val factory = JdkOAuthCallbackServerFactory(port = 0)
        val server = factory.start(expectedState = state)
        try {
            // WHEN / THEN
            assertSoftly {
                get(port = factory.boundPort, path = "/bbcloudx").responseCode shouldBe 404
                get(port = factory.boundPort, path = "/other").responseCode shouldBe 404
                post(port = factory.boundPort, path = "/bbcloud?code=c&state=$state").responseCode shouldBe 405
            }
        } finally {
            server.close()
        }
    }

    @Test
    fun `close - should free the port and be idempotent`() {
        // GIVEN a server bound to a port
        val fixedPort = freePort()
        val factory = JdkOAuthCallbackServerFactory(port = fixedPort)
        val server = factory.start(expectedState = state)
        server.close()
        // Idempotent: a second close does not throw.
        server.close()

        // WHEN a new server binds the same port
        val reopened = JdkOAuthCallbackServerFactory(port = fixedPort).start(expectedState = state)

        // THEN the port was freed
        reopened.close()
    }

    @Test
    fun `a second bind on the same port - should raise OAuthCallbackServerUnavailableException`() {
        // GIVEN a server holding a port
        val factory = JdkOAuthCallbackServerFactory(port = 0)
        val first = factory.start(expectedState = state)
        try {
            // WHEN a second server tries the same port
            val result = runCatching { JdkOAuthCallbackServerFactory(port = factory.boundPort).start(expectedState = state) }

            // THEN
            result.isFailure shouldBe true
            result.exceptionOrNull().shouldBeInstanceOf<OAuthCallbackServerUnavailableException>()
        } finally {
            first.close()
        }
    }

    private fun get(port: Int, path: String): HttpURLConnection {
        val connection = URI.create("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.requestMethod = "GET"
        // Reading the status code forces the request to be sent and the response received.
        connection.responseCode
        return connection
    }

    private fun post(port: Int, path: String): HttpURLConnection {
        val connection = URI.create("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.outputStream.use { stream -> stream.write(ByteArray(0)) }
        connection.responseCode
        return connection
    }

    private fun freePort(): Int {
        java.net.ServerSocket(0).use { socket -> return socket.localPort }
    }
}