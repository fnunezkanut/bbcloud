package com.github.fnunezkanut.bbcloud.ui

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudAuthoredPullRequestService
import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudOpenPullRequestService
import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudProjectRepositoryLinkService
import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudReviewQueueService
import com.github.fnunezkanut.bbcloud.bitbucket.OkHttpBitbucketPullRequestClient
import com.github.fnunezkanut.bbcloud.bitbucket.OkHttpBitbucketRepositoryClient
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.credentials.BbCloudCredentialStore
import com.github.fnunezkanut.bbcloud.project.LocalProjectRepositorySlug
import com.github.fnunezkanut.bbcloud.toolwindow.BbCloudToolWindowTab
import com.github.fnunezkanut.bbcloud.toolwindow.BbCloudToolWindowTabResolver
import com.github.fnunezkanut.bbcloud.ui.main.BbCloudMainPanel
import com.github.fnunezkanut.bbcloud.ui.otherprojects.BbCloudOtherProjectsPanel
import com.github.fnunezkanut.bbcloud.ui.setup.BbCloudSettingsScreen
import com.github.fnunezkanut.bbcloud.ui.setup.BbCloudSettingsViewModel
import com.github.fnunezkanut.bbcloud.ui.setup.chooseWorkspaceWithDialog
import com.github.fnunezkanut.bbcloud.ui.setup.confirmResetWithDialog
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.jewel.bridge.compose
import javax.swing.Icon
import javax.swing.JComponent

/**
 * Owns the six tool window tabs and the panels behind them. The Project - Open PRs tab opens by default once a Bitbucket
 * workspace and API token exist, otherwise the Settings tab opens. Tabs are never redirected: while the plugin is unconfigured,
 * the other tabs offer a link to the Settings tab instead.
 */
@Service(Service.Level.PROJECT)
class BbCloudToolWindowPanel(private val project: Project) {
    private val configurationService: BbCloudConfigurationService = BbCloudConfigurationService.getInstance()
    private val projectRepositoryLinkService: BbCloudProjectRepositoryLinkService = createDefaultProjectRepositoryLinkService(
        configurationService = configurationService
    )

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val settingsViewModel = createSettingsViewModel()
    private val settingsComposeComponent: JComponent = compose {
        BbCloudSettingsScreen(viewModel = settingsViewModel)
    }
    private val mainPanel = BbCloudMainPanel(
        configurationService = configurationService,
        projectRepositoryLinkService = projectRepositoryLinkService,
        openPullRequestService = createDefaultOpenPullRequestService(configurationService = configurationService),
        onGoToSettings = { selectTab(tab = BbCloudToolWindowTab.Settings) }
    )
    private val otherProjectsPanel = BbCloudOtherProjectsPanel(
        project = project,
        configurationService = configurationService,
        authoredPullRequestService = createDefaultAuthoredPullRequestService(),
        reviewQueueService = createDefaultReviewQueueService(),
        coroutineScope = coroutineScope,
        onGoToSettings = { selectTab(tab = BbCloudToolWindowTab.Settings) },
        onRefreshRequested = { refreshWorkspace() }
    )

    /** Set by the async configuration check; null while it is still in flight. */
    private var isConfigured: Boolean? = null
    private var contentManager: ContentManager? = null
    private var isProgrammaticSelection = false
    private var contentByTab: Map<BbCloudToolWindowTab, Content> = emptyMap()

    /** Non-closeable components for each of the six tabs; wired from coordinators. */
    val projectOpenPrsComponent: JComponent = mainPanel.openTabComponent
    val projectCompletedPrsComponent: JComponent = mainPanel.completedTabComponent
    val workspaceMyPrsComponent: JComponent = otherProjectsPanel.myPrsTabComponent
    val workspaceAssignedPrsComponent: JComponent = otherProjectsPanel.assignedPrsTabComponent
    val workspaceWatchedProjectsComponent: JComponent = otherProjectsPanel.watchedProjectsTabComponent
    val settingsComponent: JComponent = settingsComposeComponent

