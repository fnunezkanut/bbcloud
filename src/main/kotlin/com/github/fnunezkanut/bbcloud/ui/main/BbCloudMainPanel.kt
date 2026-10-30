@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudOpenPullRequestService
import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudProjectRepositoryLinkService
import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryUrls
import com.github.fnunezkanut.bbcloud.bitbucket.ProjectRepositoryStatus
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestListResult
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestOrdering
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewingPullRequest
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.git.BbCloudBranchCheckoutService
import com.github.fnunezkanut.bbcloud.git.BbCloudWorktreeService
import com.github.fnunezkanut.bbcloud.git.BitbucketGitRemoteReader
import com.github.fnunezkanut.bbcloud.git.BranchCheckoutResult
import com.github.fnunezkanut.bbcloud.git.WorktreeSetupResult
import com.github.fnunezkanut.bbcloud.project.LocalRepositoryProjectOpener
import com.github.fnunezkanut.bbcloud.project.ProjectRepositoryResolver
import com.github.fnunezkanut.bbcloud.project.PullRequestOpenCheck
import com.github.fnunezkanut.bbcloud.project.PullRequestOpenCheckResult
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeListing
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeResolution
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeResolver
import com.github.fnunezkanut.bbcloud.project.ResolvedProjectRepository
import com.github.fnunezkanut.bbcloud.project.StaleWorktreePruneSelection
import com.github.fnunezkanut.bbcloud.settings.PullRequestListScope
import com.github.fnunezkanut.bbcloud.ui.components.CenteredContent
import com.github.fnunezkanut.bbcloud.ui.components.NotConfiguredContent
import com.github.fnunezkanut.bbcloud.ui.otherprojects.toComposeImageBitmap
import com.github.fnunezkanut.bbcloud.ui.pullrequest.create.BbCloudNewPullRequestEditorOpener
import com.github.fnunezkanut.bbcloud.ui.pullrequest.create.NewPullRequestContext
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPendingEditorOpen
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestEditorOpenQueue
import com.github.fnunezkanut.bbcloud.ui.pullrequests.PullRequestExcludeCheckboxes
import com.github.fnunezkanut.bbcloud.ui.pullrequests.PullRequestListEntry
import com.github.fnunezkanut.bbcloud.ui.pullrequests.PullRequestListingSource
import com.github.fnunezkanut.bbcloud.ui.pullrequests.PullRequestsCard
import com.github.fnunezkanut.bbcloud.ui.pullrequests.PullRequestsListingBody
import com.github.fnunezkanut.bbcloud.ui.pullrequests.PullRequestsViewModel
import com.github.fnunezkanut.bbcloud.ui.worktree.WorktreeBranchCollisionDialog
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withModalProgress
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.ui.component.Text
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent

/** The three cards the single Current Project - PRs tab root can show. */
private enum class ViewCard {
    NotConfigured,
    Listing,
    ProjectStatus
}

/** The centered status message that stands in for the whole body of the Current Project - PRs tab root. */
private data class CenteredProjectStatus(val icon: ImageBitmap?, val message: String)

/**
 * The Compose body of the single Current Project - PRs tab root.
 *
 * It switches between the not-configured card, the centered project-status card, and the listing with its
 * [ProjectStatusHeader] on top, driven by Compose state instead of the Swing CardLayout the panel used before. The
 * [excludeContent] slot carries the Exclude: checkboxes inline into the header row; it is emitted only on the listing
 * card, so the not-configured and centered-status cards show no checkboxes.
 */
@Composable
private fun ProjectRootContent(
    card: ViewCard,
    headerState: ProjectStatusHeaderState,
    centeredStatus: CenteredProjectStatus?,
    onRefreshRequested: () -> Unit,
    onCreatePullRequestRequested: () -> Unit,
    onRepositoryLinkClicked: (String) -> Unit,
    onGoToSettings: () -> Unit,
    excludeContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    listing: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        when (card) {
            ViewCard.NotConfigured -> NotConfiguredContent(onGoToSettings = onGoToSettings)
            ViewCard.ProjectStatus -> CenteredProjectStatusContent(status = centeredStatus)
            ViewCard.Listing -> Column(modifier = Modifier.fillMaxSize()) {
                ProjectStatusHeader(
                    state = headerState,
                    onRefreshRequested = onRefreshRequested,
                    onCreatePullRequestRequested = onCreatePullRequestRequested,
                    onRepositoryLinkClicked = onRepositoryLinkClicked,
                    excludeContent = excludeContent
                )
                Box(modifier = Modifier.weight(1f)) {
                    listing()
                }
            }
        }
    }
}

/** The centered icon and message that stands in for the whole body of a Project tab root. */
@Composable
private fun CenteredProjectStatusContent(status: CenteredProjectStatus?) {
    if (status == null) {
        return
    }
    CenteredContent {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val icon = status.icon
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null)
            }
            Text(text = status.message, color = MainPanelStatusSupport.warningForeground())
        }
    }
}

