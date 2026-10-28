@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.ui.otherprojects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.AuthoredPullRequestListResult
import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudAuthoredPullRequestService
import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudOpenPullRequestService
import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudReviewQueueService
import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryUrls
import com.github.fnunezkanut.bbcloud.bitbucket.OkHttpBitbucketPullRequestClient
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewingPullRequest
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewingPullRequestListResult
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.credentials.BbCloudCredentialStore
import com.github.fnunezkanut.bbcloud.git.BbCloudWorktreeService
import com.github.fnunezkanut.bbcloud.git.BitbucketGitRemoteReader
import com.github.fnunezkanut.bbcloud.git.WorktreeRemovalResult
import com.github.fnunezkanut.bbcloud.git.WorktreeSetupResult
import com.github.fnunezkanut.bbcloud.project.LocalRepositoryProjectOpener
import com.github.fnunezkanut.bbcloud.project.ProjectPruneOutcome
import com.github.fnunezkanut.bbcloud.project.PullRequestOpenCheck
import com.github.fnunezkanut.bbcloud.project.PullRequestOpenCheckResult
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeListing
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeResolution
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeResolver
import com.github.fnunezkanut.bbcloud.project.WatchedProjectMapping
import com.github.fnunezkanut.bbcloud.project.WatchedProjectWorktreeRow
import com.github.fnunezkanut.bbcloud.project.WatchedProjectsDiscoveryResult
import com.github.fnunezkanut.bbcloud.project.WorktreePruneAction
import com.github.fnunezkanut.bbcloud.project.WorktreePruneDecision
import com.github.fnunezkanut.bbcloud.project.WorktreePruneSummary
import com.github.fnunezkanut.bbcloud.ui.components.NotConfiguredContent
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPendingEditorOpen
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestEditorOpenQueue
import com.github.fnunezkanut.bbcloud.ui.worktree.WorktreeBranchCollisionDialog
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withModalProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.bridge.compose
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope as coroutineScopeBlock

/**
 * Coordinator for the Workspace scope (tabs 3-5) that owns the shared data and UI for:
 *   - Tab 3: Workspace - My PRs (Compose body owned by WorkspaceMyPullRequestsViewModel)
 *   - Tab 4: Workspace - Assigned PRs (Compose body owned by WorkspaceAssignedPullRequestsViewModel)
 *   - Tab 5: Workspace - Watched Projects (projects directory selection and discovered project list)
 */

