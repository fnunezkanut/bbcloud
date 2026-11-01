package com.github.fnunezkanut.bbcloud.ui.pullrequest.changes

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestFileCommentCounts
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestPathMatcher
import com.github.fnunezkanut.bbcloud.git.BbCloudPullRequestBranchDiffService
import com.github.fnunezkanut.bbcloud.git.BranchDiffResult
import com.github.fnunezkanut.bbcloud.review.BbCloudPullRequestViewedFiles
import com.github.fnunezkanut.bbcloud.review.BbCloudWorktreePullRequestContext
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestChangeDiffOpener
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestChangesViewModel
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestFileChangesCard
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

/**
 * Owns the view model of the work-tree PR Changes tool window.
 *
 * The data comes from the local Git three-dot diff ([BbCloudPullRequestBranchDiffService]) and the work-tree comment
 * snapshot ([BbCloudWorktreePullRequestContext]). The tool window is the only view of the changed files: the pull
 * request editor does not load them, and it only publishes the comment snapshot this service counts comments from.
 */
@Service(Service.Level.PROJECT)
class WorktreePullRequestChangesToolWindowService(private val project: Project, private val coroutineScope: CoroutineScope) : Disposable {
    private val diffService = BbCloudPullRequestBranchDiffService()

    val viewModel = PullRequestChangesViewModel(
        pullRequestId = { currentPullRequestId },
        projectBasePath = { project.basePath },
        onRefreshRequested = { reload(reason = "refresh link") },
        onOpenChange = { change -> PullRequestChangeDiffOpener.open(project = project, change = change) },
        onOpenChangeInList = { changes, index -> openDiffChain(changes = changes, index = index) },
        isFileViewed = { pullRequestId, relativePath, revision ->
            BbCloudPullRequestViewedFiles.getInstance(project).isViewed(
                pullRequestId = pullRequestId,
                relativePath = relativePath,
                revision = revision
            )
        }
    )

    @Volatile
    private var destinationBranch: String? = null

    @Volatile
    private var sourceBranch: String? = null

    @Volatile
    private var currentPullRequestId: Long? = null

    @Volatile
    private var lastLoadedRevision: String? = null

    private val loadLock = Any()
    private var loadJob: Job? = null

    init {
        project.messageBus.connect(this).subscribe(
            GitRepository.GIT_REPO_CHANGE,
            GitRepositoryChangeListener { repository -> onRepositoryChanged(repository = repository) }
        )
    }

    /** Shows the changed files of the pull request between [destinationBranch] and [sourceBranch] and reloads them. */
    @RequiresEdt
    fun showFor(pullRequestId: Long?, destinationBranch: String, sourceBranch: String) {
        this.currentPullRequestId = pullRequestId
        this.destinationBranch = destinationBranch
        this.sourceBranch = sourceBranch
        updateTabTitle()
        reload(reason = "show changed files")
    }

    /** Reloads the changed files; [reason] is logged for diagnostics. */
    fun reload(reason: String) {
        LOG.debug("Reloading the PR Changes tool window: $reason")
        val context = BbCloudWorktreePullRequestContext.getInstance(project)
        if (context.pullRequestKnownClosed) {
            synchronized(loadLock) { loadJob?.cancel() }
            runOnEdt { viewModel.showResolved(pullRequestId = currentPullRequestId) }
            return
        }
        val destination = destinationBranch
        val source = sourceBranch
        if (destination.isNullOrBlank() || source.isNullOrBlank()) {
            LOG.info("Not reloading the PR Changes tool window: the branch names are not resolved yet")
            return
        }
        val job = coroutineScope.launch {
            try {
                withContext(Dispatchers.EDT) { viewModel.showLoading() }
                val started = TimeSource.Monotonic.markNow()
                val result = withContext(Dispatchers.IO) {
                    // Load the diff and the per-file line counts in parallel, so the tree shows the +N -N badges without
                    // waiting on two sequential Git commands.
                    val changesDeferred = async {
                        diffService.loadPullRequestChanges(
                            project = project,
                            destinationBranch = destination,
                            sourceBranch = source
                        )
                    }
                    val lineCountsDeferred = async {
                        diffService.loadPullRequestFileLineCounts(
                            project = project,
                            destinationBranch = destination,
                            sourceBranch = source
                        )
                    }
                    val diffResult = changesDeferred.await()
                    val lineCounts = lineCountsDeferred.await()
                    if (diffResult is BranchDiffResult.Success) {
                        // The revisions are read after the diff, because they need the resolved source ref. They use the
                        // same lookup and key normalization as BbCloudWorktreePullRequestContext, so the viewed mark in
                        // this tree and in the diff review bar agree.
                        val revisions = diffService.loadPullRequestFileRevisions(
                            project = project,
                            sourceRef = diffResult.sourceRef,
                            changes = diffResult.changes
                        ).mapKeys { (path, _) ->
                            PullRequestPathMatcher.normalize(
                                PullRequestPathMatcher.relativeProjectPath(
                                    projectBasePath = project.basePath,
                                    absoluteOrRelativePath = path
                                )
                            )
                        }
                        diffResult.copy(fileLineCounts = lineCounts, fileRevisions = revisions)
                    } else {
                        diffResult
                    }
                }
                val changedFileCount = (result as? BranchDiffResult.Success)?.changes?.size ?: 0
                LOG.debug(
                    "Loaded $changedFileCount changed file(s) for the PR Changes tool window in " +
                        "${started.elapsedNow().inWholeMilliseconds} ms ($reason)"
                )
                withContext(Dispatchers.EDT) {
                    viewModel.showResult(result)
                    applyCommentCounts()
                }
                lastLoadedRevision = withContext(Dispatchers.IO) { diffService.readHead(project)?.revision }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                LOG.warn("Failed to load the changed files of the PR Changes tool window", exception)
                withContext(Dispatchers.EDT) { viewModel.showResult(BranchDiffResult.Failed(message = "")) }
            }
        }
        synchronized(loadLock) {
            loadJob?.cancel()
            loadJob = job
        }
    }

