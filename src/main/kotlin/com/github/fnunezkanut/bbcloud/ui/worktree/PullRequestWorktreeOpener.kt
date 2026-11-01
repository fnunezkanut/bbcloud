package com.github.fnunezkanut.bbcloud.ui.worktree

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.auth.BbCloudAccessTokenProvider
import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudOpenPullRequestService
import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryUrls
import com.github.fnunezkanut.bbcloud.bitbucket.OkHttpBitbucketPullRequestClient
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.git.BbCloudWorktreeService
import com.github.fnunezkanut.bbcloud.git.BitbucketGitRemoteReader
import com.github.fnunezkanut.bbcloud.git.WorktreeSetupResult
import com.github.fnunezkanut.bbcloud.project.LocalRepositoryProjectOpener
import com.github.fnunezkanut.bbcloud.project.PullRequestOpenCheck
import com.github.fnunezkanut.bbcloud.project.PullRequestOpenCheckResult
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeResolution
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeResolver
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPendingEditorOpen
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestEditorOpenQueue
import com.intellij.ide.BrowserUtil
import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * Opens a pull request in its own git work tree. This is the single implementation of the open flow:
 *
 * - The Workspace and Current Project panels call [resolve], [prepareWorktree] and [openProjectAndEditor] from their own
 *   modal and error handling, so the panel code keeps only UI concerns.
 * - The browser-triggered REST path calls [openInBackground], which runs the same steps on this service's scope and
 *   reports every outcome as an IDE notification.
 *
 * The only state held here is the set of work trees currently being opened, which keeps the panels and the REST path
 * from opening the same work tree twice.
 */
@Service(Service.Level.APP)
class PullRequestWorktreeOpener(private val coroutineScope: CoroutineScope) {
    private val openingWorktrees: MutableSet<Path> = ConcurrentHashMap.newKeySet<Path>()

    private val openPullRequestService: BbCloudOpenPullRequestService by lazy {
        BbCloudOpenPullRequestService(
            accessTokenProvider = ApplicationManager.getApplication().service<BbCloudAccessTokenProvider>(),
            pullRequestClient = OkHttpBitbucketPullRequestClient()
        )
    }

    /** Finds the source clone and the work tree path for a pull request, using only the local disk. */
    fun resolve(workspace: String, repoSlug: String, prNumber: Long): PullRequestWorktreeResolution {
        val configuration = BbCloudConfigurationService.getInstance()
        return PullRequestWorktreeResolver.resolve(
            mappings = configuration.watchedProjectsForCurrentWorkspaceWithLocalPaths(),
            workTreeDirectory = configuration.workTreeDirectoryOrNull(),
            projectsDirectory = configuration.projectsDirectoryOrNull(),
            workspace = workspace,
            repoSlug = repoSlug,
            prNumber = prNumber
        )
    }

    /** Registers [worktreePath] as being opened. Returns false when another open of that work tree is still running. */
    fun tryBeginOpen(worktreePath: Path): Boolean {
        return openingWorktrees.add(normalized(worktreePath))
    }

    /** Clears the registration made by [tryBeginOpen]. Call it from a finally block so a failed open never stays registered. */
    fun endOpen(worktreePath: Path) {
        openingWorktrees.remove(normalized(worktreePath))
    }

    /** The work trees whose open is running now. Stale-worktree pruning skips these. */
    fun worktreesBeingOpened(): Set<Path> {
        return openingWorktrees.toSet()
    }

