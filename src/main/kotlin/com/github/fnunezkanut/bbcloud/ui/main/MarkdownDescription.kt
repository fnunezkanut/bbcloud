@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.jewel.intui.markdown.bridge.ProvideMarkdownStyling
import org.jetbrains.jewel.markdown.Markdown
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import java.net.URI
import java.net.URISyntaxException

/**
 * Renders pull-request Markdown and shows any image references as clickable links, because Bitbucket serves inline
 * description images only to an authenticated browser session and exposes no plugin-usable download endpoint. Clicking
 * a link opens the image URL in the browser, where the user's session can resolve it. Raw HTML images remain sanitized
 * as plain text.
 */
@Composable
internal fun MarkdownDescription(markdown: String) {
    val content = remember(markdown) { normalizeDescription(markdown) }
    val processor = remember { MarkdownProcessor() }
    ProvideMarkdownStyling {
        Markdown(
            markdown = content,
            modifier = Modifier.fillMaxWidth(),
            onUrlClick = { url: String ->
                LOG.info("User opened a pull-request description link in browser")
                BrowserUtil.browse(url)
            },
            processor = processor
        )
    }
}

internal fun normalizeDescription(markdown: String): String {
    // Strip the trailing `{: ... }` attribute fragment first so the image -> link transform sees a clean `![alt](url)`.
    val withoutImageAttributes = markdown.replace(IMAGE_ATTRIBUTE_FRAGMENT) { it.groupValues[1] }
    // Convert Markdown images into Markdown links; Bitbucket images are not fetchable by the plugin, so we never
    // render them inline.
    val withLinks = imagesToLinks(withoutImageAttributes)
    // HTML `<img>` tags are intentionally dropped (no reliable alt/label, and the Swing precedent
    // `ConversationThreadRules.removeImages` already removes them for the same authentication reason).
    val withoutHtmlImages = withLinks.replace(IMAGE_TAG, "")
    return if (HTML_TAG.containsMatchIn(withoutHtmlImages)) {
        StringUtil.stripHtml(withoutHtmlImages, " ").trim()
    } else {
        withoutHtmlImages
    }
}

/**
 * Rewrites every Markdown image `![alt](url)` into a Markdown link `[label](url)`. The label is the alt text when it is
 * non-blank, otherwise the decoded file name taken from the URL, otherwise a localized generic label. The URL is
 * preserved exactly as received so the browser session can resolve the real image. Malformed URLs never throw; they
 * fall back to the generic label.
 */
internal fun imagesToLinks(markdown: String): String {
    val conversions = MARKDOWN_IMAGE.findAll(markdown).count()
    LOG.debug("Converting pull-request description images to links with conversionCount=$conversions")
    return MARKDOWN_IMAGE.replace(markdown) { match ->
        val alt = match.groupValues[1]
        val url = match.groupValues[2]
        val label = linkLabel(alt = alt, url = url)
        "[$label]($url)"
    }
}

private fun linkLabel(alt: String, url: String): String {
    if (alt.isNotBlank()) {
        return alt
    }
    val fileName = decodedFileName(url = url)
    if (fileName != null && fileName.isNotBlank()) {
        return fileName
    }
    return BbCloudBundle.message("main.branch.overview.description.imageLink")
}

/**
 * Extracts the decoded file name from a URL, returning null when none can be derived. Never throws on a malformed URL;
 * a parse failure yields null so the caller can fall back to the generic label.
 */
private fun decodedFileName(url: String): String? {
    return try {
        // URI#getPath() already percent-decodes the path, so no extra URLDecoder pass is needed (and double-decoding
        // could corrupt a file name that legitimately contains a percent sign).
        val path = URI(url).path
        if (path.isNullOrBlank()) {
            null
        } else {
            val lastSegment = path.substringAfterLast(delimiter = '/')
            if (lastSegment.isBlank()) {
                null
            } else {
                lastSegment
            }
        }
    } catch (failure: URISyntaxException) {
        LOG.debug("Could not derive a file name from a description image URL", failure)
        null
    } catch (failure: IllegalArgumentException) {
        LOG.debug("Could not derive a file name from a description image URL", failure)
        null
    }
}

private object MarkdownDescriptionLogCategory

private val LOG = logger<MarkdownDescriptionLogCategory>()
private val HTML_TAG = Regex("<[^>]+>")
private val IMAGE_TAG = Regex("(?is)<img\\b[^>]*>")
private val MARKDOWN_IMAGE = Regex("!\\[([^]]*)]\\(([^)]*)\\)")
private val IMAGE_ATTRIBUTE_FRAGMENT = Regex("(!\\[[^]]*]\\([^)]*\\))\\{:[^}]*}")