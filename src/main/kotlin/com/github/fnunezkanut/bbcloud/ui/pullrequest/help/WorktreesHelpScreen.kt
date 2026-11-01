@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.github.fnunezkanut.bbcloud.ui.pullrequest.help

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.markdown.bridge.ProvideMarkdownStyling
import org.jetbrains.jewel.intui.markdown.bridge.create
import org.jetbrains.jewel.intui.markdown.bridge.styling.create
import org.jetbrains.jewel.intui.markdown.bridge.styling.extensions.github.tables.create
import org.jetbrains.jewel.markdown.Markdown
import org.jetbrains.jewel.markdown.extensions.github.tables.GfmTableStyling
import org.jetbrains.jewel.markdown.extensions.github.tables.GitHubTableProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.tables.GitHubTableRendererExtension
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.skia.Image as SkiaImage

/**
 * Renders the Work Trees help page. Prose goes through the Jewel Markdown composable with GFM tables enabled, and each
 * picture is drawn with Compose from the classpath bytes. The page is selectable and scrolls vertically.
 */
@Composable
fun WorktreesHelpScreen(markdown: String) {
    val segments = remember(markdown) { splitWorktreesHelpSegments(markdown = markdown) }
    val styling = remember(JewelTheme.instanceUuid) { MarkdownStyling.create() }
    val processor = remember { MarkdownProcessor(extensions = listOf(GitHubTableProcessorExtension)) }
    val blockRenderer = remember(styling) { createHelpBlockRenderer(styling = styling) }
    ProvideMarkdownStyling(
        markdownStyling = styling,
        markdownProcessor = processor,
        markdownBlockRenderer = blockRenderer
    ) {
        VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).padding(16.dp)) {
                segments.forEach { segment ->
                    when (segment) {
                        is WorktreesHelpSegment.Prose -> Markdown(
                            markdown = segment.markdown,
                            modifier = Modifier.fillMaxWidth(),
                            selectable = true,
                            onUrlClick = ::openHelpLink,
                            processor = processor,
                            blockRenderer = blockRenderer
                        )

                        is WorktreesHelpSegment.Picture -> WorktreesHelpPicture(picture = segment)
                    }
                }
            }
        }
    }
}

/** Builds the block renderer with the GFM table renderer. The table styling follows the current IDE theme. */
private fun createHelpBlockRenderer(styling: MarkdownStyling): MarkdownBlockRenderer {
    return MarkdownBlockRenderer.create(
        styling = styling,
        rendererExtensions = listOf(GitHubTableRendererExtension(GfmTableStyling.create(), styling))
    )
}

@Composable
private fun WorktreesHelpPicture(picture: WorktreesHelpSegment.Picture) {
    val bitmap = remember(picture.resourcePath) { decodeWorktreesHelpPicture(resourcePath = picture.resourcePath) }
    if (bitmap == null) {
        Text(text = picture.altText)
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = picture.altText,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High
        )
    }
}

/** Decodes a picture from the classpath, or returns null so the caller can show its alt text instead. */
private fun decodeWorktreesHelpPicture(resourcePath: String): ImageBitmap? {
    val bytes = WorktreesHelpMarkdown.readResourceBytes(resourcePath = resourcePath)
    if (bytes == null) {
        LOG.debug("The help picture is missing from the plugin resources: $resourcePath")
        return null
    }
    return try {
        SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (exception: RuntimeException) {
        LOG.debug("Could not decode the help picture $resourcePath", exception)
        null
    }
}

private fun openHelpLink(url: String) {
    LOG.info("User opened a link on the Work Trees help page in the browser")
    BrowserUtil.browse(url)
}

private object WorktreesHelpScreenLogCategory

private val LOG = logger<WorktreesHelpScreenLogCategory>()

private val CONTENT_MAX_WIDTH = 900.dp