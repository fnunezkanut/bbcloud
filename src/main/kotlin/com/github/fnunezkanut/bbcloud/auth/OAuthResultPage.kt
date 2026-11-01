package com.github.fnunezkanut.bbcloud.auth

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.intellij.openapi.util.text.StringUtil

/** The terminal outcome shown on the `/bbcloud/done` page. [message] is already user-facing text. */
data class OAuthResultPage(val success: Boolean, val message: String)

/**
 * Renders the self-contained HTML shown in the browser after the OAuth redirect. No scripts and no external resources;
 * all text comes from the bundle and every dynamic value is XML-escaped. The authorization code and state are never
 * included.
 */
internal object OAuthCallbackPage {
    fun render(page: OAuthResultPage): String {
        val heading = if (page.success) {
            BbCloudBundle.message("oauth.callback.page.success")
        } else {
            BbCloudBundle.message("oauth.callback.page.failure", page.message)
        }
        return html(body = heading)
    }

    /** The page shown when the outcome has not arrived within the wait window; tells the user to return to the IDE. */
    fun pending(): String {
        return html(body = BbCloudBundle.message("oauth.callback.page.pending"))
    }

    private fun html(body: String): String {
        val title = StringUtil.escapeXmlEntities(BbCloudBundle.message("oauth.callback.page.title"))
        val escapedBody = StringUtil.escapeXmlEntities(body)
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>$title</title>
            <style>
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #1e1f22; color: #dfe1e5; margin: 0; }
            .card { max-width: 32rem; margin: 6rem auto; padding: 2rem; background: #2b2d30; border-radius: 12px; text-align: center; }
            h1 { font-size: 1.1rem; font-weight: 600; margin: 0; }
            </style>
            </head>
            <body>
            <div class="card"><h1>$escapedBody</h1></div>
            </body>
            </html>
            """.trimIndent()
    }
}