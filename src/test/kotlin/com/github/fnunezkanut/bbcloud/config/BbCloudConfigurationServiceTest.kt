package com.github.fnunezkanut.bbcloud.config

import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketWorkspace
import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketWorkspaceClient
import com.github.fnunezkanut.bbcloud.bitbucket.WorkspaceListResult
import com.github.fnunezkanut.bbcloud.credentials.BbCloudCredentialStore
import com.github.fnunezkanut.bbcloud.project.BbCloudWatchedProjectsDiscoveryService
import com.github.fnunezkanut.bbcloud.project.WatchedProjectMapping
import com.github.fnunezkanut.bbcloud.project.WatchedProjectsDiscoveryResult
import com.github.fnunezkanut.bbcloud.settings.BbCloudSettings
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Paths

@Tag("unit")
class BbCloudConfigurationServiceTest {
    /** A real existing directory, injected per test, so the directory-validity checks in isConfigured pass. */
    @TempDir
    lateinit var projectsDir: Path

    @Test
    fun `isConfigured - should be false when slug or token missing`(): Unit =
        runBlocking {
            val settings = settings()
            val store = FakeCredentialStore(hasToken = false)
            val service = service(settings, store)

            service.isConfigured() shouldBe false

            // Slug + a valid projects directory, but still no token, is not configured.
            settings.workspaceSlug = "my-workspace"
            settings.projectsDirectory = projectsDir.toString()
            service.isConfigured() shouldBe false

            // Token stored but a blank slug is not configured.
            store.saveToken("token")
            settings.workspaceSlug = "  "
            service.isConfigured() shouldBe false
        }

    @Test
    fun `isConfigured - should be true when slug token and projects directory are set and valid`(): Unit =
        runBlocking {
            // GIVEN slug, token, and a valid projects directory (the work tree root is derived, not required to exist).
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = projectsDir.toString()
            val store = FakeCredentialStore(hasToken = true)
            val service = service(settings, store)

            // WHEN / THEN
            service.isConfigured() shouldBe true
        }

    @Test
    fun `isConfigured - should be false when projects directory not set`(): Unit =
        runBlocking {
            // GIVEN slug and token, but no projects directory
            val settings = settings(workspaceSlug = "my-workspace")
            val service = service(settings, FakeCredentialStore(hasToken = true))

            // WHEN / THEN
            service.isConfigured() shouldBe false
        }

    @Test
    fun `isConfigured - should be false when projects directory does not exist`(): Unit =
        runBlocking {
            // GIVEN a projects directory pointing at a path that is not a directory on disk
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = projectsDir.resolve("missing").toString()
            val service = service(settings, FakeCredentialStore(hasToken = true))

            // WHEN / THEN
            service.isConfigured() shouldBe false
        }

    @Test
    fun `isConfigured - should be false when no token is stored`(): Unit =
        runBlocking {
            // GIVEN slug and a valid projects directory, but no stored token
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = projectsDir.toString()
            val service = service(settings, FakeCredentialStore(hasToken = false))

            // WHEN / THEN
            service.isConfigured() shouldBe false
        }

    @Test
    fun `isConfigured - should be false when workspace slug is empty`(): Unit =
        runBlocking {
            // GIVEN token and a valid projects directory, but no workspace slug
            val settings = settings()
            settings.projectsDirectory = projectsDir.toString()
            val service = service(settings, FakeCredentialStore(hasToken = true))

            // WHEN / THEN
            service.isConfigured() shouldBe false
        }

    @Test
    fun `hasStoredToken - should follow the credential store`(): Unit =
        runBlocking {
            val store = FakeCredentialStore(hasToken = false, storedToken = "")
            val service = service(settings(workspaceSlug = "my-workspace"), store)

            service.hasStoredToken() shouldBe false

            store.saveToken("secret-token")

            service.hasStoredToken() shouldBe true
        }

    @Test
    fun `saveSetup - should require a token on first setup`(): Unit =
        runBlocking {
            val settings = settings()
            val store = FakeCredentialStore(hasToken = false)
            val service = service(settings, store)

            val result = service.saveSetup(workspaceSlug = "my-workspace", apiTokenInput = "")

            result.isFailure shouldBe true
            result.exceptionOrNull().shouldBeInstanceOf<BbCloudConfigurationService.SaveException.TokenRequired>()
            settings.workspaceSlug shouldBe ""
        }

    @Test
    fun `saveSetup - should reject an invalid workspace slug`(): Unit =
        runBlocking {
            val settings = settings()
            val store = FakeCredentialStore(hasToken = false)
            val service = service(settings, store)

            val result = service.saveSetup(workspaceSlug = "https://bitbucket.org/x/", apiTokenInput = "new-token")

            result.isFailure shouldBe true
            result.exceptionOrNull().shouldBeInstanceOf<BbCloudConfigurationService.SaveException.WorkspaceRequired>()
            store.savedToken shouldBe ""
        }

    @Test
    fun `saveSetup - should persist slug and token on first setup`(): Unit =
        runBlocking {
            val settings = settings()
            val store = FakeCredentialStore(hasToken = false)
            val service = service(settings, store)

            val result = service.saveSetup(workspaceSlug = "  my-workspace  ", apiTokenInput = "new-token")

            assertSoftly {
                result.isSuccess shouldBe true
                settings.workspaceSlug shouldBe "my-workspace"
                store.savedToken shouldBe "new-token"
            }
        }

    @Test
    fun `saveSetup - should keep the token when the update leaves it blank`(): Unit =
        runBlocking {
            val settings = settings(workspaceSlug = "old")
            val store = FakeCredentialStore(hasToken = true, storedToken = "existing-token")
            val service = service(settings, store)

            val result = service.saveSetup(workspaceSlug = "new", apiTokenInput = "")

            assertSoftly {
                result.isSuccess shouldBe true
                settings.workspaceSlug shouldBe "new"
                store.savedToken shouldBe "existing-token"
                store.saveInvocations shouldBe 0
            }
        }

