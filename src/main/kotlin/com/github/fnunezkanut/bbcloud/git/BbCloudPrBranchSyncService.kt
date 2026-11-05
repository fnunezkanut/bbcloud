package com.github.fnunezkanut.bbcloud.git

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestReviewBarRefresh
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.messages.Topic
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException

/** Notified on the project message bus when the state of the pull-request branches changed. */
fun interface PrBranchesSyncedListener {
    fun branchesSynced(snapshot: PrBranchSyncSnapshot)
}

/**
 * Keeps the local source and destination branches of the pull request in a PR work tree window in line with Bitbucket.
 *
 * Every operation runs under one [Mutex], so an evaluation that arrives while a sync runs waits for it and never
 * reports a stale "behind" state. After an operation that moved a ref, or whenever the observed state changed, the
 * [PR_BRANCHES_SYNCED] topic is published so the red warning on the `.pr` page refreshes.
 */
@Service(Service.Level.PROJECT)
class BbCloudPrBranchSyncService(private val project: Project) {
    private val mutex = Mutex()
    private var engine: PrBranchSyncEngine = PrBranchSyncEngine()

    /** The last snapshot that was published, so a session can render immediately without another fetch. */
    @Volatile
    var lastSnapshot: PrBranchSyncSnapshot? = null
        private set

    /** Fetches and fast-forwards both branches when it is safe (the automatic triggers: window open and refresh). */
    suspend fun syncAutomatically(sourceBranch: String, destinationBranch: String, reason: String): PrBranchSyncResult? {
        LOG.debug("Syncing the pull request branches automatically ($reason)")
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                runSync(sourceBranch = sourceBranch, destinationBranch = destinationBranch, mode = PrBranchSyncMode.Automatic)
            }
        }
    }

    /** Fetches (when [fetch] is true) and inspects both branches without moving any ref. */
    suspend fun evaluate(sourceBranch: String, destinationBranch: String, fetch: Boolean): PrBranchSyncSnapshot? {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                runEvaluate(sourceBranch = sourceBranch, destinationBranch = destinationBranch, fetch = fetch)
            }
        }
    }

    /** The update link: brings every behind branch up to date, which may also touch other work trees. */
    suspend fun updateOnUserRequest(sourceBranch: String, destinationBranch: String): PrBranchSyncResult? {
        return mutex.withLock {
            withBackgroundProgress(project, BbCloudBundle.message("main.branch.localBehindRemote.progressTitle")) {
                withContext(Dispatchers.IO) {
                    runSync(sourceBranch = sourceBranch, destinationBranch = destinationBranch, mode = PrBranchSyncMode.UserRequested)
                }
            }
        }
    }

    private fun runSync(sourceBranch: String, destinationBranch: String, mode: PrBranchSyncMode): PrBranchSyncResult? {
        val context = resolveContext(sourceBranch = sourceBranch, destinationBranch = destinationBranch) ?: return null
        return try {
            val result = engine.sync(
                project = project,
                prWorktreePath = context.root,
                remoteName = context.remoteName,
                remoteUrl = context.remoteUrl,
                sourceBranch = sourceBranch,
                destinationBranch = destinationBranch,
                mode = mode
            )
            afterOperation(repository = context.repository, snapshot = result.after, anyRefMoved = result.outcome.anyRefMoved)
            result
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LOG.debug("Syncing the pull request branches failed unexpectedly", exception)
            null
        }
    }

    private fun runEvaluate(sourceBranch: String, destinationBranch: String, fetch: Boolean): PrBranchSyncSnapshot? {
        val context = resolveContext(sourceBranch = sourceBranch, destinationBranch = destinationBranch) ?: return null
        return try {
            if (fetch) {
                engine.fetch(
                    project = project,
                    prWorktreePath = context.root,
                    remoteName = context.remoteName,
                    remoteUrl = context.remoteUrl,
                    branches = listOf(sourceBranch, destinationBranch)
                )
                context.repository.update()
            }
            val snapshot = engine.inspect(
                project = project,
                prWorktreePath = context.root,
                remoteName = context.remoteName,
                sourceBranch = sourceBranch,
                destinationBranch = destinationBranch
            )
            afterOperation(repository = context.repository, snapshot = snapshot, anyRefMoved = false)
            snapshot
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LOG.debug("Evaluating the pull request branches failed unexpectedly", exception)
            null
        }
    }

    private fun afterOperation(repository: GitRepository, snapshot: PrBranchSyncSnapshot, anyRefMoved: Boolean) {
        if (anyRefMoved) {
            repository.update()
            VfsUtil.markDirtyAndRefresh(false, true, false, repository.root)
        }
        val changed = snapshot != lastSnapshot
        lastSnapshot = snapshot
        if (anyRefMoved || changed) {
            if (!project.isDisposed) {
                project.messageBus.syncPublisher(PR_BRANCHES_SYNCED).branchesSynced(snapshot)
            }
        }
    }

    private fun resolveContext(sourceBranch: String, destinationBranch: String): SyncContext? {
        if (sourceBranch.isBlank() || destinationBranch.isBlank()) {
            LOG.info("Not syncing the pull request branches: a branch name is blank")
            return null
        }
        if (!BbCloudPullRequestReviewBarRefresh.isPullRequestWorkTree(project.basePath)) {
            LOG.info("Not syncing the pull request branches: the project is not a pull-request work tree")
            return null
        }
        val repository = ProjectGitRepositoryLookup.awaitRepository(project)
        if (repository == null) {
            LOG.info("Not syncing the pull request branches: no Git repository for project ${project.name}")
            return null
        }
        val remote = ProjectGitRepositoryLookup.selectRemote(repository)
        return SyncContext(
            repository = repository,
            root = repository.root.toNioPath(),
            remoteName = remote?.name ?: GitRemote.ORIGIN,
            remoteUrl = remote?.urls?.firstOrNull()
        )
    }

    private data class SyncContext(val repository: GitRepository, val root: Path, val remoteName: String, val remoteUrl: String?)

    companion object {
        private val LOG = logger<BbCloudPrBranchSyncService>()

        @JvmField
        val PR_BRANCHES_SYNCED: Topic<PrBranchesSyncedListener> = Topic.create(
            "BbCloud pull request branches synced",
            PrBranchesSyncedListener::class.java
        )

        fun getInstance(project: Project): BbCloudPrBranchSyncService {
            return project.service()
        }

        /** Builds an instance with a stubbed [engine], for unit tests only. */
        internal fun forTesting(project: Project, engine: PrBranchSyncEngine): BbCloudPrBranchSyncService {
            return BbCloudPrBranchSyncService(project).apply {
                this.engine = engine
            }
        }
    }
}