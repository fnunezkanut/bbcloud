@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.github.fnunezkanut.bbcloud.ui.pullrequest.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudOpenPullRequestService
import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryUrls
import com.github.fnunezkanut.bbcloud.bitbucket.CommentChange
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestAction
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestActionResult
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestDeclineDecision
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestDetails
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestFileCommentCounts
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestInlineCommentIndex
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestNotOpenCheck
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestReviewer
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerCandidates
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerChange
import com.github.fnunezkanut.bbcloud.bitbucket.TaskChange
import com.github.fnunezkanut.bbcloud.bitbucket.TaskEdits
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.git.BbCloudBranchCheckoutService
import com.github.fnunezkanut.bbcloud.git.BbCloudPrBranchUpdateService
import com.github.fnunezkanut.bbcloud.git.BbCloudPullRequestBranchDiffService
import com.github.fnunezkanut.bbcloud.git.BranchDiffResult
import com.github.fnunezkanut.bbcloud.git.LocalBehindRemoteDecision
import com.github.fnunezkanut.bbcloud.git.PrBranchUpdateResult
import com.github.fnunezkanut.bbcloud.git.WorktreeHead
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeMatch
import com.github.fnunezkanut.bbcloud.review.BbCloudPullRequestReviewState
import com.github.fnunezkanut.bbcloud.review.BbCloudWorktreePullRequestContext
import com.github.fnunezkanut.bbcloud.review.PullRequestChangedFilePaths
import com.github.fnunezkanut.bbcloud.review.WorktreePullRequestPageReloader
import com.github.fnunezkanut.bbcloud.review.WorktreeTodoBannerViewModel
import com.github.fnunezkanut.bbcloud.ui.diff.BbCloudPullRequestDiffCommentService
import com.github.fnunezkanut.bbcloud.ui.main.CheckedOutBranchPanel
import com.github.fnunezkanut.bbcloud.ui.main.CommentEditor
import com.github.fnunezkanut.bbcloud.ui.main.DescriptionEditor
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestActionVisibility
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestDetailTab
import com.github.fnunezkanut.bbcloud.ui.main.ReviewerEditor
import com.github.fnunezkanut.bbcloud.ui.main.TaskEditor
import com.github.fnunezkanut.bbcloud.ui.pullrequest.decline.BbCloudDeclineReasonDialog
import com.github.fnunezkanut.bbcloud.ui.pullrequest.decline.BbCloudPullRequestDeclineCoordinator
import com.github.fnunezkanut.bbcloud.ui.pullrequest.decline.ClosedPullRequestWorktreeCleanupCompletion
import com.github.fnunezkanut.bbcloud.ui.pullrequest.decline.ClosedPullRequestWorktreeCleanupRequest
import com.github.fnunezkanut.bbcloud.ui.pullrequest.decline.PullRequestDeclineCompletion
import com.github.fnunezkanut.bbcloud.ui.pullrequest.decline.PullRequestDeclineRequest
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestEditorActivation
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestEditorRestoration
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestEditorRestorationRequest
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestReviewBarRefresh
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestVirtualFile
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.selectedVirtualFile
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorNotifications
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/** Owns the pull-request detail state and requests for one editor tab. */
class BbCloudPullRequestEditorSession(
    private val project: Project,
    private val file: BbCloudPullRequestVirtualFile,
    private val configurationService: BbCloudConfigurationService,
    private val openPullRequestService: BbCloudOpenPullRequestService,
    private val branchCheckoutService: BbCloudBranchCheckoutService,
    private val branchDiffService: BbCloudPullRequestBranchDiffService,
    parentDisposable: Disposable,
    private val branchUpdateService: BbCloudPrBranchUpdateService = BbCloudPrBranchUpdateService(),
    private val selectedFileProvider: () -> BbCloudPullRequestVirtualFile? = {
        FileEditorManager.getInstance(project).selectedVirtualFile() as? BbCloudPullRequestVirtualFile
    },
    private val onEdtDispatcher: (() -> Unit) -> Unit = { action ->
        ApplicationManager.getApplication().invokeLater(action)
    },
    private val declineCoordinator: BbCloudPullRequestDeclineCoordinator = BbCloudPullRequestDeclineCoordinator.getInstance()
) : Disposable {
    val boundPullRequestId: Long = file.pullRequestPath.pullRequestId

    private val sessionDisposable = Disposer.newDisposable("BbCloudPullRequestEditorSession")
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var displayedSummary: PullRequestSummary? = file.getUserData(BbCloudPullRequestVirtualFile.PULL_REQUEST_SUMMARY_KEY)
    private var displayedPullRequestId: Long? = null
    private var repositoryWebUrl: String = file.getUserData(BbCloudPullRequestVirtualFile.REPOSITORY_WEB_URL_KEY).orEmpty()
    private var branchName: String = file.getUserData(BbCloudPullRequestVirtualFile.BRANCH_NAME_KEY)
        ?: displayedSummary?.sourceBranch.orEmpty()
    private var organizationUrl: String? = file.getUserData(BbCloudPullRequestVirtualFile.ORGANIZATION_URL_KEY)
        ?: file.pullRequestPath.workspaceUrl

    private var isActionInProgress = false
    private var isReloadInProgress = false
    private var isReviewerUpdateInProgress = false
    private var isTaskUpdateInProgress = false
    private var isCommentUpdateInProgress = false
    private var isDescriptionUpdateInProgress = false
    private var loadedFilesChangedKey: String? = null
    private var filesChangedLoadInProgress = false
    private var loadedInlineCommentsPullRequestId: Long? = null
    private var loadedInlineComments: List<PullRequestComment>? = null
    private var inlineCommentIndex: PullRequestInlineCommentIndex? = null
    private val fileCommentCountsLoadState = PullRequestFileCommentCountsLoadState()
    private var disposed = false
    private var isTakenOver = false
    private var takeoverComponent: JComponent? = null
    private var takeoverCleanupViewModel: TakeoverCleanupViewModel? = null
    private var isCleanupInProgress = false
    private var pollerStarted = false
    private var pollerJob: Job? = null
    private var isLocalBranchUpdateInProgress = false
    private var localBehindRemoteJob: Job? = null
    private var lastObservedHead: WorktreeHead? = null
    private var gitChangeSubscribed = false

    private val checkedOutBranchPanel = CheckedOutBranchPanel(
        configurationService = configurationService,
        coroutineScope = coroutineScope,
        onRefreshRequested = { reloadPullRequestPage() },
        onActionRequested = { action -> performPullRequestAction(action) },
        reviewerEditor = object : ReviewerEditor {
            override fun addReviewer(candidate: ReviewerCandidate) {
                updateReviewer(change = ReviewerChange.Add(candidate.uuid), displayName = candidate.displayName)
            }

            override fun removeReviewer(reviewer: PullRequestReviewer) {
                updateReviewer(change = ReviewerChange.Remove(reviewer.uuid), displayName = reviewer.displayName)
            }

            override fun loadCandidates(onLoaded: (ReviewerCandidates?) -> Unit) {
                loadReviewerCandidates(onLoaded)
            }
        },
        taskEditor = object : TaskEditor {
            override fun createTask(content: String) {
                updateTasks(TaskChange.Create(content))
            }

            override fun setTasksResolved(taskIds: List<Long>, resolved: Boolean) {
                updateTasks(TaskChange.SetResolved(taskIds = taskIds, resolved = resolved))
            }

            override fun saveTaskEdits(edits: TaskEdits) {
                updateTasks(TaskChange.Edit(edits))
            }
        },
        commentEditor = object : CommentEditor {
            override fun project(): Project {
                return this@BbCloudPullRequestEditorSession.project
            }

            override fun addComment(content: String, onResult: (Boolean) -> Unit) {
                addPullRequestComment(content = content, onResult = onResult)
            }

            override fun applyCommentChange(change: CommentChange, onResult: (Boolean) -> Unit) {
                updateComment(change = change, onResult = onResult)
            }
        },
        descriptionEditor = object : DescriptionEditor {
            override fun updateDescription(content: String, onResult: (Boolean) -> Unit) {
                submitDescriptionUpdate(content = content, onResult = onResult)
            }
        },
        onChangesTabSelected = {
            loadPullRequestFilesChanged(force = false)
            loadPullRequestInlineComments(force = false)
        },
        onFilesChangedRefreshRequested = {
            loadPullRequestFilesChanged(force = true)
            loadPullRequestInlineComments(force = true)
        },
        onUpdateLocalBranchRequested = { updateLocalBranch() }
    )

    val component: JPanel = JPanel(BorderLayout()).apply {
        add(checkedOutBranchPanel.component, BorderLayout.CENTER)
    }

    /**
     * The shared page-reload handler registered with [WorktreePullRequestPageReloader] so the work tree banner's
     * refresh button drives the same full-panel reload as the editor header did. Held as a field so dispose can
     * unregister exactly this handler.
     */
    private val reloadHandler: () -> Unit = { reloadPullRequestPage() }

    init {
        Disposer.register(parentDisposable, sessionDisposable)
        checkedOutBranchPanel.attachProject(project = project, parentDisposable = sessionDisposable)
        WorktreePullRequestPageReloader.getInstance(project).registerSessionReloadHandler(reloadHandler)
        subscribeToGitRepositoryChanges()
        displayedSummary?.let { summary -> bind(summary = summary) } ?: loadRestoredPullRequest()
    }

    /**
     * Re-checks the local-behind-remote state when the HEAD of the work tree moves (decision 8b: debounced
     * `GIT_REPO_CHANGE`). The topic fires for many unrelated updates (a fetch, an index refresh), so the re-check only
     * runs when the branch or revision really changed, mirroring [BbCloudWorktreePullRequestContext].
     */
    private fun subscribeToGitRepositoryChanges() {
        if (gitChangeSubscribed || project.isDisposed) {
            return
        }
        gitChangeSubscribed = true
        project.messageBus.connect(sessionDisposable).subscribe(
            GitRepository.GIT_REPO_CHANGE,
            GitRepositoryChangeListener { repository -> onRepositoryChanged(repository = repository) }
        )
    }

    private fun onRepositoryChanged(repository: GitRepository) {
        if (disposed || project.isDisposed) {
            return
        }
        val head = WorktreeHead(
            branchName = repository.currentBranchName,
            revision = repository.currentRevision
        )
        val previous = lastObservedHead
        if (previous != null && previous.branchName == head.branchName && previous.revision == head.revision) {
            return
        }
        lastObservedHead = head
        refreshLocalBehindRemote()
    }

    private fun loadRestoredPullRequest() {
        val request = BbCloudPullRequestEditorRestoration.request(
            projectBasePath = project.basePath,
            file = file
        ) ?: return
        loadRestoredPullRequestDetails(
            scope = coroutineScope,
            request = request,
            detailsLoader = { restorationRequest ->
                openPullRequestService.pullRequestDetails(
                    projectBasePath = restorationRequest.projectBasePath,
                    organizationUrl = restorationRequest.organizationUrl,
                    pullRequestId = restorationRequest.pullRequestId
                )
            },
            onHydrated = { details ->
                invokeOnEdt {
                    if (details == null) {
                        LOG.info("Cannot restore pull request editor for #${request.pullRequestId}: details are unavailable")
                        return@invokeOnEdt
                    }
                    val summary = details.summary
                    file.setPullRequestSummary(summary)
                    file.putUserData(BbCloudPullRequestVirtualFile.REPOSITORY_WEB_URL_KEY, summary.webUrl)
                    file.putUserData(BbCloudPullRequestVirtualFile.BRANCH_NAME_KEY, summary.sourceBranch)
                    file.putUserData(BbCloudPullRequestVirtualFile.ORGANIZATION_URL_KEY, request.organizationUrl)
                    repositoryWebUrl = summary.webUrl
                    branchName = summary.sourceBranch
                    organizationUrl = request.organizationUrl
                    bind(summary = summary)
                    activateRestoredSelectionIfNeeded()
                }
            }
        )
    }

    private fun activateRestoredSelectionIfNeeded() {
        val selectedFile = selectedFileProvider()
        activateRestoredPullRequestSelection(
            selectedFile = selectedFile,
            file = file,
            reviewState = BbCloudPullRequestReviewState.getInstance(project),
            onReviewStateChanged = {
                BbCloudPullRequestDiffCommentService.getInstance(project).refreshOpenEditors()
            }
        )
    }

    fun select() {
        if (disposed) {
            return
        }
        val pullRequestId = displayedPullRequestId ?: return
        val reviewState = BbCloudPullRequestReviewState.getInstance(project)
        val wasBound = reviewState.activePullRequestId != pullRequestId
        reviewState.bindPullRequest(pullRequestId)
        if (wasBound) {
            EditorNotifications.getInstance(project).updateAllNotifications()
        }
        if (loadedInlineCommentsPullRequestId == pullRequestId) {
            reviewState.updateInlineComments(
                pullRequestId = pullRequestId,
                index = inlineCommentIndex
            )
            BbCloudPullRequestDiffCommentService.getInstance(project).refreshOpenEditors()
        } else {
            loadPullRequestInlineComments(force = false)
        }
    }

    private fun bind(summary: PullRequestSummary, resetSelectedTab: Boolean = true, inPlaceRefresh: Boolean = false) {
        val previousPullRequestId = displayedPullRequestId
        displayedSummary = summary
        displayedPullRequestId = summary.id
        if (previousPullRequestId != summary.id && !project.isDisposed) {
            // Clear the banner's stale whole-PR line totals immediately on a PR switch (decision 3a).
            WorktreeTodoBannerViewModel.getInstance(project).clearLineTotals()
        }
        evaluatePullRequestState(summary)
        fileCommentCountsLoadState.bind(summary.id)
        branchName = summary.sourceBranch
        checkedOutBranchPanel.bind(
            repositoryWebUrl = repositoryWebUrl,
            branchName = branchName,
            summary = summary,
            organizationUrl = organizationUrl ?: configurationService.organizationUrlOrNull(),
            workspace = BitbucketRepositoryUrls.workspaceFromOrganizationUrl(file.pullRequestPath.workspaceUrl).orEmpty(),
            repositorySlug = file.pullRequestPath.repositorySlug,
            repositoryAvatarUrl = configurationService.repoAvatarUrlsForCurrentWorkspace()[file.pullRequestPath.repositorySlug],
            coroutineScope = coroutineScope,
            resetSelectedTab = resetSelectedTab
        )
        loadPullRequestCounts(summary.id)
        loadPullRequestLineTotals(summary)
        loadPullRequestOverview(summary = summary, inPlaceRefresh = inPlaceRefresh)
        loadPullRequestInlineComments(force = true)
        loadPullRequestDetails(pullRequestId = summary.id, refresh = DetailsRefresh.Initial)
        startStatefulPoller()
        refreshLocalBehindRemote()
    }

    private fun reloadPullRequestPage() {
        val pullRequestId = displayedPullRequestId ?: boundPullRequestId
        if (disposed) {
            LOG.debug("Skipping pull request page reload for #$pullRequestId; editor is disposed")
            return
        }
        if (isTakenOver) {
            LOG.debug("Skipping pull request page reload for #$pullRequestId; view was taken over")
            return
        }
        if (isReloadInProgress) {
            LOG.debug("Skipping pull request page reload for #$pullRequestId; reload is already in progress")
            return
        }
        val basePath = project.basePath
        val url = organizationUrl ?: configurationService.organizationUrlOrNull()
        if (basePath == null || url == null) {
            LOG.info("Cannot reload pull request page for #$pullRequestId: project base path or organization URL is unavailable")
            return
        }
        val tabBeforeReload = checkedOutBranchPanel.selectedTab()
        LOG.info("Reloading pull request page for #$pullRequestId")
        LOG.debug("Preserving the $tabBeforeReload tab while reloading pull request #$pullRequestId")
        isReloadInProgress = true
        setReloadRefreshEnabled(false)
        resetReloadableState()
        coroutineScope.launch {
            val details = try {
                openPullRequestService.pullRequestDetails(
                    projectBasePath = basePath,
                    organizationUrl = url,
                    pullRequestId = pullRequestId
                )
            } catch (exception: Exception) {
                if (exception is CancellationException) {
                    throw exception
                }
                invokeOnEdt {
                    isReloadInProgress = false
                    setReloadRefreshEnabled(true)
                    LOG.warn("Failed to reload pull request #$pullRequestId", exception)
                }
                return@launch
            }
            invokeOnEdt {
                isReloadInProgress = false
                setReloadRefreshEnabled(true)
                if (details == null) {
                    LOG.warn("Reload of pull request #$pullRequestId returned no details; leaving the current view in place")
                    return@invokeOnEdt
                }
                val summary = details.summary
                file.setPullRequestSummary(summary)
                file.putUserData(BbCloudPullRequestVirtualFile.REPOSITORY_WEB_URL_KEY, summary.webUrl)
                file.putUserData(BbCloudPullRequestVirtualFile.BRANCH_NAME_KEY, summary.sourceBranch)
                file.putUserData(BbCloudPullRequestVirtualFile.ORGANIZATION_URL_KEY, url)
                repositoryWebUrl = summary.webUrl
                branchName = summary.sourceBranch
                organizationUrl = url
                bind(
                    summary = summary,
                    resetSelectedTab = false,
                    inPlaceRefresh = tabBeforeReload == PullRequestDetailTab.Conversation
                )
                if (tabBeforeReload == PullRequestDetailTab.Changes) {
                    loadPullRequestFilesChanged(force = true)
                }
                refreshWorkTreePullRequestContext(pullRequestId = summary.id)
            }
        }
    }

    /**
     * Toggles the refresh-enabled state on both the detail panel's header and the shared work tree banner, so the
     * banner's refresh button reflects an in-flight reload driven from the editor session. Called on the EDT.
     */
    private fun setReloadRefreshEnabled(enabled: Boolean) {
        checkedOutBranchPanel.setRefreshEnabled(enabled)
        if (!project.isDisposed) {
            WorktreeTodoBannerViewModel.getInstance(project).setRefreshEnabled(enabled)
        }
    }

    private fun resetReloadableState() {
        loadedFilesChangedKey = null
        loadedInlineCommentsPullRequestId = null
        loadedInlineComments = null
        inlineCommentIndex = null
        filesChangedLoadInProgress = false
    }

    private fun buildTakeoverComponent(webUrl: String): JComponent {
        val cleanupViewModel = TakeoverCleanupViewModel(showCleanupButton = canCleanupClosedWorktree())
        takeoverCleanupViewModel = cleanupViewModel
        return compose {
            TakeoverContent(
                text = BbCloudBundle.message("main.branch.closed.notOpen"),
                tooltip = BbCloudBundle.message("main.branch.closed.openInBitbucket"),
                onOpenBitbucket = {
                    LOG.info("User opened closed pull request #$boundPullRequestId in Bitbucket")
                    BrowserUtil.browse(webUrl)
                },
                cleanupButtonLabel = BbCloudBundle.message("main.branch.closed.cleanup.button"),
                viewModel = cleanupViewModel,
                onCleanupWorktree = { onCleanupClosedPullRequestWorktree() }
            )
        }
    }

    /**
     * Whether the closed-PR takeover should offer the "Cleanup this PR Work Tree" button (decisions 3a and 6a): the
     * project must be a pull-request work tree, a work tree directory must be configured, and the project base path
     * must be exactly the expected `$workTreeDirectory/$repoSlug--pr-$prNumber` path for this pull request. When any of
     * these does not hold the button is omitted entirely rather than shown only to error on click.
     */
    private fun canCleanupClosedWorktree(): Boolean {
        val basePath = project.basePath ?: return false
        if (!BbCloudPullRequestReviewBarRefresh.isPullRequestWorkTree(basePath)) {
            return false
        }
        val workTreeDirectory = configurationService.workTreeDirectoryOrNull() ?: return false
        val expectedPath = PullRequestWorktreeMatch.expectedWorktreePath(
            projectBasePath = basePath,
            workTreeDirectory = workTreeDirectory,
            currentRepositorySlug = file.pullRequestPath.repositorySlug,
            currentPullRequestId = boundPullRequestId,
            declinedRepositorySlug = file.pullRequestPath.repositorySlug,
            declinedPullRequestId = boundPullRequestId
        )
        return expectedPath != null
    }

    private fun showTakeover(webUrl: String) {
        if (isTakenOver) {
            return
        }
        isTakenOver = true
        LOG.debug("Switching pull request #${displayedPullRequestId ?: boundPullRequestId} to the no longer Open view")
        component.remove(checkedOutBranchPanel.component)
        val takeover = takeoverComponent ?: buildTakeoverComponent(webUrl).also { takeoverComponent = it }
        component.add(takeover, BorderLayout.CENTER)
        component.revalidate()
        component.repaint()
        stopStatefulPoller()
        // The closed-PR takeover replaced the detail view, so the red local-behind-remote banner is gone with it;
        // cancel any in-flight check so it does not try to touch a detached view model (decision 4.3).
        localBehindRemoteJob?.cancel()
        localBehindRemoteJob = null
    }

    /**
     * Cleans up the git work tree of this already-closed pull request: a project-scoped modal runs while the
     * application coordinator closes the IDE project (decision 1a) and removes the work tree. On a source-resolution
     * failure (decision 4a) the project is left open and a notification is shown; on a removal failure after close
     * (decision 5a) a warning notification is shown. The button is disabled for the duration (decision 8a).
     */
    private fun onCleanupClosedPullRequestWorktree() {
        if (isCleanupInProgress) {
            LOG.info("Ignored cleanup for closed pull request #$boundPullRequestId; cleanup is already in progress")
            return
        }
        val basePath = project.basePath
        val url = organizationUrl ?: configurationService.organizationUrlOrNull()
        if (basePath == null || url == null) {
            LOG.info("Cannot clean up closed pull request #$boundPullRequestId: project base path or organization URL is unavailable")
            return
        }
        if (!canCleanupClosedWorktree()) {
            LOG.info("Cannot clean up closed pull request #$boundPullRequestId: project is not its work tree")
            return
        }
        LOG.info("User requested work tree cleanup for closed pull request #$boundPullRequestId at $basePath")
        isCleanupInProgress = true
        takeoverCleanupViewModel?.setCleanupInProgress(true)
        val request = ClosedPullRequestWorktreeCleanupRequest(
            project = project,
            projectBasePath = basePath,
            organizationUrl = url,
            repositorySlug = file.pullRequestPath.repositorySlug,
            pullRequestId = boundPullRequestId,
            configurationService = configurationService
        )
        // The coordinator owns the modal and runs on the application scope, so it outlives this session when a
        // successful cleanup closes the project window. The completion only matters on the paths that leave the
        // project open (decisions 4a, 5a): it re-enables the button so the user can retry.
        declineCoordinator.launchClosedPullRequestWorktreeCleanup(
            request = request,
            onCompleted = { completion ->
                val staysOpen = completion !is ClosedPullRequestWorktreeCleanupCompletion.Success
                finishCleanup(reEnable = staysOpen)
            }
        )
    }

    private fun finishCleanup(reEnable: Boolean) {
        invokeOnEdt {
            isCleanupInProgress = false
            if (reEnable) {
                takeoverCleanupViewModel?.setCleanupInProgress(false)
            }
        }
    }

    private fun evaluatePullRequestState(summary: PullRequestSummary) {
        if (!PullRequestNotOpenCheck.check(summary)) {
            return
        }
        val alreadyTakenOver = isTakenOver
        val webUrl = summary.webUrl.ifBlank { repositoryWebUrl }
        showTakeover(webUrl = webUrl)
        if (!alreadyTakenOver) {
            // The pull request just became non-Open (for example a background poll caught the merge). Persist the
            // closed summary on the virtual file so the banner's synchronous resolve sees it, reload the work tree
            // context so its known-closed flag flips, then refresh the banner and all editor notifications so the
            // blue bar disappears on the code editors (decisions 1a, 5a).
            LOG.info("Pull request #${displayedPullRequestId ?: boundPullRequestId} is no longer open; hiding the blue bar")
            syncWorkTreeOnClosure(summary = summary)
        }
    }

    /**
     * Pushes the closed summary onto the virtual file, reloads the work tree pull request context, and refreshes the
     * banner and editor notifications so the blue bar is removed from the code editors of the work tree.
     */
    private fun syncWorkTreeOnClosure(summary: PullRequestSummary) {
        file.setPullRequestSummary(summary)
        if (BbCloudPullRequestReviewBarRefresh.isPullRequestWorkTree(project.basePath)) {
            BbCloudWorktreePullRequestContext.getInstance(project).reload(reason = "pull request no longer open")
        }
        invokeOnEdt {
            if (project.isDisposed) {
                return@invokeOnEdt
            }
            WorktreeTodoBannerViewModel.getInstance(project).refreshFromSources()
            EditorNotifications.getInstance(project).updateAllNotifications()
        }
    }

    private fun startStatefulPoller() {
        if (pollerStarted || disposed || isTakenOver) {
            return
        }
        pollerStarted = true
        LOG.debug("Started 30-second pull-request state poller for #${displayedPullRequestId ?: boundPullRequestId}")
        pollerJob = coroutineScope.launch {
            while (isActive && !isTakenOver) {
                delay(POLL_INTERVAL_MS)
                if (disposed || isTakenOver) {
                    break
                }
                pollPullRequestState()
            }
        }
    }

    private fun pollPullRequestState() {
        val pullRequestId = displayedPullRequestId ?: return
        if (!loadPullRequestDetails(pullRequestId = pullRequestId, refresh = DetailsRefresh.BackgroundPoll)) {
            LOG.debug("Skipping pull-request state poll for #$pullRequestId; project base path or organization URL is unavailable")
        }
    }

    private fun stopStatefulPoller() {
        if (!pollerStarted) {
            return
        }
        pollerJob?.cancel()
        pollerJob = null
        pollerStarted = false
        LOG.debug("Stopped pull-request state poller for #${displayedPullRequestId ?: boundPullRequestId}")
    }

    private enum class DetailsRefresh {
        Initial,
        AfterAction,
        ReviewersOnly,
        BackgroundPoll
    }

    private fun loadPullRequestCounts(pullRequestId: Long) {
        val basePath = project.basePath ?: return
        val url = organizationUrl ?: configurationService.organizationUrlOrNull() ?: return
        coroutineScope.launch {
            val counts = openPullRequestService.pullRequestCounts(
                projectBasePath = basePath,
                organizationUrl = url,
                pullRequestId = pullRequestId
            )
            invokeOnEdt {
                if (pullRequestId != displayedPullRequestId) {
                    LOG.debug("Ignoring counts for pull request #$pullRequestId; this editor shows #$displayedPullRequestId")
                    return@invokeOnEdt
                }
                checkedOutBranchPanel.showCounts(counts)
            }
        }
    }

    private fun loadPullRequestOverview(summary: PullRequestSummary, inPlaceRefresh: Boolean = false) {
        val basePath = project.basePath ?: return
        val url = organizationUrl ?: configurationService.organizationUrlOrNull() ?: return
        coroutineScope.launch {
            val overview = openPullRequestService.pullRequestOverview(
                projectBasePath = basePath,
                organizationUrl = url,
                summary = summary
            )
            invokeOnEdt {
                if (summary.id != displayedPullRequestId) {
                    LOG.debug("Ignoring overview for pull request #${summary.id}; this editor shows #$displayedPullRequestId")
                    return@invokeOnEdt
                }
                checkedOutBranchPanel.showOverview(
                    pullRequestId = summary.id,
                    overview = overview,
                    destinationBranch = summary.destinationBranch,
                    coroutineScope = coroutineScope,
                    inPlaceRefresh = inPlaceRefresh
                )
            }
        }
    }

    private fun loadPullRequestInlineComments(force: Boolean) {
        val basePath = project.basePath ?: return
        val pullRequestId = displayedPullRequestId ?: return
        val url = organizationUrl ?: configurationService.organizationUrlOrNull() ?: return
        when (fileCommentCountsLoadState.request(pullRequestId = pullRequestId, force = force)) {
            PullRequestFileCommentCountsLoadState.Request.AlreadyInProgress -> {
                LOG.debug("Skipping inline comment reload; request already in progress for pull request #$pullRequestId")
                return
            }
            PullRequestFileCommentCountsLoadState.Request.AlreadyLoaded -> {
                LOG.debug("Skipping inline comment reload; already loaded for pull request #$pullRequestId")
                pushFileCommentCountsForCurrentComments()
                return
            }
            PullRequestFileCommentCountsLoadState.Request.Started -> {
                // Proceed to load inline comments below.
            }
        }
        coroutineScope.launch {
            val comments = openPullRequestService.listPullRequestComments(
                projectBasePath = basePath,
                organizationUrl = url,
                pullRequestId = pullRequestId
            )
            val userUuid = openPullRequestService.currentUserUuid(
                projectBasePath = basePath,
                organizationUrl = url
            )
            val commentItems = comments?.items
            val mentionNames = commentItems?.let { items ->
                openPullRequestService.pullRequestMentionNames(
                    projectBasePath = basePath,
                    organizationUrl = url,
                    comments = items
                )
            }.orEmpty()
            invokeOnEdt {
                val completion = fileCommentCountsLoadState.complete(
                    pullRequestId = pullRequestId,
                    comments = commentItems
                )
                if (!completion.isForDisplayedPullRequest) {
                    LOG.debug("Ignoring inline comments for pull request #$pullRequestId; this editor shows #$displayedPullRequestId")
                    return@invokeOnEdt
                }
                val index = commentItems?.let { items -> PullRequestInlineCommentIndex.from(comments = items) }
                loadedInlineCommentsPullRequestId = pullRequestId
                loadedInlineComments = commentItems
                inlineCommentIndex = index
                commentItems?.let { items ->
                    BbCloudWorktreePullRequestContext.getInstance(project).publishComments(
                        pullRequestId = pullRequestId,
                        comments = items,
                        userUuid = userUuid,
                        mentionNames = mentionNames
                    )
                }
                val reviewState = BbCloudPullRequestReviewState.getInstance(project)
                if (reviewState.updateInlineComments(pullRequestId = pullRequestId, index = index)) {
                    BbCloudPullRequestDiffCommentService.getInstance(project).refreshOpenEditors()
                }
                applyFileCommentCounts(pullRequestId = pullRequestId, counts = completion.counts)
                if (completion.shouldReload) {
                    loadPullRequestInlineComments(force = true)
                }
            }
        }
    }

    private fun applyFileCommentCounts(pullRequestId: Long, counts: PullRequestFileCommentCounts?) {
        if (pullRequestId != displayedPullRequestId) {
            LOG.debug("Ignoring comment counts for pull request #$pullRequestId; this editor shows #$displayedPullRequestId")
            return
        }
        checkedOutBranchPanel.setFilesChangedCommentCounts(counts)
    }

    private fun pushFileCommentCountsForCurrentComments() {
        val pullRequestId = displayedPullRequestId ?: return
        val counts = fileCommentCountsLoadState.filesChangedCompleted(pullRequestId) ?: return
        LOG.debug("Computed comment counts for ${counts.pathsWithComments().size} files for pull request #$pullRequestId")
        invokeOnEdt {
            applyFileCommentCounts(pullRequestId = pullRequestId, counts = counts)
        }
    }

    private fun loadPullRequestLineTotals(summary: PullRequestSummary) {
        coroutineScope.launch {
            val totals = withContext(Dispatchers.IO) {
                branchDiffService.loadPullRequestLineTotals(
                    project = project,
                    destinationBranch = summary.destinationBranch,
                    sourceBranch = summary.sourceBranch
                )
            }
            invokeOnEdt {
                if (summary.id != displayedPullRequestId) {
                    LOG.debug("Ignoring line totals for pull request #${summary.id}; this editor shows #$displayedPullRequestId")
                    return@invokeOnEdt
                }
                if (!project.isDisposed) {
                    WorktreeTodoBannerViewModel.getInstance(project).showLineTotals(totals)
                }
            }
        }
    }

    /**
     * Re-evaluates whether the local pull-request branch is behind `origin/<sourceBranch>` and shows or hides the red
     * banner accordingly.
     *
     * The check is skipped (and the banner cleared) when the view is taken over or the pull request is not Open, so no
     * stale "behind" state lingers (decisions 5a, and takeover handling). While a pull is in flight the banner is left
     * as-is so its "updating" link state is not stomped. The fetch and git counting run on [Dispatchers.IO]; the view
     * model is only touched on the EDT. The last launched check wins, so a burst of triggers resolves once.
     */
    private fun refreshLocalBehindRemote() {
        if (disposed || isTakenOver) {
            return
        }
        val summary = displayedSummary
        if (summary == null || summary.state != PullRequestState.Open) {
            checkedOutBranchPanel.clearLocalBehindRemote()
            return
        }
        if (isLocalBranchUpdateInProgress) {
            LOG.debug("Skipping local-behind-remote check for #${summary.id}; a branch update is in progress")
            return
        }
        val sourceBranch = summary.sourceBranch
        val pullRequestId = summary.id
        localBehindRemoteJob?.cancel()
        localBehindRemoteJob = coroutineScope.launch {
            val decision = withContext(Dispatchers.IO) {
                branchDiffService.evaluateLocalBehindRemote(project = project, sourceBranch = sourceBranch)
            }
            invokeOnEdt {
                if (pullRequestId != displayedPullRequestId) {
                    LOG.debug("Ignoring local-behind-remote result for #$pullRequestId; this editor shows #$displayedPullRequestId")
                    return@invokeOnEdt
                }
                if (isTakenOver || isLocalBranchUpdateInProgress) {
                    return@invokeOnEdt
                }
                when (decision) {
                    is LocalBehindRemoteDecision.Behind -> {
                        LOG.info("Local branch of pull request #$pullRequestId is ${decision.commitsBehind} commit(s) behind remote")
                        checkedOutBranchPanel.showLocalBehindRemote(commitsBehind = decision.commitsBehind)
                    }
                    LocalBehindRemoteDecision.Hidden -> {
                        checkedOutBranchPanel.clearLocalBehindRemote()
                    }
                }
            }
        }
    }

    /**
     * Fetches the remote source branch and fast-forwards/merges the local pull-request branch (decision 2d), guarded
     * against a concurrent update or page reload (decision 7a). A dirty work tree aborts with a notification (decision
     * 9a); a conflict or any other git failure shows the sanitized output (decision 6a); a success reloads the whole
     * pull-request page (decision 11a).
     */
    private fun updateLocalBranch() {
        if (isLocalBranchUpdateInProgress || isReloadInProgress) {
            LOG.info("Ignored local branch update for pull request #$boundPullRequestId; an update or reload is already in progress")
            return
        }
        val summary = displayedSummary
        if (summary == null) {
            LOG.info("Cannot update local branch: no pull request is displayed")
            return
        }
        val sourceBranch = summary.sourceBranch
        val pullRequestId = summary.id
        LOG.info("User requested local branch update for pull request #$pullRequestId")
        isLocalBranchUpdateInProgress = true
        checkedOutBranchPanel.setLocalBehindRemoteUpdateInProgress(true)
        coroutineScope.launch {
            val result = branchUpdateService.updateBranch(project = project, sourceBranch = sourceBranch)
            invokeOnEdt {
                isLocalBranchUpdateInProgress = false
                when (result) {
                    PrBranchUpdateResult.Success -> {
                        LOG.info("Updated local branch of pull request #$pullRequestId from remote; reloading the page")
                        reloadPullRequestPage()
                    }
                    PrBranchUpdateResult.DirtyWorktree -> {
                        checkedOutBranchPanel.setLocalBehindRemoteUpdateInProgress(false)
                        showFailureNotification(
                            title = BbCloudBundle.message("main.branch.localBehindRemote.failedPull"),
                            message = BbCloudBundle.message("main.branch.localBehindRemote.dirtyWorktree")
                        )
                    }
                    is PrBranchUpdateResult.Failed -> {
                        checkedOutBranchPanel.setLocalBehindRemoteUpdateInProgress(false)
                        LOG.warn("Failed to update local branch of pull request #$pullRequestId from remote", result.cause)
                        showFailureNotification(
                            title = BbCloudBundle.message("main.branch.localBehindRemote.failedPull"),
                            message = result.output
                        )
                    }
                }
            }
        }
    }

    private fun loadPullRequestFilesChanged(force: Boolean) {
        val summary = displayedSummary ?: return
        val cacheKey = filesChangedCacheKey(summary)
        if (!force && cacheKey == loadedFilesChangedKey) {
            return
        }
        if (filesChangedLoadInProgress && !force) {
            return
        }
        filesChangedLoadInProgress = true
        checkedOutBranchPanel.showFilesChangedLoading()
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                // Load the diff and the per-file line counts in parallel: both resolve the same branches, but the
                // numstat is independent of the diff, so the tree does not wait on two sequential Git commands.
                val changesDeferred = async {
                    branchDiffService.loadPullRequestChanges(
                        project = project,
                        destinationBranch = summary.destinationBranch,
                        sourceBranch = summary.sourceBranch
                    )
                }
                val lineCountsDeferred = async {
                    branchDiffService.loadPullRequestFileLineCounts(
                        project = project,
                        destinationBranch = summary.destinationBranch,
                        sourceBranch = summary.sourceBranch
                    )
                }
                val diffResult = changesDeferred.await()
                val lineCounts = lineCountsDeferred.await()
                // Attach the per-file line counts only to a successful diff. A numstat failure returns an empty map,
                // so the tree renders without line badges rather than surfacing a card-level error.
                if (diffResult is BranchDiffResult.Success) {
                    diffResult.copy(fileLineCounts = lineCounts)
                } else {
                    diffResult
                }
            }
            invokeOnEdt {
                filesChangedLoadInProgress = false
                if (summary.id != displayedPullRequestId) {
                    LOG.debug("Ignoring file changes for pull request #${summary.id}; this editor shows #$displayedPullRequestId")
                    return@invokeOnEdt
                }
                if (result is BranchDiffResult.Success) {
                    loadedFilesChangedKey = cacheKey
                } else {
                    loadedFilesChangedKey = null
                }
                checkedOutBranchPanel.showFilesChangedResult(result)
                pushFileCommentCountsForCurrentComments()
                recordChangedFilePaths(summary = summary, result = result)
            }
        }
    }

    /**
     * Records the files the pull request changes so the editor review bar knows which files it belongs to.
     *
     * The review bar is absent until the changed files are known, because the bar only shows in the files the pull
     * request touches.
     */
    private fun recordChangedFilePaths(summary: PullRequestSummary, result: BranchDiffResult) {
        if (result !is BranchDiffResult.Success) {
            return
        }
        val projectBasePath = project.basePath
        val paths = PullRequestChangedFilePaths.from(projectBasePath = projectBasePath, changes = result.changes)
        val reviewState = BbCloudPullRequestReviewState.getInstance(project)
        if (!reviewState.updateChangedFilePaths(pullRequestId = summary.id, paths = paths)) {
            LOG.debug("Ignoring the changed files of pull request #${summary.id}; this editor shows #$displayedPullRequestId")
            return
        }
        LOG.debug("Recorded ${paths.size} changed file(s) of pull request #${summary.id}")
        EditorNotifications.getInstance(project).updateAllNotifications()
        refreshWorkTreePullRequestContext(pullRequestId = summary.id)
    }

    /**
     * Resolves the work tree pull request context again after the editor reloaded the pull request.
     *
     * A source-branch push the editor picks up changes the files the pull request changes, so the "Requires review"
     * bar of the work tree has to be resolved again. Only the pull request the work tree is bound to is refreshed, and
     * an ordinary project does no extra work at all.
     */
    private fun refreshWorkTreePullRequestContext(pullRequestId: Long) {
        if (!BbCloudPullRequestReviewBarRefresh.isPullRequestWorkTree(project.basePath)) {
            return
        }
        val context = BbCloudWorktreePullRequestContext.getInstance(project)
        val boundPullRequestId = context.binding?.pullRequestId
        if (boundPullRequestId != null && boundPullRequestId != pullRequestId) {
            return
        }
        context.reload(reason = "pull request editor refreshed")
    }

    private fun filesChangedCacheKey(summary: PullRequestSummary): String {
        return "${summary.id}:${summary.destinationBranch}:${summary.sourceBranch}"
    }

    private fun loadPullRequestDetails(pullRequestId: Long, refresh: DetailsRefresh): Boolean {
        val basePath = project.basePath ?: return false
        val url = organizationUrl ?: configurationService.organizationUrlOrNull() ?: return false
        coroutineScope.launch {
            val details = openPullRequestService.pullRequestDetails(
                projectBasePath = basePath,
                organizationUrl = url,
                pullRequestId = pullRequestId
            )
            invokeOnEdt { applyPullRequestDetails(pullRequestId = pullRequestId, details = details, refresh = refresh) }
        }
        return true
    }

    private fun applyPullRequestDetails(pullRequestId: Long, details: PullRequestDetails?, refresh: DetailsRefresh) {
        if (pullRequestId != displayedPullRequestId) {
            return
        }
        if (details == null) {
            finishDetailUpdate(refresh)
            return
        }
        displayedSummary = details.summary
        evaluatePullRequestState(details.summary)
        if (isTakenOver) {
            finishDetailUpdate(refresh)
            return
        }
        when (refresh) {
            DetailsRefresh.Initial -> {
                checkedOutBranchPanel.showActions(details)
                isActionInProgress = false
            }
            DetailsRefresh.AfterAction -> {
                checkedOutBranchPanel.showPullRequestDetails(
                    details = details,
                    organizationUrl = organizationUrl ?: configurationService.organizationUrlOrNull(),
                    coroutineScope = coroutineScope
                )
                isActionInProgress = false
            }
            DetailsRefresh.ReviewersOnly -> {
                isReviewerUpdateInProgress = false
            }
            DetailsRefresh.BackgroundPoll -> {
                // A background poll only re-evaluates the pull-request state (see evaluatePullRequestState above).
                // The PR is still Open, so the normal view is left untouched to avoid flickering the UI. The one
                // extra thing the poll does is re-check whether the local branch fell behind the remote (decision 8b).
                refreshLocalBehindRemote()
            }
        }
        if (refresh != DetailsRefresh.BackgroundPoll) {
            checkedOutBranchPanel.showReviewers(details = details, coroutineScope = coroutineScope)
        }
    }

    private fun finishDetailUpdate(refresh: DetailsRefresh) {
        when (refresh) {
            DetailsRefresh.BackgroundPoll -> {
                // No action to finish for a background poll.
            }
            DetailsRefresh.ReviewersOnly -> {
                isReviewerUpdateInProgress = false
                checkedOutBranchPanel.setReviewersUpdating(false)
            }
            DetailsRefresh.Initial, DetailsRefresh.AfterAction -> {
                isActionInProgress = false
                checkedOutBranchPanel.setActionsEnabled(true)
            }
        }
    }

    private fun loadReviewerCandidates(onLoaded: (ReviewerCandidates?) -> Unit) {
        val basePath = project.basePath
        val url = organizationUrl ?: configurationService.organizationUrlOrNull()
        if (basePath == null || url == null) {
            onLoaded(null)
            return
        }
        coroutineScope.launch {
            val candidates = openPullRequestService.reviewerCandidates(
                projectBasePath = basePath,
                organizationUrl = url
            )
            invokeOnEdt { onLoaded(candidates) }
        }
    }

    private fun updateReviewer(change: ReviewerChange, displayName: String) {
        if (isReviewerUpdateInProgress) {
            return
        }
        val summary = displayedSummary
        val basePath = project.basePath
        val url = organizationUrl ?: configurationService.organizationUrlOrNull()
        if (summary == null || basePath == null || url == null) {
            checkedOutBranchPanel.setReviewersUpdating(false)
            return
        }
        isReviewerUpdateInProgress = true
        coroutineScope.launch {
            val result = openPullRequestService.updateReviewer(
                projectBasePath = basePath,
                organizationUrl = url,
                pullRequestId = summary.id,
                change = change
            )
            invokeOnEdt {
                if (summary.id != displayedPullRequestId) {
                    isReviewerUpdateInProgress = false
                    return@invokeOnEdt
                }
                when (result) {
                    PullRequestActionResult.Success -> {
                        if (!loadPullRequestDetails(summary.id, DetailsRefresh.ReviewersOnly)) {
                            isReviewerUpdateInProgress = false
                        }
                        loadPullRequestOverview(summary)
                    }
                    is PullRequestActionResult.Failed -> {
                        isReviewerUpdateInProgress = false
                        checkedOutBranchPanel.setReviewersUpdating(false)
                        showFailureNotification(
                            title = reviewerFailureTitle(change = change, displayName = displayName, pullRequestId = summary.id),
                            message = result.message
                        )
                    }
                }
            }
        }
    }

    private fun reviewerFailureTitle(change: ReviewerChange, displayName: String, pullRequestId: Long): String {
        val key = when (change) {
            is ReviewerChange.Add -> "main.branch.reviewers.failed.add"
            is ReviewerChange.Remove -> "main.branch.reviewers.failed.remove"
        }
        return BbCloudBundle.message(key, displayName, pullRequestId.toString())
    }

    private fun updateTasks(change: TaskChange) {
        if (isTaskUpdateInProgress) {
            return
        }
        val summary = displayedSummary
        val basePath = project.basePath
        val url = organizationUrl ?: configurationService.organizationUrlOrNull()
        if (summary == null || basePath == null || url == null) {
            checkedOutBranchPanel.setTasksUpdating(false)
            return
        }
        isTaskUpdateInProgress = true
        coroutineScope.launch {
            val result = openPullRequestService.applyTaskChange(
                projectBasePath = basePath,
                organizationUrl = url,
                pullRequestId = summary.id,
                change = change
            )
            invokeOnEdt {
                isTaskUpdateInProgress = false
                if (summary.id != displayedPullRequestId) {
                    return@invokeOnEdt
                }
                when (result) {
                    PullRequestActionResult.Success -> {
                        loadPullRequestOverview(summary)
                    }
                    is PullRequestActionResult.Failed -> {
                        checkedOutBranchPanel.setTasksUpdating(false)
                        showFailureNotification(
                            title = taskFailureTitle(change = change, pullRequestId = summary.id),
                            message = result.message
                        )
                        loadPullRequestOverview(summary)
                    }
                }
            }
        }
    }

    private fun taskFailureTitle(change: TaskChange, pullRequestId: Long): String {
        val key = when (change) {
            is TaskChange.Create -> "main.branch.tasks.failed.create"
            is TaskChange.SetResolved, is TaskChange.Edit -> "main.branch.tasks.failed.update"
        }
        return BbCloudBundle.message(key, pullRequestId.toString())
    }

    private fun addPullRequestComment(content: String, onResult: (Boolean) -> Unit) {
        submitCommentChange(CommentChange.Create(content), onResult = onResult)
    }

    private fun updateComment(change: CommentChange, onResult: (Boolean) -> Unit) {
        submitCommentChange(
            change = change,
            reloadsInlineComments = true,
            onResult = onResult
        )
    }

    private fun submitCommentChange(change: CommentChange, reloadsInlineComments: Boolean = false, onResult: (Boolean) -> Unit) {
        if (isCommentUpdateInProgress) {
            onResult(false)
            return
        }
        val summary = displayedSummary
        val basePath = project.basePath
        val url = organizationUrl ?: configurationService.organizationUrlOrNull()
        if (summary == null || basePath == null || url == null) {
            onResult(false)
            return
        }
        isCommentUpdateInProgress = true
        coroutineScope.launch {
            val result = openPullRequestService.applyCommentChange(
                projectBasePath = basePath,
                organizationUrl = url,
                pullRequestId = summary.id,
                change = change
            )
            invokeOnEdt {
                isCommentUpdateInProgress = false
                when (result) {
                    PullRequestActionResult.Success -> {
                        onResult(true)
                        if (summary.id == displayedPullRequestId) {
                            loadPullRequestOverview(summary)
                            if (reloadsInlineComments) {
                                loadPullRequestInlineComments(force = true)
                            }
                        }
                    }
                    is PullRequestActionResult.Failed -> {
                        onResult(false)
                        showFailureNotification(
                            title = commentChangeFailureTitle(change = change, pullRequestId = summary.id),
                            message = result.message
                        )
                    }
                }
            }
        }
    }

    private fun submitDescriptionUpdate(content: String, onResult: (Boolean) -> Unit) {
        if (isDescriptionUpdateInProgress) {
            onResult(false)
            return
        }
        val summary = displayedSummary
        val basePath = project.basePath
        val url = organizationUrl ?: configurationService.organizationUrlOrNull()
        if (summary == null || basePath == null || url == null) {
            LOG.info("Cannot update pull request description: required metadata is missing")
            onResult(false)
            return
        }
        isDescriptionUpdateInProgress = true
        LOG.info("User submitted a description edit for pull request #${summary.id}")
        coroutineScope.launch {
            val result = openPullRequestService.updateDescription(
                projectBasePath = basePath,
                organizationUrl = url,
                pullRequestId = summary.id,
                title = summary.title,
                description = content
            )
            invokeOnEdt {
                isDescriptionUpdateInProgress = false
                when (result) {
                    PullRequestActionResult.Success -> {
                        onResult(true)
                        if (summary.id == displayedPullRequestId) {
                            // Re-run a details load so renderDescription() shows the new text (loadPullRequestOverview alone does not).
                            loadPullRequestDetails(summary.id, DetailsRefresh.AfterAction)
                        }
                    }
                    is PullRequestActionResult.Failed -> {
                        onResult(false)
                        showFailureNotification(
                            title = BbCloudBundle.message("main.branch.overview.description.failed", summary.id.toString()),
                            message = result.message
                        )
                    }
                }
            }
        }
    }

    private fun commentChangeFailureTitle(change: CommentChange, pullRequestId: Long): String {
        val pullRequest = pullRequestId.toString()
        return when (change) {
            is CommentChange.Create -> BbCloudBundle.message("main.branch.comments.failed.create", pullRequest)
            is CommentChange.CreateFileScoped -> BbCloudBundle.message("main.branch.comments.failed.createFileScoped", pullRequest)
            is CommentChange.CreateInlineLine -> BbCloudBundle.message("main.branch.comments.failed.createInlineLine", pullRequest)
            is CommentChange.Reply -> BbCloudBundle.message("main.branch.comments.failed.reply", change.commentId.toString(), pullRequest)
            is CommentChange.Edit -> BbCloudBundle.message("main.branch.comments.failed.edit", change.commentId.toString(), pullRequest)
            is CommentChange.Resolve -> BbCloudBundle.message("main.branch.comments.failed.resolve", change.commentId.toString(), pullRequest)
            is CommentChange.Unresolve -> BbCloudBundle.message("main.branch.comments.failed.unresolve", change.commentId.toString(), pullRequest)
            is CommentChange.Delete -> BbCloudBundle.message("main.branch.comments.failed.delete", change.commentId.toString(), pullRequest)
        }
    }

    private fun performPullRequestAction(action: PullRequestAction) {
        if (isActionInProgress) {
            LOG.info("Ignored $action for pull request #$boundPullRequestId because another action is in progress")
            return
        }
        val summary = displayedSummary
        val basePath = project.basePath
        val url = organizationUrl ?: configurationService.organizationUrlOrNull()
        if (summary == null || basePath == null || url == null) {
            LOG.info("Cannot perform $action for pull request #$boundPullRequestId: required project metadata is missing")
            checkedOutBranchPanel.setActionsEnabled(true)
            return
        }
        val confirmation = confirmAction(action = action, summary = summary)
        if (confirmation is ActionConfirmation.Cancelled) {
            LOG.info("User cancelled $action for pull request #${summary.id}")
            checkedOutBranchPanel.setActionsEnabled(true)
            return
        }

        isActionInProgress = true
        if (action == PullRequestAction.Decline) {
            val reason = (confirmation as ActionConfirmation.Confirmed).reason
            LOG.info("User confirmed decline for pull request #${summary.id}")
            declineCoordinator.launch(
                request = PullRequestDeclineRequest(
                    project = project,
                    projectBasePath = basePath,
                    organizationUrl = url,
                    repositorySlug = file.pullRequestPath.repositorySlug,
                    pullRequest = summary,
                    reason = reason,
                    configurationService = configurationService,
                    openPullRequestService = openPullRequestService
                ),
                onCompleted = { result ->
                    invokeOnEdt {
                        applyDeclineCompletion(
                            pullRequestId = summary.id,
                            completion = result
                        )
                    }
                }
            )
            return
        }

        coroutineScope.launch {
            val result = openPullRequestService.performAction(
                projectBasePath = basePath,
                organizationUrl = url,
                pullRequest = summary,
                action = action
            )
            invokeOnEdt { applyPullRequestActionResult(pullRequestId = summary.id, action = action, result = result) }
        }
    }

    private fun confirmAction(action: PullRequestAction, summary: PullRequestSummary): ActionConfirmation {
        if (action == PullRequestAction.Decline) {
            return when (val decision = BbCloudDeclineReasonDialog(project = project, pullRequest = summary).showAndGetDecision()) {
                PullRequestDeclineDecision.Cancel -> ActionConfirmation.Cancelled
                is PullRequestDeclineDecision.Confirmed -> ActionConfirmation.Confirmed(reason = decision.reason)
            }
        }
        if (action != PullRequestAction.Merge) {
            return ActionConfirmation.Confirmed(reason = "")
        }

        val answer = Messages.showYesNoDialog(
            project,
            BbCloudBundle.message(
                "main.branch.action.confirm.merge",
                summary.id.toString(),
                summary.sourceBranch,
                summary.destinationBranch
            ),
            PullRequestActionVisibility.label(action),
            Messages.getQuestionIcon()
        )
        return if (answer == Messages.YES) {
            ActionConfirmation.Confirmed(reason = "")
        } else {
            ActionConfirmation.Cancelled
        }
    }

    private fun applyDeclineCompletion(pullRequestId: Long, completion: PullRequestDeclineCompletion) {
        when (completion) {
            is PullRequestDeclineCompletion.Failed -> {
                LOG.warn("Decline failed for pull request #$pullRequestId", completion.result.cause)
                applyPullRequestActionResult(
                    pullRequestId = pullRequestId,
                    action = PullRequestAction.Decline,
                    result = completion.result
                )
            }
            is PullRequestDeclineCompletion.Success -> {
                if (completion.projectWillClose) {
                    finishAction()
                } else {
                    applyPullRequestActionResult(
                        pullRequestId = pullRequestId,
                        action = PullRequestAction.Decline,
                        result = PullRequestActionResult.Success
                    )
                }
            }
        }
    }

    private sealed interface ActionConfirmation {
        data object Cancelled : ActionConfirmation

        data class Confirmed(val reason: String) : ActionConfirmation
    }

    private fun applyPullRequestActionResult(pullRequestId: Long, action: PullRequestAction, result: PullRequestActionResult) {
        when (result) {
            PullRequestActionResult.Success -> {
                val isRefreshing = pullRequestId == displayedPullRequestId &&
                    loadPullRequestDetails(pullRequestId = pullRequestId, refresh = DetailsRefresh.AfterAction)
                if (isRefreshing) {
                    displayedSummary?.let { summary -> loadPullRequestOverview(summary) }
                } else {
                    finishAction()
                }
            }
            is PullRequestActionResult.Failed -> {
                finishAction()
                showFailureNotification(
                    title = BbCloudBundle.message(
                        "main.branch.action.failed.title",
                        PullRequestActionVisibility.label(action),
                        pullRequestId.toString()
                    ),
                    message = result.message
                )
            }
        }
    }

    private fun finishAction() {
        isActionInProgress = false
        checkedOutBranchPanel.setActionsEnabled(true)
    }

    private fun showFailureNotification(title: String, message: String?) {
        val content = message ?: BbCloudBundle.message("main.branch.action.failed.generic")
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, content, NotificationType.ERROR)
            .notify(project)
    }

    private fun invokeOnEdt(action: () -> Unit) {
        if (disposed) {
            return
        }
        onEdtDispatcher {
            if (!disposed) {
                action()
            }
        }
    }

    override fun dispose() {
        if (disposed) {
            return
        }
        disposed = true
        if (!project.isDisposed) {
            WorktreePullRequestPageReloader.getInstance(project).unregisterSessionReloadHandler(reloadHandler)
        }
        coroutineScope.cancel()
        Disposer.dispose(sessionDisposable)
    }

    companion object {
        private val LOG = logger<BbCloudPullRequestEditorSession>()
        private const val NOTIFICATION_GROUP_ID = "BbCloud"
        private const val POLL_INTERVAL_MS = 30_000L
    }
}

