package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.ItemCount
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestAction
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestCounts
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestDetails
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestFileCommentCounts
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestOverview
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.git.BranchDiffResult
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.jewel.bridge.compose
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Hosts the pull-request detail view inside a pull-request editor session.
 *
 * The view itself is one Compose tree (see [CheckedOutBranchScreen]); this class only keeps the state the session
 * pushes into it and forwards the user actions back.
 */
class CheckedOutBranchPanel(
    private val configurationService: BbCloudConfigurationService,
    private val coroutineScope: CoroutineScope,
    private val onRefreshRequested: () -> Unit,
    onActionRequested: (PullRequestAction) -> Unit,
    reviewerEditor: ReviewerEditor,
    taskEditor: TaskEditor,
    descriptionEditor: DescriptionEditor,
    commentEditor: CommentEditor,
    private val onChangesTabSelected: () -> Unit,
    private val onFilesChangedRefreshRequested: () -> Unit,
    private val onUpdateLocalBranchRequested: () -> Unit = {}
) {
    private var attachedProject: Project? = null
    private var displayedPullRequestId: Long? = null
    private var displayedPullRequestState: PullRequestState? = null
    private var infoRepositoryWebUrl = ""
    private var infoWorkspace = ""
    private var infoRepositorySlug = ""
    private var infoRepositoryAvatarUrl: String? = null

    private val overviewViewModel = PullRequestOverviewViewModel(
        reviewerEditor = reviewerEditor,
        taskEditor = taskEditor,
        descriptionEditor = descriptionEditor,
        openSearchPopup = { request -> openReviewerSearchPopup(request) }
    )
    private val headerViewModel = PullRequestHeaderViewModel()
    private val infoViewModel = PullRequestInfoViewModel()
    private val detailViewModel = PullRequestDetailViewModel(
        onActionRequested = onActionRequested,
        onRefreshRequested = onRefreshRequested,
        onConversationTabSelected = { conversationViewModel.onConversationTabShown() },
        onChangesTabSelected = onChangesTabSelected
    )

    // The Changes tab is entirely Jewel/Compose: the changed-file tree and the History feed are Compose content.
    private val changesViewModel = PullRequestChangesViewModel(
        pullRequestId = { displayedPullRequestId },
        projectBasePath = { attachedProject?.basePath },
        onRefreshRequested = onFilesChangedRefreshRequested,
        onOpenChange = { change -> openChangedFile(change = change) }
    )
    private val historyViewModel = PullRequestHistoryViewModel(pullRequestId = { displayedPullRequestId })

    // The Conversation tab is entirely Jewel/Compose: the comment composer and the comment threads are Compose content.
    private val conversationViewModel = PullRequestConversationViewModel(
        pullRequestId = { displayedPullRequestId },
        commentEditor = commentEditor
    )

    /**
     * The compose host is wrapped in a BorderLayout panel so the ComposePanel fills a bounded area rather than being
     * sized against an unbounded height.
     *
     * The editor session adds this panel to a BorderLayout, and BorderLayout.preferredLayoutSize forwards straight to
     * the CENTER child, which would trigger ComposePanel.getPreferredSize. That actualizes the Compose content
     * against an unbounded maximum height and blows up the VerticallyScrollableContainer inside the tab content. We
     * short-circuit the wrapper's preferred/minimum size so the ComposePanel is never asked to actualize itself
     * outside a real (bounded) layout pass; the tab area still gives the panel its full bounds via setBounds, and the
     * ComposePanel fills that bounded space.
     *
     * The Compose tree no longer needs the other two workarounds the Swing shell needed: the `JBTabbedPane` is gone,
     * because the tab strip and the tab content are one Compose tree now, and the info card is no longer pinned to a
     * width with `pinInfoCardWidth()`, because Compose lays the card out inside the real width of the column instead of
     * inside a Swing `BorderLayout` north band that only knew the preferred width of its children.
     */
    private val detailComposeComponent: JComponent = compose {
        CheckedOutBranchScreen(
            detailViewModel = detailViewModel,
            overviewViewModel = overviewViewModel,
            conversationViewModel = conversationViewModel,
            changesViewModel = changesViewModel,
            historyViewModel = historyViewModel,
            infoViewModel = infoViewModel,
            headerViewModel = headerViewModel,
            configurationService = configurationService,
            coroutineScope = coroutineScope,
            onUpdateLocalBranch = onUpdateLocalBranchRequested
        )
    }

    private val detailHost = object : JPanel(BorderLayout()) {
        override fun getPreferredSize() = JBUI.emptySize()

        override fun getMinimumSize() = JBUI.emptySize()
    }.apply {
        isOpaque = false
        add(detailComposeComponent, BorderLayout.CENTER)
    }

    val component: JPanel = JPanel(BorderLayout()).apply {
        // Uniform 8px edge padding so every tab (Overview, Conversation, Changes)
        // gets the same gap from the editor tab edges (decision A1).
        border = JBUI.Borders.empty(JBUI.scale(8))
        add(detailHost, BorderLayout.CENTER)
    }

    private fun openReviewerSearchPopup(request: ReviewerSearchRequest) {
        val popup = ReviewerSearchPopup(
            excludedUuids = request.excludedUuids,
            avatarCache = request.avatarCache,
            coroutineScope = request.coroutineScope,
            onSelected = request.onSelected
        )
        popup.show(anchor = detailComposeComponent)
        request.loadCandidates { candidates -> popup.showCandidates(candidates) }
    }

    fun attachProject(project: Project, parentDisposable: Disposable) {
        if (attachedProject != null) {
            return
        }
        LOG.debug("Attaching pull request changes tab for project ${project.name}")
        attachedProject = project
        Disposer.register(
            parentDisposable,
            Disposable {
                LOG.debug("Shutting down the pull request changes tab for project ${project.name}")
                attachedProject = null
                changesViewModel.reset()
                historyViewModel.reset()
                conversationViewModel.reset()
            }
        )
    }

    /** The tab currently selected in the pull-request detail view. */
    fun selectedTab(): PullRequestDetailTab {
        return detailViewModel.state.value.selectedTab
    }

    /** Binds a pull request; a new pull request resets the selected tab, while a header refresh preserves it. */
    fun bind(
        repositoryWebUrl: String,
        branchName: String,
        summary: PullRequestSummary,
        organizationUrl: String?,
        workspace: String = "",
        repositorySlug: String = "",
        repositoryAvatarUrl: String? = null,
        coroutineScope: CoroutineScope,
        resetSelectedTab: Boolean = true
    ) {
        infoRepositoryWebUrl = repositoryWebUrl
        infoWorkspace = workspace
        infoRepositorySlug = repositorySlug
        infoRepositoryAvatarUrl = repositoryAvatarUrl
        infoViewModel.reset()
        displayedPullRequestId = summary.id
        displayedPullRequestState = summary.state
        infoViewModel.bind(
            summary = summary,
            repositoryWebUrl = repositoryWebUrl,
            organizationUrl = organizationUrl,
            workspace = workspace,
            repositorySlug = repositorySlug,
            repositoryAvatarUrl = repositoryAvatarUrl,
            coroutineScope = coroutineScope
        )
        headerViewModel.clearCommitsBehind()
        headerViewModel.clearLocalBehindRemote()
        detailViewModel.bind(pullRequestId = summary.id)
        if (resetSelectedTab) {
            detailViewModel.resetTab()
        }
        detailViewModel.setConversationCount(
            if (summary.commentCount > 0) {
                ItemCount(value = summary.commentCount, isTruncated = false)
            } else {
                null
            }
        )
        detailViewModel.setChangesCount(null)
        overviewViewModel.reset()
        conversationViewModel.reset()
        changesViewModel.reset()
        historyViewModel.reset()
    }

    /** Updates the pull-request information card after an action without resetting the selected tab or the overview. */
    fun showPullRequestDetails(details: PullRequestDetails, organizationUrl: String?, coroutineScope: CoroutineScope) {
        displayedPullRequestId = details.summary.id
        displayedPullRequestState = details.summary.state
        infoViewModel.bind(
            summary = details.summary,
            repositoryWebUrl = infoRepositoryWebUrl,
            organizationUrl = organizationUrl,
            workspace = infoWorkspace,
            repositorySlug = infoRepositorySlug,
            repositoryAvatarUrl = infoRepositoryAvatarUrl,
            coroutineScope = coroutineScope
        )
        headerViewModel.clearCommitsBehind()
        detailViewModel.bind(pullRequestId = details.summary.id)
        detailViewModel.showActions(details)
    }

    fun showActions(details: PullRequestDetails) {
        detailViewModel.showActions(details)
    }

    fun setMergeChecksState(pullRequestId: Long, state: MergeChecksState) {
        detailViewModel.setMergeChecksState(pullRequestId = pullRequestId, state = state)
    }

    fun setActionsEnabled(enabled: Boolean) {
        detailViewModel.setActionsEnabled(enabled)
    }

    fun setRefreshEnabled(enabled: Boolean) {
        detailViewModel.setRefreshEnabled(enabled)
    }

    fun showCounts(counts: PullRequestCounts) {
        detailViewModel.setChangesCount(counts.filesChanged)
    }

    /** Shows the red local-behind-remote banner for [commitsBehind] commits (decision 10a + count). */
    fun showLocalBehindRemote(commitsBehind: Int, isTruncated: Boolean = false) {
        headerViewModel.showLocalBehindRemote(commitsBehind = commitsBehind, isTruncated = isTruncated)
    }

    /** Hides the red local-behind-remote banner (decisions 3b/5a: branch mismatch, check failure, or up to date). */
    fun clearLocalBehindRemote() {
        headerViewModel.clearLocalBehindRemote()
    }

    /** Disables the update link while a pull or a page reload runs (decision 7a). */
    fun setLocalBehindRemoteUpdateInProgress(inProgress: Boolean) {
        headerViewModel.setLocalBehindRemoteUpdateInProgress(inProgress)
    }

    fun showOverview(
        pullRequestId: Long,
        overview: PullRequestOverview,
        destinationBranch: String,
        coroutineScope: CoroutineScope,
        inPlaceRefresh: Boolean = false
    ) {
        overviewViewModel.showOverview(
            pullRequestId = pullRequestId,
            overview = overview
        )
        // Unknown keeps Merge disabled until this overview supplies a loaded or failed merge-check result.
        val mergeChecksState = overview.mergeChecks?.let { checks ->
            if (MergeCheckSummary.of(checks).isBlocking) {
                MergeChecksState.Blocked
            } else {
                MergeChecksState.NotBlocked
            }
        } ?: MergeChecksState.NotBlocked
        LOG.debug("Overview reports mergeChecksState=$mergeChecksState for pull request #$pullRequestId")
        detailViewModel.setMergeChecksState(pullRequestId = pullRequestId, state = mergeChecksState)
        historyViewModel.bind(coroutineScope = coroutineScope)
        historyViewModel.showHistoryFeed(feed = overview.activityFeed)
        conversationViewModel.bind(coroutineScope = coroutineScope)
        conversationViewModel.showConversation(
            conversation = overview.conversation,
            coroutineScope = coroutineScope,
            inPlaceRefresh = inPlaceRefresh
        )
        val state = displayedPullRequestState
        if (state != null) {
            headerViewModel.showCommitsBehind(
                state = state,
                commitsBehind = overview.commitsBehind,
                destinationBranch = destinationBranch
            )
        } else {
            headerViewModel.clearCommitsBehind()
        }
    }

    fun showReviewers(details: PullRequestDetails, coroutineScope: CoroutineScope) {
        displayedPullRequestState = details.summary.state
        overviewViewModel.showReviewers(details = details, coroutineScope = coroutineScope)
    }

    fun setReviewersUpdating(updating: Boolean) {
        overviewViewModel.setReviewersUpdating(updating)
    }

    fun setTasksUpdating(updating: Boolean) {
        overviewViewModel.setTasksUpdating(updating)
    }

    fun showFilesChangedLoading() {
        changesViewModel.showLoading()
    }

    fun showFilesChangedResult(result: BranchDiffResult) {
        changesViewModel.showResult(result)
    }

    fun setFilesChangedCommentCounts(counts: PullRequestFileCommentCounts?) {
        changesViewModel.setCommentCounts(counts)
    }

    private fun openChangedFile(change: Change) {
        val project = attachedProject
        if (project == null) {
            LOG.info("Cannot open the diff of a changed file: the project is not attached yet")
            return
        }
        PullRequestChangeDiffOpener.open(project = project, change = change)
    }

    companion object {
        private val LOG = logger<CheckedOutBranchPanel>()
    }
}