package com.github.fnunezkanut.bbcloud.review

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudOpenPullRequestService
import com.github.fnunezkanut.bbcloud.bitbucket.CommentChange
import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentSide
import com.github.fnunezkanut.bbcloud.bitbucket.OkHttpBitbucketPullRequestClient
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestActionResult
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestCommentThreads
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestInlineCommentIndex
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestPathMatcher
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.credentials.BbCloudCredentialStore
import com.github.fnunezkanut.bbcloud.git.BbCloudPullRequestBranchDiffService
import com.github.fnunezkanut.bbcloud.git.BranchDiffResult
import com.github.fnunezkanut.bbcloud.git.FileLineCount
import com.github.fnunezkanut.bbcloud.git.WorktreeHead
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeLayout
import com.github.fnunezkanut.bbcloud.ui.diff.BbCloudPullRequestDiffCommentService
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestReviewBarRefresh
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Paths
import kotlin.time.Duration.Companion.milliseconds

private data class ChangedFilePathsAndRevisions(val paths: Set<String>, val fileRevisions: Map<String, String>)

/**
 * The pull request a pull-request work tree belongs to.
 *
 * The context is derived from the work tree alone: the folder name carries the pull request number, Bitbucket
 * carries the branches and the commits of the pull request, and the local repository carries the checked-out branch
 * and HEAD. It therefore does not depend on which editor tab is selected, which is what lets the "Requires review"
 * bar show in the normal text editor of the work tree as well.
 */
@Service(Service.Level.PROJECT)
class BbCloudWorktreePullRequestContext(private val project: Project, private val coroutineScope: CoroutineScope) : Disposable {
    /**
     * The pull request the work tree is bound to, or null when the work tree belongs to no open pull request.
     *
     * A binding exists only when the work tree is a pull-request work tree, the pull request is open, the HEAD of
     * the work tree is part of the pull request and the files the pull request changes are known.
     */
    @Volatile
    var binding: WorktreePullRequestBinding? = null
        private set

    /**
     * True once a successful Bitbucket resolve proved the pull request is merged or declined.
     *
     * The flag is the "proven non-Open" signal the banner reads to hide the blue bar even when no editor summary is
     * available. A failed resolve (no organization URL, HTTP error, null details) does not set this true, so an
     * unproven state leaves the banner hidden under the "hide until Open is proven" rule rather than treating the
     * pull request as closed.
     */
    @Volatile
    var pullRequestKnownClosed: Boolean = false
        private set

    /**
     * True once a successful Bitbucket resolve proved the pull request is Open (including draft pull requests, which
     * are Open with a draft flag).
     *
     * The flag is the "proven Open" signal the banner reads to show the blue bar. It can be true while [binding] is
     * still null, because the HEAD of the work tree may not be part of the pull request yet; the banner visibility is
     * therefore not tied to the binding.
     */
    @Volatile
    var pullRequestKnownOpen: Boolean = false
        private set

    private val configurationService = BbCloudConfigurationService.getInstance()
    private val openPullRequestService = createOpenPullRequestService(configurationService)
    private val branchDiffService = BbCloudPullRequestBranchDiffService()
    private val reloadLock = Any()
    private val commentLoadLock = Any()
    private var reloadJob: Job? = null
    private var commentLoadJob: Job? = null
    private var commentReloadQueued = false
    private var commentUpdateInProgress = false
    private var loadedCommentsPullRequestId: Long? = null
    private var lastObservedHead: WorktreeHead? = null

    /** Raw comments shared by review bars and the pull-request editor session. */
    @Volatile
    var comments: List<PullRequestComment> = emptyList()
        private set

    /** Display names of the `@{account-key}` mentions in [comments], read by the inline comment cards. */
    @Volatile
    var mentionNames: Map<String, String> = emptyMap()
        private set

    /** Avatar URL of the configured Bitbucket user, used by the file comment composer. */
    @Volatile
    var currentUserAvatarUrl: String? = null
        private set

    /** UUID of the configured Bitbucket user, used to match Conversation action visibility. */
    @Volatile
    var currentUserUuid: String? = null
        private set

    /** Changes whenever the shared comment snapshot is replaced, so Compose bars can re-read it. */
    val commentsRevision: MutableState<Int> = mutableStateOf(0)

