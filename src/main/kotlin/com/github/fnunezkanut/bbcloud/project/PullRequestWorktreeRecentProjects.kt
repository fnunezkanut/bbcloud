package com.github.fnunezkanut.bbcloud.project

import com.intellij.ide.ProjectGroup
import com.intellij.ide.RecentProjectsManager
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

/**
 * The part of [RecentProjectsManager] that [PullRequestWorktreeRecentProjects] needs. [PlatformRecentProjectsGateway]
 * is the production implementation; tests inject a fake so the grouping rules can run without the IDE.
 */
internal interface RecentProjectsGateway {
    fun findGroup(name: String): ProjectGroup?

    fun addGroup(group: ProjectGroup)

    fun removeGroup(group: ProjectGroup)

    fun moveProjectToGroup(path: String, group: ProjectGroup)

    fun removePath(path: String)
}

/** Forwards every call to the public [RecentProjectsManager] API. The manager is looked up on each call. */
internal object PlatformRecentProjectsGateway : RecentProjectsGateway {
    override fun findGroup(name: String): ProjectGroup? {
        val manager = RecentProjectsManager.getInstance()
        return manager.groups.firstOrNull { group -> group.name == name }
    }

    override fun addGroup(group: ProjectGroup) {
        RecentProjectsManager.getInstance().addGroup(group)
    }

    override fun removeGroup(group: ProjectGroup) {
        RecentProjectsManager.getInstance().removeGroup(group)
    }

    override fun moveProjectToGroup(path: String, group: ProjectGroup) {
        RecentProjectsManager.getInstance().moveProjectToGroup(path, group)
    }

    override fun removePath(path: String) {
        RecentProjectsManager.getInstance().removePath(path)
    }
}

/**
 * Keeps pull request worktrees together in one Recent Projects group and removes them from the list when bbcloud
 * deletes the worktree.
 *
 * Only folders named `<repoSlug>--pr-<number>` (see [PullRequestWorktreeLayout]) are grouped, so plain source clones
 * that share the same open path are left alone. Both entry points swallow platform failures and log them at `warn`,
 * because a missing group must not break opening or removing a pull request.
 */
class PullRequestWorktreeRecentProjects internal constructor(private val gateway: RecentProjectsGateway) {
    /**
     * Places [worktreePath] in the group named [groupName], creating the group when it does not exist yet. The
     * platform moves the path out of any other group. Does nothing for folders that are not pull request worktrees.
     *
     * The group name is the identity of the group, so changing its translation later creates a new group and orphans
     * the old one.
     */
    fun addToGroup(worktreePath: Path, groupName: String) {
        val folderName = worktreePath.fileName?.toString()
        if (folderName == null || PullRequestWorktreeLayout.repoSlugFromWorktreeFolderName(folderName) == null) {
            LOG.debug("Not adding a folder to the recent projects group: it is not a pull request worktree")
            return
        }

        try {
            val group = findOrCreateGroup(groupName = groupName)
            gateway.moveProjectToGroup(path = recentProjectKey(worktreePath = worktreePath), group = group)
            LOG.debug("Added pull request worktree $folderName to the recent projects group")
        } catch (exception: Exception) {
            LOG.warn("Could not add pull request worktree $folderName to the recent projects group", exception)
        }
    }

    /**
     * Removes [worktreePath] from the recent projects list and deletes the group named [groupName] when it is left
     * empty. The platform ignores paths it does not know, so this is safe for worktrees that were never opened.
     */
    fun forget(worktreePath: Path, groupName: String) {
        val folderName = worktreePath.fileName?.toString()
        try {
            gateway.removePath(path = recentProjectKey(worktreePath = worktreePath))
            val group = gateway.findGroup(name = groupName)
            if (group != null && group.projects.isEmpty()) {
                gateway.removeGroup(group = group)
                LOG.debug("Removed the empty recent projects group after forgetting $folderName")
            }
        } catch (exception: Exception) {
            LOG.warn("Could not remove pull request worktree $folderName from recent projects", exception)
        }
    }

    private fun findOrCreateGroup(groupName: String): ProjectGroup {
        val existing = gateway.findGroup(name = groupName)
        if (existing != null) {
            return existing
        }
        val created = ProjectGroup(groupName)
        gateway.addGroup(group = created)
        return created
    }

    /**
     * The key the platform stores for a project path: the absolute, normalized path with forward slashes. Using the
     * same form keeps removal working on Windows, where `Path.toString()` would use backslashes.
     */
    private fun recentProjectKey(worktreePath: Path): String {
        return worktreePath.toAbsolutePath().normalize().invariantSeparatorsPathString
    }

    companion object {
        private val LOG = logger<PullRequestWorktreeRecentProjects>()
        private val SHARED = PullRequestWorktreeRecentProjects(gateway = PlatformRecentProjectsGateway)

        fun getInstance(): PullRequestWorktreeRecentProjects {
            return SHARED
        }
    }
}