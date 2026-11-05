package com.github.fnunezkanut.bbcloud.git

import com.intellij.openapi.project.Project
import git4idea.commands.GitCommand
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@Tag("unit")
class PrBranchSyncEngineTest {
    private data class Call(val workingDir: Path, val command: GitCommand, val parameters: List<String>)

    /** A scripted git that answers from simple maps and records every call. */
    private class FakeGit : GitCommandRunner {
        val calls = mutableListOf<Call>()
        val remoteRefs = mutableMapOf<String, String>()
        val localRefs = mutableMapOf<String, String>()
        val counts = mutableMapOf<String, String>()
        val dirty = mutableMapOf<Path, Boolean>()
        var worktreeList: String? = ""
        val failingCommands = mutableSetOf<GitCommand>()
        var failCombinedFetch = false
        var fetchSucceedsFor: Set<String>? = null

        override fun run(project: Project?, workingDir: Path, command: GitCommand, parameters: List<String>, remoteUrl: String?): GitCommandResult {
            calls += Call(workingDir = workingDir, command = command, parameters = parameters)
            return when (command) {
                GitCommand.FETCH -> fetch(parameters)
                GitCommand.REV_PARSE -> revParse(parameters.last())
                GitCommand.REV_LIST -> revList(parameters.last())
                GitCommand.WORKTREE -> worktreeList?.let { output -> ok("worktree list --porcelain", output) } ?: bad()
                GitCommand.STATUS -> ok("status --porcelain", if (dirty[workingDir] == true) " M file.txt" else "")
                else -> if (command in failingCommands) bad() else ok(command.name())
            }
        }

        private fun fetch(parameters: List<String>): GitCommandResult {
            val refspecs = parameters.drop(1)
            if (refspecs.size > 1 && failCombinedFetch) {
                return bad()
            }
            val allowed = fetchSucceedsFor ?: return ok("fetch")
            return if (refspecs.all { refspec -> allowed.contains(refspec) }) ok("fetch") else bad()
        }

        private fun revParse(argument: String): GitCommandResult {
            val ref = argument.removeSuffix("^{commit}")
            val revision = when {
                ref.startsWith("refs/remotes/origin/") -> remoteRefs[ref.removePrefix("refs/remotes/origin/")]
                ref.startsWith("refs/heads/") -> localRefs[ref.removePrefix("refs/heads/")]
                else -> null
            }
            return if (revision == null) bad() else ok("rev-parse", revision)
        }

        private fun revList(range: String): GitCommandResult {
            val branch = range.substringBefore("...").removePrefix("refs/heads/")
            val count = counts[branch] ?: return bad()
            return ok("rev-list", count)
        }

        private fun ok(commandLine: String, output: String = ""): GitCommandResult {
            val body = listOf("$ git $commandLine", output).filter { part -> part.isNotEmpty() }.joinToString("\n")
            return GitCommandResult(success = true, combinedOutput = body)
        }

        private fun bad(): GitCommandResult {
            return GitCommandResult(success = false, combinedOutput = "$ git failed\nfatal: nope")
        }
    }

    private val noProjectLocator = OpenProjectLocator { null }

    private fun engineWith(git: FakeGit): PrBranchSyncEngine {
        return PrBranchSyncEngine(gitRunner = git, openProjectLocator = noProjectLocator)
    }

    private fun worktreeBlock(path: Path, branch: String): String {
        return "worktree ${path.toAbsolutePath().normalize()}\nHEAD abc\nbranch refs/heads/$branch"
    }

    private fun inspect(engine: PrBranchSyncEngine, prWorktree: Path, source: String = "feature/foo", destination: String = "main"): PrBranchSyncSnapshot {
        return engine.inspect(
            project = null,
            prWorktreePath = prWorktree,
            remoteName = "origin",
            sourceBranch = source,
            destinationBranch = destination
        )
    }

    @Nested
    inner class NormalizeBranchName {
        private val engine = engineWith(FakeGit())

        @Test
        fun `normalizeBranchName - should keep slashes inside the branch name`() {
            engine.normalizeBranchName(branchName = "feature/foo", remoteName = "origin") shouldBe "feature/foo"
        }

        @Test
        fun `normalizeBranchName - should strip only a leading remote or heads prefix`() {
            engine.normalizeBranchName(branchName = " origin/feature/foo ", remoteName = "origin") shouldBe "feature/foo"
            engine.normalizeBranchName(branchName = "refs/heads/main", remoteName = "origin") shouldBe "main"
        }
    }