internal fun loadRestoredPullRequestDetails(
    scope: CoroutineScope,
    request: BbCloudPullRequestEditorRestorationRequest,
    detailsLoader: suspend (BbCloudPullRequestEditorRestorationRequest) -> PullRequestDetails?,
    onHydrated: (PullRequestDetails?) -> Unit
) {
    scope.launch {
        onHydrated(detailsLoader(request))
    }
}

/** Activates a restored editor only if hydration completed while its file remained selected. */
internal fun activateRestoredPullRequestSelection(
    selectedFile: BbCloudPullRequestVirtualFile?,
    file: BbCloudPullRequestVirtualFile,
    reviewState: BbCloudPullRequestReviewState,
    onReviewStateChanged: () -> Unit
): Boolean {
    return BbCloudPullRequestEditorActivation.activateSelectedPullRequest(
        selectedFile = selectedFile,
        file = file,
        reviewState = reviewState,
        onReviewStateChanged = onReviewStateChanged
    )
}

/**
 * Holds the mutable Compose state the closed-PR takeover reads: whether to offer the cleanup button at all (decided
 * once at build time per decisions 3a and 6a) and whether a cleanup is currently running (so the button disables while
 * it works, decision 8a). The editor session mutates [cleanupInProgress] from the EDT.
 */
