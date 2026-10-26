package com.github.fnunezkanut.bbcloud.ui.pullrequest.create

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class NewPullRequestBranchOptionsTest {
    @Test
    fun `defaultDestination - should prefer the main branch then main then master then the first branch`() {
        // GIVEN
        val branches = listOf("develop", "main", "master", "feature/x")

        // THEN
        assertSoftly {
            NewPullRequestBranchOptions.defaultDestination(branches = branches, mainBranchName = "develop") shouldBe "develop"
            NewPullRequestBranchOptions.defaultDestination(branches = listOf("main", "feature/x"), mainBranchName = null) shouldBe "main"
            NewPullRequestBranchOptions.defaultDestination(branches = listOf("master", "feature/x"), mainBranchName = null) shouldBe "master"
            NewPullRequestBranchOptions.defaultDestination(branches = listOf("feature/x", "feature/y"), mainBranchName = null) shouldBe "feature/x"
            NewPullRequestBranchOptions.defaultDestination(branches = emptyList(), mainBranchName = null).shouldBeNull()
        }
    }

    @Test
    fun `sourceOptions - should exclude main master the main branch and the destination`() {
        // GIVEN
        val branches = listOf("develop", "main", "master", "feature/x", "feature/y")

        // WHEN
        val options = NewPullRequestBranchOptions.sourceOptions(
            branches = branches,
            mainBranchName = "develop",
            destinationBranch = "feature/y"
        )

        // THEN
        options shouldBe listOf("feature/x")
    }

    @Test
    fun `defaultSource - should prefer the current local branch when present else the first option`() {
        // GIVEN
        val options = listOf("feature/x", "feature/y")

        // THEN
        assertSoftly {
            NewPullRequestBranchOptions.defaultSource(options = options, currentLocalBranch = "feature/y") shouldBe "feature/y"
            NewPullRequestBranchOptions.defaultSource(options = options, currentLocalBranch = "not-an-option") shouldBe "feature/x"
            NewPullRequestBranchOptions.defaultSource(options = emptyList(), currentLocalBranch = "feature/x").shouldBeNull()
        }
    }

    @Test
    fun `reconcileSource - should keep a still-valid selection and replace an invalid one`() {
        // GIVEN
        val options = listOf("feature/x", "feature/y")

        // THEN
        assertSoftly {
            NewPullRequestBranchOptions.reconcileSource(selectedSource = "feature/y", options = options, currentLocalBranch = null) shouldBe "feature/y"
            NewPullRequestBranchOptions.reconcileSource(selectedSource = "gone", options = options, currentLocalBranch = "feature/y") shouldBe "feature/y"
            NewPullRequestBranchOptions.reconcileSource(selectedSource = "gone", options = options, currentLocalBranch = null) shouldBe "feature/x"
        }
    }

    @Test
    fun `destinationOptions - should put the preferred branches first and drop duplicates`() {
        // GIVEN
        val branches = listOf("feature/x", "main", "master", "develop")

        // WHEN
        val options = NewPullRequestBranchOptions.destinationOptions(branches = branches, mainBranchName = "develop")

        // THEN develop, then main, then master, then the rest in API order
        options shouldBe listOf("develop", "main", "master", "feature/x")
    }

    @Test
    fun `empty branch lists - yield empty options and null defaults`() {
        // THEN
        assertSoftly {
            NewPullRequestBranchOptions.sourceOptions(branches = emptyList(), mainBranchName = null, destinationBranch = null) shouldBe emptyList()
            NewPullRequestBranchOptions.destinationOptions(branches = emptyList(), mainBranchName = null) shouldBe emptyList()
            NewPullRequestBranchOptions.defaultSource(options = emptyList(), currentLocalBranch = null).shouldBeNull()
        }
    }

    @Test
    fun `localMainBranch - should prefer mainbranch then main then master else null`() {
        // THEN
        assertSoftly {
            NewPullRequestBranchOptions.localMainBranch(branches = listOf("develop"), mainBranchName = "develop") shouldBe "develop"
            NewPullRequestBranchOptions.localMainBranch(branches = listOf("main", "feature/x"), mainBranchName = null) shouldBe "main"
            NewPullRequestBranchOptions.localMainBranch(branches = listOf("master", "feature/x"), mainBranchName = null) shouldBe "master"
            NewPullRequestBranchOptions.localMainBranch(branches = listOf("feature/x"), mainBranchName = null).shouldBeNull()
        }
    }

    @Test
    fun `mainBranchNames - should include the main branch and main and master`() {
        // THEN
        assertSoftly {
            NewPullRequestBranchOptions.mainBranchNames(mainBranchName = "develop") shouldBe setOf("develop", "main", "master")
            NewPullRequestBranchOptions.mainBranchNames(mainBranchName = null) shouldBe setOf("main", "master")
        }
    }
}