    /**
     * Re-checks with Bitbucket that the pull request is still open, then creates or reconciles the work tree on the
     * pull request branch. The git work runs on IO inside [BbCloudWorktreeService.ensureWorktree]. Call this from a
     * coroutine. It shows no UI.
     */
    suspend fun prepareWorktree(
        workspace: String,
        repoSlug: String,
        prNumber: Long,
        organizationUrl: String,
        resolved: PullRequestWorktreeResolution.Resolved
    ): WorktreeOpenOutcome {
        val details = try {
            openPullRequestService.pullRequestDetailsFor(
                workspace = workspace,
                repoSlug = repoSlug,
                organizationUrl = organizationUrl,
                pullRequestId = prNumber
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LOG.warn("PR state check failed for pull request #$prNumber", exception)
            return WorktreeOpenMapping.stateCheckFailure(prNumber = prNumber)
        }

        val summary = when (val check = PullRequestOpenCheck.evaluate(details = details)) {
            is PullRequestOpenCheckResult.StillOpen -> {
                check.details.summary
            }
            is PullRequestOpenCheckResult.NotOpen -> {
                LOG.info("Cannot open pull request #$prNumber: it is ${check.state} (no longer open)")
                return WorktreeOpenMapping.notOpenFailure(prNumber = prNumber, state = check.state)
            }
            PullRequestOpenCheckResult.NotFound -> {
                LOG.info("Cannot open pull request #$prNumber: not found on Bitbucket")
                return WorktreeOpenMapping.notFoundFailure(prNumber = prNumber)
            }
            is PullRequestOpenCheckResult.Failed -> {
                LOG.warn("PR state check failed for pull request #$prNumber", check.cause)
                return WorktreeOpenMapping.stateCheckFailure(prNumber = prNumber)
            }
        }

        val remoteUrl = withContext(Dispatchers.IO) {
            BitbucketGitRemoteReader.readRemoteUrl(checkoutRoot = resolved.sourceCheckoutPath)
        }
        if (remoteUrl == null) {
            LOG.info("Cannot open pull request #$prNumber: source checkout is not a git repository or has no origin remote")
            return WorktreeOpenOutcome.Failed(content = BbCloudBundle.message("otherProjects.worktree.failed.notAGitRepo"))
        }

        val setup = BbCloudWorktreeService.getInstance().ensureWorktree(
            sourceCheckoutRoot = resolved.sourceCheckoutPath,
            worktreePath = resolved.worktreePath,
            remoteUrl = remoteUrl,
            prNumber = prNumber,
            sourceBranch = summary.sourceBranch
        )
        logSetupFailure(prNumber = prNumber, setup = setup)
        return WorktreeOpenMapping.setupOutcome(summary = summary, setup = setup)
    }

    /**
     * Opens or focuses the IDE window for [worktreePath] and queues the pull request editor tab in that window. Returns
     * the window, or null when the IDE could not open the project. Editor failures are logged and do not change the result.
     */
    suspend fun openProjectAndEditor(
        summary: PullRequestSummary,
        workspace: String,
        repoSlug: String,
        organizationUrl: String?,
        worktreePath: Path
    ): LocalRepositoryProjectOpener.OpenedProject? {
        val opened = LocalRepositoryProjectOpener.openOrFocus(repoSlug = repoSlug, checkoutPath = worktreePath)
        if (opened == null) {
            LOG.info("Cannot open pull request #${summary.id}: the IDE could not open the work tree project")
            return null
        }
        queuePullRequestEditor(
            opened = opened,
            summary = summary,
            workspace = workspace,
            repoSlug = repoSlug,
            organizationUrl = organizationUrl
        )
        return opened
    }

    /**
     * Opens the pull request with no UI, for the browser-triggered REST path. The work runs on this service's scope.
     * Failures, a missing configuration, and a branch collision become IDE notifications. A collision never switches
     * the user's checkout from a browser request.
     */
    fun openInBackground(workspace: String, repoSlug: String, prNumber: Long) {
        coroutineScope.launch {
            try {
                openFromBrowserRequest(workspace = workspace, repoSlug = repoSlug, prNumber = prNumber)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                LOG.warn("Opening pull request #$prNumber from the browser failed", exception)
                notifyFailure(
                    prNumber = prNumber,
                    content = BbCloudBundle.message("otherProjects.worktree.failed.generic"),
                    webUrl = pullRequestWebUrl(workspace = workspace, repoSlug = repoSlug, prNumber = prNumber)
                )
            }
        }
    }

    private suspend fun openFromBrowserRequest(workspace: String, repoSlug: String, prNumber: Long) {
        val webUrl = pullRequestWebUrl(workspace = workspace, repoSlug = repoSlug, prNumber = prNumber)
        val organizationUrl = BbCloudConfigurationService.getInstance().organizationUrlOrNull()
        if (organizationUrl == null) {
            LOG.info("Cannot open pull request #$prNumber from the browser: no organization URL configured")
            notifyFailure(prNumber = prNumber, content = BbCloudBundle.message("otherProjects.worktree.failed.noOrgUrl"), webUrl = webUrl)
            return
        }
        val resolved = when (val resolution = resolve(workspace = workspace, repoSlug = repoSlug, prNumber = prNumber)) {
            is PullRequestWorktreeResolution.Resolved -> {
                resolution
            }
            PullRequestWorktreeResolution.WorkTreeDirMissing -> {
                LOG.info("Cannot open pull request #$prNumber from the browser: work tree directory is unavailable")
                notifyFailure(
                    prNumber = prNumber,
                    content = BbCloudBundle.message("otherProjects.worktree.failed.workTreeDirMissing"),
                    webUrl = webUrl
                )
                return
            }
            PullRequestWorktreeResolution.SourceCheckoutMissing -> {
                LOG.info("Cannot open pull request #$prNumber from the browser: no local clone of $repoSlug")
                notifyFailure(
                    prNumber = prNumber,
                    content = BbCloudBundle.message("otherProjects.worktree.failed.sourceCheckoutMissing", repoSlug),
                    webUrl = webUrl
                )
                return
            }
        }
        if (!tryBeginOpen(worktreePath = resolved.worktreePath)) {
            LOG.info("Opening pull request #$prNumber from the browser is already in progress")
            return
        }
        try {
            when (
                val outcome = prepareWorktree(
                    workspace = workspace,
                    repoSlug = repoSlug,
                    prNumber = prNumber,
                    organizationUrl = organizationUrl,
                    resolved = resolved
                )
            ) {
                is WorktreeOpenOutcome.Prepared -> {
                    val opened = openProjectAndEditor(
                        summary = outcome.summary,
                        workspace = workspace,
                        repoSlug = repoSlug,
                        organizationUrl = organizationUrl,
                        worktreePath = outcome.worktreePath
                    )
                    if (opened == null) {
                        notifyFailure(
                            prNumber = prNumber,
                            content = BbCloudBundle.message("otherProjects.worktree.failed.openProjectFailed"),
                            webUrl = outcome.summary.webUrl
                        )
                    } else {
                        focusProject(project = opened.project)
                    }
                }
                is WorktreeOpenOutcome.Failed -> {
                    notifyFailure(prNumber = prNumber, content = outcome.content, webUrl = webUrl)
                }
                is WorktreeOpenOutcome.BranchCollision -> {
                    LOG.info("Pull request #$prNumber from the browser needs its branch off the source clone; reporting only")
                    notifyBranchCollision(prNumber = prNumber, outcome = outcome, webUrl = webUrl)
                }
            }
        } finally {
            endOpen(worktreePath = resolved.worktreePath)
        }
    }

    /**
     * Queues the pull request editor tab for [summary] in the window [opened]. The queue waits until the window is ready,
     * so the tab lands in the new window and not in the current one. When the window was already open, the tab opens
     * right away. Failures are logged only, because the user keeps working in the checkout.
     */
    fun queuePullRequestEditor(
        opened: LocalRepositoryProjectOpener.OpenedProject,
        summary: PullRequestSummary,
        workspace: String,
        repoSlug: String,
        organizationUrl: String?
    ) {
        val project = opened.project
        if (project.isDisposed) {
            LOG.info("The project was disposed before the pull request editor could be opened")
            return
        }
        // The Workspace tabs and the REST path both pass the workspace of the pull request, so the URL follows it.
        val webUrl = BitbucketRepositoryUrls.repositoryWebUrlForWorkspace(workspace = workspace, repoSlug = repoSlug)
            ?: organizationUrl?.let { url -> BitbucketRepositoryUrls.repositoryWebUrl(organizationUrl = url, repoSlug = repoSlug) }
        if (webUrl == null) {
            LOG.info("Cannot open pull request #${summary.id}: no repository web URL")
            return
        }
        val queue = project.service<BbCloudPullRequestEditorOpenQueue>()
        queue.enqueue(
            BbCloudPendingEditorOpen(
                summary = summary,
                branchName = summary.sourceBranch,
                repositoryWebUrl = webUrl,
                repositorySlug = repoSlug
            )
        )
        if (opened.wasAlreadyOpen) {
            queue.drainAndOpen(project)
        }
    }

    private fun focusProject(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                // macOS may only bounce the Dock icon when the app is inactive; the sandbox check covers this.
                ProjectUtil.focusProjectWindow(project = project, stealFocusIfAppInactive = true)
            }
        }
    }

    private fun logSetupFailure(prNumber: Long, setup: WorktreeSetupResult) {
        when (setup) {
            is WorktreeSetupResult.Failed -> {
                LOG.warn("Could not prepare the work tree for pull request #$prNumber: ${setup.output}", setup.cause)
            }
            is WorktreeSetupResult.BranchCheckedOutInSource -> {
                LOG.info(
                    "Cannot open pull request #$prNumber in its own work tree: the current checkout is on the PR branch '${setup.branchName}'"
                )
            }
            is WorktreeSetupResult.Success -> {
                LOG.debug("Work tree for pull request #$prNumber is ready")
            }
        }
    }

    private fun notifyFailure(prNumber: Long, content: String, webUrl: String?) {
        notify(
            title = BbCloudBundle.message("otherProjects.worktree.failed.title", prNumber),
            content = content,
            webUrl = webUrl,
            prNumber = prNumber
        )
    }

    private fun notifyBranchCollision(prNumber: Long, outcome: WorktreeOpenOutcome.BranchCollision, webUrl: String?) {
        notify(
            title = BbCloudBundle.message("otherProjects.worktree.branchCheckedOut.title", prNumber),
            content = BbCloudBundle.message(
                "rest.open.branchCollision",
                outcome.branchName,
                outcome.sourceCheckoutPath.toString()
            ),
            webUrl = webUrl,
            prNumber = prNumber
        )
    }

    private fun notify(title: String, content: String, webUrl: String?, prNumber: Long) {
        val trimmed = content.trim().ifBlank { BbCloudBundle.message("otherProjects.worktree.failed.generic") }
        val body = if (trimmed.length > MAX_NOTIFICATION_CONTENT_LENGTH) {
            trimmed.substring(0, MAX_NOTIFICATION_CONTENT_LENGTH) + "\u2026"
        } else {
            trimmed
        }
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, body, NotificationType.ERROR)
        if (webUrl != null) {
            notification.addAction(
                NotificationAction.createSimpleExpiring(BbCloudBundle.message("otherProjects.worktree.openInBitbucket")) {
                    LOG.info("User opened Bitbucket PR #$prNumber from a work tree notification")
                    BrowserUtil.browse(webUrl)
                }
            )
        }
        notification.notify(null)
    }

    private fun pullRequestWebUrl(workspace: String, repoSlug: String, prNumber: Long): String? {
        val repositoryUrl = BitbucketRepositoryUrls.repositoryWebUrlForWorkspace(workspace = workspace, repoSlug = repoSlug)
            ?: return null
        return "$repositoryUrl/pull-requests/$prNumber"
    }

    private fun normalized(path: Path): Path {
        return path.toAbsolutePath().normalize()
    }

    companion object {
        private val LOG = logger<PullRequestWorktreeOpener>()
        private const val NOTIFICATION_GROUP_ID = "BbCloud"
        private const val MAX_NOTIFICATION_CONTENT_LENGTH = 2_000

        fun getInstance(): PullRequestWorktreeOpener {
            return ApplicationManager.getApplication().service()
        }
    }
}