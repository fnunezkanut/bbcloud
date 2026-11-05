package com.github.fnunezkanut.bbcloud.git

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path

@Tag("unit")
class PrBranchFastForwardPolicyTest {
    private val prWorktree: Path = Path.of("/tmp/.worktrees/repo--pr-9")
    private val otherWorktree: Path = Path.of("/tmp/projects/repo")

    private fun behind(ahead: Int = 0, checkedOutAt: Path? = null, dirty: Boolean? = null): BranchSyncStatus.Behind {
        return BranchSyncStatus.Behind(
            commitsBehind = 3,
            commitsAhead = ahead,
            localRevision = "old",
            remoteRevision = "new",
            checkedOutAt = checkedOutAt,
            checkedOutWorktreeDirty = dirty
        )
    }

    private fun decide(role: PrBranchRole, status: BranchSyncStatus, mode: PrBranchSyncMode): PrBranchSyncAction {
        return PrBranchFastForwardPolicy.decide(role = role, status = status, prWorktreePath = prWorktree, mode = mode)
    }

    @Nested
    inner class Automatic {
        private val mode = PrBranchSyncMode.Automatic

        @Test
        fun `decide - should create a missing local branch for both roles`() {
            for (role in PrBranchRole.entries) {
                decide(role = role, status = BranchSyncStatus.MissingLocal(remoteRevision = "r"), mode = mode) shouldBe PrBranchSyncAction.CreateLocalBranch
            }
        }

        @Test
        fun `decide - should do nothing when the branch is not behind`() {
            for (role in PrBranchRole.entries) {
                decide(role = role, status = BranchSyncStatus.UpToDate(commitsAhead = 1), mode = mode) shouldBe PrBranchSyncAction.None
                decide(role = role, status = BranchSyncStatus.NoRemoteBranch, mode = mode) shouldBe PrBranchSyncAction.None
                decide(role = role, status = BranchSyncStatus.Unknown, mode = mode) shouldBe PrBranchSyncAction.None
            }
        }

        @Test
        fun `decide - should update the ref when the branch is checked out nowhere`() {
            for (role in PrBranchRole.entries) {
                decide(role = role, status = behind(), mode = mode) shouldBe PrBranchSyncAction.UpdateRef(newRevision = "new", oldRevision = "old")
            }
        }

        @Test
        fun `decide - should merge fast forward in the clean PR work tree`() {
            for (role in PrBranchRole.entries) {
                decide(role = role, status = behind(checkedOutAt = prWorktree, dirty = false), mode = mode) shouldBe
                    PrBranchSyncAction.MergeFastForwardInWorktree(worktreePath = prWorktree)
            }
        }

        @Test
        fun `decide - should skip a dirty PR work tree`() {
            for (role in PrBranchRole.entries) {
                decide(role = role, status = behind(checkedOutAt = prWorktree, dirty = true), mode = mode) shouldBe
                    PrBranchSyncAction.Skip(PrBranchSyncSkipReason.UncommittedChanges)
            }
        }

        @Test
        fun `decide - should skip a branch checked out in another work tree`() {
            for (role in PrBranchRole.entries) {
                decide(role = role, status = behind(checkedOutAt = otherWorktree, dirty = false), mode = mode) shouldBe
                    PrBranchSyncAction.Skip(PrBranchSyncSkipReason.CheckedOutInOtherWorktree)
                decide(role = role, status = behind(checkedOutAt = otherWorktree, dirty = true), mode = mode) shouldBe
                    PrBranchSyncAction.Skip(PrBranchSyncSkipReason.CheckedOutInOtherWorktree)
            }
        }

        @Test
        fun `decide - should skip when the dirty state is unknown`() {
            for (role in PrBranchRole.entries) {
                decide(role = role, status = behind(checkedOutAt = prWorktree, dirty = null), mode = mode) shouldBe
                    PrBranchSyncAction.Skip(PrBranchSyncSkipReason.CheckedOutWorktreeStateUnknown)
            }
        }

        @Test
        fun `decide - should skip a diverged branch`() {
            for (role in PrBranchRole.entries) {
                decide(role = role, status = behind(ahead = 2, checkedOutAt = prWorktree, dirty = false), mode = mode) shouldBe
                    PrBranchSyncAction.Skip(PrBranchSyncSkipReason.LocalCommitsNotOnRemote)
                decide(role = role, status = behind(ahead = 2), mode = mode) shouldBe
                    PrBranchSyncAction.Skip(PrBranchSyncSkipReason.LocalCommitsNotOnRemote)
            }
        }
    }

