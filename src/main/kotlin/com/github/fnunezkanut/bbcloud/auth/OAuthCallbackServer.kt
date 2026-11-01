package com.github.fnunezkanut.bbcloud.auth

import com.intellij.openapi.diagnostic.logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import java.io.IOException
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Raised when the local callback server cannot bind, for example because the port is already in use. */
class OAuthCallbackServerUnavailableException(cause: Throwable) : Exception(cause)

/**
 * A short-lived local HTTP server that receives the OAuth redirect. [callback] completes with the first valid redirect;
 * [publishOutcome] hands the token-exchange outcome to the `/bbcloud/done` page, and [resultPageServed] completes once
 * that page has been written to the browser. [close] stops the server and is idempotent.
 */
interface OAuthCallbackServer : AutoCloseable {
    val callback: Deferred<OAuthCallbackResult>

    fun publishOutcome(page: OAuthResultPage)

    val resultPageServed: Deferred<Unit>

    override fun close()
}

/** Starts an [OAuthCallbackServer] for [expectedState], or throws [OAuthCallbackServerUnavailableException]. */
fun interface OAuthCallbackServerFactory {
    fun start(expectedState: String): OAuthCallbackServer
}

/**
 * Production factory: binds a JDK [HttpServer] to IPv4 loopback ([BbCloudOAuthConfig.CALLBACK_HOST]) on [port], never a
 * wildcard address. The executor is a single named daemon thread. [resultPageWait] is injectable so tests stay fast.
 */
class JdkOAuthCallbackServerFactory(
    private val port: Int = BbCloudOAuthConfig.CALLBACK_PORT,
    private val resultPageWait: kotlin.time.Duration = BbCloudOAuthConfig.RESULT_PAGE_WAIT
) : OAuthCallbackServerFactory {
    /** The port the most recently started server actually bound to; useful when [port] is 0 in tests. */
    @Volatile
    internal var boundPort: Int = port
        private set

    override fun start(expectedState: String): OAuthCallbackServer {
        val server = try {
            HttpServer.create(InetSocketAddress(InetAddress.getByName(BbCloudOAuthConfig.CALLBACK_HOST), port), 0)
        } catch (exception: BindException) {
            throw OAuthCallbackServerUnavailableException(cause = exception)
        } catch (exception: IOException) {
            throw OAuthCallbackServerUnavailableException(cause = exception)
        }
        boundPort = server.address.port
        return JdkOAuthCallbackServer(
            server = server,
            expectedState = expectedState,
            resultPageWaitMillis = resultPageWait.inWholeMilliseconds
        )
    }
}