    /** Builds the six non-closeable tabs, in header order. */
    fun createContents(): List<Content> {
        val contentFactory = ContentFactory.getInstance()
        val contents = BbCloudToolWindowTab.entries.map { tab ->
            val component = componentFor(tab = tab)
            contentFactory.createContent(component, BbCloudBundle.message(titleKeyFor(tab)), false).apply {
                // Default left-to-right orientation places the icon before the label
                isCloseable = false
                putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
                putUserData(ToolWindowContentUi.NOT_SELECTED_TAB_ICON_TRANSPARENT, false)
                icon = iconFor(tab)
            }
        }
        contentByTab = BbCloudToolWindowTab.entries.zip(contents).toMap()
        return contents
    }

    /**
     * Hands a freshly created pull request to the main panel, which switches the current checkout to the main branch,
     * reloads the listings, and opens the pull request in its own work tree. Returns false when the main panel is not
     * bound to a project.
     */
    fun openCreatedPullRequest(summary: PullRequestSummary, localMainBranch: String?): Boolean {
        LOG.debug("Handing created pull request #${summary.id} to the main panel")
        return mainPanel.openCreatedPullRequest(summary = summary, localMainBranch = localMainBranch)
    }

    /** Selects Project - Open PRs and restores its listing card without starting a reload. */
    fun showProjectOpenPullRequestsListing(): Boolean {
        val contentManager = contentManager ?: return false
        val content = contentByTab[BbCloudToolWindowTab.ProjectOpenPrs] ?: return false
        isProgrammaticSelection = true
        try {
            contentManager.setSelectedContent(content)
        } finally {
            isProgrammaticSelection = false
        }
        mainPanel.showProjectOpenPullRequestsListing()
        return true
    }

    /** Selects the Settings tab and rebinds its state. Returns false if the tab or its content is unavailable. */
    fun showSettings(): Boolean {
        contentManager ?: return false
        contentByTab[BbCloudToolWindowTab.Settings] ?: return false
        LOG.info("User opened Bitbucket Cloud PR Review settings from the Tools settings page")
        selectTab(tab = BbCloudToolWindowTab.Settings)
        return true
    }

    /** Starts listening for tab selection and resolves the tab that opens with the tool window. */
    fun bind(contentManager: ContentManager, parentDisposable: Disposable) {
        this.contentManager = contentManager
        val listener = object : ContentManagerListener {
            override fun selectionChanged(event: ContentManagerEvent) {
                if (isProgrammaticSelection) {
                    return
                }
                if (event.operation != ContentManagerEvent.ContentOperation.add) {
                    return
                }
                val tab = tabFor(content = event.content) ?: return
                LOG.info("User selected the $tab tab in the Bitbucket Cloud PR Review tool window")
                applyTabSelection(tab = tab)
            }
        }
        contentManager.addContentManagerListener(listener)
        Disposer.register(parentDisposable) {
            contentManager.removeContentManagerListener(listener)
        }
        coroutineScope.launch {
            val configured = configurationService.isConfigured()
            LOG.debug("Initial tool window state: configured=$configured")
            ApplicationManager.getApplication().invokeLater {
                applyInitialTabState(configured = configured)
            }
        }
    }

    private fun applyInitialTabState(configured: Boolean) {
        isConfigured = configured
        selectTab(tab = BbCloudToolWindowTabResolver.initialTab(isConfigured = configured))
    }

    private fun selectTab(tab: BbCloudToolWindowTab) {
        val contentManager = contentManager ?: return
        val content = contentByTab[tab] ?: return
        isProgrammaticSelection = true
        try {
            contentManager.setSelectedContent(content)
        } finally {
            isProgrammaticSelection = false
        }
        applyTabSelection(tab = tab)
    }

