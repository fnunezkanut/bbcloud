package com.github.fnunezkanut.bbcloud.review

import com.intellij.openapi.project.Project
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class WorktreePullRequestPageReloaderTest {
    private fun reloader(): WorktreePullRequestPageReloader {
        val project = mockk<Project>(relaxed = true)
        every { project.basePath } returns null
        return WorktreePullRequestPageReloader(project)
    }

    @Test
    fun `requestReload - should pass the sync request through to the registered session handler`() {
        // GIVEN
        val reloader = reloader()
        val received = mutableListOf<PageReloadRequest>()
        reloader.registerSessionReloadHandler { request -> received += request }

        // WHEN
        reloader.requestReload(PageReloadRequest(syncBranches = true))
        reloader.requestReload(PageReloadRequest(revealNewestActivity = true))
        reloader.requestReload()

        // THEN
        received shouldContainExactly listOf(
            PageReloadRequest(syncBranches = true),
            PageReloadRequest(revealNewestActivity = true),
            PageReloadRequest()
        )
    }

    @Test
    fun `requestReload - should stop calling a handler after it was unregistered`() {
        // GIVEN
        val reloader = reloader()
        val received = mutableListOf<PageReloadRequest>()
        val handler: (PageReloadRequest) -> Unit = { request -> received += request }
        reloader.registerSessionReloadHandler(handler)
        reloader.unregisterSessionReloadHandler(handler)

        // WHEN the project is not a work tree, so the context-only fallback is skipped
        reloader.requestReload(PageReloadRequest(syncBranches = true))

        // THEN
        received.shouldBeEmpty()
    }

    @Test
    fun `PageReloadRequest - should default to neither revealing nor syncing`() {
        val request = PageReloadRequest()

        request.revealNewestActivity shouldBe false
        request.syncBranches shouldBe false
    }
}