class BbCloudMainPanel(
    private val configurationService: BbCloudConfigurationService,
    private val projectRepositoryLinkService: BbCloudProjectRepositoryLinkService,
    private val openPullRequestService: BbCloudOpenPullRequestService,
    private val onGoToSettings: () -> Unit
) {
    private var boundProject: Project? = null
    private var boundCoroutineScope: CoroutineScope? = null
    private var repositoryWebUrl: String? = null
    private var repositoryAvatarUrl: String? = null
    private var currentRepository: ResolvedProjectRepository? = null
    private val worktreeService = BbCloudWorktreeService.getInstance()
    private val branchCheckoutService = BbCloudBranchCheckoutService()

    /** Guards duplicate worktree opens; all mutations happen on the EDT. */
    private val worktreesInProgress = mutableSetOf<Path>()
    private val pruneInProgress = AtomicBoolean(false)

    /**
     * The one reusable merged view model for the Current Project - PRs tab. Its two sources are the open and resolved
     * project pull requests, which one listing request supplies together. The title-click behavior is routed per
     * source by [openProjectPullRequestTitle]: an open row opens its own work tree (former Open tab behavior) and a
     * resolved row opens the browser (former Completed tab behavior).
     */
    private val projectPullRequestsViewModel = PullRequestsViewModel(
        logName = "Current Project - PRs",
        sourcesOfTab = setOf(PullRequestListingSource.ProjectOpen, PullRequestListingSource.ProjectResolved),
        scope = PullRequestListScope.CurrentProject,
        initialFilter = configurationService.pullRequestListFilter(scope = PullRequestListScope.CurrentProject),
        organizationUrl = { configurationService.organizationUrlOrNull() },
        onFilterChangedCallback = { filter ->
            configurationService.savePullRequestListFilter(scope = PullRequestListScope.CurrentProject, filter = filter)
        },
        onOpenTitleCallback = ::openProjectPullRequestTitle,
        onOpenPrOnWebCallback = { entry -> openPullRequestOnWeb(entry.pullRequest.summary) },
        onOpenRepositoryCallback = { entry -> openRepositoryInOwnWindow(entry.pullRequest.summary) },
        onCountsChanged = ::updateOpenCount
    )
    private val projectHeaderViewModel = ProjectStatusHeaderViewModel(
        onRefreshRequested = { triggerLoadPullRequests(source = "refresh button") },
        onCreatePullRequestRequested = { openNewPullRequestEditor() }
    )

    /** The three cards of the Current Project - PRs tab root, driven by Compose state instead of a Swing CardLayout. */
    private val projectCard: MutableState<ViewCard> = mutableStateOf(ViewCard.Listing)

    /**
     * Stands in for the whole body when the project repository status cannot be resolved: the listing and
     * the reload button are both unavailable, so the message is centered instead of sitting in a status row
     * above an empty area. The warning color matches the icons used for the same states.
     */
    private val projectCenteredStatus: MutableState<CenteredProjectStatus?> = mutableStateOf(null)

    val projectPrsTabComponent: JComponent = compose {
        ProjectRootContent(
            card = projectCard.value,
            headerState = projectHeaderViewModel.state.value,
            centeredStatus = projectCenteredStatus.value,
            onRefreshRequested = projectHeaderViewModel::onRefreshClicked,
            onCreatePullRequestRequested = projectHeaderViewModel::onCreatePullRequestClicked,
            onRepositoryLinkClicked = projectHeaderViewModel::onRepositoryLinkClicked,
            onGoToSettings = onGoToSettings,
            excludeContent = {
                PullRequestExcludeCheckboxes(
                    filter = projectPullRequestsViewModel.state.value.filter,
                    viewModel = projectPullRequestsViewModel
                )
            },
            listing = {
                PullRequestsListingBody(
                    viewModel = projectPullRequestsViewModel,
                    emptyMessageKey = "pullRequests.project.none",
                    errorMessageKey = "main.pullRequests.loadFailed",
                    footer = { }
                )
            }
        )
    }

    /**
     * Routes a title click on a Current Project - PRs row by its source: an open row opens its own work tree (former
     * Open tab behavior), a resolved row opens the browser (former Completed tab behavior).
     */
    private fun openProjectPullRequestTitle(entry: PullRequestListEntry) {
        when (entry.source) {
            PullRequestListingSource.ProjectOpen -> {
                openPullRequestInWorktree(summary = entry.pullRequest.summary)
            }
            PullRequestListingSource.ProjectResolved -> {
                LOG.info("User opened resolved pull request #${entry.pullRequest.summary.id} in the browser via card")
                BrowserUtil.browse(entry.pullRequest.summary.webUrl)
            }
            else -> {
                LOG.info("Ignoring a ${entry.source} row on the Current Project - PRs tab")
            }
        }
    }

    fun refresh(project: Project, coroutineScope: CoroutineScope) {
        LOG.debug("Refreshing main panel for project ${project.name}")
        boundProject = project
        boundCoroutineScope = coroutineScope
        repositoryWebUrl = null
        repositoryAvatarUrl = null
        currentRepository = null
        projectHeaderViewModel.reset()
        projectHeaderViewModel.setRefreshVisible(visible = false)
        projectHeaderViewModel.setCreatePullRequestVisible(visible = false)
        showCard(ViewCard.Listing)
        projectPullRequestsViewModel.showHidden()
        projectHeaderViewModel.showChecking()

        coroutineScope.launch {
            val organizationUrl = configurationService.organizationUrlOrNull()
            if (organizationUrl == null || !configurationService.isConfigured()) {
                ApplicationManager.getApplication().invokeLater {
                    showNotConfigured()
                }
                return@launch
            }
            val resolvedRepository = resolveCurrentRepository(project = project)
            val status = projectRepositoryLinkService.resolveAndVerify(
                projectBasePath = project.basePath,
                organizationUrl = organizationUrl
            )
            ApplicationManager.getApplication().invokeLater {
                currentRepository = resolvedRepository
                applyProjectStatus(status)
            }
        }
    }

    /** Restores the Current Project - PRs listing without affecting open pull-request editor tabs or review state. */
    fun showCurrentProjectPullRequestsListing() {
        LOG.info("Restoring the Current Project - PRs listing from a pull request editor")
        showCard(ViewCard.Listing)
        projectPullRequestsViewModel.showListingCard()
    }

    /** Replaces the listing with the link to the Settings tab, used while the plugin is unconfigured. */
    fun showNotConfigured() {
        LOG.debug("Showing the unconfigured state; the organization URL or API token is missing")
        showCard(ViewCard.NotConfigured)
    }

    private fun applyProjectStatus(status: ProjectRepositoryStatus) {
        LOG.debug("Project repository status resolved: $status")
        when (status) {
            is ProjectRepositoryStatus.Found -> {
                repositoryWebUrl = status.repositoryWebUrl
                repositoryAvatarUrl = status.avatarUrl
                configurationService.cacheRepoAvatarUrl(
                    repoSlug = currentRepository?.repoSlug.orEmpty(),
                    avatarUrl = status.avatarUrl
                )
                projectHeaderViewModel.showProjectFound(
                    repositoryWebUrl = status.repositoryWebUrl,
                    repositorySlug = currentRepository?.repoSlug.orEmpty()
                )
                projectHeaderViewModel.setRefreshVisible(visible = true)
                projectHeaderViewModel.setCreatePullRequestVisible(visible = true)
                loadPullRequests()
            }
            is ProjectRepositoryStatus.NotFound -> {
                val message = BbCloudBundle.message("main.project.notFound", status.repositoryWebUrl)
                showCenteredProjectStatus(icon = AllIcons.General.Warning.toComposeImageBitmap(), message = message)
            }
            ProjectRepositoryStatus.NoLocalProjectPath -> {
                val message = BbCloudBundle.message("main.project.noBasePath")
                showCenteredProjectStatus(icon = null, message = message)
            }
            is ProjectRepositoryStatus.CheckFailed -> {
                val detail = status.repositoryWebUrl?.let { url -> " at $url" }.orEmpty()
                if (status.cause != null) {
                    LOG.warn("Could not verify Bitbucket repository$detail", status.cause)
                } else {
                    LOG.warn("Could not verify Bitbucket repository$detail")
                }
                val message = if (status.repositoryWebUrl != null) {
                    BbCloudBundle.message("main.project.checkFailed", status.repositoryWebUrl)
                } else {
                    BbCloudBundle.message("main.project.checkFailedGeneric")
                }
                showCenteredProjectStatus(icon = null, message = message)
            }
        }
    }

    /**
     * Replaces the whole body with [message] centered on both axes. Used by the statuses that leave nothing
     * to list and no way to retry, so the message is the entire content of the tab.
     */
    private fun showCenteredProjectStatus(icon: ImageBitmap?, message: String) {
        val status = CenteredProjectStatus(icon = icon, message = message)
        projectCenteredStatus.value = status
        showCard(ViewCard.ProjectStatus)
    }

    private fun triggerLoadPullRequests(source: String) {
        LOG.info("User requested pull requests via $source")
        loadPullRequests()
    }

    /**
     * Updates the open-count pill from the view model's counts callback. [openCount] is the number of visible open
     * rows after the Exclude filters (decision 4c), so toggling an Exclude checkbox that hides open rows updates the
     * pill. The pill is hidden on the Hidden, Loading, and Error cards, matching the former "Open (n)" indicator's
     * visibility rule.
     */
    private fun updateOpenCount(openCount: Int, card: PullRequestsCard) {
        projectHeaderViewModel.setOpenCount(
            text = openCount.toString(),
            visible = card != PullRequestsCard.Hidden &&
                card != PullRequestsCard.Loading &&
                card != PullRequestsCard.Error
        )
    }

    private fun loadPullRequests() {
        val project = boundProject ?: return
        val coroutineScope = boundCoroutineScope ?: return
        val organizationUrl = configurationService.organizationUrlOrNull() ?: return

        projectHeaderViewModel.setRefreshEnabled(false)
        val generation = projectPullRequestsViewModel.beginRefresh()
        coroutineScope.launch {
            // The service caches the uuid per token, so only the first call makes a request; the Mine and
            // Approved-by-me filters need it to match the current user.
            val currentUserUuid = openPullRequestService.currentUserUuid(
                projectBasePath = project.basePath,
                organizationUrl = organizationUrl
            )
            val result = openPullRequestService.listRecentPullRequests(
                projectBasePath = project.basePath,
                organizationUrl = organizationUrl
            )
            ApplicationManager.getApplication().invokeLater {
                applyPullRequestListResult(
                    result = result,
                    generation = generation,
                    currentUserUuid = currentUserUuid,
                    coroutineScope = coroutineScope
                )
            }
        }
    }

    private fun applyPullRequestListResult(result: PullRequestListResult, generation: Long, currentUserUuid: String?, coroutineScope: CoroutineScope) {
        projectHeaderViewModel.setRefreshEnabled(true)
        when (result) {
            is PullRequestListResult.Success -> {
                LOG.debug("Loaded ${result.pullRequests.size} pull requests")
                val listing = PullRequestOrdering.partitionForDisplay(result.pullRequests)
                val repository = currentRepository
                val workspace = repository?.workspace.orEmpty()
                val repoSlug = repository?.repoSlug.orEmpty()
                val repoAvatarUrl = repositoryAvatarUrl
                // An empty result makes both sources Loaded(empty), and the card derivation shows Empty.
                projectPullRequestsViewModel.applySource(
                    generation = generation,
                    source = PullRequestListingSource.ProjectOpen,
                    entries = listing.open.map { summary ->
                        PullRequestListEntry(
                            pullRequest = ReviewingPullRequest(summary = summary, workspace = workspace, repoSlug = repoSlug),
                            source = PullRequestListingSource.ProjectOpen,
                            repoAvatarUrl = repoAvatarUrl
                        )
                    },
                    isTruncated = false,
                    currentUserUuid = currentUserUuid,
                    coroutineScope = coroutineScope
                )
                projectPullRequestsViewModel.applySource(
                    generation = generation,
                    source = PullRequestListingSource.ProjectResolved,
                    entries = listing.closed.map { summary ->
                        PullRequestListEntry(
                            pullRequest = ReviewingPullRequest(summary = summary, workspace = workspace, repoSlug = repoSlug),
                            source = PullRequestListingSource.ProjectResolved,
                            repoAvatarUrl = repoAvatarUrl
                        )
                    },
                    isTruncated = false,
                    currentUserUuid = currentUserUuid,
                    coroutineScope = coroutineScope
                )
                pruneStaleWorktrees()
            }
            is PullRequestListResult.Failed -> {
                if (result.cause != null) {
                    LOG.warn("Could not load pull requests", result.cause)
                } else {
                    LOG.warn("Could not load pull requests")
                }
                projectPullRequestsViewModel.failSource(generation = generation, source = PullRequestListingSource.ProjectOpen)
                projectPullRequestsViewModel.failSource(generation = generation, source = PullRequestListingSource.ProjectResolved)
            }
        }
    }

    private suspend fun resolveCurrentRepository(project: Project): ResolvedProjectRepository? {
        val resolved = try {
            withContext(Dispatchers.IO) {
                ProjectRepositoryResolver.resolve(
                    projectBasePath = project.basePath,
                    configuredWorkspace = configurationService.workspaceSlugOrNull(),
                    workTreeDirectory = configurationService.workTreeDirectoryOrNull()
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LOG.info("Could not resolve the current project repository", exception)
            return null
        }
        if (resolved == null) {
            LOG.info("Could not resolve the current project repository")
        } else {
            LOG.debug("Resolved current project repository: ${resolved.workspace}/${resolved.repoSlug}")
        }
        return resolved
    }

    private fun openPullRequestOnWeb(summary: PullRequestSummary) {
        val url = summary.webUrl
        if (url.isBlank()) {
            LOG.info("Cannot open pull request #${summary.id} in the browser: URL is blank")
            return
        }
        BrowserUtil.browse(url)
    }

    private fun openRepositoryInOwnWindow(summary: PullRequestSummary) {
        val repository = currentRepository
        val repoSlug = repository?.repoSlug
        if (repository == null || repoSlug.isNullOrBlank()) {
            LOG.info("Cannot open the repository for pull request #${summary.id}: repository identity is unavailable")
            return
        }
        val webUrl = repositoryWebUrl ?: BitbucketRepositoryUrls.repositoryWebUrlForWorkspace(
            workspace = repository.workspace,
            repoSlug = repoSlug
        )
        val localPath = configurationService.localCheckoutPathForRepo(repoSlug)
        if (localPath.isBlank()) {
            LOG.info("Opened Bitbucket repository $repoSlug from a Project PR table (no local checkout)")
            webUrl?.let { url -> BrowserUtil.browse(url) }
            return
        }
        val scope = boundCoroutineScope
        if (scope == null) {
            LOG.info("Cannot open local repository $repoSlug: the main panel is not bound to a coroutine scope")
            webUrl?.let { url -> BrowserUtil.browse(url) }
            return
        }
        scope.launch {
            val opened = LocalRepositoryProjectOpener.openOrFocus(
                repoSlug = repoSlug,
                checkoutPath = Path.of(localPath)
            )
            if (opened != null) {
                LOG.info("Opened local project $repoSlug from a Project PR table")
            } else {
                LOG.warn("Could not open local project $repoSlug; falling back to the Bitbucket web page")
                ApplicationManager.getApplication().invokeLater {
                    webUrl?.let { url -> BrowserUtil.browse(url) }
                }
            }
        }
    }

    private fun openPullRequestInWorktree(summary: PullRequestSummary, source: String = "card") {
        val project = boundProject
        val coroutineScope = boundCoroutineScope
        if (project == null || coroutineScope == null) {
            LOG.info("Cannot open pull request #${summary.id}: the main panel is not bound to a project")
            return
        }
        LOG.info("User requested to open pull request #${summary.id} in its own work tree via $source")
        coroutineScope.launch {
            val resolvedRepository = currentRepository ?: resolveCurrentRepository(project = project)
            if (resolvedRepository == null) {
                showWorktreeErrorNotification(
                    summary = summary,
                    content = BbCloudBundle.message("otherProjects.worktree.failed.workTreeDirMissing")
                )
                return@launch
            }
            val resolution = PullRequestWorktreeResolver.resolve(
                mappings = configurationService.watchedProjectsForCurrentWorkspaceWithLocalPaths(),
                workTreeDirectory = configurationService.workTreeDirectoryOrNull(),
                projectsDirectory = configurationService.projectsDirectoryOrNull(),
                workspace = resolvedRepository.workspace,
                repoSlug = resolvedRepository.repoSlug,
                prNumber = summary.id
            )
            val resolved = when (resolution) {
                is PullRequestWorktreeResolution.Resolved -> {
                    resolution
                }
                PullRequestWorktreeResolution.WorkTreeDirMissing -> {
                    LOG.info("Cannot open pull request #${summary.id}: work tree directory is unavailable")
                    showWorktreeErrorNotification(
                        summary = summary,
                        content = BbCloudBundle.message("otherProjects.worktree.failed.workTreeDirMissing")
                    )
                    return@launch
                }
                PullRequestWorktreeResolution.SourceCheckoutMissing -> {
                    LOG.info("Cannot open pull request #${summary.id}: source checkout is unavailable")
                    showWorktreeErrorNotification(
                        summary = summary,
                        content = BbCloudBundle.message(
                            "otherProjects.worktree.failed.sourceCheckoutMissing",
                            resolvedRepository.repoSlug
                        )
                    )
                    return@launch
                }
            }
            val worktreePath = resolved.worktreePath
            if (!addWorktreeInProgress(worktreePath = worktreePath)) {
                LOG.info("Opening pull request #${summary.id} is already in progress for $worktreePath")
                return@launch
            }
            try {
                val preparation = runWorktreeModal(
                    summary = summary,
                    repository = resolvedRepository,
                    resolution = resolved
                )
                when (preparation) {
                    is WorktreePreparation.Prepared -> {
                        openWorktreeProjectAndEditor(
                            summary = summary,
                            repoSlug = resolvedRepository.repoSlug,
                            repository = resolvedRepository,
                            worktreePath = preparation.worktreePath
                        )
                    }
                    is WorktreePreparation.Failed -> {
                        showWorktreeErrorNotification(summary = summary, content = preparation.content)
                    }
                    is WorktreePreparation.BranchCheckedOutInSource -> {
                        // The modal marshals to and blocks on the EDT itself, so call it directly from the coroutine
                        // (the progress modal has already closed and no write lock is held here).
                        val choice = WorktreeBranchCollisionDialog.show(
                            project = project,
                            prNumber = summary.id,
                            branchName = preparation.branchName,
                            sourceCheckoutPath = preparation.sourceCheckoutPath
                        )
                        if (choice == WorktreeBranchCollisionDialog.Choice.OPEN_IN_BITBUCKET) {
                            LOG.info("User opened Bitbucket PR #${summary.id} from the branch-collision modal")
                            BrowserUtil.browse(summary.webUrl)
                        }
                    }
                }
            } catch (exception: CancellationException) {
                LOG.info("User cancelled work tree preparation for pull request #${summary.id}")
                throw exception
            } finally {
                ApplicationManager.getApplication().invokeLater {
                    worktreesInProgress.remove(worktreePath)
                }
            }
        }
    }

    private fun addWorktreeInProgress(worktreePath: Path): Boolean {
        if (ApplicationManager.getApplication().isDispatchThread) {
            return worktreesInProgress.add(worktreePath)
        }
        var added = false
        ApplicationManager.getApplication().invokeAndWait({
            added = worktreesInProgress.add(worktreePath)
        }, ModalityState.any())
        return added
    }

    private sealed interface WorktreePreparation {
        data class Prepared(val worktreePath: Path) : WorktreePreparation

        /** A localized failure body or sanitized git output shown in the worktree error balloon. */
        data class Failed(val content: String) : WorktreePreparation

        /**
         * The source clone is currently on the pull request branch, so the work tree could not be created. The caller
         * shows the explanatory [WorktreeBranchCollisionDialog] modal instead of the error balloon.
         */
        data class BranchCheckedOutInSource(val branchName: String, val sourceCheckoutPath: Path) : WorktreePreparation
    }

    private suspend fun runWorktreeModal(
        summary: PullRequestSummary,
        repository: ResolvedProjectRepository,
        resolution: PullRequestWorktreeResolution.Resolved
    ): WorktreePreparation {
        val organizationUrl = configurationService.organizationUrlOrNull()
        if (organizationUrl == null) {
            LOG.info("Cannot open pull request #${summary.id}: no organization URL configured")
            return WorktreePreparation.Failed(content = BbCloudBundle.message("otherProjects.worktree.failed.noOrgUrl"))
        }
        return withModalProgress(
            owner = ModalTaskOwner.guess(),
            title = BbCloudBundle.message("otherProjects.worktree.modalTitle"),
            cancellation = TaskCancellation.cancellable()
        ) {
            val checkResult = checkStillOpen(
                summary = summary,
                repository = repository,
                organizationUrl = organizationUrl
            )
            if (checkResult != null) {
                return@withModalProgress checkResult
            }
            val remoteUrl = withContext(Dispatchers.IO) {
                BitbucketGitRemoteReader.readRemoteUrl(checkoutRoot = resolution.sourceCheckoutPath)
            }
            if (remoteUrl == null) {
                LOG.info("Cannot open pull request #${summary.id}: source checkout is not a git repository")
                return@withModalProgress WorktreePreparation.Failed(
                    content = BbCloudBundle.message("otherProjects.worktree.failed.notAGitRepo")
                )
            }
            when (
                val setupResult = worktreeService.ensureWorktree(
                    sourceCheckoutRoot = resolution.sourceCheckoutPath,
                    worktreePath = resolution.worktreePath,
                    remoteUrl = remoteUrl,
                    prNumber = summary.id,
                    sourceBranch = summary.sourceBranch
                )
            ) {
                is WorktreeSetupResult.Success -> {
                    WorktreePreparation.Prepared(worktreePath = setupResult.worktreePath)
                }
                is WorktreeSetupResult.Failed -> {
                    LOG.warn("Could not prepare the work tree for pull request #${summary.id}: ${setupResult.output}", setupResult.cause)
                    WorktreePreparation.Failed(content = setupResult.output)
                }
                is WorktreeSetupResult.BranchCheckedOutInSource -> {
                    LOG.info(
                        "Cannot open pull request #${summary.id} in its own work tree: " +
                            "the current checkout is on the PR branch '${setupResult.branchName}'"
                    )
                    WorktreePreparation.BranchCheckedOutInSource(
                        branchName = setupResult.branchName,
                        sourceCheckoutPath = setupResult.sourceCheckoutPath
                    )
                }
            }
        }
    }

    private suspend fun checkStillOpen(
        summary: PullRequestSummary,
        repository: ResolvedProjectRepository,
        organizationUrl: String
    ): WorktreePreparation.Failed? {
        val details = try {
            openPullRequestService.pullRequestDetailsFor(
                workspace = repository.workspace,
                repoSlug = repository.repoSlug,
                organizationUrl = organizationUrl,
                pullRequestId = summary.id
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LOG.warn("PR state check failed for pull request #${summary.id}", exception)
            return WorktreePreparation.Failed(
                content = BbCloudBundle.message("otherProjects.worktree.failed.prStateCheckFailed", summary.id)
            )
        }
        return when (val result = PullRequestOpenCheck.evaluate(details = details)) {
            is PullRequestOpenCheckResult.StillOpen -> {
                null
            }
            is PullRequestOpenCheckResult.NotOpen -> {
                LOG.info("Cannot open pull request #${summary.id}: it is ${result.state}")
                WorktreePreparation.Failed(
                    content = BbCloudBundle.message("otherProjects.worktree.failed.prNotOpen", summary.id, result.state)
                )
            }
            PullRequestOpenCheckResult.NotFound -> {
                LOG.info("Cannot open pull request #${summary.id}: it was not found")
                WorktreePreparation.Failed(
                    content = BbCloudBundle.message("otherProjects.worktree.failed.prNotFound", summary.id)
                )
            }
            is PullRequestOpenCheckResult.Failed -> {
                LOG.warn("PR state check failed for pull request #${summary.id}", result.cause)
                WorktreePreparation.Failed(
                    content = BbCloudBundle.message("otherProjects.worktree.failed.prStateCheckFailed", summary.id)
                )
            }
        }
    }

    private suspend fun openWorktreeProjectAndEditor(summary: PullRequestSummary, repoSlug: String, repository: ResolvedProjectRepository, worktreePath: Path) {
        val openedProject = LocalRepositoryProjectOpener.openOrFocus(repoSlug = repoSlug, checkoutPath = worktreePath)
        if (openedProject == null) {
            LOG.info("Cannot open pull request #${summary.id}: the IDE could not open the work tree project")
            showWorktreeErrorNotification(
                summary = summary,
                content = BbCloudBundle.message("otherProjects.worktree.failed.openProjectFailed")
            )
            return
        }
        openPullRequestEditor(
            openedProject = openedProject,
            summary = summary,
            repoSlug = repoSlug,
            repository = repository
        )
    }

    private fun openPullRequestEditor(
        openedProject: LocalRepositoryProjectOpener.OpenedProject,
        summary: PullRequestSummary,
        repoSlug: String,
        repository: ResolvedProjectRepository
    ) {
        val project = openedProject.project
        if (project.isDisposed) {
            LOG.info("The project was disposed before the pull request editor could be opened")
            return
        }
        val repositoryWebUrl = configurationService.organizationUrlOrNull()?.let { organizationUrl ->
            BitbucketRepositoryUrls.repositoryWebUrlForWorkspace(
                workspace = repository.workspace,
                repoSlug = repoSlug
            ) ?: BitbucketRepositoryUrls.repositoryWebUrl(organizationUrl = organizationUrl, repoSlug = repoSlug)
        }
        if (repositoryWebUrl == null) {
            LOG.info("Cannot open pull request #${summary.id}: no repository web URL")
            return
        }
        val queue = project.service<BbCloudPullRequestEditorOpenQueue>()
        queue.enqueue(
            BbCloudPendingEditorOpen(
                summary = summary,
                branchName = summary.sourceBranch,
                repositoryWebUrl = repositoryWebUrl,
                repositorySlug = repoSlug
            )
        )
        if (openedProject.wasAlreadyOpen) {
            queue.drainAndOpen(project)
        }
    }

    private fun showWorktreeErrorNotification(summary: PullRequestSummary, content: String) {
        val project = boundProject ?: return
        val trimmed = content.trim()
        val balloonContent = if (trimmed.isBlank()) {
            BbCloudBundle.message("otherProjects.worktree.failed.generic")
        } else if (trimmed.length > MAX_WORKTREE_FAILURE_POPUP_LENGTH) {
            trimmed.substring(0, MAX_WORKTREE_FAILURE_POPUP_LENGTH) + "\u2026"
        } else {
            trimmed
        }
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                BbCloudBundle.message("otherProjects.worktree.failed.title", summary.id),
                balloonContent,
                NotificationType.ERROR
            )
        notification.addAction(
            NotificationAction.createSimpleExpiring(BbCloudBundle.message("otherProjects.worktree.openInBitbucket")) {
                LOG.info("User opened Bitbucket PR #${summary.id} from the work tree error popup")
                BrowserUtil.browse(summary.webUrl)
            }
        )
        notification.notify(project)
    }

    private fun pruneStaleWorktrees() {
        val project = boundProject
        val coroutineScope = boundCoroutineScope
        if (project == null || coroutineScope == null) {
            LOG.info("Cannot prune stale worktrees: the main panel is not bound to a project")
            return
        }
        val workTreeDirectory = configurationService.workTreeDirectoryOrNull()
        val organizationUrl = configurationService.organizationUrlOrNull()
        if (workTreeDirectory == null || organizationUrl == null) {
            LOG.info("Cannot prune stale worktrees: work tree directory or organization URL is unavailable")
            return
        }
        if (!pruneInProgress.compareAndSet(false, true)) {
            LOG.debug("Stale worktree pruning is already in progress")
            return
        }
        coroutineScope.launch {
            try {
                val openResult = openPullRequestService.listAllOpenPullRequests(
                    projectBasePath = project.basePath,
                    organizationUrl = organizationUrl
                )
                if (openResult is PullRequestListResult.Failed) {
                    LOG.info("Skipping stale worktree pruning because the complete open pull-request listing failed")
                    return@launch
                }
                val repository = resolveCurrentRepository(project = project)
                if (repository == null) {
                    LOG.info("Skipping stale worktree pruning because the current repository could not be resolved")
                    return@launch
                }
                val workTreePath = try {
                    Path.of(workTreeDirectory)
                } catch (exception: Exception) {
                    LOG.info("Skipping stale worktree pruning because the work tree directory is invalid", exception)
                    return@launch
                }
                val discovered = withContext(Dispatchers.IO) {
                    PullRequestWorktreeListing.worktreesForRepo(
                        workTreeDirectory = workTreePath,
                        repoSlug = repository.repoSlug
                    )
                }
                val openPrNumbers = (openResult as PullRequestListResult.Success).pullRequests.map { summary -> summary.id }.toSet()
                val inProgress = worktreesInProgressSnapshot()
                val stale = StaleWorktreePruneSelection.worktreesToPrune(
                    discovered = discovered,
                    openPrNumbers = openPrNumbers
                )
                val candidates = stale.filterNot { worktree -> worktree.worktreePath in inProgress }
                val resolution = PullRequestWorktreeResolver.resolve(
                    mappings = configurationService.watchedProjectsForCurrentWorkspaceWithLocalPaths(),
                    workTreeDirectory = workTreeDirectory,
                    projectsDirectory = configurationService.projectsDirectoryOrNull(),
                    workspace = repository.workspace,
                    repoSlug = repository.repoSlug,
                    prNumber = candidates.firstOrNull()?.prNumber ?: return@launch
                )
                val sourceCheckoutPath = when (resolution) {
                    is PullRequestWorktreeResolution.Resolved -> {
                        resolution.sourceCheckoutPath
                    }
                    PullRequestWorktreeResolution.WorkTreeDirMissing,
                    PullRequestWorktreeResolution.SourceCheckoutMissing -> {
                        LOG.info("Skipping stale worktree pruning because the source checkout could not be resolved")
                        return@launch
                    }
                }
                var removed = 0
                var failed = 0
                var skipped = discovered.size - candidates.size
                for (worktree in candidates) {
                    if (worktreeService.hasUncommittedChanges(worktreePath = worktree.worktreePath)) {
                        skipped++
                        LOG.info("Keeping stale worktree ${worktree.worktreePath}: it has uncommitted changes")
                        continue
                    }
                    when (
                        val removal = worktreeService.removeWorktree(
                            sourceCheckoutRoot = sourceCheckoutPath,
                            worktreePath = worktree.worktreePath,
                            workTreeDirectory = workTreePath
                        )
                    ) {
                        com.github.fnunezkanut.bbcloud.git.WorktreeRemovalResult.Success -> {
                            removed++
                            LOG.debug("Pruned stale worktree ${worktree.worktreePath}")
                        }
                        is com.github.fnunezkanut.bbcloud.git.WorktreeRemovalResult.Failed -> {
                            failed++
                            LOG.warn("Could not prune stale worktree ${worktree.worktreePath}: ${removal.output}")
                        }
                    }
                }
                LOG.debug("Stale worktree pruning completed: removed=$removed, failed=$failed, skipped=$skipped")
            } finally {
                pruneInProgress.set(false)
            }
        }
    }

    private fun worktreesInProgressSnapshot(): Set<Path> {
        if (ApplicationManager.getApplication().isDispatchThread) {
            return worktreesInProgress.toSet()
        }
        var snapshot = emptySet<Path>()
        ApplicationManager.getApplication().invokeAndWait({
            snapshot = worktreesInProgress.toSet()
        }, ModalityState.any())
        return snapshot
    }

    private fun showCard(card: ViewCard) {
        LOG.debug("Showing Current Project - PRs card $card")
        projectCard.value = card
    }

    /** Opens (or focuses) the new pull request form in a `new.pr` editor tab of the current project. */
    private fun openNewPullRequestEditor() {
        val project = boundProject
        val repository = currentRepository
        val organizationUrl = configurationService.organizationUrlOrNull()
        if (project == null || repository == null || organizationUrl == null) {
            LOG.info("Cannot open the new pull request form: the project repository or organization URL is unavailable")
            return
        }
        val repositoryWebUrl = repositoryWebUrl
            ?: BitbucketRepositoryUrls.repositoryWebUrlForWorkspace(workspace = repository.workspace, repoSlug = repository.repoSlug)
        if (repositoryWebUrl == null) {
            LOG.info("Cannot open the new pull request form: the repository web URL is unavailable")
            return
        }
        val coroutineScope = boundCoroutineScope
        if (coroutineScope == null) {
            LOG.info("Cannot open the new pull request form: the main panel is not bound to a coroutine scope")
            return
        }
        coroutineScope.launch {
            val currentLocalBranch = withContext(Dispatchers.IO) { readCurrentLocalBranch(project = project) }
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) {
                    return@invokeLater
                }
                project.service<BbCloudNewPullRequestEditorOpener>().open(
                    NewPullRequestContext(
                        organizationUrl = organizationUrl,
                        workspace = repository.workspace,
                        repositorySlug = repository.repoSlug,
                        repositoryWebUrl = repositoryWebUrl,
                        currentLocalBranch = currentLocalBranch
                    )
                )
            }
        }
    }

    private fun readCurrentLocalBranch(project: Project): String? {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        val basePath = project.basePath
        val baseDirectory = basePath?.let { path -> LocalFileSystem.getInstance().findFileByPath(path) }
        val repository = baseDirectory?.let { directory -> repositoryManager.getRepositoryForRootQuick(directory) }
            ?: repositoryManager.repositories.singleOrNull()
        return repository?.currentBranchName
    }

    /**
     * Orchestrates everything after a successful create: switches the current checkout to the main branch, reloads the
     * listings, and opens the created pull request in its own work tree. Runs in [boundCoroutineScope], which outlives
     * the `new.pr` tab. Returns false when the panel is not bound to a project or scope.
     */
    fun openCreatedPullRequest(summary: PullRequestSummary, localMainBranch: String?): Boolean {
        val project = boundProject
        val coroutineScope = boundCoroutineScope
        if (project == null || coroutineScope == null) {
            LOG.info("Cannot open created pull request #${summary.id}: the main panel is not bound to a project")
            return false
        }
        LOG.info("Opening created pull request #${summary.id} in its own work tree")
        coroutineScope.launch {
            val switched = switchCurrentCheckoutToMainBranch(project = project, localMainBranch = localMainBranch, summary = summary)
            if (!switched) {
                return@launch
            }
            ApplicationManager.getApplication().invokeLater {
                triggerLoadPullRequests(source = "pull request created")
                openPullRequestInWorktree(summary = summary, source = "pull request creation")
            }
        }
        return true
    }

    /**
     * Switches the current project's checkout to [localMainBranch] so the created pull request's branch is free for a
     * work tree. Never switches over uncommitted work. Returns false when the switch could not be done, having already
     * shown the failure balloon.
     */
    private suspend fun switchCurrentCheckoutToMainBranch(project: Project, localMainBranch: String?, summary: PullRequestSummary): Boolean {
        if (localMainBranch == null) {
            LOG.info("No main branch known; skipping the branch switch before opening pull request #${summary.id}")
            return true
        }
        val status = branchCheckoutService.workingTreeStatus(project = project)
        when {
            status?.currentBranch == localMainBranch -> {
                LOG.debug("Current checkout is already on the main branch")
                return true
            }
            status != null && status.hasUncommittedChanges -> {
                LOG.info("Not switching to $localMainBranch before opening pull request #${summary.id}: uncommitted changes")
                showSwitchMainDirtyNotification(summary = summary, localMainBranch = localMainBranch)
                return false
            }
            status == null -> {
                LOG.info("Could not read the working tree before opening pull request #${summary.id}; attempting the checkout anyway")
            }
        }
        return when (val result = branchCheckoutService.checkout(project = project, branchName = localMainBranch)) {
            is BranchCheckoutResult.Success -> {
                LOG.info("Switched the current checkout to $localMainBranch before opening pull request #${summary.id}")
                true
            }
            is BranchCheckoutResult.Failed -> {
                LOG.warn("Could not switch the current checkout to the main branch before opening pull request #${summary.id}", result.cause)
                showSwitchMainFailedNotification(summary = summary, output = result.output)
                false
            }
        }
    }

    private fun showSwitchMainDirtyNotification(summary: PullRequestSummary, localMainBranch: String) {
        val project = boundProject ?: return
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                BbCloudBundle.message("newPr.switchMain.dirty.title"),
                BbCloudBundle.message("newPr.switchMain.dirty.message", localMainBranch),
                NotificationType.ERROR
            )
        notification.addAction(
            NotificationAction.createSimpleExpiring(BbCloudBundle.message("otherProjects.worktree.openInBitbucket")) {
                BrowserUtil.browse(summary.webUrl)
            }
        )
        notification.notify(project)
    }

    private fun showSwitchMainFailedNotification(summary: PullRequestSummary, output: String) {
        val project = boundProject ?: return
        val trimmed = output.trim()
        val balloonContent = if (trimmed.isBlank()) {
            BbCloudBundle.message("newPr.switchMain.failed.generic")
        } else if (trimmed.length > MAX_WORKTREE_FAILURE_POPUP_LENGTH) {
            trimmed.substring(0, MAX_WORKTREE_FAILURE_POPUP_LENGTH) + "\u2026"
        } else {
            trimmed
        }
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                BbCloudBundle.message("newPr.switchMain.failed.title", summary.id),
                balloonContent,
                NotificationType.ERROR
            )
        notification.addAction(
            NotificationAction.createSimpleExpiring(BbCloudBundle.message("otherProjects.worktree.openInBitbucket")) {
                BrowserUtil.browse(summary.webUrl)
            }
        )
        notification.notify(project)
    }

    companion object {
        private val LOG = logger<BbCloudMainPanel>()
        private const val NOTIFICATION_GROUP_ID = "BbCloud"
        private const val MAX_WORKTREE_FAILURE_POPUP_LENGTH = 2_000
    }
}