    @Nested
    inner class Fetch {
        @Test
        fun `fetch - should use explicit forced refspecs with full branch names in one call`(
            @TempDir tempDir: Path
        ) {
            // GIVEN
            val git = FakeGit()

            // WHEN
            val fetched = engineWith(git).fetch(
                project = null,
                prWorktreePath = tempDir,
                remoteName = "origin",
                remoteUrl = null,
                branches = listOf("feature/foo", "main")
            )

            // THEN
            assertSoftly {
                fetched shouldBe true
                git.calls.map { call -> call.parameters } shouldContainExactly listOf(
                    listOf(
                        "origin",
                        "+refs/heads/feature/foo:refs/remotes/origin/feature/foo",
                        "+refs/heads/main:refs/remotes/origin/main"
                    )
                )
            }
        }

        @Test
        fun `fetch - should retry each refspec on its own when the combined fetch fails`(
            @TempDir tempDir: Path
        ) {
            // GIVEN a combined fetch that fails and a destination fetch that succeeds
            val git = FakeGit().apply {
                failCombinedFetch = true
                fetchSucceedsFor = setOf("+refs/heads/main:refs/remotes/origin/main")
            }

            // WHEN
            val fetched = engineWith(git).fetch(
                project = null,
                prWorktreePath = tempDir,
                remoteName = "origin",
                remoteUrl = null,
                branches = listOf("feature/foo", "main")
            )

            // THEN
            assertSoftly {
                fetched shouldBe true
                git.calls.size shouldBe 3
            }
        }

        @Test
        fun `fetch - should return false when every fetch fails`(
            @TempDir tempDir: Path
        ) {
            val git = FakeGit().apply {
                failingCommands += GitCommand.FETCH
                fetchSucceedsFor = emptySet()
            }

            val fetched = engineWith(git).fetch(
                project = null,
                prWorktreePath = tempDir,
                remoteName = "origin",
                remoteUrl = null,
                branches = listOf("a", "b")
            )

            fetched shouldBe false
        }

        @Test
        fun `fetch - should de-duplicate equal branches and drop blank ones`(
            @TempDir tempDir: Path
        ) {
            val git = FakeGit()

            engineWith(git).fetch(
                project = null,
                prWorktreePath = tempDir,
                remoteName = "origin",
                remoteUrl = null,
                branches = listOf("main", " ", "origin/main")
            )

            git.calls.map { call -> call.parameters } shouldContainExactly listOf(
                listOf("origin", "+refs/heads/main:refs/remotes/origin/main")
            )
        }

        @Test
        fun `fetch - should not run git when there is no branch`(
            @TempDir tempDir: Path
        ) {
            val git = FakeGit()

            val fetched = engineWith(git).fetch(project = null, prWorktreePath = tempDir, remoteName = "origin", remoteUrl = null, branches = listOf(""))

            assertSoftly {
                fetched shouldBe false
                git.calls.shouldBeEmpty()
            }
        }
    }