    @Nested
    inner class UserRequested {
        private val mode = PrBranchSyncMode.UserRequested

        @Test
        fun `decide - should create a missing local branch for both roles`() {
            for (role in PrBranchRole.entries) {
                decide(role = role, status = BranchSyncStatus.MissingLocal(remoteRevision = "r"), mode = mode) shouldBe PrBranchSyncAction.CreateLocalBranch
            }
        }

        @Test
        fun `decide - should do nothing when the branch is not behind`() {
            for (role in PrBranchRole.entries) {
                decide(role = role, status = BranchSyncStatus.UpToDate(commitsAhead = 0), mode = mode) shouldBe PrBranchSyncAction.None
                decide(role = role, status = BranchSyncStatus.NoRemoteBranch, mode = mode) shouldBe PrBranchSyncAction.None
                decide(role = role, status = BranchSyncStatus.Unknown, mode = mode) shouldBe PrBranchSyncAction.None
            }
        }

        @Test
        fun `decide - should update the ref when the branch is checked out nowhere`() {
            for (role in PrBranchRole.entries) {
                decide(role = role, status = behind(), mode = mode) shouldBe PrBranchSyncAction.UpdateRef(newRevision = "new", oldRevision = "old")
            }
        }

        @Test
        fun `decide - should merge fast forward in the clean PR work tree and skip a dirty one`() {
            for (role in PrBranchRole.entries) {
                decide(role = role, status = behind(checkedOutAt = prWorktree, dirty = false), mode = mode) shouldBe
                    PrBranchSyncAction.MergeFastForwardInWorktree(worktreePath = prWorktree)
                decide(role = role, status = behind(checkedOutAt = prWorktree, dirty = true), mode = mode) shouldBe
                    PrBranchSyncAction.Skip(PrBranchSyncSkipReason.UncommittedChanges)
            }
        }

        @Test
        fun `decide - should merge fast forward in a clean other work tree and skip a dirty one`() {
            for (role in PrBranchRole.entries) {
                decide(role = role, status = behind(checkedOutAt = otherWorktree, dirty = false), mode = mode) shouldBe
                    PrBranchSyncAction.MergeFastForwardInWorktree(worktreePath = otherWorktree)
                decide(role = role, status = behind(checkedOutAt = otherWorktree, dirty = true), mode = mode) shouldBe
                    PrBranchSyncAction.Skip(PrBranchSyncSkipReason.UncommittedChanges)
            }
        }

        @Test
        fun `decide - should skip when the dirty state is unknown`() {
            for (role in PrBranchRole.entries) {
                decide(role = role, status = behind(checkedOutAt = otherWorktree, dirty = null), mode = mode) shouldBe
                    PrBranchSyncAction.Skip(PrBranchSyncSkipReason.CheckedOutWorktreeStateUnknown)
            }
        }

        @Test
        fun `decide - should pull a diverged source branch that is checked out in the clean PR work tree`() {
            decide(role = PrBranchRole.Source, status = behind(ahead = 1, checkedOutAt = prWorktree, dirty = false), mode = mode) shouldBe
                PrBranchSyncAction.PullIntoPrWorktree
        }

        @Test
        fun `decide - should skip every other diverged case`() {
            val skip = PrBranchSyncAction.Skip(PrBranchSyncSkipReason.LocalCommitsNotOnRemote)

            decide(role = PrBranchRole.Destination, status = behind(ahead = 1, checkedOutAt = prWorktree, dirty = false), mode = mode) shouldBe skip
            decide(role = PrBranchRole.Source, status = behind(ahead = 1, checkedOutAt = prWorktree, dirty = true), mode = mode) shouldBe skip
            decide(role = PrBranchRole.Source, status = behind(ahead = 1, checkedOutAt = otherWorktree, dirty = false), mode = mode) shouldBe skip
            decide(role = PrBranchRole.Source, status = behind(ahead = 1), mode = mode) shouldBe skip
        }
    }

    @Test
    fun `decide - should compare paths after normalizing them`() {
        val status = behind(checkedOutAt = Path.of("/tmp/.worktrees/x/../repo--pr-9"), dirty = false)

        PrBranchFastForwardPolicy.decide(
            role = PrBranchRole.Source,
            status = status,
            prWorktreePath = prWorktree,
            mode = PrBranchSyncMode.Automatic
        ) shouldBe PrBranchSyncAction.MergeFastForwardInWorktree(worktreePath = prWorktree)
    }
}