package com.github.fnunezkanut.bbcloud.review

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryUrls
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestNotOpenCheck
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.git.BbCloudPullRequestBranchDiffService
import com.github.fnunezkanut.bbcloud.git.BranchDiffLineTotals
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeLayout
import com.github.fnunezkanut.bbcloud.ui.main.AuthorAvatarCache
import com.github.fnunezkanut.bbcloud.ui.main.HeaderLinesChanged
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestVirtualFile
import com.github.fnunezkanut.bbcloud.ui.pullrequest.projectView.BbCloudPullRequestVirtualFiles
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Paths
import javax.swing.Icon

/**
 * The data the worktree todo banner header renders.
 *
 * The slug and pull request id come from the work tree folder name and are stable for the lifetime of the project; the
 * branches, the web URL, and the avatar URL resolve later, from the work tree context binding or the pull request
 * editor's virtual file, so they are nullable until known.
 *
 * @param isWorktree whether the blue bar should render. It is true only when the project is a plugin-managed
 *   pull-request work tree whose pull request is proven Open (not merely not-proven-closed), and not proven closed. The
 *   banner renders nothing and skips line-total loads when false, which hides the bar on merged or declined pull
 *   requests and while the Bitbucket state is still unknown (decisions 2b, 3a, 6a).
 * @param repoSlug the repository slug recovered from the work tree folder name.
 * @param pullRequestId the pull request number recovered from the work tree folder name.
 * @param pullRequestWebUrl the Bitbucket pull request page URL, or null until a summary or URL builder resolves it.
 * @param sourceBranch the source branch of the pull request, or null while the binding loads.
 * @param destinationBranch the destination branch of the pull request, or null while the binding loads.
 * @param repositoryAvatarUrl the repository avatar URL, or null when unknown.
 * @param refreshEnabled whether the banner's refresh button is enabled.
 * @param linesChanged the whole-pull-request lines-changed indicator, or null while the totals are loading or
 *   unavailable; a null value renders no indicator (there is no loading placeholder).
 */
data class WorktreeTodoBannerUiState(
    val isWorktree: Boolean = false,
    val repoSlug: String = "",
    val pullRequestId: Long = 0L,
    val pullRequestWebUrl: String? = null,
    val sourceBranch: String? = null,
    val destinationBranch: String? = null,
    val repositoryAvatarUrl: String? = null,
    val refreshEnabled: Boolean = true,
    val avatarRevision: Int = 0,
    val linesChanged: HeaderLinesChanged? = null
)

/**
 * The inputs [WorktreeTodoBannerViewModel] reads from the live platform, gathered into a plain value so the state
 * resolution is pure and unit-testable.
 *
 * @param projectBasePath the project base path (the work tree folder).
 * @param organizationUrl the configured organization URL, or null.
 * @param binding the resolved work tree pull request binding, or null while it loads.
 * @param summary the pull request summary from the editor virtual file, or null when the editor was never opened.
 * @param repositoryAvatarUrl the repository avatar URL from the configuration cache, or null.
 * @param pullRequestKnownClosed whether a Bitbucket resolve proved the pull request is merged or declined; hides the
 *   blue bar even when no editor summary is available.
 * @param pullRequestKnownOpen whether a Bitbucket resolve proved the pull request is Open (including draft); shows the
 *   blue bar even when no binding has formed yet (decision 3a).
 */
data class WorktreeTodoBannerInputs(
    val projectBasePath: String?,
    val organizationUrl: String?,
    val binding: WorktreePullRequestBinding?,
    val summary: PullRequestSummary?,
    val repositoryAvatarUrl: String?,
    val pullRequestKnownClosed: Boolean = false,
    val pullRequestKnownOpen: Boolean = false
)

/**
 * Owns the state the worktree todo banner renders, as a project-level service shared by every editor's banner so they
 * all show the same header and refresh state.
 *
 * The slug and pull request id are derived from the work tree folder name on first use. The branches, the web URL, and
 * the repository avatar are merged from, in order of preference, the work tree [BbCloudWorktreePullRequestContext]
 * binding and then the pull request editor's virtual file, so the header fills in as those sources resolve.
 * [BbCloudWorktreePullRequestContext] notifies this view model when the binding changes, so the branch chips stay in
 * sync without polling.
 */