    @Test
    fun `organizationUrlOrNull - should derive the URL from the slug`(): Unit =
        runBlocking {
            val settings = settings(workspaceSlug = "my-workspace")
            val service = service(settings, FakeCredentialStore(hasToken = true))

            service.organizationUrlOrNull() shouldBe "https://bitbucket.org/my-workspace/"
        }

    @Test
    fun `organizationUrlOrNull - should be null without a slug`(): Unit =
        runBlocking {
            service(settings(), FakeCredentialStore(hasToken = true)).organizationUrlOrNull() shouldBe null
        }

    @Test
    fun `workspaceSlugOrNull - should migrate a legacy organization url once`(): Unit =
        runBlocking {
            val settings = settings(organizationUrl = "https://bitbucket.org/legacy-workspace/")
            val service = service(settings, FakeCredentialStore(hasToken = true))

            assertSoftly {
                service.workspaceSlugOrNull() shouldBe "legacy-workspace"
                service.organizationUrlOrNull() shouldBe "https://bitbucket.org/legacy-workspace/"
                settings.workspaceSlug shouldBe "legacy-workspace"
            }
        }

    @Test
    fun `workspaceSlugOrNull - should prefer the stored slug over the legacy url`(): Unit =
        runBlocking {
            val settings = settings(
                workspaceSlug = "current",
                organizationUrl = "https://bitbucket.org/legacy/"
            )

            service(settings, FakeCredentialStore(hasToken = true)).workspaceSlugOrNull() shouldBe "current"
        }

    @Test
    fun `saveProjectsDirectory - should store the normalized absolute path`(): Unit =
        runBlocking {
            val settings = settings()
            val service = service(settings, FakeCredentialStore(hasToken = true))

            service.saveProjectsDirectory(absolutePath = "  ${RELATIVE_ROOT}/bbcloud/../bbcloud  ")

            val expected = Paths.get(RELATIVE_ROOT, "bbcloud").toAbsolutePath().normalize().toString()
            assertSoftly {
                settings.projectsDirectory shouldBe expected
                service.projectsDirectoryOrNull() shouldBe expected
            }
        }