    @Nested
    inner class Inspect {
        @Test
        fun `inspect - should map a missing remote branch to NoRemoteBranch`(
            @TempDir tempDir: Path
        ) {
            val git = FakeGit().apply { localRefs["feature/foo"] = "l1" }

            val snapshot = inspect(engineWith(git), tempDir)

            snapshot.source shouldBe BranchSyncStatus.NoRemoteBranch
        }

        @Test
        fun `inspect - should map a missing local branch with an existing remote to MissingLocal`(
            @TempDir tempDir: Path
        ) {
            val git = FakeGit().apply { remoteRefs["main"] = "r1" }

            val snapshot = inspect(engineWith(git), tempDir)

            snapshot.destination shouldBe BranchSyncStatus.MissingLocal(remoteRevision = "r1")
        }

        @Test
        fun `inspect - should map a failed rev-list to Unknown`(
            @TempDir tempDir: Path
        ) {
            val git = FakeGit().apply {
                remoteRefs["main"] = "r1"
                localRefs["main"] = "l1"
            }

            val snapshot = inspect(engineWith(git), tempDir)

            snapshot.destination shouldBe BranchSyncStatus.Unknown
        }

        @Test
        fun `inspect - should map behind zero to UpToDate even when ahead`(
            @TempDir tempDir: Path
        ) {
            val git = FakeGit().apply {
                remoteRefs["main"] = "r1"
                localRefs["main"] = "l1"
                counts["main"] = "2\t0"
            }

            val snapshot = inspect(engineWith(git), tempDir)

            snapshot.destination shouldBe BranchSyncStatus.UpToDate(commitsAhead = 2)
        }

        @Test
        fun `inspect - should report a behind branch that is checked out nowhere`(
            @TempDir tempDir: Path
        ) {
            val git = FakeGit().apply {
                remoteRefs["main"] = "r1"
                localRefs["main"] = "l1"
                counts["main"] = "0\t4"
                worktreeList = worktreeBlock(path = tempDir, branch = "feature/foo")
            }

            val snapshot = inspect(engineWith(git), tempDir)

            snapshot.destination shouldBe BranchSyncStatus.Behind(
                commitsBehind = 4,
                commitsAhead = 0,
                localRevision = "l1",
                remoteRevision = "r1",
                checkedOutAt = null,
                checkedOutWorktreeDirty = null
            )
        }

        @Test
        fun `inspect - should read the dirty state of the work tree the branch is checked out in`(
            @TempDir tempDir: Path
        ) {
            // GIVEN a behind source branch checked out in the PR work tree, which has a modified file
            val git = FakeGit().apply {
                remoteRefs["feature/foo"] = "r1"
                localRefs["feature/foo"] = "l1"
                counts["feature/foo"] = "0\t1"
                worktreeList = worktreeBlock(path = tempDir, branch = "feature/foo")
                dirty[tempDir] = true
            }

            // WHEN
            val snapshot = inspect(engineWith(git), tempDir)

            // THEN
            val behind = snapshot.source.shouldBeInstanceOf<BranchSyncStatus.Behind>()
            assertSoftly(behind) {
                checkedOutAt shouldBe tempDir.toAbsolutePath().normalize()
                checkedOutWorktreeDirty shouldBe true
            }
        }

        @Test
        fun `inspect - should report a clean work tree as not dirty`(
            @TempDir tempDir: Path
        ) {
            val git = FakeGit().apply {
                remoteRefs["feature/foo"] = "r1"
                localRefs["feature/foo"] = "l1"
                counts["feature/foo"] = "0\t1"
                worktreeList = worktreeBlock(path = tempDir, branch = "feature/foo")
            }

            val snapshot = inspect(engineWith(git), tempDir)

            snapshot.source.shouldBeInstanceOf<BranchSyncStatus.Behind>().checkedOutWorktreeDirty shouldBe false
        }

        @Test
        fun `inspect - should report an unknown dirty state when the work tree path does not exist`(
            @TempDir tempDir: Path
        ) {
            val gone = tempDir.resolve("gone")
            val git = FakeGit().apply {
                remoteRefs["main"] = "r1"
                localRefs["main"] = "l1"
                counts["main"] = "0\t1"
                worktreeList = worktreeBlock(path = gone, branch = "main")
            }

            val snapshot = inspect(engineWith(git), tempDir)

            val behind = snapshot.destination.shouldBeInstanceOf<BranchSyncStatus.Behind>()
            assertSoftly(behind) {
                checkedOutAt shouldBe gone.toAbsolutePath().normalize()
                checkedOutWorktreeDirty shouldBe null
            }
        }

        @Test
        fun `inspect - should map a behind branch to Unknown when the work tree list fails`(
            @TempDir tempDir: Path
        ) {
            val git = FakeGit().apply {
                remoteRefs["main"] = "r1"
                localRefs["main"] = "l1"
                counts["main"] = "0\t1"
                worktreeList = null
            }

            val snapshot = inspect(engineWith(git), tempDir)

            snapshot.destination shouldBe BranchSyncStatus.Unknown
        }

        @Test
        fun `inspect - should evaluate equal branch names once`(
            @TempDir tempDir: Path
        ) {
            val git = FakeGit().apply {
                remoteRefs["main"] = "r1"
                localRefs["main"] = "l1"
                counts["main"] = "0\t0"
            }

            val snapshot = inspect(engineWith(git), tempDir, source = "main", destination = "origin/main")

            assertSoftly {
                snapshot.source shouldBe snapshot.destination
                git.calls.count { call -> call.command == GitCommand.REV_LIST } shouldBe 1
            }
        }
    }

