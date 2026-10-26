package com.github.fnunezkanut.bbcloud.ui.pullrequest.create

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposePanel
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudOpenPullRequestService
import com.github.fnunezkanut.bbcloud.bitbucket.CreatePullRequestResult
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.git.BbCloudBranchCheckoutService
import com.github.fnunezkanut.bbcloud.ui.BbCloudToolWindowPanel
import com.github.fnunezkanut.bbcloud.ui.main.ReviewerSearchPopup
import com.github.fnunezkanut.bbcloud.ui.main.ReviewerSearchRequest
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.jewel.bridge.compose
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** Owns the new pull request form state and the service, modal and navigation seams for one `new.pr` editor tab. */
class BbCloudNewPullRequestSession(
    private val project: Project,
    private val file: BbCloudNewPullRequestVirtualFile,
    openPullRequestService: BbCloudOpenPullRequestService,
    parentDisposable: Disposable,
    branchCheckoutService: BbCloudBranchCheckoutService = BbCloudBranchCheckoutService(),
    private val onEdtDispatcher: (() -> Unit) -> Unit = { action ->
        ApplicationManager.getApplication().invokeLater(action)
    }
) : Disposable {
    private val sessionDisposable = Disposer.newDisposable("BbCloudNewPullRequestSession")
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var disposed = false
    private var composePanel: ComposePanel? = null

    // The view model is declared before the Compose host so it is initialized first; the compose { } block captures it
    // by reference and is not invoked until the panel is shown.
    private val viewModel: NewPullRequestViewModel = NewPullRequestViewModel(
        coroutineScope = coroutineScope,
        loadBranches = {
            openPullRequestService.repositoryBranches(
                projectBasePath = project.basePath,
                organizationUrl = file.context.organizationUrl
            )
        },
        loadReviewerCandidates = {
            openPullRequestService.reviewerCandidates(
                projectBasePath = project.basePath,
                organizationUrl = file.context.organizationUrl
            )
        },
        loadCurrentUserUuid = {
            openPullRequestService.currentUserUuid(
                projectBasePath = project.basePath,
                organizationUrl = file.context.organizationUrl
            )
        },
        findOpenPullRequest = { source, destination ->
            openPullRequestService.findOpenPullRequest(
                projectBasePath = project.basePath,
                organizationUrl = file.context.organizationUrl,
                sourceBranch = source,
                destinationBranch = destination
            )
        },
        createPullRequest = { request ->
            openPullRequestService.createPullRequest(
                projectBasePath = project.basePath,
                organizationUrl = file.context.organizationUrl,
                request = request
            )
        },
        currentLocalBranch = file.context.currentLocalBranch,
        openReviewerSearch = { request -> openReviewerSearch(request) },
        loadWorkingTreeStatus = { branchCheckoutService.workingTreeStatus(project) },
        confirmExistingCheckRetry = { confirmExistingCheckRetry() },
        confirmDiscardDraft = { confirmDiscardDraft() },
        onCreated = { summary, localMainBranch -> onCreated(summary = summary, localMainBranch = localMainBranch) },
        onCreateFailed = { failure -> onCreateFailed(failure) },
        onCancel = { closeTab() },
        onOpenExistingPullRequest = { url -> BrowserUtil.browse(url) },
        onEdtDispatcher = { action -> invokeOnEdt(action) },
        genericFailureMessage = BbCloudBundle.message("newPr.create.failed.generic"),
        descriptionPlaceholder = BbCloudBundle.message("newPr.description.placeholder"),
        descriptionSubmitLabel = BbCloudBundle.message("newPr.description.submit"),
        descriptionSubmitHint = BbCloudBundle.message("newPr.description.submitHint"),
        descriptionFailureMessage = BbCloudBundle.message("newPr.description.failed")
    )

    // The ComposePanel must never be asked to size against unbounded height, or the VerticallyScrollableContainer
    // inside blows up; the empty preferred and minimum sizes force BorderLayout to give it the available area instead.
    private val composeComponent: JComponent = compose(config = { composePanel = this }) {
        NewPullRequestScreen(viewModel = viewModel, repositorySlug = file.context.repositorySlug)
    }
    private val composeHost = object : JPanel(BorderLayout()) {
        override fun getPreferredSize() = JBUI.emptySize()

        override fun getMinimumSize() = JBUI.emptySize()
    }.apply {
        isOpaque = false
        add(composeComponent, BorderLayout.CENTER)
    }

    val component: JPanel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(JBUI.scale(8))
        add(composeHost, BorderLayout.CENTER)
    }

    init {
        Disposer.register(parentDisposable, sessionDisposable)
        LOG.debug("Created new pull request session")
        viewModel.start()
    }

    /** Called from the file editor when its tab is (re)selected, so a commit made elsewhere clears the warning. */
    fun onEditorSelected() {
        viewModel.onEditorSelected()
    }

    private fun openReviewerSearch(request: ReviewerSearchRequest) {
        val popup = ReviewerSearchPopup(
            excludedUuids = request.excludedUuids,
            avatarCache = request.avatarCache,
            coroutineScope = request.coroutineScope,
            onSelected = request.onSelected
        )
        popup.show(anchor = composeComponent)
        request.loadCandidates { candidates -> popup.showCandidates(candidates) }
    }

    private fun confirmExistingCheckRetry(): Boolean {
        return runOnEdtAndWait {
            Messages.showYesNoDialog(
                project,
                BbCloudBundle.message("newPr.existing.failed.message"),
                BbCloudBundle.message("newPr.existing.failed.title"),
                BbCloudBundle.message("newPr.existing.failed.retry"),
                BbCloudBundle.message("newPr.existing.failed.continue"),
                Messages.getErrorIcon()
            ) == Messages.YES
        }
    }

    private fun confirmDiscardDraft(): Boolean {
        return runOnEdtAndWait {
            Messages.showYesNoDialog(
                project,
                BbCloudBundle.message("newPr.discard.message"),
                BbCloudBundle.message("newPr.discard.title"),
                BbCloudBundle.message("newPr.discard.confirm"),
                BbCloudBundle.message("newPr.discard.keep"),
                Messages.getWarningIcon()
            ) == Messages.YES
        }
    }

    private fun onCreated(summary: PullRequestSummary, localMainBranch: String?) {
        LOG.info("Created pull request #${summary.id} from the new pull request form")
        val handedOff = project.service<BbCloudToolWindowPanel>().openCreatedPullRequest(
            summary = summary,
            localMainBranch = localMainBranch
        )
        if (!handedOff) {
            LOG.info("The main panel is not bound; showing a notification for the created pull request #${summary.id}")
            val notification = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(
                    BbCloudBundle.message("newPr.created.title", summary.id),
                    "",
                    NotificationType.INFORMATION
                )
            notification.addAction(
                NotificationAction.createSimpleExpiring(BbCloudBundle.message("newPr.created.openInBitbucket")) {
                    BrowserUtil.browse(summary.webUrl)
                }
            )
            notification.notify(project)
        }
        closeTab()
    }

    private fun onCreateFailed(failure: CreatePullRequestResult.Failed) {
        LOG.warn("Could not create a pull request", failure.cause)
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                BbCloudBundle.message("newPr.create.failed.title"),
                failure.message ?: BbCloudBundle.message("newPr.create.failed.generic"),
                NotificationType.ERROR
            )
            .notify(project)
    }

    private fun closeTab() {
        invokeOnEdt {
            if (!project.isDisposed) {
                FileEditorManager.getInstance(project).closeFile(file)
            }
        }
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

    private fun <T> runOnEdtAndWait(action: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) {
            return action()
        }
        var result: T? = null
        ApplicationManager.getApplication().invokeAndWait {
            result = action()
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun dispose() {
        disposed = true
        coroutineScope.cancel()
        composePanel?.dispose()
        Disposer.dispose(sessionDisposable)
        LOG.debug("Disposed new pull request session")
    }

    companion object {
        private val LOG = logger<BbCloudNewPullRequestSession>()
        private const val NOTIFICATION_GROUP_ID = "BbCloud"
    }
}