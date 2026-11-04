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

        /**
         * The Exclude checkbox states and the Created by dropdown of the Current Project tab. Each is a view preference,
         * persisted across restarts and independent of the Workspace tab's states (see
         * [workspaceExcludeApprovedByMePullRequests] etc.). The option names are part of the persisted XML; do not
         * rename them without a migration (see [watchedRepos] for the earlier corruption incident). `BaseState` omits
         * values equal to the default, so existing installs get these defaults automatically with no migration.
         *
         * Default true: hide pull requests that are not Open (Merged, Declined).
         */
        var projectExcludeResolvedPullRequests by property(true)

        /** Default false: when true, hide the PRs the current user has approved. */
        var projectExcludeApprovedByMePullRequests by property(false)

        /** Default true: hide draft pull requests. */
        var projectExcludeDraftPullRequests by property(true)

        /**
         * The user scope dropdown of the Current Project tab, persisted as the [PullRequestUserScope] name. Default
         * [PullRequestUserScope.AnyUser]. The name is new, so the removed boolean `projectExcludeMinePullRequests`
         * option is never read as this value. The property keeps its persisted name, so saved choices survive the
         * rename of the type.
         */
        var projectPullRequestAuthorScope by string(PullRequestUserScope.AnyUser.name)

        /**
         * The Exclude checkbox states and the Created by dropdown of the Workspace tab. There is no workspace Resolved
         * property: the two workspace sources request open pull requests only, so that option is neither shown nor
         * persisted. Each is a view preference, persisted across restarts and independent of the Current Project
         * states. The option names are part of the persisted XML; do not rename them without a migration.
         */
        var workspaceExcludeApprovedByMePullRequests by property(false)

        /** Default true: hide draft pull requests. */
        var workspaceExcludeDraftPullRequests by property(true)

        /**
         * The user scope dropdown of the Workspace tab, persisted as the [PullRequestUserScope] name. Default
         * [PullRequestUserScope.AnyUser]. See [projectPullRequestAuthorScope] for why the name is new and why the
         * property keeps its persisted name.
         */
        var workspacePullRequestAuthorScope by string(PullRequestUserScope.AnyUser.name)

        /**
         * The divider position of the pull-request page split layout, as the fraction of the width given to the left
         * pane. It is a view preference shared by all pull-request tabs and survives IDE restarts. The option name is
         * part of the persisted XML; do not rename it without a migration. The value is clamped to
         * [MIN_PR_PAGE_SPLIT_FRACTION]..[MAX_PR_PAGE_SPLIT_FRACTION] on read and write, so a hand-edited value cannot
         * push the divider to an edge.
         *
         * Default [DEFAULT_PR_PAGE_SPLIT_FRACTION]: the left pane takes three quarters of the width.
         */
        var prPageSplitFraction by property(DEFAULT_PR_PAGE_SPLIT_FRACTION)

        /**
         * Default false: when true, the Watched Projects table lists only watched projects. It is a view preference,
         * so it is kept across a settings reset. The option name is part of the persisted XML; do not rename it
         * without a migration.
         */
        var watchedProjectsShowOnlyWatched by property(false)
    }

    /** Whether the Watched Projects table lists only watched projects. See [State.watchedProjectsShowOnlyWatched]. */
    var watchedProjectsShowOnlyWatched: Boolean
        get() = state.watchedProjectsShowOnlyWatched
        set(value) {
            state.watchedProjectsShowOnlyWatched = value
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

    /**
     * The pull-request page divider position as the left pane fraction, clamped to
     * [MIN_PR_PAGE_SPLIT_FRACTION]..[MAX_PR_PAGE_SPLIT_FRACTION]. See [State.prPageSplitFraction].
     */
    var prPageSplitFraction: Float
        get() = state.prPageSplitFraction.coerceIn(MIN_PR_PAGE_SPLIT_FRACTION, MAX_PR_PAGE_SPLIT_FRACTION)
        set(value) {
            state.prPageSplitFraction = value.coerceIn(MIN_PR_PAGE_SPLIT_FRACTION, MAX_PR_PAGE_SPLIT_FRACTION)
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

    /**
     * Reads the persisted Exclude: checkbox states of [scope]. For [PullRequestListScope.Workspace] the returned filter
     * always carries `excludeResolved = false`, because that tab offers no Resolved checkbox.
     */
    fun pullRequestListFilter(scope: PullRequestListScope): PullRequestListFilter {
        return when (scope) {
            PullRequestListScope.CurrentProject -> PullRequestListFilter(
                excludeResolved = state.projectExcludeResolvedPullRequests,
                excludeApprovedByMe = state.projectExcludeApprovedByMePullRequests,
                excludeDrafts = state.projectExcludeDraftPullRequests,
                userScope = parseUserScope(stored = state.projectPullRequestAuthorScope)
            )
            PullRequestListScope.Workspace -> PullRequestListFilter(
                excludeResolved = false,
                excludeApprovedByMe = state.workspaceExcludeApprovedByMePullRequests,
                excludeDrafts = state.workspaceExcludeDraftPullRequests,
                userScope = parseUserScope(stored = state.workspacePullRequestAuthorScope)
            )
        }
    }

    /**
     * Persists the Exclude: checkbox states of [scope]. For [PullRequestListScope.Workspace], [filter]'s
     * `excludeResolved` is ignored, because that tab has no Resolved checkbox to persist.
     */
    fun savePullRequestListFilter(scope: PullRequestListScope, filter: PullRequestListFilter) {
        when (scope) {
            PullRequestListScope.CurrentProject -> {
                state.projectExcludeResolvedPullRequests = filter.excludeResolved
                state.projectExcludeApprovedByMePullRequests = filter.excludeApprovedByMe
                state.projectExcludeDraftPullRequests = filter.excludeDrafts
                state.projectPullRequestAuthorScope = filter.userScope.name
            }
            PullRequestListScope.Workspace -> {
                state.workspaceExcludeApprovedByMePullRequests = filter.excludeApprovedByMe
                state.workspaceExcludeDraftPullRequests = filter.excludeDrafts
                state.workspacePullRequestAuthorScope = filter.userScope.name
            }
        }
    }

    companion object {
        const val DEFAULT_PR_PAGE_SPLIT_FRACTION = 0.75f
        const val MIN_PR_PAGE_SPLIT_FRACTION = 0.2f
        const val MAX_PR_PAGE_SPLIT_FRACTION = 0.8f

        fun getInstance(): BbCloudSettings {
            return ApplicationManager.getApplication().service()
        }

        /** Maps a stored [PullRequestUserScope] name back to the enum. Unset or unknown values fall back to AnyUser. */
        private fun parseUserScope(stored: String?): PullRequestUserScope {
            return PullRequestUserScope.entries.firstOrNull { scope -> scope.name == stored } ?: PullRequestUserScope.AnyUser
        }
    }
}