internal class TakeoverCleanupViewModel(val showCleanupButton: Boolean) {
    private val cleanupInProgressState = mutableStateOf(false)
    val cleanupInProgress: Boolean by cleanupInProgressState

    fun setCleanupInProgress(inProgress: Boolean) {
        cleanupInProgressState.value = inProgress
    }
}

/**
 * The view that replaces the pull-request detail once the pull request is no longer Open: a link that opens the pull
 * request in Bitbucket and, when the project is the pull request's git work tree, a "Cleanup this PR Work Tree" button
 * beside it (decision 2c).
 */
@Composable
private fun TakeoverContent(
    text: String,
    tooltip: String,
    onOpenBitbucket: () -> Unit,
    cleanupButtonLabel: String,
    viewModel: TakeoverCleanupViewModel,
    onCleanupWorktree: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Tooltip(tooltip = { Text(text = tooltip) }) {
            Link(text = text, onClick = onOpenBitbucket)
        }
        if (viewModel.showCleanupButton) {
            OutlinedButton(
                onClick = onCleanupWorktree,
                enabled = !viewModel.cleanupInProgress
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        key = AllIconsKeys.Actions.GC,
                        contentDescription = cleanupButtonLabel
                    )
                    Text(text = cleanupButtonLabel)
                }
            }
        }
    }
}