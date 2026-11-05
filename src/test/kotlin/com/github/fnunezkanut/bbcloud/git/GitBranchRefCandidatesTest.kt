package com.github.fnunezkanut.bbcloud.git

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class GitBranchRefCandidatesTest {
    @Test
    fun `candidates - should try local name then origin remote for a short branch name`() {
        val candidates = GitBranchRefCandidates.candidates(branchName = "ABC-663_test", remoteName = "origin")

        candidates shouldContainExactly listOf("ABC-663_test", "origin/ABC-663_test")
    }

    @Test
    fun `candidates - should strip origin prefix when branch name is already remote`() {
        val candidates = GitBranchRefCandidates.candidates(branchName = "origin/master", remoteName = "origin")

        assertSoftly {
            candidates.size shouldBe 2
            candidates[0] shouldBe "origin/master"
            candidates[1] shouldBe "master"
        }
    }

    @Test
    fun `candidates - should return empty list for blank branch name`() {
        GitBranchRefCandidates.candidates(branchName = "   ") shouldBe emptyList()
    }

    @Test
    fun `candidates - should try the remote name first when preferRemote is set`() {
        val candidates = GitBranchRefCandidates.candidates(branchName = "feature/foo", remoteName = "origin", preferRemote = true)

        candidates shouldContainExactly listOf("origin/feature/foo", "feature/foo")
    }

    @Test
    fun `candidates - should keep the remote name first for a remote branch name when preferRemote is set`() {
        val candidates = GitBranchRefCandidates.candidates(branchName = "origin/master", remoteName = "origin", preferRemote = true)

        candidates shouldContainExactly listOf("origin/master", "master")
    }

    @Test
    fun `candidates - should keep the default order when preferRemote is not set`() {
        val candidates = GitBranchRefCandidates.candidates(branchName = "feature/foo", remoteName = "origin")

        candidates shouldContainExactly listOf("feature/foo", "origin/feature/foo")
    }
}