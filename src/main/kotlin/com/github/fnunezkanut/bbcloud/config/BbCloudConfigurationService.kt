package com.github.fnunezkanut.bbcloud.config

import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryUrls
import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketWorkspaceClient
import com.github.fnunezkanut.bbcloud.bitbucket.OkHttpBitbucketRepositoryClient
import com.github.fnunezkanut.bbcloud.bitbucket.OkHttpBitbucketWorkspaceClient
import com.github.fnunezkanut.bbcloud.bitbucket.WorkspaceListResult
import com.github.fnunezkanut.bbcloud.credentials.BbCloudCredentialStore
import com.github.fnunezkanut.bbcloud.project.BbCloudWatchedProjectsDiscoveryService
import com.github.fnunezkanut.bbcloud.project.WatchedProjectMapping
import com.github.fnunezkanut.bbcloud.project.WatchedProjectsDiscoveryResult
import com.github.fnunezkanut.bbcloud.settings.BbCloudSettings
import com.github.fnunezkanut.bbcloud.settings.PullRequestListFilter
import com.github.fnunezkanut.bbcloud.settings.PullRequestListScope
import com.github.fnunezkanut.bbcloud.validation.BitbucketWorkspaceValidator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

class BbCloudConfigurationService(
    private val settings: BbCloudSettings,
    private val credentialStore: BbCloudCredentialStore,
    private val workspaceClient: BitbucketWorkspaceClient,
    private val discoveryService: BbCloudWatchedProjectsDiscoveryService
) {
    /**
     * Serializes [rescanWatchedProjects] so the scan triggers (a chosen projects directory, a workspace slug change or
     * token change in [saveSetup], and the Sync button) queue instead of interleaving their discover-and-write critical
     * section. Without it two concurrent scans could interleave the state rebuild and lose or corrupt the persisted
     * watched map.
     */
    private val rescanMutex = Mutex()

    /**
     * Whether the plugin is fully configured. True only when the workspace slug is set, an API token is stored, and the
     * projects directory is set and resolves to an existing directory on disk. "Valid directory" means the stored path
     * points to an existing directory (see [isExistingDirectory]), so a stored-but-deleted folder counts as not
     * configured. This is the single source of truth the tool window consults to gate every non-Settings tab behind the
     * centered go-to-settings link.
     *
     * The work tree root is no longer a separate configured directory: it is derived as `$projectsDirectory/.worktrees`
     * (see [workTreeDirectoryOrNull]) and is created on demand by the worktree flow, so its prior existence on disk is
     * not required for the plugin to be configured. The projects-directory existence check is therefore the sole
     * directory gate.
     */
    suspend fun isConfigured(): Boolean {
        val slug = storedWorkspaceSlug()
        if (slug.isEmpty()) {
            thisLogger().info("Not configured: no workspace slug")
            return false
        }
        if (!credentialStore.hasToken()) {
            thisLogger().info("Not configured: no stored token")
            return false
        }
        if (!isExistingDirectory(projectsDirectoryOrNull())) {
            thisLogger().info("Not configured: projects directory not set or not an existing directory")
            return false
        }
        return true
    }

    /** Whether [path] is set and resolves to an existing directory on disk, mirroring the discovery-service check. */
    private fun isExistingDirectory(path: String?): Boolean {
        if (path.isNullOrEmpty()) {
            return false
        }
        return Files.isDirectory(Paths.get(path))
    }

    /** Whether an API token is stored; the Settings panel reports this through the token field's own state. */
    suspend fun hasStoredToken(): Boolean {
        return credentialStore.hasToken()
    }

    suspend fun saveSetup(workspaceSlug: String, apiTokenInput: String): Result<Unit> {
        val normalizedSlug = BitbucketWorkspaceValidator.validate(workspaceSlug).getOrElse { error ->
            thisLogger().info("Setup rejected: invalid workspace slug (${error::class.simpleName})")
            return Result.failure(SaveException.WorkspaceRequired)
        }

        val tokenTrimmed = apiTokenInput.trim()
        val hasStoredToken = credentialStore.hasToken()

        if (!hasStoredToken && tokenTrimmed.isEmpty()) {
            thisLogger().info("Setup rejected: API token required and none stored")
            return Result.failure(SaveException.TokenRequired)
        }

        // A blank field means "keep the stored token", so it is not a token change. A non-empty field is a change only
        // when it differs from what is already stored. The token value itself is never logged.
        val previouslyStoredToken = credentialStore.getToken()
        val tokenChanged = tokenTrimmed.isNotEmpty() && tokenTrimmed != previouslyStoredToken

        val oldSlug = storedWorkspaceSlug()
        settings.workspaceSlug = normalizedSlug

        val tokenUpdated = tokenTrimmed.isNotEmpty()
        if (tokenUpdated) {
            credentialStore.saveToken(tokenTrimmed)
        }

        val slugChanged = oldSlug != normalizedSlug
        thisLogger().info(
            "Setup saved: workspaceSlug=$normalizedSlug, tokenUpdated=$tokenUpdated, tokenChanged=$tokenChanged, slugChanged=$slugChanged"
        )

        // A workspace-slug or API-token change invalidates the watched map: the previous entries referred to a
        // different workspace or were verified with a different token. Empty it once, then rebuild from the current
        // directory (when one is set) with a clear-first rescan so rediscovered repositories come in unwatched. When no
        // directory is set there is nothing to rebuild, so the map is simply left empty.
        if (slugChanged || tokenChanged) {
            thisLogger().info("Workspace slug or API token changed; emptying and rebuilding watched projects")
            clearWatchedProjects()
            if (projectsDirectoryOrNull() != null) {
                rescanWatchedProjects(clearFirst = true)
            }
        }

        return Result.success(Unit)
    }

    fun organizationUrlOrNull(): String? {
        val slug = workspaceSlugOrNull() ?: return null
        return "$BITBUCKET_ORGANIZATION_BASE$slug/"
    }

    /**
     * Resolves the configured workspace slug, migrating a legacy stored organization URL to a slug on first read so the
     * URL is only ever derived from the slug. Deliberately not `suspend`: every consumer of [organizationUrlOrNull] is
     * a plain read.
     */
    fun workspaceSlugOrNull(): String? {
        storedWorkspaceSlug().takeIf { it.isNotEmpty() }?.let { return it }
        val legacyUrl = settings.organizationUrl
        val migratedSlug = BitbucketRepositoryUrls.workspaceFromOrganizationUrl(legacyUrl) ?: return null
        thisLogger().info("Migrating stored organization URL to workspace slug: $migratedSlug")
        settings.workspaceSlug = migratedSlug
        return migratedSlug
    }

    suspend fun listWorkspaces(apiToken: String): WorkspaceListResult {
        return workspaceClient.listWorkspaces(apiToken)
    }

    /** The configured projects directory, or null when unset; the Workspace - Watched Projects panel binds it into its folder field. */
    fun projectsDirectoryOrNull(): String? {
        return settings.projectsDirectory.trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Stores [absolutePath] trimmed and normalized to an absolute path, so a directory picked through the folder chooser
     * and one typed with relative segments end up as the same stored value, matching the trim [projectsDirectoryOrNull]
     * applies on read. Persisted immediately rather than on Save, because the browse dialog has no later apply step.
     *
     * Also ensures the derived `$projectsDirectory/.worktrees` work tree root exists so it is already in place by the
     * time any PR-open flow runs. The directory is created idempotently and best-effort: a creation failure is logged
     * at `warn` but does not fail the save (the worktree flow re-creates it on demand). See [workTreeDirectoryOrNull].
     *
     * The watched map is emptied here, before the caller triggers a rescan, because the previous entries referred to a
     * different projects directory. The rescan then rebuilds the map from the new directory with every rediscovered
     * repository unwatched by default. Emptying is idempotent, so a caller that also triggers a clear-first rescan does
     * no harm.
     */
    fun saveProjectsDirectory(absolutePath: String) {
        val normalizedPath = Paths.get(absolutePath.trim()).toAbsolutePath().normalize().toString()
        settings.projectsDirectory = normalizedPath
        ensureWorkTreeRootExists()
        clearWatchedProjects()
        thisLogger().info("Projects directory saved: $normalizedPath")
    }

    /**
     * Creates the derived `$projectsDirectory/.worktrees` work tree root when it is derivable, idempotently and
     * best-effort: an [IOException] (permissions, read-only parent, or a non-directory file already at that path) is
     * logged at `warn` and swallowed so callers never fail because of the worktree folder.
     */
    private fun ensureWorkTreeRootExists() {
        val workTreeRoot = workTreeDirectoryOrNull() ?: return
        try {
            Files.createDirectories(Paths.get(workTreeRoot))
            thisLogger().debug("Work tree root ensured at $workTreeRoot")
        } catch (exception: IOException) {
            thisLogger().warn("Could not create the work tree root at $workTreeRoot", exception)
        }
    }

    /**
     * The derived work tree root, `$projectsDirectory/.worktrees`, normalized to an absolute path, or null when no
     * projects directory is set (there is nothing to derive from). The work tree root is no longer a stored setting; it
     * is always derived from the projects directory, so every consumer stays in sync automatically.
     */
    fun workTreeDirectoryOrNull(): String? {
        val projectsDirectory = projectsDirectoryOrNull() ?: return null
        return Paths.get(projectsDirectory).resolve(WORK_TREE_FOLDER_NAME).toAbsolutePath().normalize().toString()
    }

    /** The stored API token, or null; only the Settings panel fetches the workspace list with it. */
    suspend fun storedTokenOrNull(): String? {
        return credentialStore.getToken()
    }

    /**
     * Clears the stored projects directory and all watched project mappings. The workspace slug and API token are
     * kept.
     */
    fun clearProjectsDirectory() {
        settings.projectsDirectory = ""
        clearWatchedProjects()
        thisLogger().info("Projects directory cleared")
    }

    /**
     * Removes the stored API token and empties the watched map, since the map was verified with the now-removed token
     * and the "token change empties the map" rule applies to a reset too. The workspace slug and the projects directory
     * are kept, so the next Save and the Workspace - Watched Projects tab keep their values. The plugin is unconfigured
     * afterwards, because [isConfigured] requires a token.
     */
    suspend fun resetApiToken() {
        credentialStore.clearToken()
        clearWatchedProjects()
        thisLogger().info("Bitbucket Cloud PR Review API token reset")
    }

    /**
     * Wipes every persisted plugin setting and the stored API token: the token, the workspace slug, the legacy
     * organization URL, the projects directory, and all watched project mappings. The plugin is unconfigured
     * afterwards, so the Settings tab must be filled in again before the plugin can be used. Called by the Settings
     * tab's Reset Plugin button, which is a wider reset than [resetApiToken]. The derived work tree root is not deleted
     * from disk (deleting user-adjacent directories is out of scope); clearing the projects directory is enough for
     * [workTreeDirectoryOrNull] to report null until a projects directory is saved again.
     */
    suspend fun resetAll() {
        credentialStore.clearToken()
        settings.workspaceSlug = ""
        settings.organizationUrl = ""
        settings.projectsDirectory = ""
        clearWatchedProjects()
        // The PR list Exclude: filters are view preferences, not setup, so they are intentionally kept across a reset.
        thisLogger().info("Bitbucket Cloud PR Review plugin settings fully reset")
    }

    /** Reads the persisted Exclude: checkbox states of [scope]; the Workspace scope always reports excludeResolved=false. */
    fun pullRequestListFilter(scope: PullRequestListScope): PullRequestListFilter {
        return settings.pullRequestListFilter(scope = scope)
    }

    /** Persists the Exclude: checkbox states of [scope]; the Workspace scope ignores [filter]'s excludeResolved. */
    fun savePullRequestListFilter(scope: PullRequestListScope, filter: PullRequestListFilter) {
        settings.savePullRequestListFilter(scope = scope, filter = filter)
        thisLogger().info(
            "Saved $scope pull request list filter: resolved=${filter.excludeResolved}, mine=${filter.excludeMine}, " +
                "approvedByMe=${filter.excludeApprovedByMe}, drafts=${filter.excludeDrafts}"
        )
    }

    /**
     * The watched project mappings, or empty when no slug is configured. The persisted map is a flat
     * repository-slug to watched-boolean map (no workspace layer), so this returns every stored entry; the configured
     * workspace slug is attached to each mapping for the callers that need it. The local folder path is not persisted,
     * so each mapping comes back with an empty [WatchedProjectMapping.localFolderPath]; callers that need a real path
     * (the Watched Projects table and the open-local-checkout path) use [watchedProjectsForCurrentWorkspaceWithLocalPaths]
     * instead, which recomputes the path from disk without a Bitbucket API call. The method name is historical; there
     * is no longer a per-workspace filter because the map only ever holds the current configuration's repositories.
     */
    fun watchedProjectsForCurrentWorkspace(): List<WatchedProjectMapping> {
        val slug = workspaceSlugOrNull() ?: return emptyList()
        return settings.watchedProjects.map { mapping -> mapping.copy(workspace = slug) }
    }

    /**
     * The watched project mappings, each with its [WatchedProjectMapping.localFolderPath] recomputed from disk. The
     * local folder path is not persisted, so it is resolved by checking `<projectsDirectory>/<repoSlug>` for a matching
     * Bitbucket Cloud checkout (see [BbCloudWatchedProjectsDiscoveryService.resolveLocalFolderPath]). This runs no
     * Bitbucket API call, so it is safe to call on bind after an IDE restart. A mapping whose folder cannot be resolved
     * keeps an empty path, which the table renders as a blank cell and the checkout resolver treats as a missing
     * folder. The configured workspace slug is still needed by the resolver for the origin-remote match, so it is read
     * here even though it is no longer a persistence key.
     */
    fun watchedProjectsForCurrentWorkspaceWithLocalPaths(): List<WatchedProjectMapping> {
        val slug = workspaceSlugOrNull() ?: return emptyList()
        val projectsDirectory = projectsDirectoryOrNull()
        return settings.watchedProjects
            .map { mapping ->
                val resolvedPath = if (projectsDirectory == null) {
                    ""
                } else {
                    discoveryService.resolveLocalFolderPath(
                        projectsDirectory = projectsDirectory,
                        configuredWorkspaceSlug = slug,
                        repoSlug = mapping.remoteRepoSlug
                    )
                }
                mapping.copy(workspace = slug, localFolderPath = resolvedPath)
            }
    }

    /**
     * The persisted avatar/icon URL of each watched repository, keyed by repository slug. Read from plugin storage
     * with no Bitbucket API call: the map is written only on a Sync/rescan and is already scoped to the single current
     * configuration, mirroring [watchedProjectsForCurrentWorkspace]. The Watched Projects tab binds these URLs into its
     * in-memory image cache; repositories without an avatar are simply absent and render a neutral placeholder.
     */
    fun repoAvatarUrlsForCurrentWorkspace(): Map<String, String> {
        val urls = settings.repoAvatarUrls
        thisLogger().debug("Read ${urls.size} persisted repository avatar URLs")
        return urls
    }

    /** Caches a verified repository avatar URL for reuse by the Workspace and Watched Projects views. */
    fun cacheRepoAvatarUrl(repoSlug: String, avatarUrl: String?) {
        if (repoSlug.isBlank() || avatarUrl.isNullOrBlank()) {
            return
        }
        settings.mergeRepoAvatarUrls(mapOf(repoSlug to avatarUrl))
        thisLogger().debug("Cached repository avatar URL for $repoSlug")
    }

    /**
     * The absolute path of the local checkout of [repoSlug] under the configured projects directory, or an empty
     * string when no such checkout is available. The Workspace PR tabs' Repository-cell click uses this to decide
     * between opening the local project and browsing the repository's Bitbucket web page.
     *
     * Resolution is filesystem-only, so it is safe to call on the EDT and runs no Bitbucket API call: the configured
     * workspace slug and projects directory are read, and `<projectsDirectory>/<repoSlug>` is accepted only when it is
     * a Bitbucket Cloud checkout of the configured workspace (see
     * [BbCloudWatchedProjectsDiscoveryService.resolveLocalFolderPath]). An unset workspace slug or projects directory,
     * a missing folder, or a folder whose origin remote names a different workspace all resolve to an empty string.
     */
    fun localCheckoutPathForRepo(repoSlug: String): String {
        val slug = workspaceSlugOrNull()
        val projectsDirectory = projectsDirectoryOrNull()
        if (slug == null || projectsDirectory == null) {
            thisLogger().debug("No local checkout for $repoSlug: workspace slug or projects directory is not set")
            return ""
        }
        val resolvedPath = discoveryService.resolveLocalFolderPath(
            projectsDirectory = projectsDirectory,
            configuredWorkspaceSlug = slug,
            repoSlug = repoSlug
        )
        if (resolvedPath.isEmpty()) {
            thisLogger().debug("No local checkout resolved for $repoSlug under $projectsDirectory")
        } else {
            thisLogger().debug("Resolved local checkout for $repoSlug at $resolvedPath")
        }
        return resolvedPath
    }

    /** The repository slugs whose watched flag is on; these are the only ones whose PRs are listed. */
    fun watchedRepoSlugsForCurrentWorkspace(): List<String> {
        return watchedProjectsForCurrentWorkspace()
            .filter { mapping -> mapping.watched }
            .map { mapping -> mapping.remoteRepoSlug }
    }

    /** Sets the watched flag of the entry identified by repository slug, leaving the other entries as they are. */
    fun setWatched(mapping: WatchedProjectMapping, watched: Boolean) {
        val updated = settings.watchedProjects.map { stored ->
            if (stored.remoteRepoSlug == mapping.remoteRepoSlug) {
                stored.copy(watched = watched)
            } else {
                stored
            }
        }
        replaceWatchedProjects(updated)
        thisLogger().info("Watched project ${mapping.remoteRepoSlug} set to watched=$watched")
    }

    /** Sets the watched flag of every stored entry. */
    fun setAllWatched(watched: Boolean) {
        val updated = settings.watchedProjects.map { stored -> stored.copy(watched = watched) }
        replaceWatchedProjects(updated)
        thisLogger().info("All watched projects set to watched=$watched")
    }

    fun replaceWatchedProjects(mappings: List<WatchedProjectMapping>) {
        settings.replaceWatchedProjects(mappings)
        thisLogger().info("Watched projects replaced: ${mappings.size} entries")
    }

    fun clearWatchedProjects() {
        settings.clearWatchedProjects()
        thisLogger().info("Watched projects cleared")
    }

    /**
     * Scans the projects directory and writes the result to the persisted flat watched map.
     *
     * The whole discover-and-write section runs under [rescanMutex] so overlapping triggers (a chosen directory, a
     * workspace slug change, a token change, the Sync button) queue rather than interleave. [saveSetup] and
     * [saveProjectsDirectory]-driven flows call this once and never hold the mutex itself, so there is no re-entrancy
     * or deadlock.
     *
     * [clearFirst] selects between two modes:
     * - `true` (a configuration change: the projects directory, workspace slug, or API token changed): the map is
     *   emptied BEFORE discovery and the scanned mappings are written with their default (unwatched) flags, because the
     *   old watched choices referred to a different directory/workspace/token. On `VerifyFailed` or
     *   `ProjectsDirectoryUnreadable` the map is left empty, since the user explicitly asked to empty first on a config
     *   change; there are no stale entries to preserve.
     * - `false` (a manual Sync with no configuration change): the previous watched flags are carried over for
     *   rediscovered repositories and the previous mappings are preserved on a transient `VerifyFailed` /
     *   `ProjectsDirectoryUnreadable`, so a briefly offline mount or an `IOException` from the directory listing does
     *   not destroy the user's watched choices.
     */
    suspend fun rescanWatchedProjects(clearFirst: Boolean = false): WatchedProjectsDiscoveryResult {
        return rescanMutex.withLock {
            val projectsDirectory = projectsDirectoryOrNull()
            val workspaceSlug = workspaceSlugOrNull()
            val apiToken = storedTokenOrNull()

            if (projectsDirectory.isNullOrEmpty() || workspaceSlug.isNullOrEmpty() || apiToken.isNullOrEmpty()) {
                thisLogger().info("Cannot rescan watched projects: not configured")
                return@withLock WatchedProjectsDiscoveryResult.NotConfigured
            }

            // On a configuration change the old selections no longer apply; empty the map before discovery so a
            // failed verify leaves nothing stale behind. The carry-over map is captured before the clear.
            val carriedWatchedBySlug = settings.watchedProjects.associate { mapping ->
                mapping.remoteRepoSlug to mapping.watched
            }
            if (clearFirst) {
                clearWatchedProjects()
            }

            val result = discoveryService.discover(
                projectsDirectory = projectsDirectory,
                configuredWorkspaceSlug = workspaceSlug,
                apiToken = apiToken
            )

            when (result) {
                is WatchedProjectsDiscoveryResult.Success -> {
                    val mappings = if (clearFirst) {
                        result.mappings
                    } else {
                        withCarriedWatchedFlags(scanned = result.mappings, carriedWatchedBySlug = carriedWatchedBySlug)
                    }
                    replaceWatchedProjects(mappings)
                    // Persist the avatar URLs harvested from the same repo-existence call. On a config change
                    // (clearFirst) the URL set is replaced wholesale; on a manual Sync it is merged so rediscovered
                    // repositories refresh their URL and untouched ones are preserved.
                    if (clearFirst) {
                        settings.replaceRepoAvatarUrls(result.avatarUrlBySlug)
                    } else {
                        settings.mergeRepoAvatarUrls(result.avatarUrlBySlug)
                    }
                    thisLogger().debug("Persisted ${result.avatarUrlBySlug.size} repository avatar URLs")
                }
                is WatchedProjectsDiscoveryResult.VerifyFailed -> {
                    if (clearFirst) {
                        thisLogger().warn("Watched projects verification failed after a config change; map left empty")
                    } else {
                        thisLogger().warn("Watched projects verification failed; keeping previous mappings")
                    }
                }
                is WatchedProjectsDiscoveryResult.ProjectsDirectoryUnreadable -> {
                    if (clearFirst) {
                        thisLogger().warn("Projects directory unreadable after a config change; map left empty")
                    } else {
                        // A transient read failure must not destroy persisted watched state; keep the previous
                        // mappings and let the UI surface its warning.
                        thisLogger().warn("Projects directory unreadable; keeping previous watched mappings")
                    }
                }
                is WatchedProjectsDiscoveryResult.NotConfigured -> {
                    // Already handled above
                }
            }

            result
        }
    }

    /**
     * The mappings to store after a manual Sync: the [scanned] mappings with their watched flag carried over from
     * [carriedWatchedBySlug].
     *
     * A scanned entry keeps the watched flag it had before the scan when the same repository slug is already stored, so
     * a Sync must not flip a user's watched choices back on or off. A scanned entry without a stored match keeps its own
     * (unwatched by default) flag, so newly discovered projects come in unchecked. Carry-over is keyed by repository
     * slug, matching the flat persisted map.
     */
    private fun withCarriedWatchedFlags(scanned: List<WatchedProjectMapping>, carriedWatchedBySlug: Map<String, Boolean>): List<WatchedProjectMapping> {
        return scanned.map { mapping ->
            val storedWatched = carriedWatchedBySlug[mapping.remoteRepoSlug] ?: mapping.watched
            mapping.copy(watched = storedWatched)
        }
    }

    private fun storedWorkspaceSlug(): String {
        return settings.workspaceSlug.trim()
    }

    sealed class SaveException : Exception() {
        data object WorkspaceRequired : SaveException()

        data object TokenRequired : SaveException()
    }

    companion object {
        private const val BITBUCKET_ORGANIZATION_BASE = "https://bitbucket.org/"

        /** The hidden folder name under the projects directory that holds per-pull-request git worktrees. */
        private const val WORK_TREE_FOLDER_NAME = ".worktrees"

        fun getInstance(): BbCloudConfigurationService {
            val application = ApplicationManager.getApplication()
            val discoveryService = BbCloudWatchedProjectsDiscoveryService(OkHttpBitbucketRepositoryClient())
            return BbCloudConfigurationService(
                settings = BbCloudSettings.getInstance(),
                credentialStore = application.service<BbCloudCredentialStore>(),
                workspaceClient = OkHttpBitbucketWorkspaceClient(),
                discoveryService = discoveryService
            )
        }
    }
}