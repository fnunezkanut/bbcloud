package com.github.fnunezkanut.bbcloud.external

import com.github.fnunezkanut.bbcloud.BbCloudBuildInfo
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.ui.worktree.PullRequestWorktreeOpener
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.origin
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.RestService
import org.jetbrains.io.response

/**
 * The browser entry point of the "Open in IntelliJ" feature, served at `/api/bbcloud/open`.
 *
 * - `GET` is the port-discovery ping. It answers `{"plugin":"bbcloud","version":...}` and reveals nothing else.
 * - `POST` opens a pull request. Only [BbCloudExternalOrigins.ALLOWED_EXTENSION_ORIGIN] may send it. The body is
 *   validated, the reply is `accepted` or `rejected`, and the open runs in the background.
 *
 * Requests run on a Netty I/O thread, so this class does no git, network or project work itself.
 */
class BbCloudOpenPullRequestRestService : RestService() {
    override fun getServiceName(): String {
        return SERVICE_NAME
    }

    override fun isMethodSupported(method: HttpMethod): Boolean {
        return method == HttpMethod.GET || method == HttpMethod.POST
    }

    /**
     * Lets requests from any Chrome extension reach [execute]. Without this, the platform's accessibility check drops a
     * request from an unknown extension and answers 404, and the extension cannot tell why it failed. [execute] keeps the
     * real gate: it answers the ping for any extension, and it accepts a POST only from
     * [BbCloudExternalOrigins.ALLOWED_EXTENSION_ORIGIN]. Other origins keep the platform's default handling.
     */
    override fun isOriginAllowed(request: HttpRequest): OriginCheckResult {
        val origin = request.origin
        val isChromeExtension = origin != null && origin.startsWith(BbCloudExternalOrigins.CHROME_EXTENSION_PREFIX)
        return if (isChromeExtension) OriginCheckResult.ALLOW else super.isOriginAllowed(request)
    }

    override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
        val startedAt = System.nanoTime()
        if (request.method() == HttpMethod.GET) {
            LOG.debug("Answering the bbcloud ping")
            sendJson(body = pingBody(), request = request, context = context)
        } else {
            handleOpen(request = request, context = context)
        }
        LOG.debug("Handled ${request.method()} $SERVICE_PATH in ${(System.nanoTime() - startedAt) / NANOS_PER_MILLI} ms")
        return null
    }

    private fun handleOpen(request: FullHttpRequest, context: ChannelHandlerContext) {
        if (request.origin != BbCloudExternalOrigins.ALLOWED_EXTENSION_ORIGIN) {
            LOG.info("Rejected an open request from an origin that is not the bbcloud extension")
            sendJson(body = statusBody(status = STATUS_REJECTED, message = FORBIDDEN_ORIGIN), request = request, context = context)
            return
        }

        val body = request.content().toString(Charsets.UTF_8)
        val configuredWorkspace = BbCloudConfigurationService.getInstance().workspaceSlugOrNull()
        when (val validation = BbCloudOpenRequestValidator.validate(body = body, configuredWorkspace = configuredWorkspace)) {
            is BbCloudOpenRequestValidation.Rejected -> {
                LOG.info("Rejected an open request: ${validation.message}")
                sendJson(body = statusBody(status = STATUS_REJECTED, message = validation.message), request = request, context = context)
            }
            is BbCloudOpenRequestValidation.Valid -> {
                val open = validation.request
                LOG.info("Accepted an open request for pull request #${open.prNumber}")
                PullRequestWorktreeOpener.getInstance().openInBackground(
                    workspace = open.workspace,
                    repoSlug = open.repoSlug,
                    prNumber = open.prNumber
                )
                val message = "Opening pull request #${open.prNumber} in IntelliJ."
                sendJson(body = statusBody(status = STATUS_ACCEPTED, message = message), request = request, context = context)
            }
        }
    }

    private fun pingBody(): String {
        val body = JsonObject()
        body.addProperty("plugin", PLUGIN_NAME)
        body.addProperty("version", BbCloudBuildInfo.version() ?: UNKNOWN_VERSION)
        return body.toString()
    }

    private fun statusBody(status: String, message: String): String {
        val body = JsonObject()
        body.addProperty("status", status)
        body.addProperty("message", message)
        return body.toString()
    }

    private fun sendJson(body: String, request: FullHttpRequest, context: ChannelHandlerContext) {
        val httpResponse = response(JSON_CONTENT_TYPE, Unpooled.copiedBuffer(body, Charsets.UTF_8))
        sendResponse(request, context, httpResponse)
    }

    companion object {
        private val LOG = logger<BbCloudOpenPullRequestRestService>()

        private const val SERVICE_NAME = "bbcloud/open"
        private const val SERVICE_PATH = "/api/bbcloud/open"
        private const val PLUGIN_NAME = "bbcloud"
        private const val UNKNOWN_VERSION = "unknown"
        private const val JSON_CONTENT_TYPE = "application/json"
        private const val STATUS_ACCEPTED = "accepted"
        private const val STATUS_REJECTED = "rejected"
        private const val FORBIDDEN_ORIGIN = "This browser extension is not allowed to open pull requests."
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}