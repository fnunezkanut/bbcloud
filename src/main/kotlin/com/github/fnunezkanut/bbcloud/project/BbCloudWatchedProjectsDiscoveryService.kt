package com.github.fnunezkanut.bbcloud.project

import com.github.fnunezkanut.bbcloud.bitbucket.AvatarUrlUpgrader
import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryClient
import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryRemoteUrl
import com.github.fnunezkanut.bbcloud.bitbucket.RepositoryCheckResult
import com.github.fnunezkanut.bbcloud.git.BitbucketGitRemoteReader
import com.github.fnunezkanut.bbcloud.validation.BitbucketRepositorySlugValidator
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Discovers and verifies local Git checkouts under a projects directory that map to Bitbucket Cloud
 * repositories for the configured workspace.
 *
 * The discovery runs in four stages:
 * 1. List immediate child directories (skipping hidden dot-directories).
 * 2. Local filters: slug format, `.git` existence, remote URL is Bitbucket Cloud, workspace matches,
 *    and folder name equals the parsed repository slug.
 * 3. API verification: `GET /2.0/repositories/{workspace}/{repo_slug}` for each survivor.
 * 4. If all API checks fail with non-404 errors, return [WatchedProjectsDiscoveryResult.VerifyFailed]
 *    so the caller can keep the previous mappings instead of wiping them.
 */
open class BbCloudWatchedProjectsDiscoveryService(private val repositoryClient: BitbucketRepositoryClient) {
    open suspend fun discover(projectsDirectory: String, configuredWorkspaceSlug: String, apiToken: String): WatchedProjectsDiscoveryResult {
        return withContext(Dispatchers.IO) {
            doDiscover(projectsDirectory, configuredWorkspaceSlug, apiToken)
        }
    }

    private suspend fun doDiscover(projectsDirectory: String, configuredWorkspaceSlug: String, apiToken: String): WatchedProjectsDiscoveryResult {
        val dirPath = Paths.get(projectsDirectory.trim())
        if (!Files.isDirectory(dirPath)) {
            LOG.warn("Projects directory is not a readable directory: $projectsDirectory")
            return WatchedProjectsDiscoveryResult.ProjectsDirectoryUnreadable
        }

        val children = try {
            Files.list(dirPath)
                .filter { entry -> Files.isDirectory(entry) }
                .filter { entry -> !entry.fileName.toString().startsWith(".") }
                .toList()
        } catch (e: IOException) {
            LOG.warn("Cannot read projects directory: $projectsDirectory", e)
            return WatchedProjectsDiscoveryResult.ProjectsDirectoryUnreadable
        }

        val candidates = children.mapNotNull { child -> toCandidateIfValid(child, configuredWorkspaceSlug) }
        LOG.info(
            "Watched projects discovery: candidates=${children.size}, passed local filters=${candidates.size}"
        )

        if (candidates.isEmpty()) {
            return WatchedProjectsDiscoveryResult.Success(emptyList())
        }

        val verified = verifyCandidates(candidates, configuredWorkspaceSlug, apiToken)
        return verified
    }

    private fun toCandidateIfValid(folder: Path, configuredWorkspaceSlug: String): WatchedProjectMapping? {
        val folderName = folder.fileName.toString()

        if (!BitbucketRepositorySlugValidator.validate(folderName).isSuccess) {
            return null
        }

        val gitDir = folder.resolve(".git")
        if (!Files.exists(gitDir)) {
            return null
        }

        val remoteUrl = BitbucketGitRemoteReader.readRemoteUrl(folder)
        if (remoteUrl == null) {
            return null
        }

        val remote = matchingBitbucketRemote(remoteUrl, configuredWorkspaceSlug) ?: return null
        if (remote.repoSlug != folderName) {
            return null
        }

        return WatchedProjectMapping(
            localFolderPath = folder.toAbsolutePath().normalize().toString(),
            remoteRepoSlug = remote.repoSlug,
            workspace = remote.workspace
        )
    }

    /**
     * Resolves the local checkout folder of [repoSlug] in [configuredWorkspaceSlug] without any Bitbucket API call,
     * so the Watched Projects table and the "open local checkout" path can rebind after an IDE restart (the local
     * folder path is not persisted). It mirrors the filesystem part of [toCandidateIfValid]: it checks
     * `<projectsDirectory>/<repoSlug>` for a `.git` entry whose Git remote is a Bitbucket Cloud repository of the
     * configured workspace, requiring the folder name to equal [repoSlug]. Returns the absolute normalized path when
     * all checks pass, or an empty string when the folder cannot be resolved. No network, not `suspend`.
     */
    open fun resolveLocalFolderPath(projectsDirectory: String, configuredWorkspaceSlug: String, repoSlug: String): String {
        val trimmedDirectory = projectsDirectory.trim()
        if (trimmedDirectory.isEmpty()) {
            return ""
        }
        val candidate = toCandidateIfValid(Paths.get(trimmedDirectory).resolve(repoSlug), configuredWorkspaceSlug)
        if (candidate == null || candidate.remoteRepoSlug != repoSlug) {
            return ""
        }
        return candidate.localFolderPath
    }