class BbCloudOtherProjectsPanel(
    private val project: Project,
    private val configurationService: BbCloudConfigurationService,
    private val authoredPullRequestService: BbCloudAuthoredPullRequestService,
    private val reviewQueueService: BbCloudReviewQueueService,
    private val coroutineScope: CoroutineScope,
    private val onGoToSettings: () -> Unit,
    private val onRefreshRequested: () -> Unit
) {
    /** Prepares the per-pull-request git worktree the pull request is opened into, without mutating the shared clone. */
    private val worktreeService = BbCloudWorktreeService.getInstance()

    /** Re-checks with Bitbucket whether a pull request is still open at click time, keyed by the row's workspace and slug. */
    private val openPullRequestService = BbCloudOpenPullRequestService(
        credentialStore = ApplicationManager.getApplication().service<BbCloudCredentialStore>(),
        pullRequestClient = OkHttpBitbucketPullRequestClient()
    )

    /** Prevents repeated prune requests for all watched projects while the first request is running. */
    private val pruneAllInProgress = AtomicBoolean(false)

    // Three tab bodies
    private val myPrsViewModel = WorkspaceMyPullRequestsViewModel(
        onOpenLocalProjectCallback = ::openLocalProjectForPullRequest,
        onOpenPrOnWebCallback = ::openPrOnWebForPullRequest,
        onOpenRepositoryCallback = ::openRepositoryForPullRequest
    )
    private val assignedPrsViewModel = WorkspaceAssignedPullRequestsViewModel(
        onOpenLocalProjectCallback = ::openLocalProjectForPullRequest,
        onOpenPrOnWebCallback = ::openPrOnWebForPullRequest,
        onOpenRepositoryCallback = ::openRepositoryForPullRequest
    )
    private val watchedProjectsViewModel = WatchedProjectsViewModel(
        coroutineScope = coroutineScope,
        onWatchedChangedCallback = { mapping, watched ->
            configurationService.setWatched(mapping = mapping, watched = watched)
            refreshWatchedRows()
        },
        onAllWatchedChangedCallback = { watched ->
            configurationService.setAllWatched(watched = watched)
            refreshWatchedRows()
        },
        onSyncRequestedCallback = { rescanWatchedProjects() },
        onOpenLocalProjectCallback = ::openLocalCheckoutProject,
        onOpenWorktreeCallback = ::openWatchedPrWorktree,
        onPruneWorktreesCallback = ::pruneAllWatchedWorktrees
    )

    // Two separate workspace headers, one per tab root (My PRs and Assigned PRs)
    private val myPrsHeaderViewModel = WorkspaceHeaderViewModel(onRefreshRequested = onRefreshRequested)
    private val assignedPrsHeaderViewModel = WorkspaceHeaderViewModel(onRefreshRequested = onRefreshRequested)

    /** The two cards of each Workspace tab root, driven by Compose state instead of a Swing CardLayout. */
    private val myPrsCard: MutableState<ViewCard> = mutableStateOf(ViewCard.Content)
    private val assignedPrsCard: MutableState<ViewCard> = mutableStateOf(ViewCard.Content)
    private val watchedProjectsCard: MutableState<ViewCard> = mutableStateOf(ViewCard.Content)

    /** Counter so overlapping loads keep the refresh button disabled until all finish. */
    private var activeLoadCount = 0

    val myPrsTabComponent: JComponent = compose {
        WorkspaceRootContent(
            card = myPrsCard.value,
            headerState = myPrsHeaderViewModel.state.value,
            onRefreshRequested = myPrsHeaderViewModel::onRefreshClicked,
            onGoToSettings = onGoToSettings,
            content = {
                WorkspacePullRequestsScreen(
                    viewModel = myPrsViewModel,
                    tab = WorkspacePullRequestsTab.MyPrs
                )
            }
        )
    }

    val assignedPrsTabComponent: JComponent = compose {
        WorkspaceRootContent(
            card = assignedPrsCard.value,
            headerState = assignedPrsHeaderViewModel.state.value,
            onRefreshRequested = assignedPrsHeaderViewModel::onRefreshClicked,
            onGoToSettings = onGoToSettings,
            content = {
                WorkspacePullRequestsScreen(
                    viewModel = assignedPrsViewModel,
                    tab = WorkspacePullRequestsTab.AssignedPrs
                )
            }
        )
    }

    val watchedProjectsTabComponent: JComponent = compose {
        WorkspaceRootContent(
            card = watchedProjectsCard.value,
            headerState = null,
            onRefreshRequested = {},
            onGoToSettings = onGoToSettings,
            content = { WatchedProjectsScreen(viewModel = watchedProjectsViewModel) }
        )
    }

    /**
     * Shows the persisted mappings of the current workspace on the Watched Projects tab, or routes to the generic
     * not-configured card when no projects directory is set. Does not rescan: a rescan runs only after a directory is
     * chosen, or a workspace slug change is saved in Settings (see [rescanWatchedProjects] and
     * [BbCloudConfigurationService.saveSetup]).
     */
    fun bindWatchedProjects() {
        LOG.debug("Binding the Workspace - Watched Projects tab")
        val projectsDirectory = configurationService.projectsDirectoryOrNull()
        if (projectsDirectory == null) {
            LOG.info("Watched Projects: projects directory not set; prompting for Settings")
            // Route to the generic centered not-configured card (R2) rather than the directory-specific prompt with
            // the status row still visible. Hide the Sync button as well, since the content card is not shown.
            watchedProjectsViewModel.setSyncVisible(false)
            showWatchedProjectsNotConfigured()
            return
        }
        watchedProjectsViewModel.setSyncVisible(true)
        watchedProjectsViewModel.setSyncEnabled(true)
        // Avatar URLs come from plugin storage (written on Sync), so this bind makes no Bitbucket API call. The only
        // network is the unauthenticated public-CDN image prefetch kicked off inside showResult.
        watchedProjectsViewModel.showResult(
            mappings = configurationService.watchedProjectsForCurrentWorkspaceWithLocalPaths(),
            organizationUrl = configurationService.organizationUrlOrNull(),
            avatarUrlBySlug = configurationService.repoAvatarUrlsForCurrentWorkspace(),
            coroutineScope = coroutineScope,
            workTreeDirectory = watchedWorkTreeDirectoryOrNull()
        )
        watchedProjectsCard.value = ViewCard.Content
    }

    /**
     * Reloads the rows of the Watched Projects table from the stored list after a watched flag changed. No rescan runs:
     * the flag is a local choice and the list itself does not change.
     */
    private fun refreshWatchedRows() {
        // A watched-flag toggle does not change the avatar-URL set, so no avatar rebind is needed here; the ViewModel
        // keeps resolving icons from the already-bound slug-to-URL map.
        watchedProjectsViewModel.refreshRows(
            mappings = configurationService.watchedProjectsForCurrentWorkspaceWithLocalPaths(),
            workTreeDirectory = watchedWorkTreeDirectoryOrNull(),
            coroutineScope = coroutineScope
        )
    }

    private fun watchedWorkTreeDirectoryOrNull(): Path? {
        return configurationService.workTreeDirectoryOrNull()?.let { directory -> Path.of(directory) }
    }

    /**
     * Drops the in-memory decoded repository avatars on the Watched Projects tab. Called by the Reset Plugin flow after
     * [BbCloudConfigurationService.resetApiToken] and [BbCloudConfigurationService.clearProjectsDirectory] paths also
     * clear the persisted URL map; they need no in-memory wipe because the next bind replaces the slug->URL map, so any
     * leftover decoded icons are simply unreferenced.
     */
    fun resetWatchedProjectsAvatars() {
        watchedProjectsViewModel.clearAvatarCache()
    }

    /**
     * Scans and verifies the projects directory, then shows the outcome of the rescan on the Workspace - Watched
     * Projects tab. Public so the tool window can trigger the scan after the Settings projects directory is chosen,
     * keeping the scan and the Watched Projects UI update owned here.
     *
     * Pass [clearFirst] = true when the scan follows a configuration change (a chosen projects directory), so the
     * watched map is emptied before discovery and rediscovered repositories come in unwatched. The Sync button leaves
     * it false, which carries over the user's existing watched choices for repositories that are found again.
     */
    fun rescanWatchedProjects(clearFirst: Boolean = false) {
        val organizationUrl = configurationService.organizationUrlOrNull()
        if (organizationUrl == null || configurationService.projectsDirectoryOrNull() == null) {
            bindWatchedProjects()
            return
        }

        watchedProjectsViewModel.setSyncEnabled(false)
        watchedProjectsViewModel.showLoading()
        coroutineScope.launch {
            val result = configurationService.rescanWatchedProjects(clearFirst = clearFirst)
            ApplicationManager.getApplication().invokeLater {
                showRescanResult(result = result, organizationUrl = organizationUrl)
            }
        }
    }

    private fun showRescanResult(result: WatchedProjectsDiscoveryResult, organizationUrl: String) {
        when (result) {
            is WatchedProjectsDiscoveryResult.Success -> {
                val mappings = configurationService.watchedProjectsForCurrentWorkspaceWithLocalPaths()
                LOG.info("Watched projects rescan succeeded: ${mappings.size} projects")
                // The persisted avatar-URL map was already updated by rescanWatchedProjects before this UI read.
                watchedProjectsViewModel.showResult(
                    mappings = mappings,
                    organizationUrl = organizationUrl,
                    avatarUrlBySlug = configurationService.repoAvatarUrlsForCurrentWorkspace(),
                    coroutineScope = coroutineScope,
                    workTreeDirectory = watchedWorkTreeDirectoryOrNull()
                )
            }
            is WatchedProjectsDiscoveryResult.VerifyFailed -> {
                LOG.info("Watched projects rescan could not be verified; previous projects kept")
                watchedProjectsViewModel.showResult(
                    mappings = configurationService.watchedProjectsForCurrentWorkspaceWithLocalPaths(),
                    organizationUrl = organizationUrl,
                    rescanFailed = true,
                    avatarUrlBySlug = configurationService.repoAvatarUrlsForCurrentWorkspace(),
                    coroutineScope = coroutineScope,
                    workTreeDirectory = watchedWorkTreeDirectoryOrNull()
                )
            }
            is WatchedProjectsDiscoveryResult.ProjectsDirectoryUnreadable -> {
                LOG.warn("Projects directory is not readable; watched projects cleared")
                Messages.showWarningDialog(
                    watchedProjectsTabComponent,
                    BbCloudBundle.message("workspace.watchedProjects.sync.unreadable.message"),
                    BbCloudBundle.message("workspace.watchedProjects.sync.unreadable.title")
                )
                watchedProjectsViewModel.showResult(
                    mappings = configurationService.watchedProjectsForCurrentWorkspaceWithLocalPaths(),
                    organizationUrl = organizationUrl,
                    avatarUrlBySlug = configurationService.repoAvatarUrlsForCurrentWorkspace(),
                    coroutineScope = coroutineScope,
                    workTreeDirectory = watchedWorkTreeDirectoryOrNull()
                )
            }
            is WatchedProjectsDiscoveryResult.NotConfigured -> {
                LOG.info("Cannot rescan watched projects: not configured")
                watchedProjectsViewModel.showEmpty()
            }
        }
        watchedProjectsViewModel.setSyncEnabled(true)
    }

    fun showWatchedProjectsNotConfigured() {
        LOG.debug("Showing the Workspace - Watched Projects not-configured card")
        watchedProjectsCard.value = ViewCard.NotConfigured
    }

    fun showNotConfigured() {
        myPrsHeaderViewModel.setRefreshVisible(false)
        assignedPrsHeaderViewModel.setRefreshVisible(false)
        myPrsHeaderViewModel.setRefreshEnabled(false)
        assignedPrsHeaderViewModel.setRefreshEnabled(false)
        myPrsViewModel.showNotConfigured()
        myPrsCard.value = ViewCard.NotConfigured
        assignedPrsCard.value = ViewCard.NotConfigured
    }

    fun loadAuthored(excludedRepoSlug: String?, coroutineScope: CoroutineScope) {
        val organizationUrl = configurationService.organizationUrlOrNull()
        if (organizationUrl == null) {
            LOG.info("Cannot load authored PRs: no organization URL")
            myPrsViewModel.showError()
            return
        }
        val workspaceOverviewUrl = BitbucketRepositoryUrls.workspaceOverviewUrl(organizationUrl)
        myPrsHeaderViewModel.setWorkspaceUrl(workspaceOverviewUrl)
        myPrsHeaderViewModel.setRefreshVisible(true)
        myPrsViewModel.showLoading()
        myPrsCard.value = ViewCard.Content
        beginLoad()
        coroutineScope.launch {
            val result = authoredPullRequestService.listAuthoredPullRequests(
                organizationUrl = organizationUrl,
                excludedRepoSlug = excludedRepoSlug
            )
            ApplicationManager.getApplication().invokeLater {
                showAuthoredResult(result, coroutineScope)
                endLoad()
            }
        }
    }

    fun loadReviewing(excludedRepoSlug: String?, coroutineScope: CoroutineScope) {
        val organizationUrl = configurationService.organizationUrlOrNull()
        val projectsDirectory = configurationService.projectsDirectoryOrNull()
        val workspaceOverviewUrl = organizationUrl?.let { BitbucketRepositoryUrls.workspaceOverviewUrl(it) }
        assignedPrsHeaderViewModel.setWorkspaceUrl(workspaceOverviewUrl)
        assignedPrsHeaderViewModel.setRefreshVisible(workspaceOverviewUrl != null)
        if (organizationUrl == null) {
            LOG.info("Cannot load reviewing PRs: no organization URL")
            assignedPrsViewModel.showError()
            return
        }
        if (projectsDirectory == null) {
            LOG.info("Assigned PRs: projects directory not set; prompting for Settings")
            // Route to the generic centered not-configured card (R2) rather than showing partial content with the
            // header still visible. showNotConfigured switches both My PRs and Assigned PRs and hides their refresh
            // buttons.
            showNotConfigured()
            return
        }
        val repoSlugs = configurationService.watchedRepoSlugsForCurrentWorkspace()
        if (repoSlugs.isEmpty()) {
            LOG.info("No watched projects are enabled for the current workspace, so no pull requests to review are listed")
        }
        assignedPrsViewModel.showLoading()
        assignedPrsHeaderViewModel.setRefreshVisible(true)
        assignedPrsCard.value = ViewCard.Content
        beginLoad()
        coroutineScope.launch {
            val result = reviewQueueService.listReviewingPullRequests(
                organizationUrl = organizationUrl,
                excludedRepoSlug = excludedRepoSlug,
                repoSlugs = repoSlugs
            )
            ApplicationManager.getApplication().invokeLater {
                showReviewingResult(result, coroutineScope)
                endLoad()
            }
        }
    }

    private fun showAuthoredResult(result: AuthoredPullRequestListResult, coroutineScope: CoroutineScope) {
        when (result) {
            is AuthoredPullRequestListResult.Success -> {
                myPrsViewModel.showResult(
                    result = result,
                    organizationUrl = configurationService.organizationUrlOrNull(),
                    coroutineScope = coroutineScope,
                    avatarUrlBySlug = configurationService.repoAvatarUrlsForCurrentWorkspace()
                )
            }
            is AuthoredPullRequestListResult.Failed -> {
                if (result.cause != null) {
                    LOG.warn("Could not load the pull requests you opened", result.cause)
                } else {
                    LOG.warn("Could not load the pull requests you opened")
                }
                myPrsViewModel.showError()
            }
        }
    }

    private fun showReviewingResult(result: ReviewingPullRequestListResult, coroutineScope: CoroutineScope) {
        when (result) {
            is ReviewingPullRequestListResult.Success -> {
                assignedPrsViewModel.showResult(
                    result = result,
                    organizationUrl = configurationService.organizationUrlOrNull(),
                    coroutineScope = coroutineScope,
                    avatarUrlBySlug = configurationService.repoAvatarUrlsForCurrentWorkspace()
                )
            }
            is ReviewingPullRequestListResult.Failed -> {
                if (result.cause != null) {
                    LOG.warn("Could not load the pull requests to review", result.cause)
                } else {
                    LOG.warn("Could not load the pull requests to review")
                }
                assignedPrsViewModel.showError()
            }
        }
    }

    /** Guards against duplicate open coroutines for the same worktree path. Mutated on the EDT only. */
    private val worktreesInProgress = mutableSetOf<Path>()

    private fun openLocalProjectForPullRequest(pullRequest: ReviewingPullRequest) {
        startOpenPullRequestInWorktree(pullRequest = pullRequest)
    }

    private fun openPrOnWebForPullRequest(pullRequest: ReviewingPullRequest) {
        val url = pullRequest.summary.webUrl
        if (url.isBlank()) {
            LOG.info("Cannot open pull request #${pullRequest.summary.id} in the browser: URL is blank")
            return
        }
        LOG.info("User opened pull request #${pullRequest.summary.id} in the browser from a Workspace PR table")
        BrowserUtil.browse(url)
    }

    /**
     * Handles a click on the Repository cell of the Workspace PR tables: when a local checkout of the row's repository
     * exists under the configured projects directory, that project is opened in a new IDE window (or refocused if it is
     * already open); otherwise the repository's Bitbucket web page is opened in a browser. Resolution and URL building
     * run no Bitbucket API call; the project open runs on [coroutineScope] because it is a suspend operation.
     */
    private fun openRepositoryForPullRequest(pullRequest: ReviewingPullRequest) {
        val repoSlug = pullRequest.repoSlug
        val webUrl = configurationService.organizationUrlOrNull()?.let { orgUrl ->
            BitbucketRepositoryUrls.repositoryWebUrl(organizationUrl = orgUrl, repoSlug = repoSlug)
        }
        val localPath = configurationService.localCheckoutPathForRepo(repoSlug)
        if (localPath.isBlank()) {
            LOG.info("Opened Bitbucket repository $repoSlug from a Workspace PR table (no local checkout)")
            webUrl?.let { url -> BrowserUtil.browse(url) }
            return
        }
        coroutineScope.launch {
            val opened = LocalRepositoryProjectOpener.openOrFocus(repoSlug = repoSlug, checkoutPath = Path.of(localPath))
            if (opened != null) {
                LOG.info("Opened local project $repoSlug from a Workspace PR table")
            } else {
                LOG.warn("Could not open local project $repoSlug; falling back to the Bitbucket web page")
                ApplicationManager.getApplication().invokeLater {
                    webUrl?.let { url -> BrowserUtil.browse(url) }
                }
            }
        }
    }

    /**
     * Opens [pullRequest] in a dedicated git worktree rather than mutating the shared local clone. The whole operation
     * runs on [coroutineScope] so the EDT is not blocked:
     *   1. a modal re-checks with Bitbucket that the pull request is still open and prepares the worktree;
     *   2. once the modal closes, a new IDE window is opened for the worktree project;
     *   3. the pull request editor tab is opened in that NEW window after it is fully initialized.
     *
     * Every failure path (pull request no longer open, missing directories, worktree preparation failure, or an open
     * failure) STOPS and shows an error popup with a clickable link to the pull request on Bitbucket; nothing is
     * redirected automatically (decision D-2). User cancellation is the one exception: it shows no popup.
     */
    private fun startOpenPullRequestInWorktree(pullRequest: ReviewingPullRequest) {
        val summary = pullRequest.summary
        LOG.info("User requested to open pull request #${summary.id} of ${pullRequest.repoSlug} in its own work tree")

        val resolution = PullRequestWorktreeResolver.resolve(
            mappings = configurationService.watchedProjectsForCurrentWorkspaceWithLocalPaths(),
            workTreeDirectory = configurationService.workTreeDirectoryOrNull(),
            projectsDirectory = configurationService.projectsDirectoryOrNull(),
            workspace = pullRequest.workspace,
            repoSlug = pullRequest.repoSlug,
            prNumber = summary.id
        )
        val resolved = when (resolution) {
            is PullRequestWorktreeResolution.Resolved -> {
                resolution
            }
            is PullRequestWorktreeResolution.WorkTreeDirMissing -> {
                LOG.info("Cannot open pull request #${summary.id}: work tree directory could not be derived (no projects directory configured)")
                showWorktreeErrorNotification(
                    pullRequest = pullRequest,
                    content = BbCloudBundle.message("otherProjects.worktree.failed.workTreeDirMissing")
                )
                return
            }
            is PullRequestWorktreeResolution.SourceCheckoutMissing -> {
                LOG.info(
                    "Cannot open pull request #${summary.id}: local clone of ${pullRequest.repoSlug} not found under the watched mapping or the projects directory"
                )
                showWorktreeErrorNotification(
                    pullRequest = pullRequest,
                    content = BbCloudBundle.message("otherProjects.worktree.failed.sourceCheckoutMissing", pullRequest.repoSlug)
                )
                return
            }
        }

        val worktreePath = resolved.worktreePath
        if (!worktreesInProgress.add(worktreePath)) {
            LOG.info("Opening pull request #${summary.id} is already in progress for $worktreePath")
            return
        }

        coroutineScope.launch {
            try {
                val preparation = runWorktreeModal(pullRequest = pullRequest, resolution = resolved)
                when (preparation) {
                    is WorktreePreparation.Prepared -> {
                        openWorktreeProjectAndEditor(pullRequest = pullRequest, worktreePath = preparation.worktreePath)
                    }
                    is WorktreePreparation.Failed -> {
                        // D-2: every failure stops here and shows an error popup with a clickable Bitbucket link; it
                        // never redirects automatically.
                        showWorktreeErrorNotification(pullRequest = pullRequest, content = preparation.content)
                    }
                    is WorktreePreparation.BranchCheckedOutInSource -> {
                        // The source clone is on the PR branch; show the explanatory modal rather than the raw git
                        // balloon. The modal marshals to and blocks on the EDT itself, so call it directly here.
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
                // D-2: the user cancelled the modal mid-preparation; cleanup already ran in the worktree service, and
                // no popup is shown because the user chose to cancel.
                LOG.info("User cancelled work tree preparation for pull request #${summary.id}")
                throw exception
            } finally {
                ApplicationManager.getApplication().invokeLater {
                    worktreesInProgress.remove(worktreePath)
                }
            }
        }
    }

    private sealed interface WorktreePreparation {
        data class Prepared(val worktreePath: Path) : WorktreePreparation

        /**
         * Worktree preparation failed (decision D-2). [content] is the already-localized popup body for the failure:
         * the real git output for a worktree-git failure, or a bundle message for a precondition failure (no
         * organization URL, pull request no longer open, not a git repository). The caller shows an error balloon with
         * [content] and a clickable link to the pull request on Bitbucket, and does NOT redirect automatically.
         */
        data class Failed(val content: String) : WorktreePreparation

        /**
         * The source clone is currently on the pull request branch, so the work tree could not be created. The caller
         * shows the explanatory [WorktreeBranchCollisionDialog] modal instead of the error balloon.
         */
        data class BranchCheckedOutInSource(val branchName: String, val sourceCheckoutPath: Path) : WorktreePreparation
    }

    /**
     * Runs the "Loading PR in its own git work tree" modal, which re-checks the pull request state and prepares the
     * worktree. The modal uses an application-level owner because the worktree project does not exist yet and the
     * current project must not be blocked. The project window is opened AFTER this returns, so the modal has already
     * closed when the new frame appears (requirement #6).
     */
    private suspend fun runWorktreeModal(pullRequest: ReviewingPullRequest, resolution: PullRequestWorktreeResolution.Resolved): WorktreePreparation {
        val summary = pullRequest.summary
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
            // Requirement #1: verify the pull request is still open before touching the disk.
            val checkResult = checkStillOpen(
                pullRequest = pullRequest,
                organizationUrl = organizationUrl
            )
            if (checkResult != null) {
                return@withModalProgress checkResult
            }

            // Requirements #2-#4: create the folder, add the worktree, and set it to the pull request branch.
            val remoteUrl = withContext(Dispatchers.IO) {
                BitbucketGitRemoteReader.readRemoteUrl(checkoutRoot = resolution.sourceCheckoutPath)
            }
            if (remoteUrl == null) {
                LOG.info("Cannot open pull request #${summary.id}: source checkout is not a git repository or has no origin remote")
                return@withModalProgress WorktreePreparation.Failed(
                    content = BbCloudBundle.message("otherProjects.worktree.failed.notAGitRepo")
                )
            }
            val setupResult = worktreeService.ensureWorktree(
                sourceCheckoutRoot = resolution.sourceCheckoutPath,
                worktreePath = resolution.worktreePath,
                remoteUrl = remoteUrl,
                prNumber = summary.id,
                sourceBranch = summary.sourceBranch
            )
            when (setupResult) {
                is WorktreeSetupResult.Success -> {
                    WorktreePreparation.Prepared(worktreePath = setupResult.worktreePath)
                }
                is WorktreeSetupResult.Failed -> {
                    LOG.warn("Could not prepare the work tree for pull request #${summary.id}: ${setupResult.output}", setupResult.cause)
                    // D-2: the worktree could not be created on the source branch; carry the real git output so the
                    // caller can show it in the error popup. There is no automatic Bitbucket redirect.
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

    /** Returns a failed preparation when the pull request is no longer open or its state cannot be verified, or null when it is still open. */
    private suspend fun checkStillOpen(pullRequest: ReviewingPullRequest, organizationUrl: String): WorktreePreparation.Failed? {
        val summary = pullRequest.summary
        val details = try {
            openPullRequestService.pullRequestDetailsFor(
                workspace = pullRequest.workspace,
                repoSlug = pullRequest.repoSlug,
                organizationUrl = organizationUrl,
                pullRequestId = summary.id
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LOG.warn("PR state check failed for pull request #${summary.id}", exception)
            return WorktreePreparation.Failed(content = BbCloudBundle.message("otherProjects.worktree.failed.prStateCheckFailed", summary.id))
        }
        return when (val checkResult = PullRequestOpenCheck.evaluate(details = details)) {
            is PullRequestOpenCheckResult.StillOpen -> {
                null
            }
            is PullRequestOpenCheckResult.NotOpen -> {
                LOG.info("Cannot open pull request #${summary.id}: it is ${checkResult.state} (no longer open)")
                WorktreePreparation.Failed(content = BbCloudBundle.message("otherProjects.worktree.failed.prNotOpen", summary.id, checkResult.state))
            }
            is PullRequestOpenCheckResult.NotFound -> {
                LOG.info("Cannot open pull request #${summary.id}: not found on Bitbucket")
                WorktreePreparation.Failed(content = BbCloudBundle.message("otherProjects.worktree.failed.prNotFound", summary.id))
            }
            is PullRequestOpenCheckResult.Failed -> {
                LOG.warn("PR state check failed for pull request #${summary.id}", checkResult.cause)
                WorktreePreparation.Failed(content = BbCloudBundle.message("otherProjects.worktree.failed.prStateCheckFailed", summary.id))
            }
        }
    }

    /**
     * Opens (or focuses) the IDE window for the prepared worktree, then opens the pull request editor tab in that target
     * window. The returned project result identifies the target window; the tab is opened against it so it never lands in
     * the current window (the hard constraint, requirement #7).
     */
    private suspend fun openWorktreeProjectAndEditor(pullRequest: ReviewingPullRequest, worktreePath: Path) {
        val openedProject = LocalRepositoryProjectOpener.openOrFocus(
            repoSlug = pullRequest.repoSlug,
            checkoutPath = worktreePath
        )
        if (openedProject == null) {
            LOG.info("Cannot open pull request #${pullRequest.summary.id}: the IDE could not open the work tree project")
            showWorktreeErrorNotification(
                pullRequest = pullRequest,
                content = BbCloudBundle.message("otherProjects.worktree.failed.openProjectFailed")
            )
            return
        }
        openPullRequestEditor(openedProject = openedProject, pullRequest = pullRequest)
    }

    /**
     * Opens the pull request editor in the target project window using the Bitbucket repository slug of the watched
     * entry. Failures are logged but never shown as balloons, so the user keeps working in the checkout.
     */
    private fun openPullRequestEditor(openedProject: LocalRepositoryProjectOpener.OpenedProject, pullRequest: ReviewingPullRequest) {
        val project = openedProject.project
        if (project.isDisposed) {
            LOG.info("The project was disposed before the pull request editor could be opened")
            return
        }
        val organizationUrl = configurationService.organizationUrlOrNull()
        val repositoryWebUrl = organizationUrl?.let { url ->
            BitbucketRepositoryUrls.repositoryWebUrl(organizationUrl = url, repoSlug = pullRequest.repoSlug)
        }
        if (repositoryWebUrl == null) {
            LOG.info("Cannot open pull request #${pullRequest.summary.id}: no repository web URL")
            return
        }
        // D6: queue the open so the tab lands in the NEW window after services and indexing have initialized.
        val queue = project.service<BbCloudPullRequestEditorOpenQueue>()
        queue.enqueue(
            BbCloudPendingEditorOpen(
                summary = pullRequest.summary,
                branchName = pullRequest.summary.sourceBranch,
                repositoryWebUrl = repositoryWebUrl,
                repositorySlug = pullRequest.repoSlug
            )
        )
        if (openedProject.wasAlreadyOpen) {
            queue.drainAndOpen(project)
        }
    }

    /**
     * Shows an ERROR balloon (decision D-2) in the registered `BbCloud` notification group describing a failed attempt
     * to open the pull request in its own work tree, then STOPS. The balloon's own close control is the only dismiss
     * (it does nothing else, no redirect); a single "Open PR in Bitbucket" action lets the user open the pull request
     * on Bitbucket in a browser when they choose to. [content] is the already-localized failure body: a bundle message
     * for precondition failures, or the real git output for a worktree-git failure. The git output contains no
     * credentials, tokens, Authorization headers, or raw HTTP bodies (git worktree output has none); very long output
     * is trimmed to keep the balloon readable, and a blank body falls back to a generic message. The `info`/`warn`
     * logs at the call sites already record the reason, so this popup is additive and does not escalate to an `error`.
     */
    private fun showWorktreeErrorNotification(pullRequest: ReviewingPullRequest, content: String) {
        val summary = pullRequest.summary
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

    /**
     * Opens a watched project's local checkout as an IntelliJ project in a new IDE window. Unlike
     * [openRepositoryForPullRequest], this opens the plain checkout (not a PR) and does not launch the pull request
     * editor tab. Missing directories and open failures surface as `BbCloud` error balloons and stay on the table.
     */
    private fun openLocalCheckoutProject(mapping: WatchedProjectMapping) {
        val checkoutPath = Path.of(mapping.localFolderPath)
        if (mapping.localFolderPath.isBlank() || !java.nio.file.Files.isDirectory(checkoutPath)) {
            LOG.info("Cannot open local project ${mapping.remoteRepoSlug}: checkout folder is missing")
            showLocalProjectError(
                slug = mapping.remoteRepoSlug,
                contentKey = "workspace.watchedProjects.local.open.failed.folderMissing",
                contentArgs = arrayOf(mapping.remoteRepoSlug)
            )
            return
        }
        coroutineScope.launch {
            val opened = LocalRepositoryProjectOpener.openOrFocus(
                repoSlug = mapping.remoteRepoSlug,
                checkoutPath = checkoutPath
            )
            if (opened == null) {
                LOG.warn("Could not open local project window for ${mapping.remoteRepoSlug}")
                ApplicationManager.getApplication().invokeLater {
                    showLocalProjectError(
                        slug = mapping.remoteRepoSlug,
                        contentKey = "workspace.watchedProjects.local.open.failed.openProjectFailed",
                        contentArgs = arrayOf(mapping.remoteRepoSlug)
                    )
                }
            } else {
                LOG.info("Opened local project ${mapping.remoteRepoSlug}")
            }
        }
    }

    /**
     * Opens a PR worktree that already exists on disk from the Watched Projects table. The flow mirrors the existing
     * My PRs / Assigned PRs worktree-open flow, but since the worktree folder already exists we skip creation and go
     * straight to the click-time PR state check:
     *   1. a modal re-checks with Bitbucket that the pull request is still open;
     *   2. the worktree folder is verified to still exist on disk;
     *   3. a new IDE window is opened for the existing worktree project;
     *   4. the bbcloud PR editor tab is opened in that new window.
     *
     * Failures (PR no longer open, folder missing, IDE window open failure) show a `BbCloud` error balloon and stay on
     * the table. The closed/merged/not-found balloons offer a "Remove work tree" action.
     */
    private fun openWatchedPrWorktree(mapping: WatchedProjectMapping, worktree: WatchedProjectWorktreeRow) {
        coroutineScope.launch {
            val result = withModalProgress(
                owner = ModalTaskOwner.guess(),
                title = BbCloudBundle.message("workspace.watchedProjects.worktree.open.modalTitle"),
                cancellation = TaskCancellation.cancellable()
            ) {
                val organizationUrl = configurationService.organizationUrlOrNull()
                if (organizationUrl == null) {
                    LOG.info("Cannot open PR #${worktree.prNumber}: no organization URL configured")
                    return@withModalProgress WatchedWorktreeOpen.Failed(
                        failureKey = "workspace.watchedProjects.worktree.open.failed.noOrgUrl",
                        failureArgs = emptyArray()
                    )
                }
                val details = when (
                    val checkResult = checkWatchedPrStillOpen(
                        mapping = mapping,
                        organizationUrl = organizationUrl,
                        prNumber = worktree.prNumber
                    )
                ) {
                    is PullRequestOpenCheckResult.StillOpen -> checkResult.details
                    is PullRequestOpenCheckResult.NotOpen -> return@withModalProgress WatchedWorktreeOpen.Failed(
                        failureKey = "workspace.watchedProjects.worktree.open.failed.prNotOpen",
                        failureArgs = arrayOf(worktree.prNumber, checkResult.state)
                    )
                    PullRequestOpenCheckResult.NotFound -> return@withModalProgress WatchedWorktreeOpen.Failed(
                        failureKey = "workspace.watchedProjects.worktree.open.failed.prNotFound",
                        failureArgs = arrayOf(worktree.prNumber)
                    )
                    is PullRequestOpenCheckResult.Failed -> return@withModalProgress WatchedWorktreeOpen.Failed(
                        failureKey = "workspace.watchedProjects.worktree.open.failed.prStateCheckFailed",
                        failureArgs = arrayOf(worktree.prNumber)
                    )
                }

                if (!java.nio.file.Files.isDirectory(worktree.worktreePath)) {
                    LOG.info("Cannot open PR #${worktree.prNumber}: worktree folder ${worktree.worktreePath} no longer exists")
                    return@withModalProgress WatchedWorktreeOpen.Failed(
                        failureKey = "workspace.watchedProjects.worktree.open.failed.folderMissing",
                        failureArgs = arrayOf(worktree.prNumber)
                    )
                }

                val openedProject = withContext(Dispatchers.IO) {
                    LocalRepositoryProjectOpener.openOrFocus(
                        repoSlug = mapping.remoteRepoSlug,
                        checkoutPath = worktree.worktreePath
                    )
                }
                if (openedProject == null) {
                    LOG.info("Cannot open PR #${worktree.prNumber}: IDE could not open the worktree window")
                    return@withModalProgress WatchedWorktreeOpen.Failed(
                        failureKey = "workspace.watchedProjects.worktree.open.failed.openProjectFailed",
                        failureArgs = emptyArray()
                    )
                }

                val pullRequest = ReviewingPullRequest(
                    summary = details.summary,
                    workspace = mapping.workspace,
                    repoSlug = mapping.remoteRepoSlug
                )
                openPullRequestEditor(openedProject = openedProject, pullRequest = pullRequest)

                WatchedWorktreeOpen.Opened
            }
            when (result) {
                is WatchedWorktreeOpen.Opened -> {
                    LOG.info("Opened PR #${worktree.prNumber} worktree for ${mapping.remoteRepoSlug}")
                }
                is WatchedWorktreeOpen.Failed -> {
                    showWatchedWorktreeError(
                        mapping = mapping,
                        worktree = worktree,
                        prNumber = worktree.prNumber,
                        failureKey = result.failureKey,
                        failureArgs = result.failureArgs
                    )
                }
            }
        }
    }

    private sealed interface WatchedWorktreeOpen {
        data object Opened : WatchedWorktreeOpen

        data class Failed(val failureKey: String, val failureArgs: Array<Any>) : WatchedWorktreeOpen
    }

    /**
     * Re-checks whether the pull request is still open, preserving the distinct state-check outcome so the caller can
     * show the appropriate dedicated notification and use the fetched details when the PR remains open.
     */
    private suspend fun checkWatchedPrStillOpen(mapping: WatchedProjectMapping, organizationUrl: String, prNumber: Long): PullRequestOpenCheckResult {
        val details = try {
            openPullRequestService.pullRequestDetailsFor(
                workspace = mapping.workspace,
                repoSlug = mapping.remoteRepoSlug,
                organizationUrl = organizationUrl,
                pullRequestId = prNumber
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LOG.warn(
                "PR state check failed for pull request #$prNumber in ${mapping.workspace}/${mapping.remoteRepoSlug}",
                exception
            )
            return PullRequestOpenCheckResult.Failed(cause = exception)
        }
        return when (val checkResult = PullRequestOpenCheck.evaluate(details = details)) {
            is PullRequestOpenCheckResult.StillOpen -> {
                checkResult
            }
            is PullRequestOpenCheckResult.NotOpen -> {
                LOG.info("Cannot open pull request #$prNumber: it is ${checkResult.state} (no longer open)")
                checkResult
            }
            PullRequestOpenCheckResult.NotFound -> {
                LOG.info("Cannot open pull request #$prNumber: not found on Bitbucket")
                checkResult
            }
            is PullRequestOpenCheckResult.Failed -> {
                LOG.warn(
                    "PR state check failed for pull request #$prNumber in ${mapping.workspace}/${mapping.remoteRepoSlug}",
                    checkResult.cause
                )
                checkResult
            }
        }
    }

    /** Shows a `BbCloud` error balloon for a local-checkout project-open failure (Step 9.1). */
    private fun showLocalProjectError(slug: String, contentKey: String, contentArgs: Array<Any>) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                BbCloudBundle.message("workspace.watchedProjects.local.open.failed.title", slug),
                BbCloudBundle.message(contentKey, *contentArgs),
                NotificationType.ERROR
            )
        notification.notify(project)
    }

    /**
     * Shows a `BbCloud` error balloon for a watched PR worktree open failure, with "Open PR in Bitbucket" (when the
     * Bitbucket web URL can be derived) and "Remove work tree" actions. The "Remove work tree" action is only offered on
     * the closed/merged/not-found failure paths.
     */
    private fun showWatchedWorktreeError(
        mapping: WatchedProjectMapping,
        worktree: WatchedProjectWorktreeRow,
        prNumber: Long,
        failureKey: String,
        failureArgs: Array<Any>
    ) {
        val title = BbCloudBundle.message("workspace.watchedProjects.worktree.open.failed.title", prNumber)
        val content = BbCloudBundle.message(failureKey, *failureArgs)

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, content, NotificationType.ERROR)

        val organizationUrl = configurationService.organizationUrlOrNull()
        val prWebUrl = organizationUrl?.let { orgUrl ->
            BitbucketRepositoryUrls.pullRequestDiffUrl(organizationUrl = orgUrl, repoSlug = mapping.remoteRepoSlug, pullRequestId = prNumber)
        }
        prWebUrl?.let { url ->
            notification.addAction(
                NotificationAction.createSimpleExpiring(BbCloudBundle.message("workspace.watchedProjects.worktree.open.openInBitbucket")) {
                    LOG.info("User opened Bitbucket PR #$prNumber from the watched worktree error popup")
                    BrowserUtil.browse(url)
                }
            )
        }

        val showRemoveAction = failureKey in setOf(
            "workspace.watchedProjects.worktree.open.failed.prNotOpen",
            "workspace.watchedProjects.worktree.open.failed.prNotFound"
        )
        if (showRemoveAction) {
            notification.addAction(
                NotificationAction.createSimpleExpiring(BbCloudBundle.message("workspace.watchedProjects.worktree.open.removeWorkTree")) {
                    LOG.info("User requested to remove the work tree for PR #$prNumber")
                    removeWatchedWorktree(mapping = mapping, worktree = worktree, prNumber = prNumber)
                }
            )
        }
        notification.notify(project)
    }

    /**
     * Removes stale PR worktree folders of every watched project on the page, one project at a time, then notifies the
     * user once with the aggregated outcome and refreshes the Watched Projects table.
     */
    private fun pruneAllWatchedWorktrees() {
        val organizationUrl = configurationService.organizationUrlOrNull()
        if (organizationUrl == null) {
            LOG.info("Cannot prune PR worktrees: no organization URL")
            showPruneNotification(
                contentKey = "workspace.watchedProjects.prune.noOrgUrl",
                type = NotificationType.WARNING
            )
            return
        }
        val workTreeDirectoryString = configurationService.workTreeDirectoryOrNull()
        if (workTreeDirectoryString == null) {
            LOG.info("Cannot prune PR worktrees: no projects directory")
            return
        }
        if (!pruneAllInProgress.compareAndSet(false, true)) {
            LOG.info("Ignoring duplicate PR worktree prune for all watched projects")
            return
        }
        watchedProjectsViewModel.setPruneInProgress(inProgress = true)

        val workTreeDirectory = Path.of(workTreeDirectoryString)
        val mappings = configurationService.watchedProjectsForCurrentWorkspaceWithLocalPaths()
        LOG.debug("Pruning PR worktrees of ${mappings.size} watched project(s)")
        coroutineScope.launch {
            try {
                val hasToken = configurationService.hasStoredToken()
                val outcomes = mutableListOf<ProjectPruneOutcome>()
                for (mapping in mappings) {
                    outcomes += pruneWorktreesOfProject(
                        mapping = mapping,
                        organizationUrl = organizationUrl,
                        workTreeDirectory = workTreeDirectory,
                        hasToken = hasToken
                    )
                }
                val summary = WorktreePruneSummary.from(outcomes = outcomes)
                ApplicationManager.getApplication().invokeLater {
                    showPruneSummary(summary = summary)
                    rescanWatchedProjects()
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                LOG.warn("PR worktree pruning failed for all watched projects", exception)
                ApplicationManager.getApplication().invokeLater {
                    showPruneNotification(
                        contentKey = "workspace.watchedProjects.prune.failedCheck",
                        type = NotificationType.WARNING
                    )
                    rescanWatchedProjects()
                }
            } finally {
                pruneAllInProgress.set(false)
                ApplicationManager.getApplication().invokeLater {
                    watchedProjectsViewModel.setPruneInProgress(inProgress = false)
                }
            }
        }
    }

    /**
     * Removes the stale PR worktree folders of one watched project and runs `git worktree prune` through
     * [BbCloudWorktreeService.removeWorktree]. A failure of this project is logged and recorded as unverifiable, so the
     * remaining projects of the batch still run.
     */
    private suspend fun pruneWorktreesOfProject(
        mapping: WatchedProjectMapping,
        organizationUrl: String,
        workTreeDirectory: Path,
        hasToken: Boolean
    ): ProjectPruneOutcome {
        val repoSlug = mapping.remoteRepoSlug
        if (mapping.localFolderPath.isBlank()) {
            LOG.info("Skipping PR worktree prune for $repoSlug: no local checkout path")
            return ProjectPruneOutcome(repoSlug = repoSlug)
        }
        try {
            val sourceCheckoutPath = Path.of(mapping.localFolderPath)
            val worktrees = withContext(Dispatchers.IO) {
                PullRequestWorktreeListing.worktreesForRepo(
                    workTreeDirectory = workTreeDirectory,
                    repoSlug = repoSlug
                )
            }
            LOG.debug("Found ${worktrees.size} PR worktree(s) to inspect for $repoSlug")
            if (worktrees.isEmpty()) {
                return ProjectPruneOutcome(repoSlug = repoSlug)
            }

            // A missing token also makes the PR service return null. Check token presence first so that missing
            // credentials are treated as unverifiable rather than as Bitbucket NotFound responses to prune.
            if (!hasToken) {
                LOG.info("Cannot verify PR worktrees for $repoSlug: no stored API token")
                return ProjectPruneOutcome(repoSlug = repoSlug, unverifiableCount = worktrees.size)
            }

            val checkedWorktrees = withContext(Dispatchers.IO) {
                coroutineScopeBlock {
                    worktrees.map { worktree ->
                        async {
                            worktree to checkWatchedPrStillOpen(
                                mapping = mapping,
                                organizationUrl = organizationUrl,
                                prNumber = worktree.prNumber
                            )
                        }
                    }.awaitAll()
                }
            }
            val worktreesToRemove = checkedWorktrees.filter { (_, result) ->
                WorktreePruneDecision.actionFor(result) == WorktreePruneAction.Remove
            }
            val skippedCount = checkedWorktrees.count { (_, result) ->
                WorktreePruneDecision.actionFor(result) == WorktreePruneAction.Skip
            }
            var removedCount = 0
            var failedCount = 0
            worktreesToRemove.forEach { (worktree, _) ->
                when (
                    val result = worktreeService.removeWorktree(
                        sourceCheckoutRoot = sourceCheckoutPath,
                        worktreePath = worktree.worktreePath,
                        workTreeDirectory = workTreeDirectory
                    )
                ) {
                    WorktreeRemovalResult.Success -> {
                        removedCount++
                    }
                    is WorktreeRemovalResult.Failed -> {
                        failedCount++
                        LOG.warn(
                            "Could not remove PR worktree #${worktree.prNumber} for $repoSlug: ${result.output}",
                            result.cause
                        )
                    }
                }
            }
            return ProjectPruneOutcome(
                repoSlug = repoSlug,
                removedCount = removedCount,
                failedRemovalCount = failedCount,
                unverifiableCount = skippedCount
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LOG.warn("PR worktree pruning failed for $repoSlug", exception)
            return ProjectPruneOutcome(repoSlug = repoSlug, unverifiableCount = 1)
        }
    }

    /** Shows the aggregated outcome of pruning all watched projects as at most two notifications. */
    private fun showPruneSummary(summary: WorktreePruneSummary) {
        val nothingRemoved = summary.removed == 0 && summary.failedRemovals == 0
        if (nothingRemoved) {
            if (summary.unverifiable == 0) {
                showPruneNotification(
                    contentKey = "workspace.watchedProjects.prune.none",
                    type = NotificationType.INFORMATION
                )
            }
        } else if (summary.failedRemovals > 0) {
            showPruneNotification(
                contentKey = "workspace.watchedProjects.prune.partial",
                type = NotificationType.WARNING,
                contentArgs = listOf(summary.removed, summary.failedRemovals)
            )
        } else {
            showPruneNotification(
                contentKey = "workspace.watchedProjects.prune.done",
                type = NotificationType.INFORMATION,
                contentArgs = listOf(summary.removed, summary.projectsWithRemovals)
            )
        }
        if (summary.unverifiable > 0) {
            showPruneNotification(
                contentKey = "workspace.watchedProjects.prune.failedCheck",
                type = NotificationType.WARNING
            )
        }
    }

    private fun showPruneNotification(contentKey: String, type: NotificationType, contentArgs: List<Any> = emptyList()) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                title = BbCloudBundle.message("workspace.watchedProjects.prune.title"),
                content = BbCloudBundle.message(contentKey, *contentArgs.toTypedArray()),
                type = type
            ).notify(project)
    }

    private fun removeWatchedWorktree(mapping: WatchedProjectMapping, worktree: WatchedProjectWorktreeRow, prNumber: Long) {
        val workTreeDirectory = configurationService.workTreeDirectoryOrNull()
        val sourceCheckoutPath = Path.of(mapping.localFolderPath)
        coroutineScope.launch {
            val result = worktreeService.removeWorktree(
                sourceCheckoutRoot = sourceCheckoutPath,
                worktreePath = worktree.worktreePath,
                workTreeDirectory = workTreeDirectory?.let { Path.of(it) } ?: return@launch
            )
            ApplicationManager.getApplication().invokeLater {
                when (result) {
                    is WorktreeRemovalResult.Success -> {
                        LOG.info("Removed worktree for PR #$prNumber")
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup(NOTIFICATION_GROUP_ID)
                            .createNotification(
                                title = "",
                                content = BbCloudBundle.message("workspace.watchedProjects.worktree.open.removed", prNumber),
                                type = NotificationType.INFORMATION
                            ).notify(project)
                    }
                    is WorktreeRemovalResult.Failed -> {
                        LOG.warn("Could not remove worktree for PR #$prNumber: ${result.output}", result.cause)
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup(NOTIFICATION_GROUP_ID)
                            .createNotification(
                                title = "",
                                content = BbCloudBundle.message("workspace.watchedProjects.worktree.open.removeFailed", prNumber),
                                type = NotificationType.ERROR
                            ).notify(project)
                    }
                }
                rescanWatchedProjects()
            }
        }
    }

    private fun beginLoad() {
        activeLoadCount++
        myPrsHeaderViewModel.setRefreshEnabled(false)
        assignedPrsHeaderViewModel.setRefreshEnabled(false)
    }

    @Synchronized
    private fun endLoad() {
        if (--activeLoadCount <= 0) {
            activeLoadCount = 0
            myPrsHeaderViewModel.setRefreshEnabled(true)
            assignedPrsHeaderViewModel.setRefreshEnabled(true)
        }
    }

    companion object {
        private val LOG = logger<BbCloudOtherProjectsPanel>()

        /** The registered notification group id declared in plugin.xml; reused for the worktree failure balloon. */
        private const val NOTIFICATION_GROUP_ID = "BbCloud"

        /** Upper bound on the git error text shown in the worktree failure balloon so it stays readable. */
        private const val MAX_WORKTREE_FAILURE_POPUP_LENGTH = 600
    }
}

/** The two cards a Workspace tab root can show. */
private enum class ViewCard {
    Content,
    NotConfigured
}

/**
 * The Compose body of one Workspace tab root.
 *
 * It switches between the not-configured card and the content card, which carries the [WorkspaceHeader] of the My PRs
 * and Assigned PRs roots, driven by Compose state instead of the Swing CardLayout the panel used before.
 */
@Composable
private fun WorkspaceRootContent(
    card: ViewCard,
    headerState: WorkspaceHeaderState?,
    onRefreshRequested: () -> Unit,
    onGoToSettings: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        when (card) {
            ViewCard.NotConfigured -> NotConfiguredContent(onGoToSettings = onGoToSettings)
            ViewCard.Content -> Column(modifier = Modifier.fillMaxSize()) {
                if (headerState != null) {
                    WorkspaceHeader(state = headerState, onRefreshRequested = onRefreshRequested)
                }
                Box(modifier = Modifier.weight(1f)) {
                    content()
                }
            }
        }
    }
}