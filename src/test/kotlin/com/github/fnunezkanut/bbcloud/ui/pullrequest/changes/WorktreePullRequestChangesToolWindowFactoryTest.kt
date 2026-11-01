package com.github.fnunezkanut.bbcloud.ui.pullrequest.changes

import com.intellij.openapi.project.Project
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class WorktreePullRequestChangesToolWindowFactoryTest {
    private val factory = WorktreePullRequestChangesToolWindowFactory()

    @Test
    fun `isApplicableAsync - should return true for a pull request work tree base path`() {
        // GIVEN
        val project = mockk<Project> {
            every { basePath } returns "/home/dev/.worktrees/lambda4k--pr-275"
        }

        // WHEN
        val applicable = runBlocking { factory.isApplicableAsync(project) }

        // THEN
        applicable shouldBe true
    }

    @Test
    fun `isApplicableAsync - should return false for a regular project`() {
        // GIVEN
        val project = mockk<Project> {
            every { basePath } returns "/home/dev/projects/lambda4k"
        }

        // WHEN
        val applicable = runBlocking { factory.isApplicableAsync(project) }

        // THEN
        applicable shouldBe false
    }

    @Test
    fun `isApplicableAsync - should return false when basePath is null`() {
        // GIVEN
        val project = mockk<Project> {
            every { basePath } returns null
        }

        // WHEN
        val applicable = runBlocking { factory.isApplicableAsync(project) }

        // THEN
        applicable shouldBe false
    }
}