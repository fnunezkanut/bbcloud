package com.github.fnunezkanut.bbcloud.project

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger

/** An immutable copy of one Recent Projects group. */
data class RecentProjectGroupSnapshot(val name: String, val projectPaths: List<String>, val isExpanded: Boolean)

/** An immutable copy of the Recent Projects list: [recentPaths] most recent first, and the user-defined [groups]. */
data class RecentProjectsSnapshot(val recentPaths: List<String>, val groups: List<RecentProjectGroupSnapshot>)

/**
 * The read side of the IDE Recent Projects list that the Recent Projects tab needs. [PlatformRecentProjectsSource] is
 * the production implementation; tests inject a fake so the tab logic can run without the IDE.
 */
internal interface RecentProjectsSource {
    fun snapshot(): RecentProjectsSnapshot

    fun displayName(path: String): String

    fun setGroupExpanded(groupName: String, expanded: Boolean)

    fun removePath(path: String)

    /** Calls [onChange] whenever the platform reports a Recent Projects change. The callback runs on the EDT. */
    fun subscribe(parentDisposable: Disposable, onChange: () -> Unit)
}

/**
 * Reads the public [RecentProjectsManager] API. The manager is looked up on each call. Platform failures are logged at
 * debug level and rethrown, because the tab decides the user-facing outcome.
 */
internal object PlatformRecentProjectsSource : RecentProjectsSource {
    private val LOG = logger<PlatformRecentProjectsSource>()

    override fun snapshot(): RecentProjectsSnapshot {
        try {
            val manager = RecentProjectsManager.getInstance()
            val base = manager as? RecentProjectsManagerBase
            if (base == null) {
                LOG.debug("The recent projects manager is not a RecentProjectsManagerBase; listing groups only")
            }
            val groups = manager.groups.map { group ->
                RecentProjectGroupSnapshot(
                    name = group.name,
                    projectPaths = group.projects.toList(),
                    isExpanded = group.isExpanded
                )
            }
            return RecentProjectsSnapshot(
                recentPaths = base?.getRecentPaths().orEmpty(),
                groups = groups
            )
        } catch (exception: Exception) {
            LOG.debug("Could not read the recent projects", exception)
            throw exception
        }
    }

    override fun displayName(path: String): String {
        val base = RecentProjectsManager.getInstance() as? RecentProjectsManagerBase
        if (base == null) {
            return path.substringAfterLast(delimiter = '/')
        }
        val displayName = base.getDisplayName(path)
        if (!displayName.isNullOrBlank()) {
            return displayName
        }
        return base.getProjectName(path)
    }

    override fun setGroupExpanded(groupName: String, expanded: Boolean) {
        val group = RecentProjectsManager.getInstance().groups.firstOrNull { candidate -> candidate.name == groupName }
        group?.isExpanded = expanded
    }

    override fun removePath(path: String) {
        RecentProjectsManager.getInstance().removePath(path)
    }

    override fun subscribe(parentDisposable: Disposable, onChange: () -> Unit) {
        ApplicationManager.getApplication().messageBus.connect(parentDisposable).subscribe(
            RecentProjectsManager.RECENT_PROJECTS_CHANGE_TOPIC,
            object : RecentProjectsManager.RecentProjectsChange {
                override fun change() {
                    onChange()
                }
            }
        )
    }
}