    /**
     * Pushes the per-file comment counts from the work-tree comment snapshot into the view model. When the context
     * has no binding, or it is bound to a different pull request, the counts are cleared so no stale pill shows.
     */
    @RequiresEdt
    fun applyCommentCounts() {
        val context = BbCloudWorktreePullRequestContext.getInstance(project)
        val binding = context.binding
        if (binding == null || binding.pullRequestId != currentPullRequestId) {
            viewModel.setCommentCounts(null)
            return
        }
        viewModel.setCommentCounts(PullRequestFileCommentCounts.from(comments = context.comments))
    }

    /**
     * Switches to or from the resolved card when the pull request's open/closed status changed. Called from the
     * composable when [BbCloudWorktreePullRequestContext.pullRequestStatusRevision] changes.
     */
    @RequiresEdt
    fun onPullRequestStatusChanged() {
        val context = BbCloudWorktreePullRequestContext.getInstance(project)
        val card = viewModel.state.value.card
        if (context.pullRequestKnownClosed) {
            if (card != PullRequestFileChangesCard.Resolved) {
                viewModel.showResolved(pullRequestId = currentPullRequestId)
            }
            return
        }
        if (card == PullRequestFileChangesCard.Resolved) {
            reload(reason = "pull request reopened")
        }
    }

    private fun openDiffChain(changes: List<Change>, index: Int) {
        LOG.info("User opened the diff of changed file $index of pull request #$currentPullRequestId from the PR Changes tool window")
        // PullRequestDiffBarsDiffExtension attaches the pull-request bars because the platform producers set
        // ChangeDiffRequestProducer.CHANGE_KEY, so no extra wiring is needed here.
        PullRequestChangeDiffOpener.openInChain(project = project, changes = changes, index = index)
    }

    @RequiresEdt
    private fun updateTabTitle() {
        val source = sourceBranch
        val destination = destinationBranch
        if (source.isNullOrBlank() || destination.isNullOrBlank()) {
            return
        }
        val content = ToolWindowManager.getInstance(project)
            .getToolWindow(WorktreePullRequestChangesToolWindowFactory.TOOL_WINDOW_ID)
            ?.contentManager
            ?.contents
            ?.firstOrNull()
            ?: return
        content.displayName = BbCloudBundle.message("worktree.changesToolWindow.tabTitle", source, destination)
    }

    private fun onRepositoryChanged(repository: GitRepository) {
        if (project.isDisposed) {
            return
        }
        val revision = repository.currentRevision
        if (revision == lastLoadedRevision) {
            return
        }
        val visible = ToolWindowManager.getInstance(project)
            .getToolWindow(WorktreePullRequestChangesToolWindowFactory.TOOL_WINDOW_ID)
            ?.isVisible == true
        if (!visible) {
            return
        }
        lastLoadedRevision = revision
        reload(reason = "repository changed")
    }

    private fun runOnEdt(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                action()
            }
        }
    }

    override fun dispose() {
        synchronized(loadLock) { loadJob?.cancel() }
        viewModel.reset()
    }

    companion object {
        private val LOG = logger<WorktreePullRequestChangesToolWindowService>()

        fun getInstance(project: Project): WorktreePullRequestChangesToolWindowService = project.service()
    }
}