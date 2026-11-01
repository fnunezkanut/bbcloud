package com.github.fnunezkanut.bbcloud.ui.setup

import com.github.fnunezkanut.bbcloud.auth.BbCloudAuthenticationMethod
import com.github.fnunezkanut.bbcloud.auth.BbCloudOAuthLoginService
import com.github.fnunezkanut.bbcloud.auth.OAuthLoginResult
import com.github.fnunezkanut.bbcloud.auth.OAuthLoginState
import com.github.fnunezkanut.bbcloud.auth.OAuthTokens
import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketWorkspace
import com.github.fnunezkanut.bbcloud.bitbucket.WorkspaceListResult
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@Tag("unit")
class BbCloudSettingsViewModelTest {
    private val configurationService = mockk<BbCloudConfigurationService>(relaxed = false)

    private val loginState = MutableStateFlow<OAuthLoginState>(OAuthLoginState.Idle)
    private val oauthLoginService = mockk<BbCloudOAuthLoginService>(relaxed = true) {
        every { state } returns loginState
    }

    private fun oauthTokens(): OAuthTokens {
        return OAuthTokens(
            accessToken = "access",
            refreshToken = "refresh",
            expiresAt = Clock.System.now() + 2.hours
        )
    }

    private fun createViewModel(
        chooseWorkspace: (slugs: List<String>, preselectedSlug: String) -> String? = { _, _ -> null },
        confirmReset: () -> Boolean = { true },
        onExternalAuthChanged: () -> Unit = {}
    ): BbCloudSettingsViewModel {
        return BbCloudSettingsViewModel(
            configurationService = configurationService,
            // Unconfined runs each launch eagerly and synchronously up to the first real suspension; the mocked
            // suspend functions return without suspending, so the whole save/reset chain completes in-line.
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            oauthLoginService = oauthLoginService,
            onSaveSuccess = {},
            onTokenReset = {},
            onExternalAuthChanged = onExternalAuthChanged,
            onProjectsDirectoryChosenCallback = {},
            chooseWorkspace = chooseWorkspace,
            confirmReset = confirmReset
        )
    }

    @Nested
    inner class DialogLifecycle {
        @Test
        fun `onApiTokenDialogOpened - should clear token text, error and success message`() {
            // GIVEN
            val viewModel = createViewModel()
            viewModel.state.value = viewModel.state.value.copy(
                tokenFieldText = "typed",
                errorMessage = "boom",
                successMessage = "ok"
            )

            // WHEN
            viewModel.onApiTokenDialogOpened()

            // THEN
            assertSoftly {
                viewModel.state.value.tokenFieldText shouldBe ""
                viewModel.state.value.errorMessage shouldBe null
                viewModel.state.value.successMessage shouldBe null
            }
        }

        @Test
        fun `onApiTokenDialogClosed - should clear token text and error`() {
            // GIVEN
            val viewModel = createViewModel()
            viewModel.state.value = viewModel.state.value.copy(
                tokenFieldText = "half-typed",
                errorMessage = "boom"
            )

            // WHEN
            viewModel.onApiTokenDialogClosed()

            // THEN
            assertSoftly {
                viewModel.state.value.tokenFieldText shouldBe ""
                viewModel.state.value.errorMessage shouldBe null
            }
        }
    }

    @Nested
    inner class SaveListener {
        @Test
        fun `persist success - should invoke the api token save listener once`() {
            // GIVEN
            every { configurationService.workspaceSlugOrNull() } returns "acme"
            coEvery { configurationService.storedTokenOrNull() } returns null
            coEvery { configurationService.listWorkspaces(any()) } returns
                WorkspaceListResult.Success(listOf(BitbucketWorkspace(slug = "acme", uuid = null)))
            coEvery { configurationService.saveSetup(any(), any()) } returns Result.success(Unit)
            val viewModel = createViewModel()
            var invocations = 0
            viewModel.setApiTokenSaveListener { invocations++ }
            viewModel.setTokenFieldText("a-token")

            // WHEN
            viewModel.onSaveClicked()

            // THEN
            invocations shouldBe 1
        }

        @Test
        fun `persist success after dialog closed - should not invoke a cleared listener`() {
            // GIVEN
            every { configurationService.workspaceSlugOrNull() } returns "acme"
            coEvery { configurationService.storedTokenOrNull() } returns null
            coEvery { configurationService.listWorkspaces(any()) } returns
                WorkspaceListResult.Success(listOf(BitbucketWorkspace(slug = "acme", uuid = null)))
            coEvery { configurationService.saveSetup(any(), any()) } returns Result.success(Unit)
            val viewModel = createViewModel()
            var invocations = 0
            viewModel.setApiTokenSaveListener { invocations++ }
            // The dialog closed before the save completed, clearing the listener.
            viewModel.onApiTokenDialogClosed()
            viewModel.setTokenFieldText("a-token")

            // WHEN
            viewModel.onSaveClicked()

            // THEN
            invocations shouldBe 0
        }
    }

