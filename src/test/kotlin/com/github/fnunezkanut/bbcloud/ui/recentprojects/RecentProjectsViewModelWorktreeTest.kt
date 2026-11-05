package com.github.fnunezkanut.bbcloud.ui.recentprojects

import com.github.fnunezkanut.bbcloud.project.RecentProjectsSnapshot
import com.github.fnunezkanut.bbcloud.project.RecentProjectsSource
import com.intellij.openapi.Disposable
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import javax.swing.Icon

@Tag("unit")
class RecentProjectsViewModelWorktreeTest {
    private class FakeSource(var snapshot: RecentProjectsSnapshot) : RecentProjectsSource {
        val removed = mutableListOf<String>()

        override fun snapshot(): RecentProjectsSnapshot {
            return snapshot
        }

        override fun displayName(path: String): String {
            return path.substringAfterLast('/')
        }

        override fun setGroupExpanded(groupName: String, expanded: Boolean) = Unit

        override fun removePath(path: String) {
            removed += path
        }

        override fun subscribe(parentDisposable: Disposable, onChange: () -> Unit) = Unit
    }

    private class FakeAvatars(val urls: Map<String, String> = emptyMap(), val folders: Map<String, String> = emptyMap()) : RepoAvatarProvider {
        val prefetched = mutableListOf<String>()
        var onLoaded: () -> Unit = {}
        var cleared = 0

        override fun avatarUrlsBySlug(): Map<String, String> {
            return urls
        }

        override fun slugsByLocalFolder(): Map<String, String> {
            return folders
        }

        override fun avatarFor(url: String): Icon? {
            return null
        }

        override fun prefetch(urls: Collection<String>, onLoaded: () -> Unit) {
            prefetched.addAll(urls)
            this.onLoaded = onLoaded
        }

        override fun clear() {
            cleared++
        }
    }

    private val root = "/home/me/projects/.worktrees"
    private val worktree = "$root/repo--pr-12"
    private val plain = "/home/me/projects/plain"
    private val current = "/home/me/projects/current"

    private val snapshot = RecentProjectsSnapshot(recentPaths = listOf(worktree, plain, current), groups = emptyList())

    private class Calls {
        val removals = mutableListOf<Triple<Path, Path, Long>>()
        val unavailable = mutableListOf<RecentProjectRow>()
    }