    private fun applyTabSelection(tab: BbCloudToolWindowTab) {
        when (tab) {
            BbCloudToolWindowTab.ProjectOpenPrs -> refreshProjectOpenPrs()
            BbCloudToolWindowTab.ProjectCompletedPrs -> refreshProjectCompletedPrs()
            BbCloudToolWindowTab.WorkspaceMyPrs -> refreshWorkspaceMyPrs()
            BbCloudToolWindowTab.WorkspaceAssignedPrs -> refreshWorkspaceAssignedPrs()
            BbCloudToolWindowTab.WorkspaceWatchedProjects -> refreshWorkspaceWatchedProjects()
            BbCloudToolWindowTab.Settings -> bindSettingsState()
        }
    }

    private fun refreshProjectOpenPrs() {
        LOG.debug("Refreshing the Project - Open PRs tab")
        coroutineScope.launch {
            val configured = configurationService.isConfigured()
            ApplicationManager.getApplication().invokeLater {
                isConfigured = configured
                if (isSelected(tab = BbCloudToolWindowTab.ProjectOpenPrs)) {
                    if (configured) {
                        mainPanel.refresh(project = project, coroutineScope = coroutineScope)
                    } else {
                        mainPanel.showNotConfigured()
                    }
                }
            }
        }
    }

    private fun refreshProjectCompletedPrs() {
        LOG.debug("Refreshing the Project - Completed PRs tab")
        coroutineScope.launch {
            val configured = configurationService.isConfigured()
            ApplicationManager.getApplication().invokeLater {
                isConfigured = configured
                if (isSelected(tab = BbCloudToolWindowTab.ProjectCompletedPrs)) {
                    if (configured) {
                        mainPanel.refresh(project = project, coroutineScope = coroutineScope)
                    } else {
                        mainPanel.showNotConfigured()
                    }
                }
            }
        }
    }

    private fun refreshWorkspaceMyPrs() {
        LOG.debug("Refreshing the Workspace - My PRs tab")
        coroutineScope.launch {
            val configured = configurationService.isConfigured()
            ApplicationManager.getApplication().invokeLater {
                isConfigured = configured
                if (isSelected(tab = BbCloudToolWindowTab.WorkspaceMyPrs)) {
                    if (configured) {
                        otherProjectsPanel.loadAuthored(
                            excludedRepoSlug = LocalProjectRepositorySlug.fromBasePath(project.basePath),
                            coroutineScope = coroutineScope
                        )
                    } else {
                        otherProjectsPanel.showNotConfigured()
                    }
                }
            }
        }
    }

    private fun refreshWorkspaceAssignedPrs() {
        LOG.debug("Refreshing the Workspace - Assigned PRs tab")
        coroutineScope.launch {
            val configured = configurationService.isConfigured()
            ApplicationManager.getApplication().invokeLater {
                isConfigured = configured
                if (isSelected(tab = BbCloudToolWindowTab.WorkspaceAssignedPrs)) {
                    if (configured) {
                        otherProjectsPanel.loadReviewing(
                            excludedRepoSlug = LocalProjectRepositorySlug.fromBasePath(project.basePath),
                            coroutineScope = coroutineScope
                        )
                    } else {
                        otherProjectsPanel.showNotConfigured()
                    }
                }
            }
        }
    }

    private fun refreshWorkspaceWatchedProjects() {
        LOG.debug("Refreshing the Workspace - Watched Projects tab")
        coroutineScope.launch {
            val configured = configurationService.isConfigured()
            ApplicationManager.getApplication().invokeLater {
                isConfigured = configured
                if (isSelected(tab = BbCloudToolWindowTab.WorkspaceWatchedProjects)) {
                    if (configured) {
                        otherProjectsPanel.bindWatchedProjects()
                    } else {
                        otherProjectsPanel.showWatchedProjectsNotConfigured()
                    }
                }
            }
        }
    }

    /** Reloads the Settings tab from the stored state: the token outline and projects-directory status. */
    private fun bindSettingsState() {
        coroutineScope.launch {
            val hasStoredToken = configurationService.hasStoredToken()
            val configured = configurationService.isConfigured()
            ApplicationManager.getApplication().invokeLater {
                isConfigured = configured
                settingsViewModel.bindSettingsState(hasStoredToken = hasStoredToken)
            }
        }
    }