    @Nested
    inner class WorkspaceSlug {
        @Test
        fun `bindSettingsState - should populate workspaceSlug and working method from configuration service`() {
            // GIVEN
            every { configurationService.projectsDirectoryOrNull() } returns "/tmp/projects"
            every { configurationService.workspaceSlugOrNull() } returns "acme"
            val viewModel = createViewModel()

            // WHEN
            viewModel.bindSettingsState(
                hasStoredToken = true,
                hasOAuthTokens = false,
                workingMethod = BbCloudAuthenticationMethod.ApiToken
            )

            // THEN
            assertSoftly {
                viewModel.state.value.workspaceSlug shouldBe "acme"
                viewModel.state.value.authenticationMethod shouldBe BbCloudAuthenticationMethod.ApiToken
            }
        }

        @Test
        fun `bindSettingsState - should keep the oauth message across a re-bind`() {
            // GIVEN
            every { configurationService.projectsDirectoryOrNull() } returns "/tmp/projects"
            every { configurationService.workspaceSlugOrNull() } returns "acme"
            val viewModel = createViewModel()
            viewModel.state.value = viewModel.state.value.copy(oauthMessage = "kept", oauthMessageIsError = true)

            // WHEN
            viewModel.bindSettingsState(
                hasStoredToken = true,
                hasOAuthTokens = false,
                workingMethod = BbCloudAuthenticationMethod.ApiToken
            )

            // THEN
            assertSoftly {
                viewModel.state.value.oauthMessage shouldBe "kept"
                viewModel.state.value.oauthMessageIsError shouldBe true
            }
        }

        @Test
        fun `persist success - should set workspaceSlug to the saved workspace`() {
            // GIVEN
            every { configurationService.workspaceSlugOrNull() } returns "acme"
            coEvery { configurationService.storedTokenOrNull() } returns null
            coEvery { configurationService.listWorkspaces(any()) } returns
                WorkspaceListResult.Success(listOf(BitbucketWorkspace(slug = "acme", uuid = null)))
            coEvery { configurationService.saveSetup(any(), any()) } returns Result.success(Unit)
            val viewModel = createViewModel()
            viewModel.setTokenFieldText("a-token")

            // WHEN
            viewModel.onSaveClicked()

            // THEN
            assertSoftly {
                viewModel.state.value.workspaceSlug shouldBe "acme"
                viewModel.state.value.hasStoredToken shouldBe true
            }
        }

        @Test
        fun `onResetClicked - should set authenticationMethod to None and clear workspaceSlug`() {
            // GIVEN
            coEvery { configurationService.resetAll() } returns Unit
            val viewModel = createViewModel(confirmReset = { true })
            viewModel.state.value = viewModel.state.value.copy(
                hasStoredToken = true,
                workspaceSlug = "acme"
            )

            // WHEN
            viewModel.onResetClicked()

            // THEN
            assertSoftly {
                viewModel.state.value.workspaceSlug shouldBe null
                viewModel.state.value.hasStoredToken shouldBe false
                viewModel.state.value.authenticationMethod shouldBe BbCloudAuthenticationMethod.None
            }
            verify { oauthLoginService.cancel() }
        }
    }