    @Nested
    inner class FastForward {
        private fun behind(checkedOutAt: Path?, dirty: Boolean?, ahead: Int = 0): BranchSyncStatus.Behind {
            return BranchSyncStatus.Behind(
                commitsBehind = 2,
                commitsAhead = ahead,
                localRevision = "old",
                remoteRevision = "new",
                checkedOutAt = checkedOutAt,
                checkedOutWorktreeDirty = dirty
            )
        }

        private fun snapshot(prWorktree: Path, source: BranchSyncStatus, destination: BranchSyncStatus): PrBranchSyncSnapshot {
            return PrBranchSyncSnapshot(
                remoteName = "origin",
                sourceBranch = "feature/foo",
                destinationBranch = "main",
                prWorktreePath = prWorktree,
                source = source,
                destination = destination
            )
        }

        private fun fastForward(git: FakeGit, snapshot: PrBranchSyncSnapshot, mode: PrBranchSyncMode): PrBranchSyncOutcome {
            return engineWith(git).fastForward(project = null, snapshot = snapshot, remoteUrl = null, mode = mode)
        }

        @Test
        fun `fastForward - should merge ff-only in the PR work tree and update-ref a branch checked out nowhere`(
            @TempDir tempDir: Path
        ) {
            // GIVEN
            val git = FakeGit()
            val snapshot = snapshot(
                prWorktree = tempDir,
                source = behind(checkedOutAt = tempDir, dirty = false),
                destination = behind(checkedOutAt = null, dirty = null)
            )

            // WHEN
            val outcome = fastForward(git, snapshot, PrBranchSyncMode.Automatic)

            // THEN
            assertSoftly {
                git.calls.first { call -> call.command == GitCommand.MERGE } shouldBe
                    Call(workingDir = tempDir, command = GitCommand.MERGE, parameters = listOf("--ff-only", "refs/remotes/origin/feature/foo"))
                git.calls.first { call -> call.command == GitCommand.UPDATE_REF }.parameters shouldBe listOf(
                    "-m",
                    "bbcloud: fast-forward main to origin/main",
                    "refs/heads/main",
                    "new",
                    "old"
                )
                outcome.resultFor(PrBranchRole.Source) shouldBe PrBranchSyncStepResult.Updated(path = tempDir)
                outcome.resultFor(PrBranchRole.Destination) shouldBe PrBranchSyncStepResult.Updated(path = null)
                outcome.anyRefMoved shouldBe true
            }
        }

        @Test
        fun `fastForward - should never run a command in another work tree in automatic mode`(
            @TempDir tempDir: Path
        ) {
            // GIVEN a destination checked out in the main clone
            val mainClone = tempDir.resolve("main-clone").also { Files.createDirectories(it) }
            val prWorktree = tempDir.resolve("pr").also { Files.createDirectories(it) }
            val git = FakeGit()
            val snapshot = snapshot(
                prWorktree = prWorktree,
                source = BranchSyncStatus.UpToDate(commitsAhead = 0),
                destination = behind(checkedOutAt = mainClone, dirty = false)
            )

            // WHEN
            val outcome = fastForward(git, snapshot, PrBranchSyncMode.Automatic)

            // THEN
            assertSoftly {
                git.calls.shouldBeEmpty()
                outcome.resultFor(PrBranchRole.Destination) shouldBe
                    PrBranchSyncStepResult.Skipped(reason = PrBranchSyncSkipReason.CheckedOutInOtherWorktree, path = mainClone)
                outcome.anyRefMoved shouldBe false
            }
        }

        @Test
        fun `fastForward - should merge in the other work tree when the user asks`(
            @TempDir tempDir: Path
        ) {
            val mainClone = tempDir.resolve("main-clone").also { Files.createDirectories(it) }
            val prWorktree = tempDir.resolve("pr").also { Files.createDirectories(it) }
            val git = FakeGit()
            val snapshot = snapshot(
                prWorktree = prWorktree,
                source = BranchSyncStatus.UpToDate(commitsAhead = 0),
                destination = behind(checkedOutAt = mainClone, dirty = false)
            )

            fastForward(git, snapshot, PrBranchSyncMode.UserRequested)

            git.calls.single() shouldBe
                Call(workingDir = mainClone, command = GitCommand.MERGE, parameters = listOf("--ff-only", "refs/remotes/origin/main"))
        }

        @Test
        fun `fastForward - should pull a diverged source only for a user request`(
            @TempDir tempDir: Path
        ) {
            val status = behind(checkedOutAt = tempDir, dirty = false, ahead = 1)
            val snapshot = snapshot(prWorktree = tempDir, source = status, destination = BranchSyncStatus.UpToDate(commitsAhead = 0))

            val automaticGit = FakeGit()
            val automatic = fastForward(automaticGit, snapshot, PrBranchSyncMode.Automatic)
            val userGit = FakeGit()
            fastForward(userGit, snapshot, PrBranchSyncMode.UserRequested)

            assertSoftly {
                automaticGit.calls.shouldBeEmpty()
                automatic.resultFor(PrBranchRole.Source) shouldBe
                    PrBranchSyncStepResult.Skipped(reason = PrBranchSyncSkipReason.LocalCommitsNotOnRemote, path = tempDir)
                userGit.calls.single() shouldBe
                    Call(workingDir = tempDir, command = GitCommand.PULL, parameters = listOf("--no-edit", "origin", "feature/foo"))
            }
        }

        @Test
        fun `fastForward - should report Failed without throwing when the git command fails`(
            @TempDir tempDir: Path
        ) {
            val git = FakeGit().apply { failingCommands += GitCommand.UPDATE_REF }
            val snapshot = snapshot(
                prWorktree = tempDir,
                source = BranchSyncStatus.UpToDate(commitsAhead = 0),
                destination = behind(checkedOutAt = null, dirty = null)
            )

            val outcome = fastForward(git, snapshot, PrBranchSyncMode.Automatic)

            assertSoftly {
                outcome.resultFor(PrBranchRole.Destination).shouldBeInstanceOf<PrBranchSyncStepResult.Failed>()
                outcome.anyRefMoved shouldBe false
            }
        }

        @Test
        fun `fastForward - should create a missing local branch that tracks the remote`(
            @TempDir tempDir: Path
        ) {
            val git = FakeGit()
            val snapshot = snapshot(
                prWorktree = tempDir,
                source = BranchSyncStatus.UpToDate(commitsAhead = 0),
                destination = BranchSyncStatus.MissingLocal(remoteRevision = "r1")
            )

            val outcome = fastForward(git, snapshot, PrBranchSyncMode.Automatic)

            assertSoftly {
                git.calls.single() shouldBe
                    Call(workingDir = tempDir, command = GitCommand.BRANCH, parameters = listOf("--track", "main", "refs/remotes/origin/main"))
                outcome.resultFor(PrBranchRole.Destination) shouldBe PrBranchSyncStepResult.Updated(path = null)
                outcome.anyRefMoved shouldBe true
            }
        }

        @Test
        fun `fastForward - should report Failed when the branch appeared in the meantime`(
            @TempDir tempDir: Path
        ) {
            val git = FakeGit().apply { failingCommands += GitCommand.BRANCH }
            val snapshot = snapshot(
                prWorktree = tempDir,
                source = BranchSyncStatus.UpToDate(commitsAhead = 0),
                destination = BranchSyncStatus.MissingLocal(remoteRevision = "r1")
            )

            val outcome = fastForward(git, snapshot, PrBranchSyncMode.Automatic)

            assertSoftly {
                outcome.resultFor(PrBranchRole.Destination).shouldBeInstanceOf<PrBranchSyncStepResult.Failed>()
                outcome.anyRefMoved shouldBe false
            }
        }

        @Test
        fun `fastForward - should not create a branch when the remote branch is missing`(
            @TempDir tempDir: Path
        ) {
            val git = FakeGit()
            val snapshot = snapshot(
                prWorktree = tempDir,
                source = BranchSyncStatus.NoRemoteBranch,
                destination = BranchSyncStatus.NoRemoteBranch
            )

            val outcome = fastForward(git, snapshot, PrBranchSyncMode.Automatic)

            assertSoftly {
                git.calls.shouldBeEmpty()
                outcome.anyRefMoved shouldBe false
            }
        }
    }

