package com.github.fnunezkanut.bbcloud.toolwindow

/** The four permanent tabs of the Bitbucket Cloud PR Review tool window, in header order. */
enum class BbCloudToolWindowTab { ProjectPrs, WorkspacePrs, WorkspaceWatchedProjects, Settings }

object BbCloudToolWindowTabResolver {
    /**
     * The tab the tool window opens on. Settings opens first while the plugin is unconfigured, so first-time configuration
     * is one click away; the other tabs are never redirected and offer a link to Settings while unconfigured.
     *
     * [isConfigured] is null until the initial async configuration check resolves.
     */
    fun initialTab(isConfigured: Boolean?): BbCloudToolWindowTab {
        return if (isConfigured == false) {
            BbCloudToolWindowTab.Settings
        } else {
            BbCloudToolWindowTab.ProjectPrs
        }
    }
}