    private fun viewModel(
        source: FakeSource,
        avatars: FakeAvatars = FakeAvatars(),
        calls: Calls = Calls(),
        sourceRoot: Path? = Path.of("/home/me/projects/repo")
    ): RecentProjectsViewModel {
        return RecentProjectsViewModel(
            source = source,
            readBranch = { _ -> null },
            pathExists = { _ -> true },
            userHome = "/home/me",
            coroutineScope = CoroutineScope(Job() + Dispatchers.Unconfined),
            openProject = { _ -> },
            onLoadFailed = { _ -> },
            runOnEdt = { block -> block() },
            avatars = avatars,
            workTreeDirectory = { root },
            currentProjectBasePath = { current },
            resolveSourceRoot = { _, _ -> sourceRoot },
            removeWorktree = { sourceCheckoutRoot, worktreePath, prNumber -> calls.removals += Triple(sourceCheckoutRoot, worktreePath, prNumber) },
            onWorktreeRemovalUnavailable = { row -> calls.unavailable += row },
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    private fun rowOf(viewModel: RecentProjectsViewModel, name: String): RecentProjectRow {
        return viewModel.state.value.ungrouped.single { it.name == name }
    }

    @Test
    fun `reload - should flag work trees, the current project, and the repository slug`() {
        // GIVEN
        val avatars = FakeAvatars(folders = mapOf(plain to "plain-slug"))
        val viewModel = viewModel(source = FakeSource(snapshot = snapshot), avatars = avatars)

        // WHEN
        viewModel.reload()

        // THEN
        assertSoftly {
            rowOf(viewModel, "repo--pr-12").isPullRequestWorktree shouldBe true
            rowOf(viewModel, "repo--pr-12").repoSlug shouldBe "repo"
            rowOf(viewModel, "plain").isPullRequestWorktree shouldBe false
            rowOf(viewModel, "plain").repoSlug shouldBe "plain-slug"
            rowOf(viewModel, "current").isCurrentProject shouldBe true
            rowOf(viewModel, "plain").isCurrentProject shouldBe false
            rowOf(viewModel, "current").repoSlug shouldBe null
        }
    }

    @Test
    fun `reload - should prefetch the distinct avatar URLs and bump the revision when one loads`() {
        // GIVEN
        val avatars = FakeAvatars(
            urls = mapOf("repo" to "https://avatar/repo", "plain-slug" to "https://avatar/plain", "unused" to "https://avatar/unused"),
            folders = mapOf(plain to "plain-slug")
        )
        val viewModel = viewModel(source = FakeSource(snapshot = snapshot), avatars = avatars)

        // WHEN
        viewModel.reload()
        val before = viewModel.state.value.avatarRevision
        avatars.onLoaded()

        // THEN
        assertSoftly {
            avatars.prefetched.toSet() shouldBe setOf("https://avatar/repo", "https://avatar/plain")
            viewModel.state.value.avatarRevision shouldBe before + 1
        }
    }

    @Test
    fun `repoAvatarFor - should be null without a slug, a URL, or a loaded image`() {
        // GIVEN
        val avatars = FakeAvatars(urls = mapOf("repo" to "https://avatar/repo"))
        val viewModel = viewModel(source = FakeSource(snapshot = snapshot), avatars = avatars)
        viewModel.reload()

        // WHEN / THEN
        assertSoftly {
            viewModel.repoAvatarFor(row = rowOf(viewModel, "current")) shouldBe null
            viewModel.repoAvatarFor(row = rowOf(viewModel, "repo--pr-12")) shouldBe null
        }
    }

    @Test
    fun `clearAvatars - should clear the provider and bump the revision`() {
        // GIVEN
        val avatars = FakeAvatars()
        val viewModel = viewModel(source = FakeSource(snapshot = snapshot), avatars = avatars)
        viewModel.reload()
        val before = viewModel.state.value.avatarRevision

        // WHEN
        viewModel.clearAvatars()

        // THEN
        assertSoftly {
            avatars.cleared shouldBe 1
            viewModel.state.value.avatarRevision shouldBe before + 1
        }
    }

    @Test
    fun `onRemoveRequested - should remove only that path`() {
        // GIVEN
        val source = FakeSource(snapshot = snapshot)
        val viewModel = viewModel(source = source)
        viewModel.reload()

        // WHEN
        viewModel.onRemoveRequested(row = rowOf(viewModel, "plain"))

        // THEN
        source.removed shouldBe listOf(plain)
    }

    @Test
    fun `onRemoveWorktreeRequested - should hand over the resolved source, path, and PR number`() {
        // GIVEN
        val calls = Calls()
        val viewModel = viewModel(source = FakeSource(snapshot = snapshot), calls = calls)
        viewModel.reload()

        // WHEN
        viewModel.onRemoveWorktreeRequested(row = rowOf(viewModel, "repo--pr-12"))

        // THEN
        calls.removals shouldBe listOf(Triple(Path.of("/home/me/projects/repo"), Path.of(worktree), 12L))
    }

    @Test
    fun `onRemoveWorktreeRequested - should ignore plain projects and the current project`() {
        // GIVEN
        val calls = Calls()
        val viewModel = viewModel(source = FakeSource(snapshot = snapshot), calls = calls)
        viewModel.reload()

        // WHEN
        viewModel.onRemoveWorktreeRequested(row = rowOf(viewModel, "plain"))
        viewModel.onRemoveWorktreeRequested(row = rowOf(viewModel, "current"))
        viewModel.onRemoveWorktreeRequested(row = rowOf(viewModel, "repo--pr-12").copy(isCurrentProject = true))

        // THEN
        assertSoftly {
            calls.removals shouldBe emptyList()
            calls.unavailable shouldBe emptyList()
        }
    }

    @Test
    fun `onRemoveWorktreeRequested - should report when the source cannot be resolved`() {
        // GIVEN
        val calls = Calls()
        val viewModel = viewModel(source = FakeSource(snapshot = snapshot), calls = calls, sourceRoot = null)
        viewModel.reload()
        val row = rowOf(viewModel, "repo--pr-12")

        // WHEN
        viewModel.onRemoveWorktreeRequested(row = row)

        // THEN
        assertSoftly {
            calls.removals shouldBe emptyList()
            calls.unavailable shouldBe listOf(row)
        }
    }
}