@Service(Service.Level.PROJECT)
class WorktreeTodoBannerViewModel(private val project: Project, private val coroutineScope: CoroutineScope) {
    val state: MutableState<WorktreeTodoBannerUiState> = mutableStateOf(WorktreeTodoBannerUiState())

    private val avatarCache = AuthorAvatarCache(avatarSize = AVATAR_SIZE_PX)
    private var initialized = false

    /**
     * The branch pair a line-totals load is in flight for, or already loaded, so a later [refreshFromSources] that sees
     * the same pull request and branches does not spawn a duplicate Git diff (see the session's parallel load). Cleared
     * whenever the pull request or the branches change.
     */
    private var lineTotalsLoadKey: String? = null

    /** Resolves the slug and pull request id from the work tree, then merges the branch and URL sources. */
    fun ensureInitialized() {
        if (!initialized) {
            initialized = true
            LOG.debug("Initializing the worktree banner view model for ${project.basePath}")
        }
        refreshFromSources()
    }

    /** Re-reads the branch and URL sources; called on first use and whenever the work tree binding changes. */
    fun refreshFromSources() {
        val previous = state.value
        val inputs = gatherInputs()
        val resolved = resolveState(
            inputs = inputs,
            previous = previous
        )
        state.value = resolved
        if (shouldRefreshEditorNotifications(previousIsWorktree = previous.isWorktree, nextIsWorktree = resolved.isWorktree)) {
            refreshEditorNotifications()
        }
        if (!resolved.isWorktree) {
            return
        }
        prefetchRepositoryAvatar(avatarUrl = resolved.repositoryAvatarUrl)
        reconcileLineTotals(previous = previous, resolved = resolved)
    }

