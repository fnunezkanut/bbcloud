package com.github.fnunezkanut.bbcloud.bitbucket

import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.credentials.BbCloudCredentialStore
import com.github.fnunezkanut.bbcloud.git.BitbucketGitRemoteReader
import com.github.fnunezkanut.bbcloud.project.ProjectRepositoryResolver
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

sealed class ProjectRepositoryStatus {
    data class Found(val repositoryWebUrl: String, val avatarUrl: String?) : ProjectRepositoryStatus()

    data class NotFound(val repositoryWebUrl: String) : ProjectRepositoryStatus()

    data object NoLocalProjectPath : ProjectRepositoryStatus()

    data class CheckFailed(val repositoryWebUrl: String?, val cause: Throwable? = null) : ProjectRepositoryStatus()
}

class BbCloudProjectRepositoryLinkService(
    private val credentialStore: BbCloudCredentialStore,
    private val repositoryClient: BitbucketRepositoryClient,
    private val readRemoteUrl: (Path) -> String? = BitbucketGitRemoteReader::readRemoteUrl,
    private val configurationService: BbCloudConfigurationService? = null
) {
    suspend fun resolveAndVerify(projectBasePath: String?, organizationUrl: String): ProjectRepositoryStatus {
        val configuredWorkspace = BitbucketRepositoryUrls.workspaceFromOrganizationUrl(organizationUrl)
        // The git remote read touches the filesystem, so keep it off the event dispatch thread (E4).
        val resolved = withContext(Dispatchers.IO) {
            ProjectRepositoryResolver.resolve(
                projectBasePath = projectBasePath,
                configuredWorkspace = configuredWorkspace,
                readRemoteUrl = readRemoteUrl,
                workTreeDirectory = configurationService?.workTreeDirectoryOrNull()
            )
        }
        if (resolved == null) {
            thisLogger().info("Cannot verify repository: no repository resolved for project base path $projectBasePath")
            return ProjectRepositoryStatus.NoLocalProjectPath
        }

        // Build the web URL from the resolved workspace and slug so the link always points at the repository actually
        // queried, including a checkout whose remote names a different workspace than the configured one (E3).
        val repositoryWebUrl = BitbucketRepositoryUrls.repositoryWebUrlForWorkspace(
            workspace = resolved.workspace,
            repoSlug = resolved.repoSlug
        )
        if (repositoryWebUrl == null) {
            thisLogger().info("Cannot verify repository: no repository web URL for workspace ${resolved.workspace}")
            return ProjectRepositoryStatus.CheckFailed(repositoryWebUrl = null)
        }

        val apiToken = credentialStore.getToken()
        if (apiToken == null) {
            thisLogger().info("Cannot verify repository: no stored API token")
            return ProjectRepositoryStatus.CheckFailed(repositoryWebUrl = repositoryWebUrl)
        }

        thisLogger().debug("Verifying repository ${resolved.workspace}/${resolved.repoSlug}")
        val checkResult = repositoryClient.checkRepositoryExists(
            workspace = resolved.workspace,
            repoSlug = resolved.repoSlug,
            apiToken = apiToken
        )
        thisLogger().debug("Repository check for ${resolved.workspace}/${resolved.repoSlug} returned $checkResult")
        return when (checkResult) {
            is RepositoryCheckResult.Exists -> ProjectRepositoryStatus.Found(
                repositoryWebUrl = repositoryWebUrl,
                avatarUrl = AvatarUrlUpgrader.upgrade(checkResult.avatarUrl)
            )
            RepositoryCheckResult.NotFound -> ProjectRepositoryStatus.NotFound(repositoryWebUrl)
            is RepositoryCheckResult.Failed -> ProjectRepositoryStatus.CheckFailed(
                repositoryWebUrl = repositoryWebUrl,
                cause = checkResult.cause
            )
        }
    }
}