    private fun refreshAfterReset() {
        coroutineScope.launch {
            val configured = configurationService.isConfigured()
            LOG.debug("Refreshing tool window after token reset: configured=$configured")
            ApplicationManager.getApplication().invokeLater {
                isConfigured = configured
                // resetAll already wiped the persisted avatar-URL map; also drop the in-memory decoded repository
                // avatars so the Watched Projects tab shows placeholders until the next Sync repopulates them.
                otherProjectsPanel.resetWatchedProjectsAvatars()
                selectTab(tab = BbCloudToolWindowTab.Settings)
            }
        }
    }

    private fun refreshAfterSave() {
        coroutineScope.launch {
            val configured = configurationService.isConfigured()
            LOG.debug("Refreshing tool window after save: configured=$configured")
            ApplicationManager.getApplication().invokeLater {
                isConfigured = configured
                // Stay on Settings after a save (R1): a token-only save is not fully configured under the tightened
                // contract, and the user remains on Settings to finish configuring the directories. Re-binding the
                // Settings tab refreshes the saved token outline and directory status rows.
                selectTab(tab = BbCloudToolWindowTab.Settings)
            }
        }
    }

    private fun componentFor(tab: BbCloudToolWindowTab): JComponent {
        return when (tab) {
            BbCloudToolWindowTab.ProjectOpenPrs -> projectOpenPrsComponent
            BbCloudToolWindowTab.ProjectCompletedPrs -> projectCompletedPrsComponent
            BbCloudToolWindowTab.WorkspaceMyPrs -> workspaceMyPrsComponent
            BbCloudToolWindowTab.WorkspaceAssignedPrs -> workspaceAssignedPrsComponent
            BbCloudToolWindowTab.WorkspaceWatchedProjects -> workspaceWatchedProjectsComponent
            BbCloudToolWindowTab.Settings -> settingsComposeComponent
        }
    }

    private fun titleKeyFor(tab: BbCloudToolWindowTab): String {
        return when (tab) {
            BbCloudToolWindowTab.ProjectOpenPrs -> "toolwindow.tab.projectOpenPrs"
            BbCloudToolWindowTab.ProjectCompletedPrs -> "toolwindow.tab.projectCompletedPrs"
            BbCloudToolWindowTab.WorkspaceMyPrs -> "toolwindow.tab.workspaceMyPrs"
            BbCloudToolWindowTab.WorkspaceAssignedPrs -> "toolwindow.tab.workspaceAssignedPrs"
            BbCloudToolWindowTab.WorkspaceWatchedProjects -> "toolwindow.tab.workspaceWatchedProjects"
            BbCloudToolWindowTab.Settings -> "toolwindow.tab.settings"
        }
    }

    private fun iconFor(tab: BbCloudToolWindowTab): Icon {
        return when (tab) {
            BbCloudToolWindowTab.ProjectOpenPrs -> AllIcons.Nodes.IdeaProject
            BbCloudToolWindowTab.ProjectCompletedPrs -> AllIcons.Vcs.Merge
            BbCloudToolWindowTab.WorkspaceMyPrs -> AllIcons.General.User
            BbCloudToolWindowTab.WorkspaceAssignedPrs -> AllIcons.General.Information
            BbCloudToolWindowTab.WorkspaceWatchedProjects -> AllIcons.Actions.Show
            BbCloudToolWindowTab.Settings -> AllIcons.General.Settings
        }
    }

    private fun tabFor(content: Content): BbCloudToolWindowTab? {
        return contentByTab.entries.firstOrNull { entry -> entry.value == content }?.key
    }

    /** True when [tab] is still the content the platform shows, so a slow reload cannot apply to an abandoned tab. */
    private fun isSelected(tab: BbCloudToolWindowTab): Boolean {
        return contentManager?.selectedContent == contentByTab[tab]
    }

