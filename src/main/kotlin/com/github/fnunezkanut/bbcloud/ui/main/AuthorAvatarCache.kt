package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketAvatarClient
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBImageIcon
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.swing.Icon
import kotlin.math.ceil

class AuthorAvatarCache(private val avatarClient: BitbucketAvatarClient = BitbucketAvatarClient(), private val avatarSize: Int = DEFAULT_AVATAR_SIZE) {
    private val icons = ConcurrentHashMap<String, Icon>()
    private val loadingUrls: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val downloadPermits = Semaphore(MAX_PARALLEL_DOWNLOADS)
    private val placeholderIcon: Icon by lazy {
        if (avatarSize == DEFAULT_AVATAR_SIZE) {
            PLACEHOLDER_ICON
        } else {
            IconUtil.scale(PLACEHOLDER_ICON, null, avatarSize.toFloat() / DEFAULT_AVATAR_SIZE)
        }
    }

    /** Returns the repository/project placeholder scaled to this cache's configured avatar size. */
    fun repositoryPlaceholderIcon(): Icon {
        return if (avatarSize == DEFAULT_AVATAR_SIZE) {
            REPOSITORY_PLACEHOLDER_ICON
        } else {
            // IntelliJ has no consistently sized project icon variant, so scale only this fallback to match real avatars.
            IconUtil.scale(REPOSITORY_PLACEHOLDER_ICON, null, avatarSize.toFloat() / DEFAULT_AVATAR_SIZE)
        }
    }

    fun iconFor(url: String?): Icon {
        return iconFor(url = url, fallback = placeholderIcon)
    }

    /**
     * Resolves the cached icon for [url], or [fallback] when the image has not loaded yet or [url] is null. Callers
     * rendering a repository/project row pass [REPOSITORY_PLACEHOLDER_ICON] so an unloaded avatar shows a project
     * icon rather than the person [PLACEHOLDER_ICON].
     */
    fun iconFor(url: String?, fallback: Icon): Icon {
        return url?.let { icons[it] } ?: fallback
    }

    /**
     * Empties the decoded-icon cache and the in-flight download set, so subsequent [iconFor] calls return the
     * placeholder until the images are prefetched again. Used by the Reset Plugin flow to drop stale icons; it is
     * additive and does not affect existing callers.
     */
    fun clear() {
        val cleared = icons.size
        icons.clear()
        loadingUrls.clear()
        LOG.debug("Cleared $cleared cached avatars")
    }

    fun prefetch(urls: Collection<String>, coroutineScope: CoroutineScope, onLoaded: () -> Unit) {
        val pendingUrls = urls.distinct().filter { url -> !icons.containsKey(url) && loadingUrls.add(url) }
        if (pendingUrls.isEmpty()) {
            return
        }
        LOG.debug("Prefetching ${pendingUrls.size} author avatars")
        val devicePixels = ceil(JBUI.scale(avatarSize) * JBUIScale.sysScale()).toInt()
        pendingUrls.forEach { url ->
            coroutineScope.launch {
                try {
                    val bytes = downloadPermits.withPermit { avatarClient.fetchAvatar(url) } ?: return@launch
                    val image = decodeAvatar(bytes = bytes, sizePx = devicePixels) ?: return@launch
                    icons[url] = JBImageIcon(ImageUtil.ensureHiDPI(image, ScaleContext.create()))
                    ApplicationManager.getApplication().invokeLater { onLoaded() }
                } finally {
                    loadingUrls.remove(url)
                }
            }
        }
    }

    companion object {
        private val LOG = logger<AuthorAvatarCache>()

        val PLACEHOLDER_ICON: Icon = AllIcons.General.User

        // Fallback for repository/project rows (not authors) whose repository avatar has not loaded or does not
        // exist; a project icon, rather than the person [PLACEHOLDER_ICON], so a project never shows as a person.
        val REPOSITORY_PLACEHOLDER_ICON: Icon = AllIcons.Nodes.IdeaProject

        const val DEFAULT_AVATAR_SIZE = 16
        private const val MAX_PARALLEL_DOWNLOADS = 4

        fun decodeAvatar(bytes: ByteArray, sizePx: Int): BufferedImage? {
            val source = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return null
            val scaled = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB)
            val graphics = scaled.createGraphics()
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                graphics.drawImage(source, 0, 0, sizePx, sizePx, null)
            } finally {
                graphics.dispose()
            }
            return ImageUtil.createCircleImage(scaled)
        }
    }
}