    @Nested
    inner class OAuthLogin {
        @Test
        fun `onOAuthLoginClicked success with one workspace - should save and clear the oauth message`() {
            // GIVEN
            val tokens = oauthTokens()
            every { oauthLoginService.start() } returns CompletableDeferred(OAuthLoginResult.Success(tokens))
            every { configurationService.workspaceSlugOrNull() } returns "acme"
            coEvery { configurationService.listWorkspaces(any()) } returns
                WorkspaceListResult.Success(listOf(BitbucketWorkspace(slug = "acme", uuid = null)))
            coEvery { configurationService.saveOAuthSetup(any(), any()) } returns Result.success(Unit)
            var saveSuccessCalls = 0
            val viewModel = createViewModel()
            viewModel.state.value // init collector is attached

            // WHEN
            viewModel.onOAuthLoginClicked()

            // THEN
            assertSoftly {
                viewModel.state.value.workspaceSlug shouldBe "acme"
                viewModel.state.value.workingMethod shouldBe BbCloudAuthenticationMethod.OAuth
                viewModel.state.value.oauthMessage shouldBe null
                viewModel.state.value.oauthMessageIsError shouldBe false
                viewModel.state.value.isBusy shouldBe false
            }
        }

        @Test
        fun `onOAuthLoginClicked chooser cancel - should save nothing and show no error`() {
            // GIVEN two workspaces so the chooser is consulted, then cancelled
            every { oauthLoginService.start() } returns CompletableDeferred(OAuthLoginResult.Success(oauthTokens()))
            every { configurationService.workspaceSlugOrNull() } returns null
            coEvery { configurationService.listWorkspaces(any()) } returns
                WorkspaceListResult.Success(
                    listOf(
                        BitbucketWorkspace(slug = "one", uuid = null),
                        BitbucketWorkspace(slug = "two", uuid = null)
                    )
                )
            val viewModel = createViewModel(chooseWorkspace = { _, _ -> null })

            // WHEN
            viewModel.onOAuthLoginClicked()

            // THEN
            assertSoftly {
                viewModel.state.value.oauthMessageIsError shouldBe false
                viewModel.state.value.isBusy shouldBe false
            }
        }

        @Test
        fun `onOAuthLoginClicked unauthorized workspaces - should show the scopes message and not save`() {
            // GIVEN
            every { oauthLoginService.start() } returns CompletableDeferred(OAuthLoginResult.Success(oauthTokens()))
            every { configurationService.workspaceSlugOrNull() } returns null
            coEvery { configurationService.listWorkspaces(any()) } returns WorkspaceListResult.Unauthorized
            val viewModel = createViewModel()

            // WHEN
            viewModel.onOAuthLoginClicked()

            // THEN
            assertSoftly {
                viewModel.state.value.oauthMessageIsError shouldBe true
                viewModel.state.value.isBusy shouldBe false
            }
        }

        @Test
        fun `onOAuthLoginClicked port unavailable - should show an error and clear busy`() {
            // GIVEN
            every { oauthLoginService.start() } returns CompletableDeferred(OAuthLoginResult.PortUnavailable)
            val viewModel = createViewModel()

            // WHEN
            viewModel.onOAuthLoginClicked()

            // THEN
            assertSoftly {
                viewModel.state.value.oauthMessageIsError shouldBe true
                viewModel.state.value.isBusy shouldBe false
            }
        }

        @Test
        fun `external login state change - should mirror progress and notify when it finishes elsewhere`() {
            // GIVEN this view model did not start the login
            var externalChanges = 0
            createViewModel(onExternalAuthChanged = { externalChanges++ })

            // WHEN a login starts and finishes in another project's tab
            loginState.value = OAuthLoginState.InProgress(browserLaunchFailedUrl = null)
            loginState.value = OAuthLoginState.Finished(
                result = OAuthLoginResult.TimedOut,
                finishedAt = Clock.System.now()
            )

            // THEN the external-change callback ran at least once on finish
            (externalChanges >= 1) shouldBe true
        }

        @Test
        fun `onOAuthLoginCancelClicked - should cancel the shared login service`() {
            // GIVEN
            val viewModel = createViewModel()

            // WHEN
            viewModel.onOAuthLoginCancelClicked()

            // THEN
            verify { oauthLoginService.cancel() }
        }
    }
}