    private fun refreshWorkspace() {
        // Refresh all workspace tabs when the refresh button is clicked, regardless of the selected tab
        LOG.debug("User requested a reload of the Workspace tabs")
        coroutineScope.launch {
            val configured = configurationService.isConfigured()
            if (!configured) {
                otherProjectsPanel.showNotConfigured()
                // Route the Watched Projects tab to its centered not-configured card too, so all three Workspace tabs
                // show the generic link rather than leaving Watched Projects on stale content.
                otherProjectsPanel.showWatchedProjectsNotConfigured()
                return@launch
            }
            val excludedRepoSlug = LocalProjectRepositorySlug.fromBasePath(project.basePath)
            ApplicationManager.getApplication().invokeLater {
                otherProjectsPanel.loadAuthored(
                    excludedRepoSlug = excludedRepoSlug,
                    coroutineScope = coroutineScope
                )
                otherProjectsPanel.loadReviewing(
                    excludedRepoSlug = excludedRepoSlug,
                    coroutineScope = coroutineScope
                )
                otherProjectsPanel.bindWatchedProjects()
            }
        }
    }

    private fun createSettingsViewModel(): BbCloudSettingsViewModel {
        return BbCloudSettingsViewModel(
            configurationService = configurationService,
            coroutineScope = coroutineScope,
            onSaveSuccess = { refreshAfterSave() },
            onTokenReset = { refreshAfterReset() },
            // Persists the chosen projects directory and triggers the exact scan that used to run from the Watched
            // Projects green button. otherProjectsPanel is read lazily here because this panel is built before it.
            onProjectsDirectoryChosenCallback = { absolutePath ->
                LOG.debug("Settings projects directory chosen; persisting and rescanning watched projects")
                // saveProjectsDirectory empties the watched map; the rescan rebuilds it clear-first so rediscovered
                // repositories come in unwatched rather than carrying over selections from the old directory.
                configurationService.saveProjectsDirectory(absolutePath = absolutePath)
                otherProjectsPanel.rescanWatchedProjects(clearFirst = true)
            },
            chooseWorkspace = ::chooseWorkspaceWithDialog,
            confirmReset = ::confirmResetWithDialog
        )
    }

    fun disposePanel() {
        LOG.debug("Disposing Bitbucket Cloud PR Review tool window panel for project ${project.name}")
        coroutineScope.cancel()
    }

    companion object {
        private val LOG = logger<BbCloudToolWindowPanel>()

        private fun createDefaultProjectRepositoryLinkService(configurationService: BbCloudConfigurationService): BbCloudProjectRepositoryLinkService {
            val application = ApplicationManager.getApplication()
            return BbCloudProjectRepositoryLinkService(
                credentialStore = application.service<BbCloudCredentialStore>(),
                repositoryClient = OkHttpBitbucketRepositoryClient(),
                configurationService = configurationService
            )
        }

        private fun createDefaultOpenPullRequestService(configurationService: BbCloudConfigurationService): BbCloudOpenPullRequestService {
            val application = ApplicationManager.getApplication()
            return BbCloudOpenPullRequestService(
                credentialStore = application.service<BbCloudCredentialStore>(),
                pullRequestClient = OkHttpBitbucketPullRequestClient(),
                configurationService = configurationService
            )
        }

        private fun createDefaultReviewQueueService(): BbCloudReviewQueueService {
            val application = ApplicationManager.getApplication()
            return BbCloudReviewQueueService(
                credentialStore = application.service<BbCloudCredentialStore>(),
                pullRequestClient = OkHttpBitbucketPullRequestClient()
            )
        }

        /** Feeds the top section of the Other Projects tab; the review queue above is not wired to a section yet. */
        private fun createDefaultAuthoredPullRequestService(): BbCloudAuthoredPullRequestService {
            val application = ApplicationManager.getApplication()
            return BbCloudAuthoredPullRequestService(
                credentialStore = application.service<BbCloudCredentialStore>(),
                pullRequestClient = OkHttpBitbucketPullRequestClient()
            )
        }
    }
}