    /**
     * Re-queries the editor notification providers, so an editor that was opened before the state resolved shows the
     * blue bar, and an editor that was opened while the pull request was open hides it once the state changes.
     */
    private fun refreshEditorNotifications() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }
            LOG.debug("Refreshing editor notifications after the blue bar state changed")
            EditorNotifications.getInstance(project).updateAllNotifications()
        }
    }

    fun setRefreshEnabled(enabled: Boolean) {
        if (state.value.refreshEnabled == enabled) {
            return
        }
        state.value = state.value.copy(refreshEnabled = enabled)
    }

    /** Sets the lines-changed indicator from [totals]; a null value clears it (decision 3a: no placeholder). */
    fun showLineTotals(totals: BranchDiffLineTotals?) {
        if (totals == null) {
            clearLineTotals()
            return
        }
        state.value = state.value.copy(linesChanged = HeaderLinesChanged.fromTotals(totals))
    }

    /** Clears the lines-changed indicator when it is currently showing. */
    fun clearLineTotals() {
        if (state.value.linesChanged != null) {
            state.value = state.value.copy(linesChanged = null)
        }
    }

    /**
     * Keeps the lines-changed indicator in step with the resolved branches: when the pull request or the branch pair
     * changed, the previously loaded totals no longer apply, so they are cleared and a fresh load is scheduled once
     * both branches are known. While either branch is still null the chips render a placeholder and no load runs.
     */
    private fun reconcileLineTotals(previous: WorktreeTodoBannerUiState, resolved: WorktreeTodoBannerUiState) {
        val pullRequestChanged = previous.pullRequestId != resolved.pullRequestId
        val branchesChanged = previous.sourceBranch != resolved.sourceBranch ||
            previous.destinationBranch != resolved.destinationBranch
        if (pullRequestChanged || branchesChanged) {
            lineTotalsLoadKey = null
            clearLineTotals()
        }
        val sourceBranch = resolved.sourceBranch ?: return
        val destinationBranch = resolved.destinationBranch ?: return
        loadLineTotals(
            pullRequestId = resolved.pullRequestId,
            destinationBranch = destinationBranch,
            sourceBranch = sourceBranch
        )
    }

    /**
     * Loads whole-pull-request line totals off the EDT and applies them back on the EDT, deduplicating against the
     * branch pair already in flight or loaded so the session's own load and this eager load never run the same Git
     * diff twice. Stale results are dropped when the pull request or branches changed while the load ran.
     */
    private fun loadLineTotals(pullRequestId: Long, destinationBranch: String, sourceBranch: String) {
        val loadKey = "$pullRequestId:$destinationBranch:$sourceBranch"
        if (lineTotalsLoadKey == loadKey) {
            return
        }
        lineTotalsLoadKey = loadKey
        LOG.debug("Loading pull request line totals for the banner of #$pullRequestId")
        coroutineScope.launch {
            val totals = try {
                withContext(Dispatchers.IO) {
                    BbCloudPullRequestBranchDiffService().loadPullRequestLineTotals(
                        project = project,
                        destinationBranch = destinationBranch,
                        sourceBranch = sourceBranch
                    )
                }
            } catch (exception: Exception) {
                invokeOnEdt {
                    if (lineTotalsLoadKey == loadKey) {
                        lineTotalsLoadKey = null
                    }
                    LOG.warn("Failed to load line totals for the banner of pull request #$pullRequestId", exception)
                }
                return@launch
            }
            invokeOnEdt {
                val current = state.value
                if (!current.isWorktree ||
                    current.pullRequestId != pullRequestId ||
                    current.destinationBranch != destinationBranch ||
                    current.sourceBranch != sourceBranch
                ) {
                    LOG.debug("Dropping stale banner line totals for pull request #$pullRequestId")
                    return@invokeOnEdt
                }
                LOG.debug("Loaded banner line totals for pull request #$pullRequestId")
                showLineTotals(totals)
            }
        }
    }

    private fun invokeOnEdt(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                action()
            }
        }
    }

    /** Resolves the repository avatar icon, falling back to the project placeholder while it loads. */
    fun avatarIconFor(url: String?): Icon {
        return avatarCache.iconFor(url = url, fallback = avatarCache.repositoryPlaceholderIcon())
    }

    private fun gatherInputs(): WorktreeTodoBannerInputs {
        val configurationService = BbCloudConfigurationService.getInstance()
        val basePath = project.basePath
        val summary = runCatching {
            BbCloudPullRequestVirtualFiles.forWorktreeProject(project)
                ?.getUserData(BbCloudPullRequestVirtualFile.PULL_REQUEST_SUMMARY_KEY)
        }.getOrNull()
        val repoSlug = basePath
            ?.let { path -> Paths.get(path).fileName?.toString() }
            ?.let { folderName -> PullRequestWorktreeLayout.repoSlugFromWorktreeFolderName(folderName) }
        val repositoryAvatarUrl = repoSlug?.let { slug ->
            configurationService.repoAvatarUrlsForCurrentWorkspace()[slug]
        }
        val context = BbCloudWorktreePullRequestContext.getInstance(project)
        return WorktreeTodoBannerInputs(
            projectBasePath = basePath,
            organizationUrl = configurationService.organizationUrlOrNull(),
            binding = context.binding,
            summary = summary,
            repositoryAvatarUrl = repositoryAvatarUrl,
            pullRequestKnownClosed = context.pullRequestKnownClosed,
            pullRequestKnownOpen = context.pullRequestKnownOpen
        )
    }

    private fun prefetchRepositoryAvatar(avatarUrl: String?) {
        val url = avatarUrl ?: return
        avatarCache.prefetch(
            urls = listOf(url),
            coroutineScope = coroutineScope,
            onLoaded = {
                if (state.value.isWorktree && state.value.repositoryAvatarUrl == url) {
                    state.value = state.value.copy(avatarRevision = state.value.avatarRevision + 1)
                }
            }
        )
    }

    companion object {
        private val LOG = logger<WorktreeTodoBannerViewModel>()

        /** The repository avatar is rendered at 16 dp beside the slug, so the cache decodes at 16 px. */
        private const val AVATAR_SIZE_PX = 16

        fun getInstance(project: Project): WorktreeTodoBannerViewModel {
            return project.service()
        }

        /**
         * Resolves the banner state from [inputs], preserving the refresh-enabled flag, the avatar revision, and the
         * lines-changed indicator from [previous]. Pure and free of platform singletons so the merge rules can be
         * unit-tested directly. Because branch and URL refreshes preserve the line totals, callers reload them
         * separately only when the pull request or the branch pair actually changed (see `reconcileLineTotals`).
         *
         * Slug and pull request id come from the work tree folder name. When either cannot be parsed the project is not
         * a pull-request work tree and the resulting state has `isWorktree = false`. Branches prefer the binding over
         * the virtual file summary; the source branch falls back to the summary when only it is known. The web URL
         * prefers the summary's own URL and otherwise builds `…/pull-requests/{id}` from the organization URL.
         *
         * The blue bar is visible (`isWorktree = true`) only when the folder parses, the pull request is proven Open,
         * and it is not proven closed:
         * - proven closed (an Open summary gone merged/declined on the virtual file, or `pullRequestKnownClosed`) hides
         *   the bar and clears the stale `linesChanged` carried from `previous`;
         * - proven Open (an Open summary on the virtual file, or `pullRequestKnownOpen`) shows the bar, even when no
         *   binding has formed (decision 3a);
         * - an otherwise-unknown state (folder parses but neither Open nor closed is proven) keeps the bar hidden, so
         *   a merged work tree does not flash the bar before the first Bitbucket resolve (decisions 2b, 6a).
         */
        fun resolveState(inputs: WorktreeTodoBannerInputs, previous: WorktreeTodoBannerUiState): WorktreeTodoBannerUiState {
            val folderName = inputs.projectBasePath
                ?.let { path -> Paths.get(path).fileName?.toString() }
            val repoSlug = folderName?.let { PullRequestWorktreeLayout.repoSlugFromWorktreeFolderName(it) }
            val pullRequestId = folderName?.let { PullRequestWorktreeLayout.prNumberFromWorktreeFolderName(it) }
            if (repoSlug == null || pullRequestId == null) {
                return WorktreeTodoBannerUiState(
                    isWorktree = false,
                    refreshEnabled = previous.refreshEnabled
                )
            }
            val summaryProvenNonOpen = inputs.summary?.let { PullRequestNotOpenCheck.check(it) } == true
            val provenNonOpen = summaryProvenNonOpen || inputs.pullRequestKnownClosed
            val summaryProvenOpen = inputs.summary?.let { !PullRequestNotOpenCheck.check(it) } == true
            val provenOpen = summaryProvenOpen || inputs.pullRequestKnownOpen
            if (provenNonOpen || !provenOpen) {
                if (provenNonOpen) {
                    LOG.info("Hiding the blue bar: pull request #$pullRequestId is not open")
                }
                // Hidden: either proven closed or not yet proven Open. Clear the stale line totals so a later show of
                // the bar does not flash the previous pull request's numbers.
                return WorktreeTodoBannerUiState(
                    isWorktree = false,
                    repoSlug = repoSlug,
                    pullRequestId = pullRequestId,
                    refreshEnabled = previous.refreshEnabled,
                    avatarRevision = previous.avatarRevision,
                    linesChanged = null
                )
            }
            val sourceBranch = inputs.binding?.sourceBranch
                ?: inputs.summary?.sourceBranch
            val destinationBranch = inputs.binding?.destinationBranch
                ?: inputs.summary?.destinationBranch
            val webUrl = inputs.summary?.webUrl?.takeIf { it.isNotBlank() }
                ?: inputs.organizationUrl?.let { url ->
                    BitbucketRepositoryUrls.pullRequestWebUrl(
                        organizationUrl = url,
                        repoSlug = repoSlug,
                        pullRequestId = pullRequestId
                    )
                }
            return WorktreeTodoBannerUiState(
                isWorktree = true,
                repoSlug = repoSlug,
                pullRequestId = pullRequestId,
                pullRequestWebUrl = webUrl,
                sourceBranch = sourceBranch,
                destinationBranch = destinationBranch,
                repositoryAvatarUrl = inputs.repositoryAvatarUrl,
                refreshEnabled = previous.refreshEnabled,
                avatarRevision = previous.avatarRevision,
                linesChanged = previous.linesChanged
            )
        }
    }
}