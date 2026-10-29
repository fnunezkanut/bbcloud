package com.github.fnunezkanut.bbcloud.bitbucket

import com.github.fnunezkanut.bbcloud.ui.ScreenScale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI
import kotlin.math.ceil

/** Upgrades recognized avatar URLs to a source size suitable for the device-pixel rendering target. */
object AvatarUrlUpgrader {
    private const val DEFAULT_LOGICAL_AVATAR_SIZE = 48
    private const val MAX_SOURCE_SIZE = 512
    private val bitbucketAvatarSizePattern = Regex("(/avatar/)\\d+(?=/|$)")

    /**
     * Returns a larger URL only for recognized avatar providers. Unknown URLs and malformed URLs are preserved
     * exactly so an opaque provider URL cannot be broken by normalization.
     */
    fun upgrade(url: String?, logicalSize: Int = DEFAULT_LOGICAL_AVATAR_SIZE, scale: Double = ScreenScale.maxOfConnectedScreens().toDouble()): String? {
        if (url == null) {
            return null
        }
        val sourceSize = devicePixelSize(logicalSize = logicalSize, scale = scale)
        val parsed = runCatching { URI(url) }.getOrNull() ?: return url
        val host = parsed.host?.lowercase() ?: return url
        return when {
            isGravatarHost(host) -> upgradeGravatarUrl(url = url, sourceSize = sourceSize)
            isBitbucketHost(host) -> upgradeBitbucketAvatarPath(url = url, rawPath = parsed.rawPath, sourceSize = sourceSize)
            else -> url
        }
    }

    internal fun devicePixelSize(logicalSize: Int, scale: Double): Int {
        return ceil(logicalSize.coerceAtLeast(1) * scale.coerceAtLeast(1.0)).toInt().coerceAtMost(MAX_SOURCE_SIZE)
    }

    private fun upgradeGravatarUrl(url: String, sourceSize: Int): String {
        val httpUrl = url.toHttpUrlOrNull() ?: return url
        return httpUrl.newBuilder()
            .setQueryParameter("s", sourceSize.toString())
            .build()
            .toString()
    }

    private fun upgradeBitbucketAvatarPath(url: String, rawPath: String?, sourceSize: Int): String {
        if (rawPath == null) {
            return url
        }
        val match = bitbucketAvatarSizePattern.find(rawPath) ?: return url
        val upgradedPath = rawPath.replaceRange(
            match.range,
            "${match.groupValues[1]}$sourceSize"
        )
        return url.replace(rawPath, upgradedPath)
    }

    private fun isGravatarHost(host: String): Boolean {
        return host == "gravatar.com" || host.endsWith(".gravatar.com")
    }

    private fun isBitbucketHost(host: String): Boolean {
        return host == "bitbucket.org" || host.endsWith(".bitbucket.org")
    }
}