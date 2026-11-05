package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestAction
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestDetails
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestOverview
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
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
    private val mentionSource: CommentMentionSource? = null,
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
        onRefreshRequested = onRefreshRequested
    )

    // The Activity timeline of the pull-request page is Jewel/Compose content.
    private val historyViewModel = PullRequestHistoryViewModel(pullRequestId = { displayedPullRequestId })

    // The comment composer and the comment threads of the Activity timeline are Compose content.
    private val conversationViewModel = PullRequestConversationViewModel(
        pullRequestId = { displayedPullRequestId },
        commentEditor = commentEditor,
        mentionSource = mentionSource
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
     * because there is no tab strip and the whole page is one Compose tree, and the info card is no longer pinned to a
     * width with `pinInfoCardWidth()`, because Compose lays the card out inside the real width of the column instead of
     * inside a Swing `BorderLayout` north band that only knew the preferred width of its children.
     */
    private val detailComposeComponent: JComponent = compose {
        CheckedOutBranchScreen(
            detailViewModel = detailViewModel,
            overviewViewModel = overviewViewModel,
            conversationViewModel = conversationViewModel,
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
        // Uniform 8px edge padding so the pull-request page content
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
        LOG.debug("Attaching pull request detail view for project ${project.name}")
        attachedProject = project
        Disposer.register(
            parentDisposable,
            Disposable {
                LOG.debug("Shutting down the pull request detail view for project ${project.name}")
                attachedProject = null
                historyViewModel.reset()
                conversationViewModel.reset()
            }
        )
    }

    /** Binds a pull request and clears the state of the previous one. */
    fun bind(
        repositoryWebUrl: String,
        branchName: String,
        summary: PullRequestSummary,
        organizationUrl: String?,
        workspace: String = "",
        repositorySlug: String = "",
        repositoryAvatarUrl: String? = null,
        coroutineScope: CoroutineScope
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
            // The approval state is only known once the pull request details load; showPullRequestDetails adds it.
            isApprovedByMe = false,
            coroutineScope = coroutineScope
        )
        headerViewModel.clearCommitsBehind()
        headerViewModel.clearLocalBehindRemote()
        detailViewModel.bind(pullRequestId = summary.id)
        overviewViewModel.reset()
        conversationViewModel.reset()
        historyViewModel.reset()
    }

    /** Updates the pull-request information card after an action without resetting the overview. */
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
            isApprovedByMe = details.isApprovedByCurrentUser,
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

    /** Shows the red warning that the local source and/or destination branch is behind Bitbucket. */
    fun showLocalBehindRemote(warning: HeaderLocalBehindRemote) {
        headerViewModel.showLocalBehindRemote(warning = warning)
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
        inPlaceRefresh: Boolean = false,
        revealNewestActivity: Boolean = false
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
        // The conversation goes first, so its host state (current user, mentions, file existence) is in place when the
        // timeline recomposes with the threads. Mentions still pending show a placeholder until the editor session applies them.
        conversationViewModel.bind(coroutineScope = coroutineScope)
        conversationViewModel.showConversation(
            conversation = overview.conversation,
            coroutineScope = coroutineScope,
            inPlaceRefresh = inPlaceRefresh,
            forceScrollToTop = revealNewestActivity
        )
        historyViewModel.bind(coroutineScope = coroutineScope)
        historyViewModel.showActivity(feed = overview.activityFeed, conversation = overview.conversation)
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

    /** The current user's uuid, as carried by the shown Activity conversation; null until it has loaded. */
    fun currentUserUuid(): String? {
        return conversationViewModel.currentUserUuid
    }

    /** Builds the top-level Activity comment composer, wired with the mention source. */
    fun newTopLevelComposer(): CommentComposerViewModel {
        return conversationViewModel.newTopLevelComposer()
    }

    /** The revision of the conversation shown last; pass it back to [applyMentionNames]. */
    fun conversationRevision(): Int {
        return conversationViewModel.conversationRevision()
    }

    fun applyMentionNames(revision: Int, names: Map<String, String>) {
        conversationViewModel.applyMentionNames(revision = revision, names = names)
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

    companion object {
        private val LOG = logger<CheckedOutBranchPanel>()
    }
}