    @Test
    fun `saveProjectsDirectory - should empty the watched projects before a rebuild`(): Unit =
        runBlocking {
            // GIVEN watched entries from a previous directory
            val settings = settings(workspaceSlug = "my-workspace")
            settings.replaceWatchedProjects(
                listOf(WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace", watched = true))
            )
            val service = service(settings, FakeCredentialStore(hasToken = true))

            // WHEN a new projects directory is chosen
            service.saveProjectsDirectory(absolutePath = projectsDir.toString())

            // THEN the watched map is emptied; a clear-first rescan (driven by the UI) rebuilds it afterwards.
            settings.watchedProjects shouldBe emptyList()
        }

    @Test
    fun `projectsDirectoryOrNull - should be null when unset or blank`(): Unit =
        runBlocking {
            val settings = settings()
            val service = service(settings, FakeCredentialStore(hasToken = true))

            service.projectsDirectoryOrNull() shouldBe null

            settings.projectsDirectory = "   "
            service.projectsDirectoryOrNull() shouldBe null
        }

    @Test
    fun `resetApiToken - should clear the stored token and the watched projects, keeping slug and directory`(): Unit =
        runBlocking {
            // GIVEN
            val settings = settings(workspaceSlug = "my-workspace")
            val expectedDirectory = Paths.get(RELATIVE_ROOT, "bbcloud").toAbsolutePath().normalize().toString()
            settings.projectsDirectory = expectedDirectory
            settings.replaceWatchedProjects(
                listOf(WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace", watched = true))
            )
            val store = FakeCredentialStore(hasToken = true, storedToken = "token")
            val service = service(settings, store)

            // WHEN
            service.resetApiToken()

            // THEN the token and the watched map are cleared, but the slug and directory are kept.
            assertSoftly {
                store.hasToken() shouldBe false
                store.clearInvocations shouldBe 1
                settings.workspaceSlug shouldBe "my-workspace"
                service.projectsDirectoryOrNull() shouldBe expectedDirectory
                settings.watchedProjects shouldBe emptyList()
                service.isConfigured() shouldBe false
                service.organizationUrlOrNull() shouldBe "https://bitbucket.org/my-workspace/"
            }
        }

    @Test
    fun `resetAll - should clear the token slug organization url projects directory and watched projects`(): Unit =
        runBlocking {
            // GIVEN
            val settings = settings(
                workspaceSlug = "my-workspace",
                organizationUrl = "https://bitbucket.org/my-workspace/"
            )
            settings.projectsDirectory = Paths.get(RELATIVE_ROOT, "bbcloud").toAbsolutePath().normalize().toString()
            settings.replaceWatchedProjects(
                listOf(WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace"))
            )
            val store = FakeCredentialStore(hasToken = true, storedToken = "token")
            val service = service(settings, store)

            // WHEN
            service.resetAll()

            // THEN
            assertSoftly {
                store.hasToken() shouldBe false
                store.clearInvocations shouldBe 1
                settings.workspaceSlug shouldBe ""
                settings.organizationUrl shouldBe ""
                service.workspaceSlugOrNull() shouldBe null
                service.projectsDirectoryOrNull() shouldBe null
                service.workTreeDirectoryOrNull() shouldBe null
                settings.watchedProjects shouldBe emptyList()
                settings.repoAvatarUrls shouldBe emptyMap()
                service.organizationUrlOrNull() shouldBe null
                service.isConfigured() shouldBe false
            }
        }

    @Test
    fun `rescanWatchedProjects - clearFirst should replace the persisted avatar URL map`(): Unit =
        runBlocking {
            // GIVEN a stored avatar URL from a previous config and a scan that reports a fresh set
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = Paths.get("target/test-projects").toAbsolutePath().normalize().toString()
            settings.replaceRepoAvatarUrls(mapOf("old-repo" to "https://cdn.example.com/old.png"))
            val scanned = listOf(
                WatchedProjectMapping(localFolderPath = "/projects/repo-a", remoteRepoSlug = "repo-a", workspace = "my-workspace")
            )
            val discoveryService = FakeDiscoveryService(
                result = WatchedProjectsDiscoveryResult.Success(
                    mappings = scanned,
                    avatarUrlBySlug = mapOf("repo-a" to "https://cdn.example.com/a.png")
                )
            )
            val service = service(settings, FakeCredentialStore(hasToken = true, storedToken = "token"), discoveryService = discoveryService)

            // WHEN a clear-first rescan runs
            service.rescanWatchedProjects(clearFirst = true)

            // THEN the old URL is gone and only the fresh set remains
            settings.repoAvatarUrls shouldBe mapOf("repo-a" to "https://cdn.example.com/a.png")
        }

    @Test
    fun `rescanWatchedProjects - Sync should merge the persisted avatar URL map`(): Unit =
        runBlocking {
            // GIVEN a stored avatar URL for repo-b that the scan does not touch
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = Paths.get("target/test-projects").toAbsolutePath().normalize().toString()
            settings.replaceRepoAvatarUrls(mapOf("repo-b" to "https://cdn.example.com/b.png"))
            val scanned = listOf(
                WatchedProjectMapping(localFolderPath = "/projects/repo-a", remoteRepoSlug = "repo-a", workspace = "my-workspace")
            )
            val discoveryService = FakeDiscoveryService(
                result = WatchedProjectsDiscoveryResult.Success(
                    mappings = scanned,
                    avatarUrlBySlug = mapOf("repo-a" to "https://cdn.example.com/a.png")
                )
            )
            val service = service(settings, FakeCredentialStore(hasToken = true, storedToken = "token"), discoveryService = discoveryService)

            // WHEN a manual Sync (clearFirst = false) runs
            service.rescanWatchedProjects()

            // THEN repo-a is added and repo-b is preserved
            settings.repoAvatarUrls shouldBe mapOf(
                "repo-a" to "https://cdn.example.com/a.png",
                "repo-b" to "https://cdn.example.com/b.png"
            )
        }

    @Test
    fun `rescanWatchedProjects - Sync VerifyFailed should preserve the persisted avatar URL map`(): Unit =
        runBlocking {
            // GIVEN a stored avatar URL and a scan that fails to verify
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = Paths.get("target/test-projects").toAbsolutePath().normalize().toString()
            settings.replaceRepoAvatarUrls(mapOf("repo-a" to "https://cdn.example.com/a.png"))
            val discoveryService = FakeDiscoveryService(result = WatchedProjectsDiscoveryResult.VerifyFailed)
            val service = service(settings, FakeCredentialStore(hasToken = true, storedToken = "token"), discoveryService = discoveryService)

            // WHEN a manual Sync (clearFirst = false) hits a transient verify failure
            service.rescanWatchedProjects().shouldBeInstanceOf<WatchedProjectsDiscoveryResult.VerifyFailed>()

            // THEN the previous avatar URL is kept, mirroring the watched-flag carry-over
            settings.repoAvatarUrls shouldBe mapOf("repo-a" to "https://cdn.example.com/a.png")
        }

    @Test
    fun `repoAvatarUrlsForCurrentWorkspace - should return the persisted map without a Bitbucket call`(): Unit =
        runBlocking {
            // GIVEN a stored avatar URL map and a discovery service that must never be invoked by this read
            val settings = settings(workspaceSlug = "my-workspace")
            settings.replaceRepoAvatarUrls(mapOf("repo-a" to "https://cdn.example.com/a.png"))
            val discoveryService = FakeDiscoveryService()
            val service = service(settings, FakeCredentialStore(hasToken = true, storedToken = "token"), discoveryService = discoveryService)

            // WHEN
            val urls = service.repoAvatarUrlsForCurrentWorkspace()

            // THEN the persisted map is returned and no discovery (which drives the repo-existence call) happened
            assertSoftly {
                urls shouldBe mapOf("repo-a" to "https://cdn.example.com/a.png")
                discoveryService.discoverInvocations shouldBe 0
            }
        }

    @Test
    fun `localCheckoutPathForRepo - should return the resolver path without a Bitbucket call`(): Unit =
        runBlocking {
            // GIVEN a configured workspace and projects directory, and a resolver that reports a checkout path
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = projectsDir.toString()
            val discoveryService = FakeDiscoveryService(resolvedLocalFolderPath = "/projects/repo-a")
            val service = service(settings, FakeCredentialStore(hasToken = true, storedToken = "token"), discoveryService = discoveryService)

            // WHEN
            val path = service.localCheckoutPathForRepo(repoSlug = "repo-a")

            // THEN the resolver's path is returned, it was asked about repo-a, and no discovery (the repo-existence
            // call) ran
            assertSoftly {
                path shouldBe "/projects/repo-a"
                discoveryService.resolveInvocations shouldBe 1
                discoveryService.resolvedRepoSlug shouldBe "repo-a"
                discoveryService.requestedWorkspaceSlug shouldBe "my-workspace"
                discoveryService.discoverInvocations shouldBe 0
            }
        }

    @Test
    fun `localCheckoutPathForRepo - should return empty when the workspace slug or projects directory is unset`(): Unit =
        runBlocking {
            // GIVEN no workspace slug and no projects directory
            val discoveryService = FakeDiscoveryService(resolvedLocalFolderPath = "/projects/repo-a")
            val service = service(settings(), FakeCredentialStore(hasToken = true, storedToken = "token"), discoveryService = discoveryService)

            // WHEN
            val withoutSlug = service.localCheckoutPathForRepo(repoSlug = "repo-a")

            // THEN nothing resolves and the resolver is never consulted
            assertSoftly {
                withoutSlug shouldBe ""
                discoveryService.resolveInvocations shouldBe 0
            }

            // AND with a slug but still no projects directory it also returns empty without consulting the resolver
            val settingsWithSlug = settings(workspaceSlug = "my-workspace")
            val resolverGuard = FakeDiscoveryService(resolvedLocalFolderPath = "/projects/repo-a")
            val serviceWithSlug = service(settingsWithSlug, FakeCredentialStore(hasToken = true, storedToken = "token"), discoveryService = resolverGuard)

            assertSoftly {
                serviceWithSlug.localCheckoutPathForRepo(repoSlug = "repo-a") shouldBe ""
                resolverGuard.resolveInvocations shouldBe 0
            }
        }

    @Test
    fun `localCheckoutPathForRepo - should return empty when the resolver finds no checkout`(): Unit =
        runBlocking {
            // GIVEN a configured workspace and projects directory, but the resolver reports no checkout
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = projectsDir.toString()
            val discoveryService = FakeDiscoveryService(resolvedLocalFolderPath = "")
            val service = service(settings, FakeCredentialStore(hasToken = true, storedToken = "token"), discoveryService = discoveryService)

            // WHEN
            val path = service.localCheckoutPathForRepo(repoSlug = "repo-a")

            // THEN the empty result is passed through (the click handler browses the web page instead)
            assertSoftly {
                path shouldBe ""
                discoveryService.resolveInvocations shouldBe 1
            }
        }

    @Test
    fun `resetApiToken - should leave the persisted avatar URL map empty`(): Unit =
        runBlocking {
            // GIVEN a stored avatar URL map
            val settings = settings(workspaceSlug = "my-workspace")
            settings.replaceRepoAvatarUrls(mapOf("repo-a" to "https://cdn.example.com/a.png"))
            val service = service(settings, FakeCredentialStore(hasToken = true, storedToken = "token"))

            // WHEN
            service.resetApiToken()

            // THEN clearWatchedProjects wiped the avatar URLs too
            settings.repoAvatarUrls shouldBe emptyMap()
        }

    @Test
    fun `clearProjectsDirectory - should leave the persisted avatar URL map empty`(): Unit =
        runBlocking {
            // GIVEN a stored avatar URL map and a projects directory
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = Paths.get("target/test-projects").toAbsolutePath().normalize().toString()
            settings.replaceRepoAvatarUrls(mapOf("repo-a" to "https://cdn.example.com/a.png"))
            val service = service(settings, FakeCredentialStore(hasToken = true, storedToken = "token"))

            // WHEN
            service.clearProjectsDirectory()

            // THEN clearWatchedProjects wiped the avatar URLs too
            settings.repoAvatarUrls shouldBe emptyMap()
        }

    @Test
    fun `workTreeDirectoryOrNull - should derive worktrees under the projects directory`(): Unit =
        runBlocking {
            // GIVEN a stored projects directory
            val settings = settings()
            val projectsDirectory = Paths.get(RELATIVE_ROOT, "bbcloud").toAbsolutePath().normalize().toString()
            settings.projectsDirectory = projectsDirectory
            val service = service(settings, FakeCredentialStore(hasToken = true))

            // WHEN / THEN the work tree root is derived as $projectsDirectory/.worktrees, normalized.
            val expected = Paths.get(projectsDirectory).resolve(".worktrees").toAbsolutePath().normalize().toString()
            service.workTreeDirectoryOrNull() shouldBe expected
        }

    @Test
    fun `workTreeDirectoryOrNull - should be null when no projects directory is set`(): Unit =
        runBlocking {
            // GIVEN no projects directory
            val settings = settings()
            val service = service(settings, FakeCredentialStore(hasToken = true))

            // WHEN / THEN there is nothing to derive from.
            service.workTreeDirectoryOrNull() shouldBe null

            settings.projectsDirectory = "   "
            service.workTreeDirectoryOrNull() shouldBe null
        }

    @Test
    fun `saveProjectsDirectory - should derive a non-null work tree root`(): Unit =
        runBlocking {
            // GIVEN
            val settings = settings()
            val service = service(settings, FakeCredentialStore(hasToken = true))

            // WHEN a projects directory is saved
            service.saveProjectsDirectory(absolutePath = projectsDir.toString())

            // THEN the derived work tree root is $projectsDirectory/.worktrees
            val expected = Paths.get(projectsDir.toString()).resolve(".worktrees").toAbsolutePath().normalize().toString()
            assertSoftly {
                service.workTreeDirectoryOrNull() shouldBe expected
                // best-effort eager creation leaves the folder on disk
                java.nio.file.Files.isDirectory(Paths.get(expected)) shouldBe true
            }
        }

    @Test
    fun `listWorkspaces - should delegate to the workspace client`(): Unit =
        runBlocking {
            val workspaces = listOf(BitbucketWorkspace(slug = "my-workspace", uuid = null))
            val client = FakeWorkspaceClient(WorkspaceListResult.Success(workspaces))
            val service = service(settings(), FakeCredentialStore(hasToken = true), client)

            service.listWorkspaces(apiToken = "token") shouldBe WorkspaceListResult.Success(workspaces)
            client.requestedToken shouldBe "token"
        }

    @Test
    fun `storedTokenOrNull - should return the stored token`(): Unit =
        runBlocking {
            val service = service(settings(), FakeCredentialStore(hasToken = true, storedToken = "token"))

            service.storedTokenOrNull() shouldBe "token"
        }

    @Test
    fun `watchedProjectsForCurrentWorkspace - should return every stored mapping with the configured slug attached`(): Unit =
        runBlocking {
            val settings = settings(workspaceSlug = "my-workspace")
            settings.replaceWatchedProjects(
                listOf(
                    WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace"),
                    WatchedProjectMapping(localFolderPath = "/local/b", remoteRepoSlug = "repo-b", workspace = "my-workspace")
                )
            )
            val service = service(settings, FakeCredentialStore(hasToken = true))

            // THEN the flat map holds only the current configuration's repositories; the configured slug is attached.
            assertSoftly {
                service.watchedProjectsForCurrentWorkspace().map { it.remoteRepoSlug } shouldBe listOf("repo-a", "repo-b")
                service.watchedProjectsForCurrentWorkspace().map { it.workspace }.toSet() shouldBe setOf("my-workspace")
            }
        }

    @Test
    fun `watchedProjectsForCurrentWorkspace - should return empty when no slug is configured`(): Unit =
        runBlocking {
            val settings = settings()
            settings.replaceWatchedProjects(
                listOf(WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace"))
            )
            val service = service(settings, FakeCredentialStore(hasToken = true))

            service.watchedProjectsForCurrentWorkspace() shouldBe emptyList()
        }

    @Test
    fun `replaceWatchedProjects - should overwrite previous mappings`(): Unit =
        runBlocking {
            val settings = settings(workspaceSlug = "my-workspace")
            settings.replaceWatchedProjects(
                listOf(WatchedProjectMapping(localFolderPath = "/local/old", remoteRepoSlug = "old-repo", workspace = "my-workspace"))
            )
            val service = service(settings, FakeCredentialStore(hasToken = true))
            val newMappings = listOf(
                WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace")
            )

            service.replaceWatchedProjects(newMappings)

            // The flat map keys by slug only, so the workspace and local folder path are not persisted and reload empty.
            settings.watchedProjects shouldBe listOf(
                WatchedProjectMapping(localFolderPath = "", remoteRepoSlug = "repo-a", workspace = "")
            )
        }

    @Test
    fun `clearWatchedProjects - should remove all mappings`(): Unit =
        runBlocking {
            val settings = settings(workspaceSlug = "my-workspace")
            settings.replaceWatchedProjects(
                listOf(WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace"))
            )
            val service = service(settings, FakeCredentialStore(hasToken = true))

            service.clearWatchedProjects()

            settings.watchedProjects shouldBe emptyList()
        }

    @Test
    fun `clearProjectsDirectory - should clear the directory and the watched projects`(): Unit =
        runBlocking {
            // GIVEN
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = Paths.get(RELATIVE_ROOT, "bbcloud").toAbsolutePath().normalize().toString()
            settings.replaceWatchedProjects(
                listOf(WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace"))
            )
            val service = service(settings, FakeCredentialStore(hasToken = true, storedToken = "token"))

            // WHEN
            service.clearProjectsDirectory()

            // THEN
            assertSoftly {
                service.projectsDirectoryOrNull() shouldBe null
                settings.watchedProjects shouldBe emptyList()
                service.workspaceSlugOrNull() shouldBe "my-workspace"
            }
        }

    @Test
    fun `rescanWatchedProjects - should return NotConfigured when not configured`(): Unit =
        runBlocking {
            val service = service(settings(), FakeCredentialStore(hasToken = false))

            val result = service.rescanWatchedProjects()

            result.shouldBeInstanceOf<WatchedProjectsDiscoveryResult.NotConfigured>()
        }

    @Test
    fun `rescanWatchedProjects - should delegate to the discovery service and persist on success`(): Unit =
        runBlocking {
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = Paths.get("target/test-projects").toAbsolutePath().normalize().toString()
            val mappings = listOf(
                WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace")
            )
            val discoveryService = FakeDiscoveryService(result = WatchedProjectsDiscoveryResult.Success(mappings))
            val service = service(settings, FakeCredentialStore(hasToken = true, storedToken = "token"), discoveryService = discoveryService)

            val result = service.rescanWatchedProjects()

            assertSoftly {
                result.shouldBeInstanceOf<WatchedProjectsDiscoveryResult.Success>()
                discoveryService.discoverInvocations shouldBe 1
                discoveryService.requestedWorkspaceSlug shouldBe "my-workspace"
                // The flat map keys by slug only, so the workspace and local folder path are not persisted.
                settings.watchedProjects shouldBe listOf(
                    WatchedProjectMapping(localFolderPath = "", remoteRepoSlug = "repo-a", workspace = "")
                )
            }
        }

    @Test
    fun `rescanWatchedProjects - should keep previous mappings on VerifyFailed in the Sync mode`(): Unit =
        runBlocking {
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = Paths.get("target/test-projects").toAbsolutePath().normalize().toString()
            settings.replaceWatchedProjects(
                listOf(WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace"))
            )
            val discoveryService = FakeDiscoveryService(result = WatchedProjectsDiscoveryResult.VerifyFailed)
            val service = service(settings, FakeCredentialStore(hasToken = true, storedToken = "token"), discoveryService = discoveryService)

            // WHEN a manual Sync (clearFirst = false) hits a transient verify failure
            service.rescanWatchedProjects().shouldBeInstanceOf<WatchedProjectsDiscoveryResult.VerifyFailed>()

            // THEN the previous entry is kept; the slug-keyed map reloads with an empty workspace and path.
            settings.watchedProjects shouldBe listOf(
                WatchedProjectMapping(localFolderPath = "", remoteRepoSlug = "repo-a", workspace = "")
            )
        }

    @Test
    fun `rescanWatchedProjects - should keep previous mappings on ProjectsDirectoryUnreadable in the Sync mode`(): Unit =
        runBlocking {
            // GIVEN a stored mapping and a scan that reports the directory as unreadable (a transient read failure)
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = Paths.get("target/test-projects").toAbsolutePath().normalize().toString()
            settings.replaceWatchedProjects(
                listOf(WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace"))
            )
            val discoveryService = FakeDiscoveryService(result = WatchedProjectsDiscoveryResult.ProjectsDirectoryUnreadable)
            val service = service(settings, FakeCredentialStore(hasToken = true, storedToken = "token"), discoveryService = discoveryService)

            // WHEN a manual Sync (clearFirst = false) hits the unreadable directory
            service.rescanWatchedProjects().shouldBeInstanceOf<WatchedProjectsDiscoveryResult.ProjectsDirectoryUnreadable>()

            // THEN the previous mapping is kept, like VerifyFailed; the slug-keyed map reloads empty workspace and path.
            settings.watchedProjects shouldBe listOf(
                WatchedProjectMapping(localFolderPath = "", remoteRepoSlug = "repo-a", workspace = "")
            )
        }

    @Test
    fun `rescanWatchedProjects - clearFirst should empty the map before discovery and leave it empty on VerifyFailed`(): Unit =
        runBlocking {
            // GIVEN a previously watched entry and a scan that fails to verify
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = Paths.get("target/test-projects").toAbsolutePath().normalize().toString()
            settings.replaceWatchedProjects(
                listOf(WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace", watched = true))
            )
            val discoveryService = FakeDiscoveryService(result = WatchedProjectsDiscoveryResult.VerifyFailed)
            val service = service(settings, FakeCredentialStore(hasToken = true, storedToken = "token"), discoveryService = discoveryService)

            // WHEN a clear-first rescan (a config change) hits a verify failure
            service.rescanWatchedProjects(clearFirst = true).shouldBeInstanceOf<WatchedProjectsDiscoveryResult.VerifyFailed>()

            // THEN the map was emptied first and stays empty; there are no stale entries to restore.
            settings.watchedProjects shouldBe emptyList()
        }

    @Test
    fun `rescanWatchedProjects - clearFirst should rebuild rediscovered repositories as unwatched`(): Unit =
        runBlocking {
            // GIVEN a previously watched repo-a that the scan finds again
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = Paths.get("target/test-projects").toAbsolutePath().normalize().toString()
            settings.replaceWatchedProjects(
                listOf(WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace", watched = true))
            )
            val scanned = listOf(
                WatchedProjectMapping(localFolderPath = "/projects/repo-a", remoteRepoSlug = "repo-a", workspace = "my-workspace")
            )
            val discoveryService = FakeDiscoveryService(result = WatchedProjectsDiscoveryResult.Success(scanned))
            val service = service(settings, FakeCredentialStore(hasToken = true, storedToken = "token"), discoveryService = discoveryService)

            // WHEN a clear-first rescan runs
            service.rescanWatchedProjects(clearFirst = true)

            // THEN the old watched flag is NOT carried over; repo-a comes back unwatched.
            settings.watchedProjects shouldBe listOf(
                WatchedProjectMapping(localFolderPath = "", remoteRepoSlug = "repo-a", workspace = "", watched = false)
            )
        }

    @Test
    fun `rescanWatchedProjects - should serialize overlapping scans with the mutex`(): Unit =
        runBlocking {
            // GIVEN a discovery service that asserts it is never entered re-entrantly, so an interleaved rescan would fail
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = Paths.get("target/test-projects").toAbsolutePath().normalize().toString()
            val discoveryService = OverlapDetectingDiscoveryService()
            val service = service(settings, FakeCredentialStore(hasToken = true, storedToken = "token"), discoveryService = discoveryService)

            // WHEN two rescans run concurrently
            val first = async { service.rescanWatchedProjects() }
            val second = async { service.rescanWatchedProjects() }
            first.await()
            second.await()

            // THEN the mutex kept them from overlapping and both completed
            assertSoftly {
                discoveryService.observedOverlap shouldBe false
                discoveryService.discoverInvocations shouldBe 2
            }
        }

    @Test
    fun `saveSetup - should empty and rebuild watched projects when workspace slug changes and directory is set`(): Unit =
        runBlocking {
            val settings = settings(workspaceSlug = "old-slug")
            settings.projectsDirectory = Paths.get("target/test-projects").toAbsolutePath().normalize().toString()
            settings.replaceWatchedProjects(
                listOf(WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "old-slug", watched = true))
            )
            val store = FakeCredentialStore(hasToken = true, storedToken = "token")
            val discoveryService = FakeDiscoveryService(result = WatchedProjectsDiscoveryResult.Success(emptyList()))
            val service = service(settings, store, discoveryService = discoveryService)

            service.saveSetup(workspaceSlug = "new-slug", apiTokenInput = "")

            assertSoftly {
                settings.workspaceSlug shouldBe "new-slug"
                // A clear-first rescan ran for the new slug and the empty scan left the map empty.
                discoveryService.discoverInvocations shouldBe 1
                discoveryService.requestedWorkspaceSlug shouldBe "new-slug"
                settings.watchedProjects shouldBe emptyList()
            }
        }

    @Test
    fun `saveSetup - should not touch watched projects when neither slug nor token changes`(): Unit =
        runBlocking {
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = Paths.get("target/test-projects").toAbsolutePath().normalize().toString()
            settings.replaceWatchedProjects(
                listOf(WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace", watched = true))
            )
            val store = FakeCredentialStore(hasToken = true, storedToken = "token")
            val discoveryService = FakeDiscoveryService(result = WatchedProjectsDiscoveryResult.Success(emptyList()))
            val service = service(settings, store, discoveryService = discoveryService)

            // A blank token field keeps the stored token, so this is neither a slug nor a token change.
            service.saveSetup(workspaceSlug = "my-workspace", apiTokenInput = "")

            assertSoftly {
                discoveryService.discoverInvocations shouldBe 0
                // The watched map is left intact (reloads slug-keyed with empty workspace and path).
                settings.watchedProjects shouldBe listOf(
                    WatchedProjectMapping(localFolderPath = "", remoteRepoSlug = "repo-a", workspace = "", watched = true)
                )
            }
        }

    @Test
    fun `saveSetup - should empty watched projects on a slug change even when no projects directory is set`(): Unit =
        runBlocking {
            val settings = settings(workspaceSlug = "old-slug")
            settings.replaceWatchedProjects(
                listOf(WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "old-slug", watched = true))
            )
            val store = FakeCredentialStore(hasToken = true, storedToken = "token")
            val discoveryService = FakeDiscoveryService(result = WatchedProjectsDiscoveryResult.Success(emptyList()))
            val service = service(settings, store, discoveryService = discoveryService)

            service.saveSetup(workspaceSlug = "new-slug", apiTokenInput = "")

            assertSoftly {
                // With no directory there is nothing to rebuild, so no discovery runs, but the map is still emptied.
                discoveryService.discoverInvocations shouldBe 0
                settings.watchedProjects shouldBe emptyList()
            }
        }

    @Test
    fun `saveSetup - should empty and rebuild watched projects when the API token changes`(): Unit =
        runBlocking {
            // GIVEN a stored token and watched entries, with the slug left unchanged
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = Paths.get("target/test-projects").toAbsolutePath().normalize().toString()
            settings.replaceWatchedProjects(
                listOf(WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace", watched = true))
            )
            val store = FakeCredentialStore(hasToken = true, storedToken = "old-token")
            val discoveryService = FakeDiscoveryService(result = WatchedProjectsDiscoveryResult.Success(emptyList()))
            val service = service(settings, store, discoveryService = discoveryService)

            // WHEN a different, non-empty token is saved (same slug)
            service.saveSetup(workspaceSlug = "my-workspace", apiTokenInput = "new-token")

            // THEN the token is updated and the map is emptied then rebuilt (the empty scan leaves it empty).
            assertSoftly {
                store.savedToken shouldBe "new-token"
                discoveryService.discoverInvocations shouldBe 1
                settings.watchedProjects shouldBe emptyList()
            }
        }

    @Test
    fun `saveSetup - should not touch watched projects when the token is unchanged`(): Unit =
        runBlocking {
            // GIVEN a stored token and watched entries
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = Paths.get("target/test-projects").toAbsolutePath().normalize().toString()
            settings.replaceWatchedProjects(
                listOf(WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace", watched = true))
            )
            val store = FakeCredentialStore(hasToken = true, storedToken = "token")
            val discoveryService = FakeDiscoveryService(result = WatchedProjectsDiscoveryResult.Success(emptyList()))
            val service = service(settings, store, discoveryService = discoveryService)

            // WHEN the same token is re-entered (not a change) and the slug is unchanged
            service.saveSetup(workspaceSlug = "my-workspace", apiTokenInput = "token")

            // THEN nothing is rescanned and the map is intact.
            assertSoftly {
                discoveryService.discoverInvocations shouldBe 0
                settings.watchedProjects shouldBe listOf(
                    WatchedProjectMapping(localFolderPath = "", remoteRepoSlug = "repo-a", workspace = "", watched = true)
                )
            }
        }

    @Test
    fun `rescanWatchedProjects - should store a newly scanned mapping as unwatched by default`(): Unit =
        runBlocking {
            // GIVEN
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = Paths.get("target/test-projects").toAbsolutePath().normalize().toString()
            val scanned = WatchedProjectMapping(localFolderPath = "/projects/repo-a", remoteRepoSlug = "repo-a", workspace = "my-workspace")
            val discoveryService = FakeDiscoveryService(result = WatchedProjectsDiscoveryResult.Success(listOf(scanned)))
            val service = service(settings, FakeCredentialStore(hasToken = true, storedToken = "token"), discoveryService = discoveryService)

            // WHEN
            service.rescanWatchedProjects()

            // THEN
            val stored = settings.watchedProjects.single()
            assertSoftly {
                stored.remoteRepoSlug shouldBe "repo-a"
                stored.watched shouldBe false
            }
        }

    @Test
    fun `watchedRepoSlugsForCurrentWorkspace - should return only the watched slugs`(): Unit =
        runBlocking {
            // GIVEN
            val settings = settings(workspaceSlug = "my-workspace")
            settings.replaceWatchedProjects(
                listOf(
                    WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace", watched = true),
                    WatchedProjectMapping(localFolderPath = "/local/b", remoteRepoSlug = "repo-b", workspace = "my-workspace", watched = false),
                    WatchedProjectMapping(localFolderPath = "/local/c", remoteRepoSlug = "repo-c", workspace = "my-workspace", watched = true)
                )
            )
            val service = service(settings, FakeCredentialStore(hasToken = true))

            // WHEN
            val slugs = service.watchedRepoSlugsForCurrentWorkspace()

            // THEN the watched slugs come back sorted by slug.
            slugs shouldBe listOf("repo-a", "repo-c")
        }

    @Test
    fun `setWatched - should change only the matching entry`(): Unit =
        runBlocking {
            // GIVEN
            val settings = settings(workspaceSlug = "my-workspace")
            val repoA = WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace", watched = true)
            val repoB = WatchedProjectMapping(localFolderPath = "/local/b", remoteRepoSlug = "repo-b", workspace = "my-workspace", watched = true)
            settings.replaceWatchedProjects(listOf(repoA, repoB))
            val service = service(settings, FakeCredentialStore(hasToken = true))

            // WHEN
            service.setWatched(mapping = repoA, watched = false)

            // THEN the matching entry is unwatched, the other stays watched; the slug-keyed map reloads empty workspace.
            settings.watchedProjects shouldBe listOf(
                WatchedProjectMapping(localFolderPath = "", remoteRepoSlug = "repo-a", workspace = "", watched = false),
                WatchedProjectMapping(localFolderPath = "", remoteRepoSlug = "repo-b", workspace = "", watched = true)
            )
        }

    @Test
    fun `setAllWatched - should change every stored entry`(): Unit =
        runBlocking {
            // GIVEN
            val settings = settings(workspaceSlug = "my-workspace")
            val repoA = WatchedProjectMapping(localFolderPath = "/local/a", remoteRepoSlug = "repo-a", workspace = "my-workspace", watched = true)
            val repoB = WatchedProjectMapping(localFolderPath = "/local/b", remoteRepoSlug = "repo-b", workspace = "my-workspace", watched = false)
            settings.replaceWatchedProjects(listOf(repoA, repoB))
            val service = service(settings, FakeCredentialStore(hasToken = true))

            // WHEN
            service.setAllWatched(watched = false)

            // THEN every entry flips to unwatched; the slug-keyed map reloads sorted by slug with empty workspace.
            settings.watchedProjects shouldBe listOf(
                WatchedProjectMapping(localFolderPath = "", remoteRepoSlug = "repo-a", workspace = "", watched = false),
                WatchedProjectMapping(localFolderPath = "", remoteRepoSlug = "repo-b", workspace = "", watched = false)
            )
        }

    @Test
    fun `rescanWatchedProjects - should keep the watched flag of a repository that is still found in the Sync mode`(): Unit =
        runBlocking {
            // GIVEN a stored repo-a that the user has watched, and a rescan that finds it again plus a new repo-b
            val settings = settings(workspaceSlug = "my-workspace")
            settings.projectsDirectory = Paths.get("target/test-projects").toAbsolutePath().normalize().toString()
            val storedRepoA =
                WatchedProjectMapping(localFolderPath = "/projects/repo-a", remoteRepoSlug = "repo-a", workspace = "my-workspace", watched = true)
            settings.replaceWatchedProjects(listOf(storedRepoA))
            val scannedRepoA = WatchedProjectMapping(localFolderPath = "/projects/repo-a", remoteRepoSlug = "repo-a", workspace = "my-workspace")
            val scannedRepoB = WatchedProjectMapping(localFolderPath = "/projects/repo-b", remoteRepoSlug = "repo-b", workspace = "my-workspace")
            val discoveryService = FakeDiscoveryService(result = WatchedProjectsDiscoveryResult.Success(listOf(scannedRepoA, scannedRepoB)))
            val service = service(settings, FakeCredentialStore(hasToken = true, storedToken = "token"), discoveryService = discoveryService)

            // WHEN a manual Sync (clearFirst = false) runs
            service.rescanWatchedProjects()

            // THEN repo-a keeps its watched flag, repo-b comes in unwatched; the slug-keyed map reloads empty workspace.
            settings.watchedProjects shouldBe listOf(
                WatchedProjectMapping(localFolderPath = "", remoteRepoSlug = "repo-a", workspace = "", watched = true),
                WatchedProjectMapping(localFolderPath = "", remoteRepoSlug = "repo-b", workspace = "", watched = false)
            )
        }

    private fun service(
        settings: BbCloudSettings,
        store: FakeCredentialStore,
        client: FakeWorkspaceClient = FakeWorkspaceClient(WorkspaceListResult.Success(emptyList())),
        discoveryService: BbCloudWatchedProjectsDiscoveryService = FakeDiscoveryService()
    ): BbCloudConfigurationService {
        return BbCloudConfigurationService(
            settings = settings,
            credentialStore = store,
            workspaceClient = client,
            discoveryService = discoveryService
        )
    }

    private fun settings(workspaceSlug: String = "", organizationUrl: String = ""): BbCloudSettings {
        return BbCloudSettings().apply {
            this.workspaceSlug = workspaceSlug
            this.organizationUrl = organizationUrl
        }
    }

    private class FakeWorkspaceClient(private val result: WorkspaceListResult) : BitbucketWorkspaceClient {
        var requestedToken: String? = null

        override suspend fun listWorkspaces(apiToken: String): WorkspaceListResult {
            requestedToken = apiToken
            return result
        }
    }

    private class FakeCredentialStore(private var hasToken: Boolean, storedToken: String = "") : BbCloudCredentialStore {
        var savedToken: String = storedToken
        var saveInvocations: Int = 0
        var clearInvocations: Int = 0

        override suspend fun hasToken(): Boolean {
            return hasToken
        }

        override suspend fun getToken(): String? {
            return savedToken.takeIf { hasToken && it.isNotBlank() }
        }

        override suspend fun saveToken(token: String) {
            saveInvocations++
            savedToken = token
            hasToken = true
        }

        override suspend fun clearToken() {
            clearInvocations++
            savedToken = ""
            hasToken = false
        }
    }

    private class FakeDiscoveryService(
        var result: WatchedProjectsDiscoveryResult = WatchedProjectsDiscoveryResult.NotConfigured,
        private val resolvedLocalFolderPath: String = ""
    ) : BbCloudWatchedProjectsDiscoveryService(repositoryClient = mockk(relaxed = true)) {
        var requestedProjectsDirectory: String? = null
        var requestedWorkspaceSlug: String? = null
        var requestedApiToken: String? = null
        var discoverInvocations: Int = 0
        var resolveInvocations: Int = 0
        var resolvedRepoSlug: String? = null

        override suspend fun discover(projectsDirectory: String, configuredWorkspaceSlug: String, apiToken: String): WatchedProjectsDiscoveryResult {
            discoverInvocations++
            requestedProjectsDirectory = projectsDirectory
            requestedWorkspaceSlug = configuredWorkspaceSlug
            requestedApiToken = apiToken
            return result
        }

        override fun resolveLocalFolderPath(projectsDirectory: String, configuredWorkspaceSlug: String, repoSlug: String): String {
            resolveInvocations++
            requestedProjectsDirectory = projectsDirectory
            requestedWorkspaceSlug = configuredWorkspaceSlug
            resolvedRepoSlug = repoSlug
            return resolvedLocalFolderPath
        }
    }

    /**
     * A discovery fake that records whether [discover] was ever entered while a previous call was still inside it. The
     * service mutex should keep two concurrent rescans from overlapping, so [observedOverlap] must stay false. The short
     * yield widens the window an interleaving scan could slip through without introducing a timing assertion.
     */
    private class OverlapDetectingDiscoveryService : BbCloudWatchedProjectsDiscoveryService(repositoryClient = mockk(relaxed = true)) {
        var discoverInvocations: Int = 0
        var observedOverlap: Boolean = false
        private var inside: Boolean = false

        override suspend fun discover(projectsDirectory: String, configuredWorkspaceSlug: String, apiToken: String): WatchedProjectsDiscoveryResult {
            if (inside) {
                observedOverlap = true
            }
            inside = true
            discoverInvocations++
            yield()
            inside = false
            return WatchedProjectsDiscoveryResult.Success(emptyList())
        }
    }

    companion object {
        /** Relative, so the assertions stay independent of the working directory the tests happen to run in. */
        private const val RELATIVE_ROOT = "target/test-projects"
    }
}