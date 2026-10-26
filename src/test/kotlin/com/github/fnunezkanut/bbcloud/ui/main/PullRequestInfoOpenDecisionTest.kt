package com.github.fnunezkanut.bbcloud.ui.main

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PullRequestInfoOpenDecisionTest {
    @Test
    fun `repositoryOpenDecision - should browse when repository slug is blank`() {
        // GIVEN
        val repositoryUrl = "https://bitbucket.org/workspace/repository"

        // WHEN
        val result = repositoryOpenDecision(
            repoSlug = " ",
            localPath = "/projects/repository",
            repositoryWebUrl = repositoryUrl
        )

        // THEN
        result shouldBe RepositoryOpenDecision.BrowseWeb(url = repositoryUrl)
    }

    @Test
    fun `repositoryOpenDecision - should browse when local checkout path is blank`() {
        // GIVEN
        val repositoryUrl = "https://bitbucket.org/workspace/repository"

        // WHEN
        val result = repositoryOpenDecision(
            repoSlug = "repository",
            localPath = "",
            repositoryWebUrl = repositoryUrl
        )

        // THEN
        assertSoftly(result) {
            this shouldBe RepositoryOpenDecision.BrowseWeb(url = repositoryUrl)
        }
    }

    @Test
    fun `repositoryOpenDecision - should open local checkout when path is available`() {
        // WHEN
        val result = repositoryOpenDecision(
            repoSlug = "repository",
            localPath = "/projects/repository",
            repositoryWebUrl = "https://bitbucket.org/workspace/repository"
        )

        // THEN
        result shouldBe RepositoryOpenDecision.OpenLocal(path = "/projects/repository")
    }
}