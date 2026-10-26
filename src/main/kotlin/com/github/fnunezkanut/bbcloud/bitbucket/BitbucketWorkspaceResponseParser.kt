package com.github.fnunezkanut.bbcloud.bitbucket

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses the workspace list from `GET /2.0/user/workspaces`, where every value is a workspace access entry carrying
 * the workspace the token can reach. Duplicate slugs are collapsed defensively; the endpoint is expected to report
 * each workspace once.
 *
 * The workspace-less `GET /2.0/repositories` route this replaced was removed from the Bitbucket router, so it
 * answered 404 with "There is no API hosted at this URL".
 */
object BitbucketWorkspaceResponseParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parseWorkspaces(responseBody: String): List<BitbucketWorkspace> {
        val page = runCatching {
            json.decodeFromString(UserWorkspacesResponse.serializer(), responseBody)
        }.getOrNull() ?: return emptyList()
        return page.values
            .mapNotNull { entry -> entry.workspace }
            .filter { workspace -> workspace.slug.isNotBlank() }
            .distinctBy { workspace -> workspace.slug }
            .map { workspace -> BitbucketWorkspace(slug = workspace.slug, uuid = workspace.uuid) }
    }

    fun buildListWorkspacesUrl(): String {
        return "$BITBUCKET_API_BASE/user/workspaces?pagelen=$PAGE_LEN&sort=slug"
    }

    @Serializable
    private data class UserWorkspacesResponse(val values: List<WorkspaceAccess> = emptyList())

    @Serializable
    private data class WorkspaceAccess(val workspace: WorkspaceDto? = null)

    @Serializable
    private data class WorkspaceDto(val slug: String = "", val uuid: String? = null)

    private const val BITBUCKET_API_BASE = "https://api.bitbucket.org/2.0"
    private const val PAGE_LEN = 100
}