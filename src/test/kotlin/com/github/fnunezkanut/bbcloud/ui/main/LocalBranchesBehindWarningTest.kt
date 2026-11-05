package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.git.BranchSyncStatus
import com.github.fnunezkanut.bbcloud.git.PrBranchRole
import com.github.fnunezkanut.bbcloud.git.PrBranchSyncSnapshot
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

@Tag("unit")
class LocalBranchesBehindWarningTest {
    private val prWorktree: Path = Path.of("/tmp/.worktrees/repo--pr-9")

    private fun behind(commits: Int = 2, ahead: Int = 0, checkedOutAt: Path? = null, dirty: Boolean? = null): BranchSyncStatus.Behind {
        return BranchSyncStatus.Behind(
            commitsBehind = commits,
            commitsAhead = ahead,
            localRevision = "old",
            remoteRevision = "new",
            checkedOutAt = checkedOutAt,
            checkedOutWorktreeDirty = dirty
        )
    }

    private fun snapshot(source: BranchSyncStatus, destination: BranchSyncStatus): PrBranchSyncSnapshot {
        return PrBranchSyncSnapshot(
            remoteName = "origin",
            sourceBranch = "feature/foo",
            destinationBranch = "main",
            prWorktreePath = prWorktree,
            source = source,
            destination = destination
        )
    }

    private val upToDate = BranchSyncStatus.UpToDate(commitsAhead = 0)

    @Test
    fun `from - should return null for a null snapshot`() {
        LocalBranchesBehindWarning.from(null).shouldBeNull()
    }

    @Test
    fun `from - should return null when neither branch is behind`() {
        val warning = LocalBranchesBehindWarning.from(
            snapshot(source = upToDate, destination = BranchSyncStatus.MissingLocal(remoteRevision = "r"))
        )

        warning.shouldBeNull()
    }

    @Test
    fun `from - should describe only the source branch`() {
        val warning = LocalBranchesBehindWarning.from(snapshot(source = behind(), destination = upToDate)).shouldNotBeNull()

        assertSoftly(warning) {
            title shouldBe "Local branches are behind Bitbucket"
            lines.map { line -> line.role } shouldBe listOf(PrBranchRole.Source)
            lines.single().text shouldBe "Source branch \"feature/foo\" is 2 commits behind origin/feature/foo"
            updateLinkText shouldBe "Update source branch"
            explanation shouldContain "may not match"
        }
    }

    @Test
    fun `from - should describe only the destination branch`() {
        val warning = LocalBranchesBehindWarning.from(snapshot(source = upToDate, destination = behind(commits = 1))).shouldNotBeNull()

        assertSoftly(warning) {
            lines.single().text shouldBe "Destination branch \"main\" is 1 commit behind origin/main"
            updateLinkText shouldBe "Update destination branch"
        }
    }

    @Test
    fun `from - should offer one link for both branches`() {
        val warning = LocalBranchesBehindWarning.from(snapshot(source = behind(), destination = behind())).shouldNotBeNull()

        assertSoftly(warning) {
            lines.map { line -> line.role } shouldBe listOf(PrBranchRole.Source, PrBranchRole.Destination)
            updateLinkText shouldBe "Update both branches"
        }
    }

    @Test
    fun `from - should explain uncommitted changes in the work tree`() {
        val status = behind(checkedOutAt = prWorktree, dirty = true)

        val warning = LocalBranchesBehindWarning.from(snapshot(source = status, destination = upToDate)).shouldNotBeNull()

        warning.lines.single().text shouldEndWith " (not updated automatically: uncommitted changes in $prWorktree)"
    }

    @Test
    fun `from - should explain local commits that are not on Bitbucket`() {
        val single = LocalBranchesBehindWarning.from(snapshot(source = behind(ahead = 1), destination = upToDate)).shouldNotBeNull()
        val several = LocalBranchesBehindWarning.from(snapshot(source = behind(ahead = 3), destination = upToDate)).shouldNotBeNull()

        assertSoftly {
            single.lines.single().text shouldEndWith " (not updated automatically: 1 local commit is not on Bitbucket)"
            several.lines.single().text shouldEndWith " (not updated automatically: 3 local commits are not on Bitbucket)"
        }
    }

    @Test
    fun `from - should explain a branch checked out in another work tree`() {
        val other = Path.of("/tmp/projects/repo")
        val status = behind(checkedOutAt = other, dirty = false)

        val warning = LocalBranchesBehindWarning.from(snapshot(source = upToDate, destination = status)).shouldNotBeNull()

        warning.lines.single().text shouldEndWith " (not updated automatically: checked out in $other)"
    }

    @Test
    fun `from - should add no reason when the branch would be updated automatically`() {
        val warning = LocalBranchesBehindWarning.from(snapshot(source = behind(), destination = upToDate)).shouldNotBeNull()

        warning.lines.single().text shouldEndWith "behind origin/feature/foo"
    }
}