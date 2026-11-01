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
import com.github.fnunezkanut.bbcloud.auth.BbCloudAccessTokenProvider
import com.github.fnunezkanut.bbcloud.bitbucket.AuthoredPullRequestListResult
import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudAuthoredPullRequestService
import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudOpenPullRequestService
import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudReviewQueueService
import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryUrls
import com.github.fnunezkanut.bbcloud.bitbucket.OkHttpBitbucketPullRequestClient
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewingPullRequest
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewingPullRequestListResult
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.git.BbCloudBranchCheckoutService
import com.github.fnunezkanut.bbcloud.git.BbCloudWorktreeService
import com.github.fnunezkanut.bbcloud.git.WorktreeRemovalResult
import com.github.fnunezkanut.bbcloud.project.LocalRepositoryProjectOpener
import com.github.fnunezkanut.bbcloud.project.ProjectPruneOutcome
import com.github.fnunezkanut.bbcloud.project.PullRequestOpenCheck
import com.github.fnunezkanut.bbcloud.project.PullRequestOpenCheckResult
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeListing
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeResolution
import com.github.fnunezkanut.bbcloud.project.WatchedProjectMapping
import com.github.fnunezkanut.bbcloud.project.WatchedProjectWorktreeRow
import com.github.fnunezkanut.bbcloud.project.WatchedProjectsDiscoveryResult
import com.github.fnunezkanut.bbcloud.project.WorktreePruneAction
import com.github.fnunezkanut.bbcloud.project.WorktreePruneDecision
import com.github.fnunezkanut.bbcloud.project.WorktreePruneSummary
import com.github.fnunezkanut.bbcloud.settings.PullRequestListScope
import com.github.fnunezkanut.bbcloud.ui.components.NotConfiguredContent
import com.github.fnunezkanut.bbcloud.ui.pullrequest.help.BbCloudWorktreesHelpEditorOpener
import com.github.fnunezkanut.bbcloud.ui.pullrequests.PullRequestExcludeCheckboxes
import com.github.fnunezkanut.bbcloud.ui.pullrequests.PullRequestListingSource
import com.github.fnunezkanut.bbcloud.ui.pullrequests.PullRequestsListingBody
import com.github.fnunezkanut.bbcloud.ui.pullrequests.PullRequestsViewModel
import com.github.fnunezkanut.bbcloud.ui.worktree.PullRequestWorktreeOpener
import com.github.fnunezkanut.bbcloud.ui.worktree.WorktreeBranchCollisionDialog
import com.github.fnunezkanut.bbcloud.ui.worktree.WorktreeBranchCollisionResolver
import com.github.fnunezkanut.bbcloud.ui.worktree.WorktreeOpenOutcome
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
 * Coordinator for the Workspace scope that owns the shared data and UI for:
 *   - The Workspace tab: one merged listing of the pull requests the user opened and the pull requests assigned
 *     to the user for review, driven by the reusable PullRequestsViewModel (sources WorkspaceAuthored and
 *     WorkspaceAssigned).
 *   - The Workspace - Watched Projects tab: projects directory selection and the discovered project list.
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
        accessTokenProvider = ApplicationManager.getApplication().service<BbCloudAccessTokenProvider>(),
        pullRequestClient = OkHttpBitbucketPullRequestClient()
    )

    /** Switches the current checkout off a colliding PR branch; used only when the colliding clone is this window's own. */
    private val branchCheckoutService = BbCloudBranchCheckoutService()

    /** Shared resolution of a work-tree branch collision: switch this window to main, or warn, or fall back to the modal. */
    private val collisionResolver = WorktreeBranchCollisionResolver(
        branchCheckoutService = branchCheckoutService,
        openPullRequestService = openPullRequestService
    )

    /** Prevents repeated prune requests for all watched projects while the first request is running. */
    private val pruneAllInProgress = AtomicBoolean(false)

    // The one merged Workspace view model (sources WorkspaceAuthored and WorkspaceAssigned).
    private val workspacePullRequestsViewModel = PullRequestsViewModel(
        logName = "Workspace",
        sourcesOfTab = setOf(PullRequestListingSource.WorkspaceAuthored, PullRequestListingSource.WorkspaceAssigned),
        scope = PullRequestListScope.Workspace,
        initialFilter = configurationService.pullRequestListFilter(scope = PullRequestListScope.Workspace),
        organizationUrl = { configurationService.organizationUrlOrNull() },
        onFilterChangedCallback = { filter ->
            configurationService.savePullRequestListFilter(scope = PullRequestListScope.Workspace, filter = filter)
        },
        onOpenTitleCallback = { entry -> startOpenPullRequestInWorktree(pullRequest = entry.pullRequest) },
        onOpenPrOnWebCallback = { entry -> openPrOnWebForPullRequest(entry.pullRequest) },
        onOpenRepositoryCallback = { entry -> openRepositoryForPullRequest(entry.pullRequest) },
        onCountsChanged = { openCount, card -> workspaceHeaderViewModel.setOpenCount(openCount = openCount, card = card) }
    )
    private val watchedProjectsViewModel = WatchedProjectsViewModel(
        coroutineScope = coroutineScope,
        dataSource = ConfigurationWatchedProjectsDataSource(configurationService = configurationService),
        onShowContentCallback = { watchedProjectsCard.value = ViewCard.Content },
        onShowNotConfiguredCallback = ::showWatchedProjectsNotConfigured,
        onProjectsDirectoryUnreadableCallback = {
            Messages.showWarningDialog(
                watchedProjectsTabComponent,
                BbCloudBundle.message("workspace.watchedProjects.sync.unreadable.message"),
                BbCloudBundle.message("workspace.watchedProjects.sync.unreadable.title")
            )
        },
        onOpenLocalProjectCallback = ::openLocalCheckoutProject,
        onOpenWorktreeCallback = ::openWatchedPrWorktree,
        onPruneWorktreesCallback = ::pruneAllWatchedWorktrees,
        onOpenWorktreesHelpCallback = ::openWorktreesHelp
    )

    // One workspace header for the merged Workspace tab root.
    private val workspaceHeaderViewModel = WorkspaceHeaderViewModel(onRefreshRequested = onRefreshRequested)

    /** The two cards of each Workspace tab root, driven by Compose state instead of a Swing CardLayout. */
    private val workspacePrsCard: MutableState<ViewCard> = mutableStateOf(ViewCard.Content)
    private val watchedProjectsCard: MutableState<ViewCard> = mutableStateOf(ViewCard.Content)

    private val workspacePullRequestsLoader = WorkspacePullRequestsLoader(
        pullRequestsViewModel = workspacePullRequestsViewModel,
        headerViewModel = workspaceHeaderViewModel,
        dataSource = ServiceWorkspacePullRequestsDataSource(
            configurationService = configurationService,
            authoredPullRequestService = authoredPullRequestService,
            reviewQueueService = reviewQueueService
        ),
        onShowContentCallback = { workspacePrsCard.value = ViewCard.Content },
        onShowNotConfiguredCallback = { workspacePrsCard.value = ViewCard.NotConfigured }
    )

    val workspacePrsTabComponent: JComponent = compose {
        WorkspaceRootContent(
            card = workspacePrsCard.value,
            headerState = workspaceHeaderViewModel.state.value,
            onRefreshRequested = workspaceHeaderViewModel::onRefreshClicked,
            onGoToSettings = onGoToSettings,
            excludeContent = {
                PullRequestExcludeCheckboxes(
                    filter = workspacePullRequestsViewModel.state.value.filter,
                    viewModel = workspacePullRequestsViewModel
                )
            },
            content = {
                PullRequestsListingBody(
                    viewModel = workspacePullRequestsViewModel,
                    emptyMessageKey = "pullRequests.workspace.none",
                    errorMessageKey = "pullRequests.workspace.loadFailed",
                    footer = { state -> WorkspacePullRequestsFooter(state = state) }
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

    /** See [WatchedProjectsViewModel.bindWatchedProjects]. */
    fun bindWatchedProjects() {
        watchedProjectsViewModel.bindWatchedProjects()
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
     * Public so the tool window can trigger the scan after the Settings projects directory is chosen, keeping the scan
     * and the Watched Projects UI update owned by this tab. See [WatchedProjectsViewModel.rescanWatchedProjects].
     */
    fun rescanWatchedProjects(clearFirst: Boolean = false) {
        watchedProjectsViewModel.rescanWatchedProjects(clearFirst = clearFirst)
    }

    fun showWatchedProjectsNotConfigured() {
        LOG.debug("Showing the Workspace - Watched Projects not-configured card")
        watchedProjectsCard.value = ViewCard.NotConfigured
    }

    fun showNotConfigured() {
        workspacePullRequestsLoader.showNotConfigured()
    }

    /** See [WorkspacePullRequestsLoader.loadWorkspacePullRequests]. */
    fun loadWorkspacePullRequests(excludedRepoSlug: String?, coroutineScope: CoroutineScope) {
        workspacePullRequestsLoader.loadWorkspacePullRequests(excludedRepoSlug = excludedRepoSlug, coroutineScope = coroutineScope)
    }

    /** The shared open flow. It also guards against duplicate opens of the same worktree path. */
    private val worktreeOpener = PullRequestWorktreeOpener.getInstance()

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

        val resolution = worktreeOpener.resolve(
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
        if (!worktreeOpener.tryBeginOpen(worktreePath = worktreePath)) {
            LOG.info("Opening pull request #${summary.id} is already in progress for $worktreePath")
            return
        }

        coroutineScope.launch {
            try {
                // Attempt 1. On a branch collision, try one automatic switch to the main branch and retry (decision
                // 8a). This tab can open PRs of the current repository too, so the auto-switch applies when the clone
                // on the PR branch is this window's checkout; otherwise the resolver falls back to the modal.
                val firstAttempt = runWorktreeModal(pullRequest = pullRequest, resolution = resolved)
                if (firstAttempt is WorktreeOpenOutcome.BranchCollision) {
                    val switched = resolveBranchCollision(pullRequest = pullRequest, preparation = firstAttempt)
                    if (switched) {
                        val secondAttempt = runWorktreeModal(pullRequest = pullRequest, resolution = resolved)
                        handleWorktreePreparation(pullRequest = pullRequest, preparation = secondAttempt)
                    }
                } else {
                    handleWorktreePreparation(pullRequest = pullRequest, preparation = firstAttempt)
                }
            } catch (exception: CancellationException) {
                // D-2: the user cancelled the modal mid-preparation; cleanup already ran in the worktree service, and
                // no popup is shown because the user chose to cancel.
                LOG.info("User cancelled work tree preparation for pull request #${summary.id}")
                throw exception
            } finally {
                ApplicationManager.getApplication().invokeLater {
                    worktreeOpener.endOpen(worktreePath = worktreePath)
                }
            }
        }
    }

    /**
     * Handles a [runWorktreeModal] result, shared by the first and the retried attempt: opens the work tree project,
     * shows the failure balloon, or shows the fallback collision modal. A [WorktreeOpenOutcome.BranchCollision]
     * here is a collision that could not be (or was already) auto-resolved, so it always shows the explanatory modal.
     */
    private suspend fun handleWorktreePreparation(pullRequest: ReviewingPullRequest, preparation: WorktreeOpenOutcome) {
        val summary = pullRequest.summary
        when (preparation) {
            is WorktreeOpenOutcome.Prepared -> {
                openWorktreeProjectAndEditor(pullRequest = pullRequest, prepared = preparation)
            }
            is WorktreeOpenOutcome.Failed -> {
                // D-2: every failure stops here and shows an error popup with a clickable Bitbucket link; it
                // never redirects automatically.
                showWorktreeErrorNotification(pullRequest = pullRequest, content = preparation.content)
            }
            is WorktreeOpenOutcome.BranchCollision -> {
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
    }

    /**
     * Tries to free the pull request branch by switching the current checkout to the main branch via the shared
     * [WorktreeBranchCollisionResolver]. Returns true when the switch succeeded (the caller retries the work tree),
     * false when the resolver already handled the collision or when the collision must fall back to the explanatory
     * modal, which this method shows itself.
     */
    private suspend fun resolveBranchCollision(pullRequest: ReviewingPullRequest, preparation: WorktreeOpenOutcome.BranchCollision): Boolean {
        val summary = pullRequest.summary
        val resolution = collisionResolver.resolve(
            project = project,
            workspace = pullRequest.workspace,
            repoSlug = pullRequest.repoSlug,
            summary = summary,
            branchName = preparation.branchName,
            sourceCheckoutPath = preparation.sourceCheckoutPath
        )
        return when (resolution) {
            WorktreeBranchCollisionResolver.CollisionResolution.Switched -> {
                ApplicationManager.getApplication().invokeLater {
                    onRefreshRequested()
                }
                true
            }
            WorktreeBranchCollisionResolver.CollisionResolution.Handled -> {
                false
            }
            WorktreeBranchCollisionResolver.CollisionResolution.ShowDialog -> {
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
                false
            }
        }
    }

    /**
     * Runs the "Loading PR in its own git work tree" modal, which re-checks the pull request state and prepares the
     * worktree through [PullRequestWorktreeOpener]. The modal uses an application-level owner because the worktree
     * project does not exist yet and the current project must not be blocked. The project window is opened AFTER this
     * returns, so the modal has already closed when the new frame appears (requirement #6).
     */
    private suspend fun runWorktreeModal(pullRequest: ReviewingPullRequest, resolution: PullRequestWorktreeResolution.Resolved): WorktreeOpenOutcome {
        val summary = pullRequest.summary
        val organizationUrl = configurationService.organizationUrlOrNull()
        if (organizationUrl == null) {
            LOG.info("Cannot open pull request #${summary.id}: no organization URL configured")
            return WorktreeOpenOutcome.Failed(content = BbCloudBundle.message("otherProjects.worktree.failed.noOrgUrl"))
        }
        return withModalProgress(
            owner = ModalTaskOwner.guess(),
            title = BbCloudBundle.message("otherProjects.worktree.modalTitle"),
            cancellation = TaskCancellation.cancellable()
        ) {
            worktreeOpener.prepareWorktree(
                workspace = pullRequest.workspace,
                repoSlug = pullRequest.repoSlug,
                prNumber = summary.id,
                organizationUrl = organizationUrl,
                resolved = resolution
            )
        }
    }

    /**
     * Opens (or focuses) the IDE window for the prepared worktree, then opens the pull request editor tab in that target
     * window. The returned project result identifies the target window; the tab is opened against it so it never lands in
     * the current window (the hard constraint, requirement #7).
     */
    private suspend fun openWorktreeProjectAndEditor(pullRequest: ReviewingPullRequest, prepared: WorktreeOpenOutcome.Prepared) {
        val openedProject = worktreeOpener.openProjectAndEditor(
            summary = prepared.summary,
            workspace = pullRequest.workspace,
            repoSlug = pullRequest.repoSlug,
            organizationUrl = configurationService.organizationUrlOrNull(),
            worktreePath = prepared.worktreePath
        )
        if (openedProject == null) {
            showWorktreeErrorNotification(
                pullRequest = pullRequest,
                content = BbCloudBundle.message("otherProjects.worktree.failed.openProjectFailed")
            )
        }
    }

    /** Queues the pull request editor in the window [openedProject]; the editor logic lives in [PullRequestWorktreeOpener]. */
    private fun openPullRequestEditor(openedProject: LocalRepositoryProjectOpener.OpenedProject, pullRequest: ReviewingPullRequest) {
        worktreeOpener.queuePullRequestEditor(
            opened = openedProject,
            summary = pullRequest.summary,
            workspace = pullRequest.workspace,
            repoSlug = pullRequest.repoSlug,
            organizationUrl = configurationService.organizationUrlOrNull()
        )
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

    /** Opens the "What are Work Trees?" help page as a tab, or focuses the tab when it is already open. */
    private fun openWorktreesHelp() {
        project.service<BbCloudWorktreesHelpEditorOpener>().open()
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
            // A work tree that is being opened right now is never pruned, even when its pull request is closed.
            val openingNow = worktreeOpener.worktreesBeingOpened()
            val worktreesToRemove = checkedWorktrees.filter { (worktree, result) ->
                WorktreePruneDecision.actionFor(result) == WorktreePruneAction.Remove && worktree.worktreePath !in openingNow
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

    companion object {
        private val LOG = logger<BbCloudOtherProjectsPanel>()

        /** The registered notification group id declared in plugin.xml; reused for the worktree failure balloon. */
        private const val NOTIFICATION_GROUP_ID = "BbCloud"

        /** Upper bound on the git error text shown in the worktree failure balloon so it stays readable. */
        private const val MAX_WORKTREE_FAILURE_POPUP_LENGTH = 600
    }
}

/** Reads the Watched Projects data from [configurationService]. */
private class ConfigurationWatchedProjectsDataSource(private val configurationService: BbCloudConfigurationService) : WatchedProjectsDataSource {
    override fun organizationUrlOrNull(): String? {
        return configurationService.organizationUrlOrNull()
    }

    override fun isProjectsDirectorySet(): Boolean {
        return configurationService.projectsDirectoryOrNull() != null
    }

    override fun watchedProjectsWithLocalPaths(): List<WatchedProjectMapping> {
        return configurationService.watchedProjectsForCurrentWorkspaceWithLocalPaths()
    }

    override fun repoAvatarUrls(): Map<String, String> {
        return configurationService.repoAvatarUrlsForCurrentWorkspace()
    }

    override fun workTreeDirectoryOrNull(): Path? {
        return configurationService.workTreeDirectoryOrNull()?.let { directory -> Path.of(directory) }
    }

    override fun setWatched(mapping: WatchedProjectMapping, watched: Boolean) {
        configurationService.setWatched(mapping = mapping, watched = watched)
    }

    override fun setAllWatched(watched: Boolean) {
        configurationService.setAllWatched(watched = watched)
    }

    override suspend fun rescan(clearFirst: Boolean): WatchedProjectsDiscoveryResult {
        return configurationService.rescanWatchedProjects(clearFirst = clearFirst)
    }
}

/** Reads the Workspace configuration and listings from the configuration, authored-PR, and review-queue services. */
private class ServiceWorkspacePullRequestsDataSource(
    private val configurationService: BbCloudConfigurationService,
    private val authoredPullRequestService: BbCloudAuthoredPullRequestService,
    private val reviewQueueService: BbCloudReviewQueueService
) : WorkspacePullRequestsDataSource {
    override fun organizationUrlOrNull(): String? {
        return configurationService.organizationUrlOrNull()
    }

    override fun projectsDirectoryOrNull(): String? {
        return configurationService.projectsDirectoryOrNull()
    }

    override fun watchedRepoSlugs(): List<String> {
        return configurationService.watchedRepoSlugsForCurrentWorkspace()
    }

    override fun repoAvatarUrls(): Map<String, String> {
        return configurationService.repoAvatarUrlsForCurrentWorkspace()
    }

    override suspend fun listAuthored(organizationUrl: String, excludedRepoSlug: String?): AuthoredPullRequestListResult {
        return authoredPullRequestService.listAuthoredPullRequests(organizationUrl = organizationUrl, excludedRepoSlug = excludedRepoSlug)
    }

    override suspend fun listReviewing(organizationUrl: String, excludedRepoSlug: String?, repoSlugs: List<String>): ReviewingPullRequestListResult {
        return reviewQueueService.listReviewingPullRequests(
            organizationUrl = organizationUrl,
            excludedRepoSlug = excludedRepoSlug,
            repoSlugs = repoSlugs
        )
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
 * It switches between the not-configured card and the content card, which carries the [WorkspaceHeader] of the merged
 * Workspace root, driven by Compose state instead of the Swing CardLayout the panel used before. The optional
 * [excludeContent] slot carries the Exclude: checkboxes inline into the header row; the Watched Projects tab passes
 * nothing, so it shows no header and no checkboxes.
 */
@Composable
private fun WorkspaceRootContent(
    card: ViewCard,
    headerState: WorkspaceHeaderState?,
    onRefreshRequested: () -> Unit,
    onGoToSettings: () -> Unit,
    excludeContent: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        when (card) {
            ViewCard.NotConfigured -> NotConfiguredContent(onGoToSettings = onGoToSettings)
            ViewCard.Content -> Column(modifier = Modifier.fillMaxSize()) {
                if (headerState != null) {
                    WorkspaceHeader(
                        state = headerState,
                        onRefreshRequested = onRefreshRequested,
                        excludeContent = excludeContent ?: {}
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    content()
                }
            }
        }
    }
}