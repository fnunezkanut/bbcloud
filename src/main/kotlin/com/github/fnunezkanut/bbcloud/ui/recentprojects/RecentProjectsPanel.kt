package com.github.fnunezkanut.bbcloud.ui.recentprojects

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.git.GitHeadBranchReader
import com.github.fnunezkanut.bbcloud.project.LocalRepositoryProjectOpener
import com.github.fnunezkanut.bbcloud.project.PlatformRecentProjectsSource
import com.github.fnunezkanut.bbcloud.project.WorktreeSourceCheckoutResolver
import com.github.fnunezkanut.bbcloud.ui.worktree.PullRequestWorktreeOpener
import com.github.fnunezkanut.bbcloud.ui.worktree.PullRequestWorktreeRemovalLauncher
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.jewel.bridge.compose
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.swing.JComponent
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Hosts the Recent Projects tab: builds the Compose screen and view model, forwards platform change notifications to the
 * view model, and opens projects. The platform message bus connection is released by [dispose], which the tool window
 * panel calls when it is disposed.
 */
internal class RecentProjectsPanel(private val project: Project, private val coroutineScope: CoroutineScope, isPruneInProgress: () -> Boolean) : Disposable {
    private val configurationService = BbCloudConfigurationService.getInstance()
    private val avatars = PlatformRepoAvatarProvider(
        configurationService = configurationService,
        coroutineScope = coroutineScope
    )
    private val removalLauncher = PullRequestWorktreeRemovalLauncher(
        project = project,
        configurationService = configurationService,
        coroutineScope = coroutineScope,
        worktreeOpener = PullRequestWorktreeOpener.getInstance(),
        isPruneInProgress = isPruneInProgress
    )
    private val viewModel = RecentProjectsViewModel(
        source = PlatformRecentProjectsSource,
        readBranch = ::readBranchOfProjectFolder,
        pathExists = ::pathExists,
        userHome = userHomeWithInvariantSeparators(),
        coroutineScope = coroutineScope,
        openProject = ::openProject,
        onLoadFailed = { throwable -> LOG.warn("Could not load recent projects", throwable) },
        runOnEdt = { block -> ApplicationManager.getApplication().invokeLater(block) },
        avatars = avatars,
        workTreeDirectory = { configurationService.workTreeDirectoryOrNull() },
        currentProjectBasePath = { project.basePath },
        resolveSourceRoot = ::resolveSourceRoot,
        removeWorktree = ::removeWorktree,
        onWorktreeRemovalUnavailable = ::notifyWorktreeRemovalUnavailable
    )

    val component: JComponent = compose { RecentProjectsScreen(viewModel = viewModel) }

    init {
        PlatformRecentProjectsSource.subscribe(parentDisposable = this) { viewModel.onPlatformChanged() }
    }

    /** Reloads the list. Called each time the tab is selected, so branches changed elsewhere show up. */
    fun reload() {
        viewModel.reload()
    }

    /** Drops the decoded repository avatars, for the Reset Plugin flow. */
    fun resetAvatars() {
        viewModel.clearAvatars()
    }

    override fun dispose() {
        LOG.debug("Disposing the Recent Projects panel for project ${project.name}")
    }

    private fun openProject(row: RecentProjectRow) {
        val folderName = row.name
        if (!row.exists) {
            notify(
                messageKey = "recentProjects.open.missing",
                type = NotificationType.WARNING,
                folderName = folderName
            )
            return
        }
        coroutineScope.launch {
            val checkoutPath = Path.of(row.path)
            val opened = LocalRepositoryProjectOpener.openOrFocus(
                repoSlug = checkoutPath.fileName?.toString() ?: folderName,
                checkoutPath = checkoutPath
            )
            if (opened == null) {
                ApplicationManager.getApplication().invokeLater {
                    notify(
                        messageKey = "recentProjects.open.failed",
                        type = NotificationType.ERROR,
                        folderName = folderName
                    )
                }
            }
        }
    }

    private fun resolveSourceRoot(worktreePath: Path, repoSlug: String?): Path? {
        val mappingsBySlug = avatars.slugsByLocalFolder().entries.associate { (folder, slug) -> slug to folder }
        return WorktreeSourceCheckoutResolver.resolve(
            worktreePath = worktreePath,
            repoSlug = repoSlug,
            mappingsBySlug = mappingsBySlug,
            projectsDirectory = configurationService.projectsDirectoryOrNull()
        )
    }

    private fun removeWorktree(sourceCheckoutRoot: Path, worktreePath: Path, prNumber: Long) {
        removalLauncher.remove(
            sourceCheckoutRoot = sourceCheckoutRoot,
            worktreePath = worktreePath,
            prNumber = prNumber,
            askForConfirmation = true,
            onFinished = { viewModel.reload() }
        )
    }

    private fun notifyWorktreeRemovalUnavailable(row: RecentProjectRow) {
        val messageKey = if (configurationService.workTreeDirectoryOrNull() == null) {
            "recentProjects.removeWorktree.noWorkTreeDirectory"
        } else {
            "recentProjects.removeWorktree.sourceMissing"
        }
        notify(
            messageKey = messageKey,
            type = NotificationType.WARNING,
            folderName = row.name
        )
    }

    private fun notify(messageKey: String, type: NotificationType, folderName: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(BbCloudBundle.message(messageKey, folderName), type)
            .notify(project)
    }

    companion object {
        private val LOG = logger<RecentProjectsPanel>()
        private const val NOTIFICATION_GROUP_ID = "BbCloud"

        private fun userHomeWithInvariantSeparators(): String {
            return Path.of(System.getProperty("user.home")).invariantSeparatorsPathString
        }

        /** Existing files (for example solution files) count as present; the branch is read from their folder. */
        private fun pathExists(path: String): Boolean {
            return try {
                Files.exists(Path.of(path))
            } catch (exception: InvalidPathException) {
                LOG.debug("Not a valid path for a recent project", exception)
                false
            }
        }

        private suspend fun readBranchOfProjectFolder(path: Path): String? {
            val folder = if (Files.isDirectory(path)) path else path.parent
            if (folder == null) {
                return null
            }
            return GitHeadBranchReader.readBranch(projectPath = folder)
        }
    }
}