private class JdkOAuthCallbackServer(private val server: HttpServer, private val expectedState: String, private val resultPageWaitMillis: Long) :
    OAuthCallbackServer {
    private val executor = Executors.newSingleThreadExecutor(DaemonThreadFactory)

    private val callbackDeferred = CompletableDeferred<OAuthCallbackResult>()
    private val resultPageServedDeferred = CompletableDeferred<Unit>()

    private val outcomeLatch = java.util.concurrent.CountDownLatch(1)

    @Volatile
    private var outcome: OAuthResultPage? = null

    private val closed = AtomicBoolean(false)

    override val callback: Deferred<OAuthCallbackResult> get() = callbackDeferred
    override val resultPageServed: Deferred<Unit> get() = resultPageServedDeferred

    init {
        server.createContext(BbCloudOAuthConfig.CALLBACK_PATH) { exchange -> handleCallbackContext(exchange = exchange) }
        server.createContext("/") { exchange -> handleFallback(exchange = exchange) }
        server.executor = executor
        server.start()
        LOG.debug("OAuth callback server listening on ${server.address}")
    }

    override fun publishOutcome(page: OAuthResultPage) {
        outcome = page
        outcomeLatch.countDown()
    }

    private fun handleCallbackContext(exchange: HttpExchange) {
        exchange.use {
            // The JDK context matches sub-paths too, so dispatch on the exact path here.
            when (exchange.requestURI.path) {
                BbCloudOAuthConfig.CALLBACK_PATH -> handleCallback(exchange = exchange)
                BbCloudOAuthConfig.RESULT_PATH -> handleResultPage(exchange = exchange)
                else -> respondNotFound(exchange = exchange)
            }
        }
    }

    private fun handleCallback(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            respondStatus(exchange = exchange, status = 405)
            return
        }
        val params = parseQuery(rawQuery = exchange.requestURI.rawQuery)
        val stateMatches = constantTimeEquals(a = params["state"], b = expectedState)
        val hasError = params.containsKey("error")
        val hasCode = params.containsKey("code")
        LOG.debug("OAuth callback: hasCode=$hasCode, hasError=$hasError, stateMatches=$stateMatches")

        if (!stateMatches) {
            // A missing or mismatching state does not complete the callback; keep waiting for the real redirect.
            respondStatus(exchange = exchange, status = 400)
            return
        }

        // Respond with the redirect and close the exchange BEFORE completing the callback, so the token exchange and
        // outcome publishing happen after the browser has already been sent to the result page.
        respondRedirect(exchange = exchange)

        val result = when {
            hasError -> OAuthCallbackResult.Denied(
                error = params["error"].orEmpty(),
                description = params["error_description"]
            )
            hasCode -> OAuthCallbackResult.Code(code = params["code"].orEmpty())
            else -> OAuthCallbackResult.Invalid(reason = "no code or error in callback")
        }
        callbackDeferred.complete(result)
    }

    private fun handleResultPage(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            respondStatus(exchange = exchange, status = 405)
            return
        }
        // The result page cannot be probed before a valid callback has happened.
        if (!callbackDeferred.isCompleted) {
            respondNotFound(exchange = exchange)
            return
        }
        val gotOutcome = outcomeLatch.await(resultPageWaitMillis, TimeUnit.MILLISECONDS)
        val html = if (gotOutcome) {
            OAuthCallbackPage.render(page = outcome ?: OAuthResultPage(success = false, message = ""))
        } else {
            OAuthCallbackPage.pending()
        }
        writeHtml(exchange = exchange, status = 200, html = html)
        resultPageServedDeferred.complete(Unit)
    }

    private fun handleFallback(exchange: HttpExchange) {
        exchange.use {
            LOG.debug("OAuth server ignoring request to ${exchange.requestURI.path}")
            respondNotFound(exchange = exchange)
        }
    }

    private fun respondRedirect(exchange: HttpExchange) {
        exchange.responseHeaders.add("Location", BbCloudOAuthConfig.RESULT_PATH)
        exchange.responseHeaders.add("Cache-Control", "no-store")
        exchange.responseHeaders.add("Referrer-Policy", "no-referrer")
        exchange.sendResponseHeaders(302, -1)
    }

    private fun writeHtml(exchange: HttpExchange, status: Int, html: String) {
        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.responseHeaders.add("Cache-Control", "no-store")
        exchange.responseHeaders.add("Referrer-Policy", "no-referrer")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { stream -> stream.write(bytes) }
    }

    private fun respondNotFound(exchange: HttpExchange) {
        respondStatus(exchange = exchange, status = 404)
    }

    private fun respondStatus(exchange: HttpExchange, status: Int) {
        exchange.sendResponseHeaders(status, -1)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        LOG.debug("Closing OAuth callback server")
        server.stop(0)
        executor.shutdownNow()
        if (!callbackDeferred.isCompleted) {
            callbackDeferred.cancel()
        }
        if (!resultPageServedDeferred.isCompleted) {
            resultPageServedDeferred.cancel()
        }
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) {
            return emptyMap()
        }
        return rawQuery.split("&")
            .mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) {
                    null
                } else {
                    val name = URLDecoder.decode(pair.substring(0, index), StandardCharsets.UTF_8)
                    val value = URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8)
                    name to value
                }
            }
            .toMap()
    }

    private fun constantTimeEquals(a: String?, b: String?): Boolean {
        if (a == null || b == null) {
            return false
        }
        return MessageDigest.isEqual(a.toByteArray(StandardCharsets.UTF_8), b.toByteArray(StandardCharsets.UTF_8))
    }

    companion object {
        private val LOG = logger<JdkOAuthCallbackServer>()
    }
}

private object DaemonThreadFactory : ThreadFactory {
    override fun newThread(runnable: Runnable): Thread {
        val thread = Thread(runnable, "BbCloud-OAuth-Callback")
        thread.isDaemon = true
        return thread
    }
}