    /** Parses [remoteUrl] and returns it only when it is a Bitbucket Cloud URL of [configuredWorkspaceSlug]. */
    private fun matchingBitbucketRemote(remoteUrl: String, configuredWorkspaceSlug: String): BitbucketRepositoryRemoteUrl.BitbucketRemoteRepository? {
        val remote = BitbucketRepositoryRemoteUrl.parse(remoteUrl) ?: return null
        if (remote.workspace != configuredWorkspaceSlug) {
            return null
        }
        return remote
    }

    private suspend fun verifyCandidates(candidates: List<WatchedProjectMapping>, workspaceSlug: String, apiToken: String): WatchedProjectsDiscoveryResult {
        val results = coroutineScope {
            val semaphore = Semaphore(MAX_CONCURRENT_REPOSITORY_CHECKS)
            candidates.map { mapping ->
                async {
                    semaphore.withPermit {
                        LOG.debug("Checking repository existence: $workspaceSlug/${mapping.remoteRepoSlug}")
                        val checkResult = repositoryClient.checkRepositoryExists(
                            workspace = workspaceSlug,
                            repoSlug = mapping.remoteRepoSlug,
                            apiToken = apiToken
                        )
                        LOG.debug("Repository check result for $workspaceSlug/${mapping.remoteRepoSlug}: $checkResult")
                        mapping to checkResult
                    }
                }
            }.awaitAll()
        }

        val verified = results.mapNotNull { (mapping, checkResult) ->
            when (checkResult) {
                is RepositoryCheckResult.Exists -> mapping
                else -> null
            }
        }

        // Collect the avatar href of every surviving repository, keyed by slug. Repositories without an avatar are
        // simply absent from the map (rather than mapped to null), so the persisted store only ever holds real URLs.
        val avatarUrlBySlug = results.mapNotNull { (mapping, checkResult) ->
            val avatarUrl = (checkResult as? RepositoryCheckResult.Exists)?.avatarUrl
            if (avatarUrl != null) {
                mapping.remoteRepoSlug to (AvatarUrlUpgrader.upgrade(avatarUrl) ?: avatarUrl)
            } else {
                null
            }
        }.toMap()

        val droppedNotFound = results.count { (_, checkResult) ->
            checkResult is RepositoryCheckResult.NotFound
        }
        val failedCount = results.count { (_, checkResult) ->
            checkResult is RepositoryCheckResult.Failed
        }
        LOG.info(
            "Watched projects API verification: verified=${verified.size}, dropped 404=$droppedNotFound, failed=$failedCount"
        )

        if (results.all { (_, checkResult) -> checkResult is RepositoryCheckResult.Failed }) {
            LOG.warn("All repository API checks failed; keeping previous mappings")
            return WatchedProjectsDiscoveryResult.VerifyFailed
        }

        return WatchedProjectsDiscoveryResult.Success(mappings = verified, avatarUrlBySlug = avatarUrlBySlug)
    }

    companion object {
        private val LOG = logger<BbCloudWatchedProjectsDiscoveryService>()

        private const val MAX_CONCURRENT_REPOSITORY_CHECKS = 10
    }
}

sealed class WatchedProjectsDiscoveryResult {
    /**
     * [mappings] are the verified watched projects. [avatarUrlBySlug] maps each repository slug to its configured
     * avatar/icon URL (`links.avatar.href`) when the repository had one; repositories without an avatar are absent
     * from the map. The map is harvested from the same repo-existence call discovery already makes, so it costs no
     * extra authenticated request. Defaults to empty so construction sites that only care about the mappings compile.
     */
    data class Success(val mappings: List<WatchedProjectMapping>, val avatarUrlBySlug: Map<String, String> = emptyMap()) : WatchedProjectsDiscoveryResult()

    data object ProjectsDirectoryUnreadable : WatchedProjectsDiscoveryResult()

    data object NotConfigured : WatchedProjectsDiscoveryResult()

    data object VerifyFailed : WatchedProjectsDiscoveryResult()
}