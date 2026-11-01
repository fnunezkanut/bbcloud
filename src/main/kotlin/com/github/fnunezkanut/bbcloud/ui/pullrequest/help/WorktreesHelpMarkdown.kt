package com.github.fnunezkanut.bbcloud.ui.pullrequest.help

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.intellij.openapi.diagnostic.logger
import java.io.IOException

/**
 * Reads the Work Trees help page and its images from the plugin classpath. The Gradle build copies them from `docs/`
 * into the `help/` resource folder, so the README and the plugin share one copy of the content.
 */
internal object WorktreesHelpMarkdown {
    const val MARKDOWN_RESOURCE = "/help/worktrees.md"

    private val LOG = logger<WorktreesHelpMarkdown>()

    /** Returns the help page markdown, or a localized message when the resource is missing or cannot be read. */
    fun load(): String {
        val bytes = readResourceBytes(resourcePath = MARKDOWN_RESOURCE)
        if (bytes == null) {
            LOG.warn("The Work Trees help page is missing from the plugin resources: $MARKDOWN_RESOURCE")
            return BbCloudBundle.message("worktreesHelp.unavailable")
        }
        LOG.debug("Loaded the Work Trees help page (${bytes.size} bytes)")
        return bytes.toString(Charsets.UTF_8)
    }

    /** Returns the raw bytes of a classpath resource, or null when it is missing or cannot be read. */
    fun readResourceBytes(resourcePath: String): ByteArray? {
        return try {
            WorktreesHelpMarkdown::class.java.getResourceAsStream(resourcePath)?.use { stream -> stream.readBytes() }
        } catch (exception: IOException) {
            LOG.debug("Could not read the help resource $resourcePath", exception)
            null
        }
    }
}