    @Nested
    inner class Sync {
        @Test
        fun `sync - should fetch, fast-forward and inspect again`(
            @TempDir tempDir: Path
        ) {
            // GIVEN a destination that is behind and checked out nowhere
            val git = FakeGit().apply {
                remoteRefs["feature/foo"] = "r1"
                localRefs["feature/foo"] = "r1"
                counts["feature/foo"] = "0\t0"
                remoteRefs["main"] = "r2"
                localRefs["main"] = "l2"
                counts["main"] = "0\t3"
            }

            // WHEN
            val result = engineWith(git).sync(
                project = null,
                prWorktreePath = tempDir,
                remoteName = "origin",
                remoteUrl = null,
                sourceBranch = "feature/foo",
                destinationBranch = "main",
                mode = PrBranchSyncMode.Automatic
            )

            // THEN
            assertSoftly {
                git.calls.first().command shouldBe GitCommand.FETCH
                git.calls.count { call -> call.command == GitCommand.UPDATE_REF } shouldBe 1
                result.outcome.anyRefMoved shouldBe true
            }
        }

        @Test
        fun `sync - should skip the fetch when asked`(
            @TempDir tempDir: Path
        ) {
            val git = FakeGit()

            engineWith(git).sync(
                project = null,
                prWorktreePath = tempDir,
                remoteName = "origin",
                remoteUrl = null,
                sourceBranch = "feature/foo",
                destinationBranch = "main",
                mode = PrBranchSyncMode.Automatic,
                fetch = false
            )

            git.calls.count { call -> call.command == GitCommand.FETCH } shouldBe 0
        }
    }
}