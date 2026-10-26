package com.github.fnunezkanut.bbcloud.project

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Paths

@Tag("unit")
class WatchedCheckoutResolverTest {
    private val workspace = "my-workspace"
    private val repoSlug = "my-repo"
    private val otherWorkspace = "other-workspace"
    private val otherRepoSlug = "other-repo"

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `resolve - should return NoEntry when no mapping has the repository slug`() {
        // GIVEN
        val mappings = listOf(
            WatchedProjectMapping("/path/to/other", otherRepoSlug, workspace)
        )

        // WHEN
        val result = WatchedCheckoutResolver.resolve(
            mappings = mappings,
            workspace = workspace,
            repoSlug = repoSlug
        )

        // THEN
        result shouldBe WatchedCheckoutResolution.NoEntry
    }

    @Test
    fun `resolve - should return NoEntry when the matching slug belongs to another workspace`() {
        // GIVEN
        val mappings = listOf(
            WatchedProjectMapping("/path/to/repo", repoSlug, otherWorkspace)
        )

        // WHEN
        val result = WatchedCheckoutResolver.resolve(
            mappings = mappings,
            workspace = workspace,
            repoSlug = repoSlug
        )

        // THEN
        result shouldBe WatchedCheckoutResolution.NoEntry
    }

    @Test
    fun `resolve - should match the repository slug exactly and case-sensitively`() {
        // GIVEN
        val mappings = listOf(
            WatchedProjectMapping("/path/to/repo", "My-Repo", workspace)
        )

        // WHEN
        val result = WatchedCheckoutResolver.resolve(
            mappings = mappings,
            workspace = workspace,
            repoSlug = "my-repo"
        )

        // THEN
        result shouldBe WatchedCheckoutResolution.NoEntry
    }

    @Test
    fun `resolve - should return Available when the entry folder is a directory and the entry is watched`() {
        // GIVEN
        val mappings = listOf(
            WatchedProjectMapping("/valid/path", repoSlug, workspace, watched = true)
        )

        // WHEN
        val result = WatchedCheckoutResolver.resolve(
            mappings = mappings,
            workspace = workspace,
            repoSlug = repoSlug,
            isDirectory = { _ -> true }
        )

        // THEN
        val available = result.shouldBeInstanceOf<WatchedCheckoutResolution.Available>()
        available.checkoutPath shouldBe Paths.get("/valid/path").toAbsolutePath().normalize()
    }

    @Test
    fun `resolve - should return Available when the entry folder is a directory and the entry is unwatched`() {
        // GIVEN
        val mappings = listOf(
            WatchedProjectMapping("/valid/path", repoSlug, workspace, watched = false)
        )

        // WHEN
        val result = WatchedCheckoutResolver.resolve(
            mappings = mappings,
            workspace = workspace,
            repoSlug = repoSlug,
            isDirectory = { _ -> true }
        )

        // THEN
        val available = result.shouldBeInstanceOf<WatchedCheckoutResolution.Available>()
        available.checkoutPath shouldBe Paths.get("/valid/path").toAbsolutePath().normalize()
    }

    @Test
    fun `resolve - should return Available with a normalized absolute path for a real directory`() {
        // GIVEN
        val realDir = File(tempDir, "real-repo")
        realDir.mkdir()
        val mappings = listOf(
            WatchedProjectMapping(realDir.absolutePath, repoSlug, workspace)
        )

        // WHEN
        val result = WatchedCheckoutResolver.resolve(
            mappings = mappings,
            workspace = workspace,
            repoSlug = repoSlug
        )

        // THEN
        val available = result.shouldBeInstanceOf<WatchedCheckoutResolution.Available>()
        available.checkoutPath shouldBe realDir.toPath().toAbsolutePath().normalize()
    }

    @Test
    fun `resolve - should return FolderMissing when the stored folder is not a directory`() {
        // GIVEN
        val mappings = listOf(
            WatchedProjectMapping("/not/a/directory", repoSlug, workspace)
        )

        // WHEN
        val result = WatchedCheckoutResolver.resolve(
            mappings = mappings,
            workspace = workspace,
            repoSlug = repoSlug,
            isDirectory = { _ -> false }
        )

        // THEN
        val missing = result.shouldBeInstanceOf<WatchedCheckoutResolution.FolderMissing>()
        missing.localFolderPath shouldBe "/not/a/directory"
    }

    @Test
    fun `resolve - should return FolderMissing when the stored path is blank`() {
        // GIVEN
        val mappings = listOf(
            WatchedProjectMapping("   ", repoSlug, workspace)
        )

        // WHEN
        val result = WatchedCheckoutResolver.resolve(
            mappings = mappings,
            workspace = workspace,
            repoSlug = repoSlug,
            isDirectory = { _ -> true }
        )

        // THEN
        val missing = result.shouldBeInstanceOf<WatchedCheckoutResolution.FolderMissing>()
        missing.localFolderPath shouldBe "   "
    }

    @Test
    fun `resolve - should return FolderMissing when the stored path is invalid`() {
        // GIVEN
        val mappings = listOf(
            WatchedProjectMapping("/path\u0000with\u0000null", repoSlug, workspace)
        )

        // WHEN
        val result = WatchedCheckoutResolver.resolve(
            mappings = mappings,
            workspace = workspace,
            repoSlug = repoSlug,
            isDirectory = { _ -> true }
        )

        // THEN
        val missing = result.shouldBeInstanceOf<WatchedCheckoutResolution.FolderMissing>()
        missing.localFolderPath shouldBe "/path\u0000with\u0000null"
    }

    @Test
    fun `resolve - should trim the stored path before checking it`() {
        // GIVEN
        val mappings = listOf(
            WatchedProjectMapping("  /valid/path  ", repoSlug, workspace)
        )

        // WHEN
        val result = WatchedCheckoutResolver.resolve(
            mappings = mappings,
            workspace = workspace,
            repoSlug = repoSlug,
            isDirectory = { _ -> true }
        )

        // THEN
        val available = result.shouldBeInstanceOf<WatchedCheckoutResolution.Available>()
        available.checkoutPath shouldBe Paths.get("/valid/path").toAbsolutePath().normalize()
    }

    @Test
    fun `resolve - should use the first matching entry when duplicates exist`() {
        // GIVEN
        val mappings = listOf(
            WatchedProjectMapping("/first/path", repoSlug, workspace),
            WatchedProjectMapping("/second/path", repoSlug, workspace)
        )

        // WHEN
        val result = WatchedCheckoutResolver.resolve(
            mappings = mappings,
            workspace = workspace,
            repoSlug = repoSlug,
            isDirectory = { _ -> true }
        )

        // THEN
        val available = result.shouldBeInstanceOf<WatchedCheckoutResolution.Available>()
        available.checkoutPath shouldBe Paths.get("/first/path").toAbsolutePath().normalize()
    }

    @Test
    fun `resolve - should use the stored folder of an entry outside any projects directory`() {
        // GIVEN
        val mappings = listOf(
            WatchedProjectMapping("/elsewhere/other-name", repoSlug, workspace)
        )

        // WHEN
        val result = WatchedCheckoutResolver.resolve(
            mappings = mappings,
            workspace = workspace,
            repoSlug = repoSlug,
            isDirectory = { _ -> true }
        )

        // THEN
        val available = result.shouldBeInstanceOf<WatchedCheckoutResolution.Available>()
        available.checkoutPath shouldBe Paths.get("/elsewhere/other-name").toAbsolutePath().normalize()
    }
}