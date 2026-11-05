package com.github.fnunezkanut.bbcloud.ui.recentprojects

import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.project.normalizedFolderKey
import com.github.fnunezkanut.bbcloud.ui.main.AuthorAvatarCache
import kotlinx.coroutines.CoroutineScope
import javax.swing.Icon

/** The repository avatars the Recent Projects tab shows, behind an interface so the view model runs without the platform. */
internal interface RepoAvatarProvider {
    /** Persisted slug to avatar URL map (plugin storage, no network call). */
    fun avatarUrlsBySlug(): Map<String, String>

    /** Normalized local folder (see [normalizedFolderKey]) to repository slug, for each watched mapping. Reads the disk. */
    fun slugsByLocalFolder(): Map<String, String>

    /** The decoded avatar for [url], or null while it is not loaded. */
    fun avatarFor(url: String): Icon?

    /** Starts downloading [urls]; [onLoaded] runs on the EDT for each loaded image. */
    fun prefetch(urls: Collection<String>, onLoaded: () -> Unit)

    fun clear()
}

/** A provider without any avatars. */
internal object NoRepoAvatars : RepoAvatarProvider {
    override fun avatarUrlsBySlug(): Map<String, String> {
        return emptyMap()
    }

    override fun slugsByLocalFolder(): Map<String, String> {
        return emptyMap()
    }

    override fun avatarFor(url: String): Icon? {
        return null
    }

    override fun prefetch(urls: Collection<String>, onLoaded: () -> Unit) = Unit

    override fun clear() = Unit
}

/** Uses the same persisted avatar URLs and decoded-image cache (same size) as the Watched Projects tab. */
internal class PlatformRepoAvatarProvider(private val configurationService: BbCloudConfigurationService, private val coroutineScope: CoroutineScope) :
    RepoAvatarProvider {
    private val avatarCache = AuthorAvatarCache(avatarSize = AVATAR_SIZE)

    override fun avatarUrlsBySlug(): Map<String, String> {
        return configurationService.repoAvatarUrlsForCurrentWorkspace()
    }

    override fun slugsByLocalFolder(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        configurationService.watchedProjectsForCurrentWorkspaceWithLocalPaths()
            .filter { mapping -> mapping.localFolderPath.isNotBlank() }
            .forEach { mapping ->
                val key = normalizedFolderKey(path = mapping.localFolderPath)
                if (key != null) {
                    result[key] = mapping.remoteRepoSlug
                }
            }
        return result
    }

    override fun avatarFor(url: String): Icon? {
        return avatarCache.cachedIconOrNull(url = url)
    }

    override fun prefetch(urls: Collection<String>, onLoaded: () -> Unit) {
        avatarCache.prefetch(
            urls = urls,
            coroutineScope = coroutineScope,
            onLoaded = onLoaded
        )
    }

    override fun clear() {
        avatarCache.clear()
    }

    private companion object {
        const val AVATAR_SIZE = 32
    }
}