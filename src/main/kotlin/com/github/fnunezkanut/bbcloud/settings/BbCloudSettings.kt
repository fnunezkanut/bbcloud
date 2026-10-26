package com.github.fnunezkanut.bbcloud.settings

import com.github.fnunezkanut.bbcloud.project.WatchedProjectMapping
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger

@State(name = "BbCloudSettings", storages = [Storage("bbcloud-settings.xml")])
class BbCloudSettings : SimplePersistentStateComponent<BbCloudSettings.State>(State()) {
    class State : BaseState() {
        var organizationUrl by string("")

        var workspaceSlug by string("")

        var projectsDirectory by string("")

        /**
         * The watched projects, as a single-level map keyed by repository slug whose value is the watched boolean.
         * There is no workspace layer: the map is a rebuildable cache of the repositories found under the currently
         * configured projects directory, workspace, and API token. Any change to those inputs empties the map first
         * and then rebuilds it (see [com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService.saveProjectsDirectory]
         * and [com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService.saveSetup]), so there is never a moment
         * where slugs from two different workspaces coexist. As a consequence of emptying first, a transient verify
         * failure right after a directory change leaves the map empty rather than restoring stale entries; that is
         * intentional. The local folder path and the former manual flag are not persisted.
         *
         * The persisted option is deliberately named `watchedRepos`, not `watchedProjects`. An earlier build stored
         * `watchedProjects` as a two-level `map<String, WorkspaceWatchedProjectsState>`; when the field type was
         * flattened to `map<String, Boolean>`, deserializing that legacy nested element into a boolean map faulted and
         * corrupted the load of the whole component, silently dropping `workspaceSlug` and `projectsDirectory` as well.
         * Renaming the option makes the platform ignore the stale legacy `watchedProjects` element on load instead of
         * mis-deserializing it, so the other settings survive. The map is a rebuildable cache, so starting empty and
         * repopulating on the first rescan is the intended migration (no legacy values are carried over).
         */
        var watchedRepos by map<String, Boolean>()

        /**
         * The configured avatar/icon URL of each watched repository, as a flat map keyed by repository slug. Like
         * [watchedRepos], this is a rebuildable cache tied to the currently configured projects directory, workspace,
         * and API token: it is written only on a Sync/rescan (from the repo-existence call discovery already makes),
         * read with zero API calls when the Watched Projects tab binds, and cleared whenever [watchedRepos] is cleared
         * (see [clearWatchedProjects]). Only the URL is persisted, never the image bytes; the bytes live in an
         * in-memory cache and re-download cheaply over the public CDN because they carry no API token. A repository
         * without an avatar has no entry here, which the UI renders as a neutral placeholder.
         */
        var repoAvatarUrls by map<String, String>()
    }

    /** Legacy value kept only so existing installs can migrate the URL to a slug; see [organizationUrl]. */
    var organizationUrl: String
        get() = state.organizationUrl.orEmpty()
        set(value) {
            state.organizationUrl = value
        }

    var workspaceSlug: String
        get() = state.workspaceSlug.orEmpty()
        set(value) {
            state.workspaceSlug = value
        }

    /** Normalized absolute path of the folder repositories are checked out into; empty when unset. */
    var projectsDirectory: String
        get() = state.projectsDirectory.orEmpty()
        set(value) {
            state.projectsDirectory = value
        }

    /**
     * Rebuilds the persisted flat map into a list of domain mappings. The map is keyed by repository slug alone, so
     * there is no persisted workspace: each mapping comes back with an empty [WatchedProjectMapping.workspace], which
     * the service layer attaches from the configured slug where a workspace is needed. The local folder path is not
     * persisted either, so each mapping comes back with an empty [WatchedProjectMapping.localFolderPath]; callers that
     * need a real path recompute it from disk (see the Watched Projects bind path). A deterministic sorted order by
     * repository slug is imposed because `BaseState.map()` does not preserve insertion order and every consumer maps by
     * key rather than by position.
     */
    val watchedProjects: List<WatchedProjectMapping>
        get() {
            val mappings = mutableListOf<WatchedProjectMapping>()
            for ((repoSlug, watched) in state.watchedRepos) {
                mappings.add(
                    WatchedProjectMapping(
                        localFolderPath = "",
                        remoteRepoSlug = repoSlug,
                        workspace = "",
                        watched = watched
                    )
                )
            }
            return mappings.sortedBy { mapping -> mapping.remoteRepoSlug }
        }

    fun replaceWatchedProjects(mappings: List<WatchedProjectMapping>) {
        state.watchedRepos.clear()
        for (mapping in mappings) {
            if (state.watchedRepos.containsKey(mapping.remoteRepoSlug)) {
                thisLogger().debug("Duplicate watched project ${mapping.remoteRepoSlug}; last entry wins")
            }
            state.watchedRepos[mapping.remoteRepoSlug] = mapping.watched
        }
    }

    /** A read-only snapshot of the persisted avatar URLs, keyed by repository slug. */
    val repoAvatarUrls: Map<String, String>
        get() = state.repoAvatarUrls.toMap()

    /**
     * Replaces the persisted avatar URLs with [urls], storing only the non-null, non-blank hrefs. Used by the
     * clear-first rescan path, where a fresh configuration means a fresh URL set.
     */
    fun replaceRepoAvatarUrls(urls: Map<String, String?>) {
        state.repoAvatarUrls.clear()
        for ((slug, url) in urls) {
            val trimmed = url?.takeIf { it.isNotBlank() } ?: continue
            state.repoAvatarUrls[slug] = trimmed
        }
    }

    /**
     * Upserts the non-null, non-blank hrefs of [urls], leaving entries not present in [urls] untouched. Used by the
     * carry-over (non-clear-first) Sync path so rediscovered repositories refresh their URL while others are kept.
     */
    fun mergeRepoAvatarUrls(urls: Map<String, String?>) {
        for ((slug, url) in urls) {
            val trimmed = url?.takeIf { it.isNotBlank() } ?: continue
            state.repoAvatarUrls[slug] = trimmed
        }
    }

    /**
     * Clears the watched map AND the avatar-URL map, so every caller (incl.
     * [com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService.resetAll],
     * [com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService.resetApiToken], and
     * [com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService.clearProjectsDirectory]) wipes the avatar
     * URLs for free. Both maps share the same "rebuildable cache keyed by slug" lifecycle.
     */
    fun clearWatchedProjects() {
        state.watchedRepos.clear()
        state.repoAvatarUrls.clear()
    }

    companion object {
        fun getInstance(): BbCloudSettings {
            return ApplicationManager.getApplication().service()
        }
    }
}