package com.github.fnunezkanut.bbcloud.ui.pullrequest.create

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.github.fnunezkanut.bbcloud.bitbucket.CreatePullRequestResult
import com.github.fnunezkanut.bbcloud.bitbucket.ExistingPullRequestLookupResult
import com.github.fnunezkanut.bbcloud.bitbucket.NewPullRequest
import com.github.fnunezkanut.bbcloud.bitbucket.NewPullRequestBranchData
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerCandidates
import com.github.fnunezkanut.bbcloud.git.WorkingTreeStatus
import com.github.fnunezkanut.bbcloud.ui.main.AuthorAvatarCache
import com.github.fnunezkanut.bbcloud.ui.main.CommentComposerViewModel
import com.github.fnunezkanut.bbcloud.ui.main.CommentInput
import com.github.fnunezkanut.bbcloud.ui.main.ReviewerSearchRequest
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the state and the user interactions of the new pull request form. The service, modal, and navigation seams are
 * injected as lambdas so the view model is unit-testable without IntelliJ services; [CommentComposerViewModel] holds the
 * Markdown description draft.
 */
class NewPullRequestViewModel(
    private val coroutineScope: CoroutineScope,
    private val loadBranches: suspend () -> NewPullRequestBranchData?,
    private val loadReviewerCandidates: suspend () -> ReviewerCandidates?,
    private val loadCurrentUserUuid: suspend () -> String?,
    private val findOpenPullRequest: suspend (source: String, destination: String) -> ExistingPullRequestLookupResult,
    private val createPullRequest: suspend (NewPullRequest) -> CreatePullRequestResult,
    private val currentLocalBranch: String?,
    private val openReviewerSearch: (ReviewerSearchRequest) -> Unit,
    private val loadWorkingTreeStatus: suspend () -> WorkingTreeStatus?,
    private val confirmExistingCheckRetry: () -> Boolean,
    private val confirmDiscardDraft: () -> Boolean,
    private val onCreated: (summary: PullRequestSummary, localMainBranch: String?) -> Unit,
    private val onCreateFailed: (CreatePullRequestResult.Failed) -> Unit,
    private val onCancel: () -> Unit,
    private val onOpenExistingPullRequest: (String) -> Unit,
    private val onEdtDispatcher: (() -> Unit) -> Unit,
    private val genericFailureMessage: String,
    private val descriptionPlaceholder: String,
    private val descriptionSubmitLabel: String,
    private val descriptionSubmitHint: String,
    private val descriptionFailureMessage: String,
    private val avatarCache: AuthorAvatarCache = AuthorAvatarCache(avatarSize = 20),
    private val popupAvatarCache: AuthorAvatarCache = AuthorAvatarCache(),
    // Injectable so tests can remove the debounce; the UI keeps the default.
    private val existingCheckDebounceMs: Long = EXISTING_CHECK_DEBOUNCE_MS
) {
    val state: MutableState<NewPullRequestUiState> = mutableStateOf(NewPullRequestUiState())

    /** The Markdown description editor, reused from the comment composer, expanded with no submit button. */
    val descriptionComposer: CommentComposerViewModel = CommentComposerViewModel(
        input = CommentInput(
            label = "new pull request description",
            placeholder = descriptionPlaceholder,
            submitLabel = descriptionSubmitLabel,
            submitHint = descriptionSubmitHint,
            failureMessage = descriptionFailureMessage,
            initialText = "",
            // The form has no composer submit button, so this is never invoked.
            submit = { _, onResult -> onResult(false) }
        ),
        startsExpanded = true
    )

    private var branchNames: List<String> = emptyList()
    private var mainBranchName: String? = null
    private var currentUserUuid: String? = null
    private var reviewerCandidates: ReviewerCandidates? = null
    private var existingCheckJob: Job? = null
    private var workingTreeJob: Job? = null
    private var lastFailurePromptKey: String? = null

    /** Loads the branches, the current user uuid, and the reviewer candidates in parallel. */
    fun start() {
        state.value = state.value.copy(branchesLoad = BranchesLoad.Loading, isReviewersLoading = true)
        loadBranchesInternal()
        loadReviewersAndCurrentUser()
    }

    private fun loadBranchesInternal() {
        coroutineScope.launch {
            val result = loadBranches()
            onEdtDispatcher {
                if (result == null) {
                    state.value = state.value.copy(branchesLoad = BranchesLoad.Failed)
                    return@onEdtDispatcher
                }
                applyBranchData(result)
            }
        }
    }

    private fun applyBranchData(data: NewPullRequestBranchData) {
        branchNames = data.branches.names
        mainBranchName = data.mainBranchName
        val destinationOptions = NewPullRequestBranchOptions.destinationOptions(branches = branchNames, mainBranchName = mainBranchName)
        val defaultDestination = NewPullRequestBranchOptions.defaultDestination(branches = branchNames, mainBranchName = mainBranchName)
        val sourceOptions = NewPullRequestBranchOptions.sourceOptions(
            branches = branchNames,
            mainBranchName = mainBranchName,
            destinationBranch = defaultDestination
        )
        val defaultSource = NewPullRequestBranchOptions.defaultSource(options = sourceOptions, currentLocalBranch = currentLocalBranch)
        state.value = state.value.copy(
            branchesLoad = BranchesLoad.Loaded,
            destinationOptions = destinationOptions,
            selectedDestination = defaultDestination,
            sourceOptions = sourceOptions,
            selectedSource = defaultSource,
            isBranchListTruncated = data.branches.isTruncated
        )
        scheduleExistingCheck()
        recheckWorkingTree()
    }

    private fun loadReviewersAndCurrentUser() {
        coroutineScope.launch {
            val uuid = loadCurrentUserUuid()
            val candidates = loadReviewerCandidates()
            onEdtDispatcher {
                currentUserUuid = uuid
                reviewerCandidates = candidates
                if (candidates != null) {
                    val defaults = candidates.members.filter { member ->
                        member.uuid in candidates.defaultReviewerUuids && member.uuid != uuid
                    }
                    val rows = defaults.map { member ->
                        NewPullRequestReviewerRow(uuid = member.uuid, displayName = member.displayName, avatarUrl = member.avatarUrl)
                    }
                    state.value = state.value.copy(reviewers = rows, isReviewersLoading = false)
                    prefetchReviewerAvatars(rows)
                } else {
                    state.value = state.value.copy(isReviewersLoading = false)
                }
            }
        }
    }

    private fun prefetchReviewerAvatars(rows: List<NewPullRequestReviewerRow>) {
        val urls = rows.mapNotNull { row -> row.avatarUrl }
        if (urls.isEmpty()) {
            return
        }
        avatarCache.prefetch(urls = urls, coroutineScope = coroutineScope) {
            state.value = state.value.copy(avatarRevision = state.value.avatarRevision + 1)
        }
    }

    fun onRetryBranchesClicked() {
        LOG.info("User retried loading the branches in the new pull request form")
        state.value = state.value.copy(branchesLoad = BranchesLoad.Loading)
        loadBranchesInternal()
    }

    fun onTitleChanged(value: String) {
        state.value = state.value.copy(title = value)
    }

    fun onSourceSelected(index: Int) {
        LOG.info("User selected a source branch in the new pull request form")
        val source = state.value.sourceOptions.getOrNull(index) ?: return
        state.value = state.value.copy(selectedSource = source)
        scheduleExistingCheck()
    }

    fun onDestinationSelected(index: Int) {
        LOG.info("User selected a destination branch in the new pull request form")
        val destination = state.value.destinationOptions.getOrNull(index) ?: return
        val sourceOptions = NewPullRequestBranchOptions.sourceOptions(
            branches = branchNames,
            mainBranchName = mainBranchName,
            destinationBranch = destination
        )
        val reconciledSource = NewPullRequestBranchOptions.reconcileSource(
            selectedSource = state.value.selectedSource,
            options = sourceOptions,
            currentLocalBranch = currentLocalBranch
        )
        state.value = state.value.copy(
            selectedDestination = destination,
            sourceOptions = sourceOptions,
            selectedSource = reconciledSource
        )
        scheduleExistingCheck()
    }

    private fun scheduleExistingCheck() {
        existingCheckJob?.cancel()
        val source = state.value.selectedSource
        val destination = state.value.selectedDestination
        if (source == null || destination == null) {
            state.value = state.value.copy(existingPullRequest = ExistingPullRequestCheck.Idle)
            return
        }
        state.value = state.value.copy(existingPullRequest = ExistingPullRequestCheck.Checking)
        existingCheckJob = coroutineScope.launch {
            delay(existingCheckDebounceMs)
            val result = findOpenPullRequest(source, destination)
            onEdtDispatcher {
                // Ignore a stale result: the selection changed since this request was launched.
                if (state.value.selectedSource != source || state.value.selectedDestination != destination) {
                    return@onEdtDispatcher
                }
                applyExistingCheckResult(result = result, source = source, destination = destination)
            }
        }
    }

    private fun applyExistingCheckResult(result: ExistingPullRequestLookupResult, source: String, destination: String) {
        when (result) {
            is ExistingPullRequestLookupResult.Found -> {
                state.value = state.value.copy(
                    existingPullRequest = ExistingPullRequestCheck.Found(
                        pullRequestId = result.summary.id,
                        webUrl = result.summary.webUrl
                    )
                )
                lastFailurePromptKey = null
            }
            ExistingPullRequestLookupResult.None -> {
                state.value = state.value.copy(existingPullRequest = ExistingPullRequestCheck.None)
                lastFailurePromptKey = null
            }
            is ExistingPullRequestLookupResult.Failed -> {
                // Keep the buttons usable (decision 6) and prompt at most once per failing branch pair.
                state.value = state.value.copy(existingPullRequest = ExistingPullRequestCheck.Failed)
                LOG.info("Existing pull request check failed")
                val key = "$source\u0000$destination"
                if (lastFailurePromptKey == key) {
                    return
                }
                lastFailurePromptKey = key
                if (confirmExistingCheckRetry()) {
                    LOG.info("User retried the existing pull request check")
                    lastFailurePromptKey = null
                    scheduleExistingCheck()
                } else {
                    LOG.info("User continued without rechecking for an existing pull request")
                }
            }
        }
    }

    fun onCloseSourceBranchChanged(checked: Boolean) {
        LOG.info("User set 'delete source branch after merge' to $checked in the new pull request form")
        state.value = state.value.copy(closeSourceBranch = checked)
    }

    fun onAddReviewerClicked() {
        LOG.info("User clicked Add reviewer in the new pull request form")
        val excludedUuids = state.value.reviewers.map { it.uuid }.toSet() + setOfNotNull(currentUserUuid)
        openReviewerSearch(
            ReviewerSearchRequest(
                excludedUuids = excludedUuids,
                avatarCache = popupAvatarCache,
                coroutineScope = coroutineScope,
                loadCandidates = { onLoaded ->
                    val cached = reviewerCandidates
                    if (cached != null) {
                        onLoaded(cached)
                    } else {
                        coroutineScope.launch {
                            val loaded = loadReviewerCandidates()
                            onEdtDispatcher {
                                reviewerCandidates = loaded
                                onLoaded(loaded)
                            }
                        }
                    }
                },
                onSelected = { candidate -> onReviewerSelected(candidate) }
            )
        )
    }

    private fun onReviewerSelected(candidate: ReviewerCandidate) {
        LOG.info("User added a reviewer in the new pull request form")
        if (state.value.reviewers.any { row -> row.uuid == candidate.uuid }) {
            return
        }
        val row = NewPullRequestReviewerRow(
            uuid = candidate.uuid,
            displayName = candidate.displayName,
            avatarUrl = candidate.avatarUrl
        )
        state.value = state.value.copy(reviewers = state.value.reviewers + row)
        prefetchReviewerAvatars(listOf(row))
    }

    fun onRemoveReviewerClicked(uuid: String) {
        LOG.info("User removed a reviewer in the new pull request form")
        state.value = state.value.copy(reviewers = state.value.reviewers.filterNot { row -> row.uuid == uuid })
    }

    fun avatarIconFor(url: String?): javax.swing.Icon {
        return avatarCache.iconFor(url)
    }

    fun onCancelClicked() {
        LOG.info("User clicked Cancel in the new pull request form")
        if (NewPullRequestDraftRules.hasDraft(title = state.value.title, description = descriptionComposer.currentDraft())) {
            if (!confirmDiscardDraft()) {
                LOG.info("User kept the new pull request draft")
                return
            }
            LOG.info("User discarded the new pull request draft")
        }
        onCancel()
    }

    fun onCreateDraftClicked() {
        LOG.info("User clicked Create Draft in the new pull request form")
        submit(isDraft = true)
    }

    fun onCreatePullRequestClicked() {
        LOG.info("User clicked Create Pull Request in the new pull request form")
        submit(isDraft = false)
    }

    private fun submit(isDraft: Boolean) {
        if (!NewPullRequestSubmitRules.areCreateButtonsEnabled(state.value) ||
            state.value.existingPullRequest is ExistingPullRequestCheck.Found
        ) {
            LOG.debug("Ignoring create: the form is not in a submittable state")
            return
        }
        val source = state.value.selectedSource ?: return
        val destination = state.value.selectedDestination ?: return
        state.value = state.value.copy(isSubmitting = true, failureMessage = null)
        coroutineScope.launch {
            // Re-check the working tree first: the user may have edited files since the form opened.
            val status = loadWorkingTreeStatus()
            val workingTree = NewPullRequestWorkingTreeRule.evaluate(
                status = status,
                mainBranchNames = NewPullRequestBranchOptions.mainBranchNames(mainBranchName)
            )
            if (workingTree is WorkingTreeCheck.BlockedUncommittedChanges) {
                onEdtDispatcher {
                    state.value = state.value.copy(workingTree = workingTree, isSubmitting = false)
                    LOG.info("Blocked pull request creation: uncommitted changes on a non-main branch")
                }
                return@launch
            }
            if (!reRunExistingCheck(source = source, destination = destination)) {
                return@launch
            }
            performCreate(source = source, destination = destination, isDraft = isDraft)
        }
    }

    /** Returns true to proceed to the create, false when a found PR or a declined retry stops it. */
    private suspend fun reRunExistingCheck(source: String, destination: String): Boolean {
        while (true) {
            when (val result = findOpenPullRequest(source, destination)) {
                is ExistingPullRequestLookupResult.Found -> {
                    onEdtDispatcher {
                        state.value = state.value.copy(
                            existingPullRequest = ExistingPullRequestCheck.Found(
                                pullRequestId = result.summary.id,
                                webUrl = result.summary.webUrl
                            ),
                            isSubmitting = false
                        )
                    }
                    return false
                }
                ExistingPullRequestLookupResult.None -> {
                    return true
                }
                is ExistingPullRequestLookupResult.Failed -> {
                    LOG.info("Existing pull request re-check failed before creating")
                    val retry = confirmRetryOnEdt()
                    if (retry) {
                        LOG.info("User retried the existing pull request re-check")
                        continue
                    }
                    LOG.info("User continued with creation despite the failed re-check")
                    return true
                }
            }
        }
    }

    private suspend fun confirmRetryOnEdt(): Boolean {
        val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
        onEdtDispatcher {
            deferred.complete(confirmExistingCheckRetry())
        }
        return deferred.await()
    }

    private suspend fun performCreate(source: String, destination: String, isDraft: Boolean) {
        val request = NewPullRequest(
            title = state.value.title.trim(),
            description = descriptionComposer.currentDraft(),
            sourceBranch = source,
            destinationBranch = destination,
            reviewerUuids = state.value.reviewers.map { it.uuid },
            closeSourceBranch = state.value.closeSourceBranch,
            isDraft = isDraft
        )
        val result = createPullRequest(request)
        onEdtDispatcher {
            when (result) {
                is CreatePullRequestResult.Created -> {
                    val localMainBranch = NewPullRequestBranchOptions.localMainBranch(branches = branchNames, mainBranchName = mainBranchName)
                    onCreated(result.summary, localMainBranch)
                }
                is CreatePullRequestResult.Failed -> {
                    state.value = state.value.copy(
                        isSubmitting = false,
                        failureMessage = result.message ?: genericFailureMessage
                    )
                    onCreateFailed(result)
                }
            }
        }
    }

    fun onExistingPullRequestLinkClicked() {
        val found = state.value.existingPullRequest as? ExistingPullRequestCheck.Found ?: return
        LOG.info("User opened existing pull request #${found.pullRequestId} in the browser from the new pull request form")
        onOpenExistingPullRequest(found.webUrl)
    }

    fun onRecheckWorkingTreeClicked() {
        LOG.info("User re-checked uncommitted changes in the new pull request form")
        recheckWorkingTree()
    }

    fun onEditorSelected() {
        if (state.value.isSubmitting) {
            return
        }
        LOG.debug("The new pull request form was re-selected; re-checking the working tree")
        recheckWorkingTree()
    }

    private fun recheckWorkingTree() {
        workingTreeJob?.cancel()
        state.value = state.value.copy(workingTree = WorkingTreeCheck.Checking)
        workingTreeJob = coroutineScope.launch {
            val status = loadWorkingTreeStatus()
            val result = NewPullRequestWorkingTreeRule.evaluate(
                status = status,
                mainBranchNames = NewPullRequestBranchOptions.mainBranchNames(mainBranchName)
            )
            onEdtDispatcher {
                when (result) {
                    WorkingTreeCheck.Unknown -> LOG.info("Could not read the working tree status; not blocking pull request creation")
                    is WorkingTreeCheck.BlockedUncommittedChanges -> LOG.info("Uncommitted changes on a non-main branch block pull request creation")
                    else -> Unit
                }
                state.value = state.value.copy(workingTree = result)
            }
        }
    }

    companion object {
        private val LOG = logger<NewPullRequestViewModel>()

        private const val EXISTING_CHECK_DEBOUNCE_MS = 300L
    }
}