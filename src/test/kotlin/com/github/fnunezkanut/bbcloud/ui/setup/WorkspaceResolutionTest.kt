package com.github.fnunezkanut.bbcloud.ui.setup

import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketWorkspace
import com.github.fnunezkanut.bbcloud.bitbucket.WorkspaceListResult
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class WorkspaceResolutionTest {
    @Test
    fun `resolve - should auto-select the only workspace without asking`() {
        // GIVEN
        val result = success("only-workspace")

        // WHEN
        val resolution = WorkspaceResolutionPolicy.resolve(result = result, preferredSlug = "only-workspace")

        // THEN
        resolution shouldBe WorkspaceResolution.Resolved("only-workspace")
    }

    @Test
    fun `resolve - should auto-select the only workspace when another one was preferred`() {
        // GIVEN
        val result = success("only-workspace")

        // WHEN
        val resolution = WorkspaceResolutionPolicy.resolve(result = result, preferredSlug = "removed")

        // THEN
        resolution shouldBe WorkspaceResolution.Resolved("only-workspace")
    }

    @Test
    fun `resolve - should preselect the preferred slug when it is still reachable`() {
        // GIVEN
        val result = success("alpha", "bravo", "charlie")

        // WHEN
        val resolution = WorkspaceResolutionPolicy.resolve(result = result, preferredSlug = "bravo")

        // THEN
        assertSoftly {
            resolution shouldBe WorkspaceResolution.NeedsConfirmation(
                slugs = listOf("alpha", "bravo", "charlie"),
                preselectedSlug = "bravo"
            )
        }
    }

    @Test
    fun `resolve - should fall back to the first slug when the preferred one is gone`() {
        // GIVEN
        val result = success("alpha", "bravo")

        // WHEN
        val resolution = WorkspaceResolutionPolicy.resolve(result = result, preferredSlug = "removed")

        // THEN
        assertSoftly {
            resolution shouldBe WorkspaceResolution.NeedsConfirmation(
                slugs = listOf("alpha", "bravo"),
                preselectedSlug = "alpha"
            )
        }
    }

    @Test
    fun `resolve - should fall back to the first slug when no slug is preferred`() {
        // GIVEN
        val result = success("alpha", "bravo")

        // WHEN
        val resolution = WorkspaceResolutionPolicy.resolve(result = result, preferredSlug = null)

        // THEN
        assertSoftly {
            resolution shouldBe WorkspaceResolution.NeedsConfirmation(
                slugs = listOf("alpha", "bravo"),
                preselectedSlug = "alpha"
            )
        }
    }

    @Test
    fun `resolve - should report an empty workspace list`() {
        // GIVEN
        val result = WorkspaceListResult.Success(emptyList())

        // WHEN
        val resolution = WorkspaceResolutionPolicy.resolve(result = result, preferredSlug = "alpha")

        // THEN
        resolution shouldBe WorkspaceResolution.NoneAccessible
    }

    @Test
    fun `resolve - should report a rejected token`() {
        // GIVEN
        val result = WorkspaceListResult.Unauthorized

        // WHEN
        val resolution = WorkspaceResolutionPolicy.resolve(result = result, preferredSlug = "alpha")

        // THEN
        resolution shouldBe WorkspaceResolution.Unauthorized
    }

    @Test
    fun `resolve - should carry the cause of a failed request`() {
        // GIVEN
        val cause = IllegalStateException("boom")

        // WHEN
        val resolution = WorkspaceResolutionPolicy.resolve(
            result = WorkspaceListResult.Failed(cause),
            preferredSlug = "alpha"
        )

        // THEN
        assertSoftly {
            resolution shouldBe WorkspaceResolution.Failed(cause)
            (resolution as WorkspaceResolution.Failed).cause shouldBe cause
        }
    }

    private fun success(vararg slugs: String): WorkspaceListResult {
        return WorkspaceListResult.Success(slugs.map { slug -> BitbucketWorkspace(slug = slug, uuid = null) })
    }
}