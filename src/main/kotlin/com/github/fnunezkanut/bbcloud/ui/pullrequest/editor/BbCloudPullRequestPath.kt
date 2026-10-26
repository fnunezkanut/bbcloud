package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.intellij.vcs.editor.ComplexPathVirtualFileSystem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BbCloudPullRequestPath(
    override val sessionId: String,
    override val projectHash: String,
    val workspaceUrl: String,
    val repositorySlug: String,
    val pullRequestId: Long
) : ComplexPathVirtualFileSystem.ComplexPath

object BbCloudPullRequestPathSerializer : ComplexPathVirtualFileSystem.ComplexPathSerializer<BbCloudPullRequestPath> {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    override fun serialize(path: BbCloudPullRequestPath): String {
        return json.encodeToString(BbCloudPullRequestPath.serializer(), path.normalized())
    }

    override fun deserialize(rawPath: String): BbCloudPullRequestPath {
        return json.decodeFromString(BbCloudPullRequestPath.serializer(), rawPath).normalized()
    }

    private fun BbCloudPullRequestPath.normalized(): BbCloudPullRequestPath {
        return copy(workspaceUrl = normalizeWorkspaceUrl(workspaceUrl))
    }

    fun normalizeWorkspaceUrl(workspaceUrl: String): String {
        return workspaceUrl.trim().trimEnd('/')
    }
}