    init {
        project.messageBus.connect(this).subscribe(
            GitRepository.GIT_REPO_CHANGE,
            GitRepositoryChangeListener { repository -> onRepositoryChanged(repository = repository) }
        )
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    refreshReviewBar(file = file)
                }

                override fun selectionChanged(event: FileEditorManagerEvent) {
                    val file = event.newFile ?: return
                    refreshReviewBar(file = file)
                }
            }
        )
    }

    /** Tells whether the file is one of the files the bound pull request changes. */
    fun isFileChanged(relativePath: String): Boolean {
        val currentBinding = binding ?: return false
        return currentBinding.changedFilePaths.contains(PullRequestPathMatcher.normalize(relativePath))
    }

    /** The pull request the file belongs to, or null when the pull request does not change the file. */
    fun pullRequestIdForFile(relativePath: String): Long? {
        val currentBinding = binding ?: return null
        if (!isFileChanged(relativePath)) {
            return null
        }
        return currentBinding.pullRequestId
    }

    /** Returns the known line counts for a changed file, or null when counts are unavailable. */
    fun fileLineCountFor(relativePath: String): FileLineCount? {
        val currentBinding = binding ?: return null
        return currentBinding.fileLineCounts[PullRequestPathMatcher.normalize(relativePath)]
    }

    /** Returns the known source-branch blob revision for a changed file, or null when unavailable. */
    fun fileRevisionFor(relativePath: String): String? {
        val currentBinding = binding ?: return null
        return currentBinding.fileRevisions[PullRequestPathMatcher.normalize(relativePath)]
    }

    fun requestCommentsReload(force: Boolean = false) {
        val currentBinding = binding ?: return
        val pullRequestId = currentBinding.pullRequestId
        val organizationUrl = configurationService.organizationUrlOrNull() ?: return
        synchronized(commentLoadLock) {
            if (commentLoadJob?.isActive == true) {
                commentReloadQueued = commentReloadQueued || force
                return
            }
            if (!force && loadedCommentsPullRequestId == pullRequestId) {
                return
            }
            commentLoadJob = coroutineScope.launch {
                val result = openPullRequestService.listPullRequestComments(
                    projectBasePath = project.basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = pullRequestId
                )
                val avatarUrl = currentUserAvatarUrl ?: openPullRequestService.currentUserAvatarUrl(
                    projectBasePath = project.basePath,
                    organizationUrl = organizationUrl
                )
                val userUuid = currentUserUuid ?: openPullRequestService.currentUserUuid(
                    projectBasePath = project.basePath,
                    organizationUrl = organizationUrl
                )
                val mentionNames = result?.items?.let { items ->
                    openPullRequestService.pullRequestMentionNames(
                        projectBasePath = project.basePath,
                        organizationUrl = organizationUrl,
                        comments = items
                    )
                }.orEmpty()
                withContext(Dispatchers.EDT) {
                    val isCurrentBinding = binding?.pullRequestId == pullRequestId
                    if (isCurrentBinding && result != null) {
                        publishComments(
                            pullRequestId = pullRequestId,
                            comments = result.items,
                            avatarUrl = avatarUrl,
                            userUuid = userUuid,
                            mentionNames = mentionNames
                        )
                        loadedCommentsPullRequestId = pullRequestId
                        EditorNotifications.getInstance(project).updateAllNotifications()
                    }
                    val reloadAgain = synchronized(commentLoadLock) {
                        commentLoadJob = null
                        val queued = commentReloadQueued
                        commentReloadQueued = false
                        queued
                    }
                    if (reloadAgain && isCurrentBinding) {
                        requestCommentsReload(force = true)
                    }
                }
            }
        }
    }

    /** Publishes comments loaded by the editor session into the worktree snapshot, together with the names of their mentions. */
    fun publishComments(
        pullRequestId: Long,
        comments: List<PullRequestComment>,
        avatarUrl: String? = null,
        userUuid: String? = null,
        mentionNames: Map<String, String> = emptyMap()
    ) {
        if (binding?.pullRequestId != pullRequestId) {
            return
        }
        val mentionNamesChanged = this.mentionNames != mentionNames
        this.comments = comments
        this.mentionNames = mentionNames
        BbCloudPullRequestViewedThreads.getInstance().prune(
            workspaceSlug = configurationService.workspaceSlugOrNull().orEmpty(),
            pullRequestId = pullRequestId,
            keptRootCommentIds = PullRequestCommentThreads.groupIntoThreads(comments = comments)
                .map { thread -> thread.root.id }
                .toSet()
        )
        if (avatarUrl != null) {
            currentUserAvatarUrl = avatarUrl
        }
        if (userUuid != null) {
            currentUserUuid = userUuid
        }
        commentsRevision.value++
        val reviewState = BbCloudPullRequestReviewState.getInstance(project)
        if (reviewState.activePullRequestId != pullRequestId) {
            reviewState.bindPullRequest(pullRequestId = pullRequestId)
        }
        val inlineIndexChanged = reviewState.updateInlineComments(
            pullRequestId = pullRequestId,
            index = PullRequestInlineCommentIndex.from(comments = comments)
        )
        if (inlineIndexChanged || mentionNamesChanged) {
            com.github.fnunezkanut.bbcloud.ui.diff.BbCloudPullRequestDiffCommentService.getInstance(project)
                .refreshOpenEditors()
        }
    }

    /** Submits a path-only Bitbucket comment and refreshes every review surface after success. */
    fun submitFileScopedComment(pullRequestId: Long, path: String, content: String, onResult: (Boolean) -> Unit) {
        val normalizedPath = PullRequestPathMatcher.normalize(path)
        val basePath = project.basePath
        val organizationUrl = configurationService.organizationUrlOrNull()
        if (normalizedPath.isBlank() || basePath == null || organizationUrl == null) {
            LOG.info("Cannot add a file comment to pull request #$pullRequestId: required project metadata is missing")
            onResult(false)
            return
        }
        synchronized(commentLoadLock) {
            if (commentUpdateInProgress) {
                onResult(false)
                return
            }
            commentUpdateInProgress = true
        }
        LOG.info("Submitting a file comment for pull request #$pullRequestId at $normalizedPath")
        coroutineScope.launch {
            val result = openPullRequestService.applyCommentChange(
                projectBasePath = basePath,
                organizationUrl = organizationUrl,
                pullRequestId = pullRequestId,
                change = CommentChange.CreateFileScoped(content = content, path = normalizedPath)
            )
            withContext(Dispatchers.EDT) {
                synchronized(commentLoadLock) { commentUpdateInProgress = false }
                when (result) {
                    PullRequestActionResult.Success -> {
                        onResult(true)
                        requestCommentsReload(force = true)
                        WorktreePullRequestPageReloader.getInstance(project).requestReload()
                    }
                    is PullRequestActionResult.Failed -> {
                        onResult(false)
                        LOG.warn("Failed to add a file comment to pull request #$pullRequestId: ${result.message}")
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("BbCloud")
                            .createNotification(
                                BbCloudBundle.message("main.branch.comments.failed.createFileScoped", pullRequestId),
                                result.message ?: BbCloudBundle.message("main.branch.action.failed.generic"),
                                NotificationType.ERROR
                            )
                            .notify(project)
                    }
                }
            }
        }
    }

    /**
     * Submits a line-anchored Bitbucket comment and refreshes every review surface after success.
     *
     * [line] is one-based; [side] selects the diff side ([InlineCommentSide.AFTER] for the file on disk and the diff
     * right editor, [InlineCommentSide.BEFORE] for the diff left/pre-image editor).
     */
    fun submitInlineLineComment(pullRequestId: Long, path: String, line: Int, side: InlineCommentSide, content: String, onResult: (Boolean) -> Unit) {
        val normalizedPath = PullRequestPathMatcher.normalize(path)
        val basePath = project.basePath
        val organizationUrl = configurationService.organizationUrlOrNull()
        if (normalizedPath.isBlank() || line <= 0 || basePath == null || organizationUrl == null) {
            LOG.info("Cannot add a line comment to pull request #$pullRequestId: required project metadata is missing")
            onResult(false)
            return
        }
        synchronized(commentLoadLock) {
            if (commentUpdateInProgress) {
                onResult(false)
                return
            }
            commentUpdateInProgress = true
        }
        LOG.info("Submitting a line comment for pull request #$pullRequestId at $normalizedPath:$line on $side")
        coroutineScope.launch {
            val result = openPullRequestService.applyCommentChange(
                projectBasePath = basePath,
                organizationUrl = organizationUrl,
                pullRequestId = pullRequestId,
                change = CommentChange.CreateInlineLine(content = content, path = normalizedPath, line = line, side = side)
            )
            withContext(Dispatchers.EDT) {
                synchronized(commentLoadLock) { commentUpdateInProgress = false }
                when (result) {
                    PullRequestActionResult.Success -> {
                        onResult(true)
                        requestCommentsReload(force = true)
                        WorktreePullRequestPageReloader.getInstance(project).requestReload()
                    }
                    is PullRequestActionResult.Failed -> {
                        onResult(false)
                        LOG.warn("Failed to add a line comment to pull request #$pullRequestId: ${result.message}")
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("BbCloud")
                            .createNotification(
                                BbCloudBundle.message("main.branch.comments.failed.createInlineLine", pullRequestId),
                                result.message ?: BbCloudBundle.message("main.branch.action.failed.generic"),
                                NotificationType.ERROR
                            )
                            .notify(project)
                    }
                }
            }
        }
    }

    /** Applies an existing-comment mutation and refreshes all editor inlays after success. */
    fun applyCommentChange(change: CommentChange, onResult: (Boolean) -> Unit = {}) {
        val currentBinding = binding
        val pullRequestId = currentBinding?.pullRequestId
        val basePath = project.basePath
        val organizationUrl = configurationService.organizationUrlOrNull()
        if (pullRequestId == null || basePath == null || organizationUrl == null) {
            LOG.info("Cannot apply ${change::class.simpleName}: required review context is missing")
            onResult(false)
            return
        }
        synchronized(commentLoadLock) {
            if (commentUpdateInProgress) {
                onResult(false)
                return
            }
            commentUpdateInProgress = true
        }
        LOG.info("Applying ${change::class.simpleName} on pull request #$pullRequestId")
        coroutineScope.launch {
            val result = openPullRequestService.applyCommentChange(
                projectBasePath = basePath,
                organizationUrl = organizationUrl,
                pullRequestId = pullRequestId,
                change = change
            )
            withContext(Dispatchers.EDT) {
                synchronized(commentLoadLock) { commentUpdateInProgress = false }
                when (result) {
                    PullRequestActionResult.Success -> {
                        onResult(true)
                        requestCommentsReload(force = true)
                        WorktreePullRequestPageReloader.getInstance(project).requestReload()
                    }
                    is PullRequestActionResult.Failed -> {
                        onResult(false)
                        LOG.warn("Failed to apply ${change::class.simpleName} on pull request #$pullRequestId: ${result.message}")
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("BbCloud")
                            .createNotification(
                                BbCloudBundle.message("main.branch.action.failed.generic"),
                                result.message ?: BbCloudBundle.message("main.branch.action.failed.generic"),
                                NotificationType.ERROR
                            )
                            .notify(project)
                    }
                }
            }
        }
    }

    /**
     * Resolves the pull request the work tree belongs to.
     *
     * The reload of a running one is cancelled, so the last call wins, and the resolution runs off the EDT because
     * it waits for Bitbucket and for git.
     */
    fun reload(reason: String) {
        if (project.isDisposed) {
            return
        }
        synchronized(reloadLock) {
            reloadJob?.cancel()
            reloadJob = coroutineScope.launch {
                delay(RELOAD_DEBOUNCE)
                resolve(reason = reason)
            }
        }
    }

    private suspend fun resolve(reason: String) {
        try {
            val basePath = project.basePath
            if (basePath == null) {
                LOG.info("Review bar context unavailable ($reason): the project has no base path")
                applyBinding(newBinding = null)
                return
            }
            if (!BbCloudPullRequestReviewBarRefresh.isPullRequestWorkTree(projectBasePath = basePath)) {
                LOG.debug("Review bar context skipped ($reason): the project is not a pull-request work tree")
                applyBinding(newBinding = null)
                return
            }
            val pullRequestId = pullRequestIdFromWorkTree(basePath = basePath) ?: run {
                LOG.info("Review bar context unavailable ($reason): the work tree folder carries no pull request number")
                applyBinding(newBinding = null)
                return
            }
            val organizationUrl = configurationService.organizationUrlOrNull()
            if (organizationUrl == null) {
                LOG.info("Review bar context unavailable for #$pullRequestId: no organization URL is configured")
                applyBinding(newBinding = null)
                return
            }
            val details = openPullRequestService.pullRequestDetails(
                projectBasePath = basePath,
                organizationUrl = organizationUrl,
                pullRequestId = pullRequestId
            )
            if (details == null) {
                LOG.info("Review bar context unavailable for #$pullRequestId: the pull request details could not be loaded")
                applyBinding(newBinding = null)
                return
            }
            val summary = details.summary
            if (summary.state != PullRequestState.Open) {
                LOG.info("Blue bar and review bar hidden: pull request #$pullRequestId is not open")
                pullRequestKnownClosed = true
                pullRequestKnownOpen = false
                // Force the refresh even if the binding was already null, so the banner re-resolves to hidden now that
                // the pull request is proven closed.
                applyBinding(newBinding = null, forceRefresh = true)
                return
            }
            // The resolve reached a successful Open summary (draft pull requests are Open too), so the blue bar may
            // show even if the binding below stays null because the HEAD is not part of the pull request (decision 3a).
            pullRequestKnownOpen = true
            pullRequestKnownClosed = false
            val head = withContext(Dispatchers.IO) { branchDiffService.readHead(project) }
            if (head == null) {
                LOG.info("Review bar context unavailable for #$pullRequestId: the work tree HEAD could not be read")
                // The pull request is proven Open, so force the banner to re-resolve to visible even though no binding
                // forms (decision 3a: visibility is not tied to the binding).
                applyBinding(newBinding = null, forceRefresh = true)
                return
            }
            lastObservedHead = head
            val commits = openPullRequestService.pullRequestCommits(
                projectBasePath = basePath,
                organizationUrl = organizationUrl,
                pullRequestId = pullRequestId
            )
            val commitsKnown = commits != null
            val commitHashes = commits?.items?.map { commit -> commit.hash }.orEmpty()
            val isCommitListTruncated = commits?.isTruncated == true
            LOG.debug("Loaded ${commitHashes.size} commit(s) of pull request #$pullRequestId")
            val headIsDescendant = isHeadDescendantOfPullRequestTip(
                head = head,
                sourceBranch = summary.sourceBranch,
                commitHashes = commitHashes,
                commitsKnown = commitsKnown
            )
            val isPartOfPullRequest = WorktreePullRequestHeadMembership.isHeadPartOfPullRequest(
                currentBranchName = head.branchName,
                headRevision = head.revision,
                sourceBranch = summary.sourceBranch,
                pullRequestCommitHashes = commitHashes,
                isCommitListTruncated = isCommitListTruncated,
                commitsKnown = commitsKnown,
                headIsDescendantOfPullRequestTip = headIsDescendant
            )
            if (!isPartOfPullRequest) {
                LOG.info("Review bar hidden: HEAD of the work tree is not part of pull request #$pullRequestId")
                // The pull request is proven Open, so force the banner to re-resolve to visible even though the HEAD
                // is not part of the pull request and no binding forms (decision 3a).
                applyBinding(newBinding = null, forceRefresh = true)
                return
            }
            val changedFiles = changedFilePaths(
                basePath = basePath,
                destinationBranch = summary.destinationBranch,
                sourceBranch = summary.sourceBranch
            )
            if (changedFiles == null) {
                LOG.info("Review bar context unavailable for #$pullRequestId: the changed files could not be loaded")
                // The pull request is proven Open, so force the banner to re-resolve to visible even though no binding
                // forms (decision 3a).
                applyBinding(newBinding = null, forceRefresh = true)
                return
            }
            val changedFilePaths = changedFiles.paths
            val fileRevisions = changedFiles.fileRevisions
            BbCloudPullRequestViewedFiles.getInstance(project).prune(
                pullRequestId = pullRequestId,
                keptPaths = changedFilePaths
            )
            val fileLineCounts = fileLineCounts(
                destinationBranch = summary.destinationBranch,
                sourceBranch = summary.sourceBranch
            )
            val avatarUrl = openPullRequestService.currentUserAvatarUrl(
                projectBasePath = basePath,
                organizationUrl = organizationUrl
            )
            val userUuid = openPullRequestService.currentUserUuid(
                projectBasePath = basePath,
                organizationUrl = organizationUrl
            )
            LOG.debug("Resolved line counts for ${fileLineCounts.size} file(s) of pull request #$pullRequestId")
            applyBinding(
                newBinding = WorktreePullRequestBinding(
                    pullRequestId = pullRequestId,
                    sourceBranch = summary.sourceBranch,
                    destinationBranch = summary.destinationBranch,
                    headRevision = head.revision.orEmpty(),
                    changedFilePaths = changedFilePaths,
                    fileLineCounts = fileLineCounts,
                    fileRevisions = fileRevisions,
                    currentUserAvatarUrl = avatarUrl,
                    currentUserUuid = userUuid
                )
            )
            LOG.debug("Bound work tree to pull request #$pullRequestId with ${changedFilePaths.size} changed file(s)")
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LOG.warn("Failed to resolve the work tree pull request context", exception)
            applyBinding(newBinding = null)
        }
    }

    /**
     * Tells whether the HEAD of the work tree is a descendant of the tip of the pull request.
     *
     * The git call is skipped when the commit list is unknown, when the branch does not match or when the HEAD is
     * already one of the commits, because the answer is then not needed and a process spawn is not free.
     */
    private suspend fun isHeadDescendantOfPullRequestTip(head: WorktreeHead, sourceBranch: String, commitHashes: List<String>, commitsKnown: Boolean): Boolean {
        if (!commitsKnown) {
            return false
        }
        if (!WorktreePullRequestHeadMembership.isBranchMatching(
                currentBranchName = head.branchName,
                sourceBranch = sourceBranch
            )
        ) {
            return false
        }
        if (WorktreePullRequestHeadMembership.isRevisionInList(
                headRevision = head.revision,
                pullRequestCommitHashes = commitHashes
            )
        ) {
            return false
        }
        val ancestorRevision = commitHashes.firstOrNull().orEmpty()
        val descendantRevision = head.revision.orEmpty()
        return withContext(Dispatchers.IO) {
            branchDiffService.isAncestor(
                project = project,
                ancestorRevision = ancestorRevision,
                descendantRevision = descendantRevision
            )
        }
    }

    /** The normalized project-relative paths and source revisions the pull request changes, or null when unavailable. */
    private suspend fun changedFilePaths(basePath: String, destinationBranch: String, sourceBranch: String): ChangedFilePathsAndRevisions? {
        val result = withContext(Dispatchers.IO) {
            branchDiffService.loadPullRequestChanges(
                project = project,
                destinationBranch = destinationBranch,
                sourceBranch = sourceBranch
            )
        }
        if (result !is BranchDiffResult.Success) {
            return null
        }
        val paths = PullRequestChangedFilePaths.from(projectBasePath = basePath, changes = result.changes)
        val rawRevisions = withContext(Dispatchers.IO) {
            branchDiffService.loadPullRequestFileRevisions(
                project = project,
                sourceRef = result.sourceRef,
                changes = result.changes
            )
        }
        val revisions = rawRevisions.mapKeys { (path, _) ->
            PullRequestPathMatcher.normalize(
                PullRequestPathMatcher.relativeProjectPath(
                    projectBasePath = basePath,
                    absoluteOrRelativePath = path
                )
            )
        }
        return ChangedFilePathsAndRevisions(
            paths = paths,
            fileRevisions = revisions
        )
    }

    private suspend fun fileLineCounts(destinationBranch: String, sourceBranch: String): Map<String, FileLineCount> {
        return withContext(Dispatchers.IO) {
            branchDiffService.loadPullRequestFileLineCounts(
                project = project,
                destinationBranch = destinationBranch,
                sourceBranch = sourceBranch
            )
        }
    }

    /**
     * Publishes the new binding and refreshes the editor notifications when it changed.
     *
     * The notifications are refreshed on the EDT, because the platform collects the bars of the open editors there.
     */
    private suspend fun applyBinding(newBinding: WorktreePullRequestBinding?, forceRefresh: Boolean = false) {
        if (newBinding != null) {
            // A binding only forms for an Open pull request, so keep the Open/closed flags consistent with it.
            pullRequestKnownOpen = true
            pullRequestKnownClosed = false
        }
        if (binding == newBinding && !forceRefresh) {
            return
        }
        binding = newBinding
        currentUserAvatarUrl = if (newBinding == null) {
            null
        } else {
            newBinding.currentUserAvatarUrl ?: currentUserAvatarUrl
        }
        currentUserUuid = if (newBinding == null) {
            null
        } else {
            newBinding.currentUserUuid ?: currentUserUuid
        }
        if (newBinding == null || newBinding.pullRequestId != loadedCommentsPullRequestId) {
            comments = emptyList()
            mentionNames = emptyMap()
            loadedCommentsPullRequestId = null
            commentsRevision.value++
        }
        withContext(Dispatchers.EDT) {
            if (project.isDisposed) {
                return@withContext
            }
            // Update the shared banner view model before refreshing the notifications so a banner that is re-collected
            // on this same refresh already reads the resolved branch names rather than the previous binding.
            WorktreeTodoBannerViewModel.getInstance(project).refreshFromSources()
            EditorNotifications.getInstance(project).updateAllNotifications()
        }
        if (newBinding != null) {
            requestCommentsReload()
        }
    }

    /**
     * Reloads the context when the HEAD of the work tree moved.
     *
     * The repository change topic fires for many unrelated updates, such as a fetch or an index refresh, so the
     * reload only happens when the branch or the revision really changed.
     */
    private fun onRepositoryChanged(repository: GitRepository) {
        if (project.isDisposed) {
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
        reload(reason = "repository changed")
    }

    /**
     * Asks the platform for the bar of the file again.
     *
     * The bar comes from the editor notification provider, which the platform asks for the bar of a file when the
     * editor of the file is loaded and on every notification refresh. A file whose editor was already loaded before
     * the binding was resolved is never collected for that file again, because the plugin only refreshes the
     * notifications of the whole project, and only when the binding changes. The bar of such a file stays absent
     * until something unrelated triggers a refresh, so this listener asks the platform for the bar of the file that is
     * opened or selected again.
     */
    private fun refreshReviewBar(file: VirtualFile) {
        val basePath = project.basePath ?: return
        val currentBinding = binding ?: return
        val shouldRefresh = BbCloudPullRequestReviewBarRefresh.shouldRefresh(
            projectBasePath = basePath,
            activePullRequestId = currentBinding.pullRequestId,
            changedFilePaths = currentBinding.changedFilePaths,
            filePath = file.path
        )
        if (!shouldRefresh) {
            return
        }
        LOG.debug("Refreshing the Requires-review bar of '${file.name}' for pull request #${currentBinding.pullRequestId}")
        EditorNotifications.getInstance(project).updateNotifications(file)
        PullRequestCommentDecorationContext.syncInlineIndexFromWorktree(
            project = project,
            pullRequestId = currentBinding.pullRequestId
        )
        BbCloudPullRequestDiffCommentService.getInstance(project).refreshOpenEditors()
    }

    private fun pullRequestIdFromWorkTree(basePath: String): Long? {
        val folderName = Paths.get(basePath).fileName?.toString() ?: basePath
        return PullRequestWorktreeLayout.prNumberFromWorktreeFolderName(folderName)
    }

    private fun createOpenPullRequestService(configurationService: BbCloudConfigurationService): BbCloudOpenPullRequestService {
        val application = ApplicationManager.getApplication()
        return BbCloudOpenPullRequestService(
            credentialStore = application.service<BbCloudCredentialStore>(),
            pullRequestClient = OkHttpBitbucketPullRequestClient(),
            configurationService = configurationService
        )
    }

    override fun dispose() {
        // The message-bus connections and the coroutine scope are disposed with the service.
    }

    companion object {
        private val LOG = logger<BbCloudWorktreePullRequestContext>()

        /** The pause the reload waits for, so that a burst of repository changes resolves the context only once. */
        private val RELOAD_DEBOUNCE = 500.milliseconds

        fun getInstance(project: Project): BbCloudWorktreePullRequestContext {
            return project.service()
        }
    }
}

/**
 * The pull request a pull-request work tree is bound to.
 *
 * @param pullRequestId the number of the pull request.
 * @param sourceBranch the source branch of the pull request.
 * @param destinationBranch the destination branch of the pull request.
 * @param headRevision the commit the work tree is checked out on.
 * @param changedFilePaths the normalized project-relative paths the pull request changes.
 * @param fileLineCounts the per-file added/removed line counts keyed by normalized project-relative path.
 * @param fileRevisions the source-branch blob revisions keyed by normalized project-relative path.
 * @param currentUserAvatarUrl the avatar URL of the configured Bitbucket user, when available.
 * @param currentUserUuid the UUID of the configured Bitbucket user, when available.
 */
data class WorktreePullRequestBinding(
    val pullRequestId: Long,
    val sourceBranch: String,
    val destinationBranch: String,
    val headRevision: String,
    val changedFilePaths: Set<String>,
    val fileLineCounts: Map<String, FileLineCount>,
    val fileRevisions: Map<String, String> = emptyMap(),
    val currentUserAvatarUrl: String? = null,
    val currentUserUuid: String? = null
)