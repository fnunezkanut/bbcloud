package com.github.fnunezkanut.bbcloud.ui.pullrequest.create

import com.github.fnunezkanut.bbcloud.bitbucket.CreatePullRequestResult
import com.github.fnunezkanut.bbcloud.bitbucket.ExistingPullRequestLookupResult
import com.github.fnunezkanut.bbcloud.bitbucket.NewPullRequest
import com.github.fnunezkanut.bbcloud.bitbucket.NewPullRequestBranchData
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.bitbucket.RepositoryBranches
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerCandidate
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewerCandidates
import com.github.fnunezkanut.bbcloud.git.WorkingTreeStatus
import com.github.fnunezkanut.bbcloud.ui.main.AuthorAvatarCache
import com.github.fnunezkanut.bbcloud.ui.main.ReviewerSearchRequest
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class NewPullRequestViewModelTest {
    private val branchData = NewPullRequestBranchData(
        branches = RepositoryBranches(names = listOf("main", "feature/x", "feature/y"), isTruncated = false),
        mainBranchName = "main"
    )
    private val candidates = ReviewerCandidates(
        members = listOf(
            ReviewerCandidate(uuid = "{me}", displayName = "Me", nickname = null, avatarUrl = null),
            ReviewerCandidate(uuid = "{reviewer}", displayName = "Reviewer", nickname = null, avatarUrl = null)
        ),
        defaultReviewerUuids = setOf("{me}", "{reviewer}"),
        isTruncated = false
    )

    /** Joins every child coroutine of [scope], repeatedly, so chains of launched work settle before assertions. */
    private suspend fun drain(scope: CoroutineScope) {
        val job = scope.coroutineContext[Job] ?: return
        var guard = 0
        while (guard < 100) {
            val children = job.children.toList()
            if (children.isEmpty()) {
                return
            }
            children.forEach { child -> child.join() }
            guard++
        }
    }

    private fun avatarCache(): AuthorAvatarCache {
        val cache = mockk<AuthorAvatarCache>(relaxed = true)
        every { cache.iconFor(any()) } returns mockk(relaxed = true)
        return cache
    }

    private fun summary(id: Long): PullRequestSummary {
        val instant = Instant.parse("2026-09-30T12:00:00Z")
        return PullRequestSummary(
            id = id,
            title = "PR $id",
            authorDisplayName = "Author",
            authorAvatarUrl = null,
            createdOn = instant,
            updatedOn = instant,
            sourceBranch = "feature/x",
            destinationBranch = "main",
            commentCount = 0,
            webUrl = "https://bitbucket.org/ws/repo/pull-requests/$id",
            state = PullRequestState.Open,
            isDraft = false
        )
    }

    private fun buildViewModel(
        scope: CoroutineScope,
        loadBranches: suspend () -> NewPullRequestBranchData? = { branchData },
        loadReviewerCandidates: suspend () -> ReviewerCandidates? = { candidates },
        loadCurrentUserUuid: suspend () -> String? = { "{me}" },
        findOpenPullRequest: suspend (String, String) -> ExistingPullRequestLookupResult = { _, _ -> ExistingPullRequestLookupResult.None },
        createPullRequest: suspend (NewPullRequest) -> CreatePullRequestResult = { CreatePullRequestResult.Created(summary(1)) },
        currentLocalBranch: String? = "feature/y",
        loadWorkingTreeStatus: suspend () -> WorkingTreeStatus? = { WorkingTreeStatus(currentBranch = "feature/y", hasUncommittedChanges = false) },
        confirmExistingCheckRetry: () -> Boolean = { false },
        confirmDiscardDraft: () -> Boolean = { true },
        onCreated: (PullRequestSummary, String?) -> Unit = { _, _ -> },
        onCreateFailed: (CreatePullRequestResult.Failed) -> Unit = {},
        onCancel: () -> Unit = {},
        openReviewerSearch: (ReviewerSearchRequest) -> Unit = {}
    ): NewPullRequestViewModel {
        return NewPullRequestViewModel(
            coroutineScope = scope,
            loadBranches = loadBranches,
            loadReviewerCandidates = loadReviewerCandidates,
            loadCurrentUserUuid = loadCurrentUserUuid,
            findOpenPullRequest = findOpenPullRequest,
            createPullRequest = createPullRequest,
            currentLocalBranch = currentLocalBranch,
            openReviewerSearch = openReviewerSearch,
            loadWorkingTreeStatus = loadWorkingTreeStatus,
            confirmExistingCheckRetry = confirmExistingCheckRetry,
            confirmDiscardDraft = confirmDiscardDraft,
            onCreated = onCreated,
            onCreateFailed = onCreateFailed,
            onCancel = onCancel,
            onOpenExistingPullRequest = {},
            onEdtDispatcher = { it() },
            genericFailureMessage = "generic",
            descriptionPlaceholder = "placeholder",
            descriptionSubmitLabel = "submit",
            descriptionSubmitHint = "hint",
            descriptionFailureMessage = "failed",
            avatarCache = avatarCache(),
            popupAvatarCache = avatarCache(),
            existingCheckDebounceMs = 0
        )
    }

    @Test
    fun `start - applies default branches and pre-populates default reviewers excluding the current user`(): Unit =
        runBlocking {
            // GIVEN
            val vm = buildViewModel(scope = this)

            // WHEN
            vm.start()
            drain(this)

            // THEN
            assertSoftly {
                vm.state.value.branchesLoad shouldBe BranchesLoad.Loaded
                vm.state.value.selectedDestination shouldBe "main"
                vm.state.value.selectedSource shouldBe "feature/y"
                vm.state.value.reviewers.map { it.uuid } shouldBe listOf("{reviewer}")
            }
        }

    @Test
    fun `onDestinationSelected - reconciles the source when it becomes the destination`(): Unit =
        runBlocking {
            // GIVEN
            val vm = buildViewModel(scope = this)
            vm.start()
            drain(this)

            // WHEN the destination is set to feature/y which is the current source
            val destinationIndex = vm.state.value.destinationOptions.indexOf("feature/y")
            vm.onDestinationSelected(destinationIndex)
            drain(this)

            // THEN the source no longer equals the destination
            assertSoftly {
                vm.state.value.selectedDestination shouldBe "feature/y"
                (vm.state.value.selectedSource != "feature/y") shouldBe true
            }
        }

    @Test
    fun `existing check Found - hides the buttons`(): Unit =
        runBlocking {
            // GIVEN
            val vm = buildViewModel(
                scope = this,
                findOpenPullRequest = { _, _ -> ExistingPullRequestLookupResult.Found(summary(55)) }
            )

            // WHEN
            vm.start()
            drain(this)

            // THEN
            val existing = vm.state.value.existingPullRequest
            existing.shouldBeInstanceOf<ExistingPullRequestCheck.Found>()
            existing.pullRequestId shouldBe 55L
        }

    @Test
    fun `submit draft - sends isDraft true and default closeSourceBranch true and calls onCreated`(): Unit =
        runBlocking {
            // GIVEN
            var createdLocalMain: String? = "unset"
            var createdSummaryId = -1L
            val requests = mutableListOf<NewPullRequest>()
            val vm = buildViewModel(
                scope = this,
                createPullRequest = { request ->
                    requests += request
                    CreatePullRequestResult.Created(summary(77))
                },
                onCreated = { createdSummary, localMain ->
                    createdSummaryId = createdSummary.id
                    createdLocalMain = localMain
                }
            )
            vm.start()
            drain(this)
            vm.onTitleChanged("a title")

            // WHEN
            vm.onCreateDraftClicked()
            drain(this)

            // THEN
            assertSoftly {
                requests.single().isDraft shouldBe true
                requests.single().closeSourceBranch shouldBe true
                createdSummaryId shouldBe 77L
                createdLocalMain shouldBe "main"
            }
        }

    @Test
    fun `submit - re-checks for an existing pull request before creating`(): Unit =
        runBlocking {
            // GIVEN a PR appears on the pre-submit re-check
            var created = false
            val vm = buildViewModel(
                scope = this,
                findOpenPullRequest = { _, _ -> ExistingPullRequestLookupResult.Found(summary(9)) },
                createPullRequest = {
                    created = true
                    CreatePullRequestResult.Created(summary(9))
                }
            )
            vm.start()
            drain(this)
            vm.onTitleChanged("a title")

            // WHEN
            vm.onCreatePullRequestClicked()
            drain(this)

            // THEN the existing-PR state blocks the create
            assertSoftly {
                created shouldBe false
                vm.state.value.existingPullRequest.shouldBeInstanceOf<ExistingPullRequestCheck.Found>()
                vm.state.value.isSubmitting shouldBe false
            }
        }

    @Test
    fun `create Failed - sets the failure message and calls onCreateFailed`(): Unit =
        runBlocking {
            // GIVEN
            var failedCalled = false
            val vm = buildViewModel(
                scope = this,
                createPullRequest = { CreatePullRequestResult.Failed(message = "no changes") },
                onCreateFailed = { failedCalled = true }
            )
            vm.start()
            drain(this)
            vm.onTitleChanged("a title")

            // WHEN
            vm.onCreatePullRequestClicked()
            drain(this)

            // THEN
            assertSoftly {
                failedCalled shouldBe true
                vm.state.value.failureMessage shouldBe "no changes"
                vm.state.value.isSubmitting shouldBe false
            }
        }

    @Test
    fun `submit - stops before creating when the working tree is blocked`(): Unit =
        runBlocking {
            // GIVEN uncommitted changes on a feature branch
            var created = false
            val vm = buildViewModel(
                scope = this,
                loadWorkingTreeStatus = { WorkingTreeStatus(currentBranch = "feature/x", hasUncommittedChanges = true) },
                createPullRequest = {
                    created = true
                    CreatePullRequestResult.Created(summary(1))
                }
            )
            vm.start()
            drain(this)
            vm.onTitleChanged("a title")

            // WHEN
            vm.onCreatePullRequestClicked()
            drain(this)

            // THEN
            assertSoftly {
                created shouldBe false
                vm.state.value.workingTree.shouldBeInstanceOf<WorkingTreeCheck.BlockedUncommittedChanges>()
                vm.state.value.isSubmitting shouldBe false
            }
        }

    @Test
    fun `cancel with a draft - calls onCancel only when the user confirms discarding`(): Unit =
        runBlocking {
            // GIVEN a kept draft
            var cancelledKeep = false
            val vmKeep = buildViewModel(scope = this, confirmDiscardDraft = { false }, onCancel = { cancelledKeep = true })
            vmKeep.start()
            drain(this)
            vmKeep.onTitleChanged("a title")

            // WHEN
            vmKeep.onCancelClicked()

            // THEN keeping the draft does not cancel
            cancelledKeep shouldBe false

            // GIVEN a discarded draft
            var cancelledDiscard = false
            val vmDiscard = buildViewModel(scope = this, confirmDiscardDraft = { true }, onCancel = { cancelledDiscard = true })
            vmDiscard.start()
            drain(this)
            vmDiscard.onTitleChanged("a title")

            // WHEN
            vmDiscard.onCancelClicked()

            // THEN discarding cancels
            cancelledDiscard shouldBe true
        }

    @Test
    fun `cancel without a draft - cancels without prompting`(): Unit =
        runBlocking {
            // GIVEN no draft
            var prompted = false
            var cancelled = false
            val vm = buildViewModel(
                scope = this,
                confirmDiscardDraft = {
                    prompted = true
                    false
                },
                onCancel = { cancelled = true }
            )
            vm.start()
            drain(this)

            // WHEN
            vm.onCancelClicked()

            // THEN
            assertSoftly {
                prompted shouldBe false
                cancelled shouldBe true
            }
        }

    @Test
    fun `remove reviewer - updates the list without duplicates`(): Unit =
        runBlocking {
            // GIVEN
            val vm = buildViewModel(scope = this)
            vm.start()
            drain(this)

            // WHEN
            vm.onRemoveReviewerClicked("{reviewer}")

            // THEN
            vm.state.value.reviewers shouldBe emptyList()
        }
}