package com.github.fnunezkanut.bbcloud.git

import com.intellij.openapi.project.Project
import git4idea.commands.GitCommand
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Drives [BbCloudWorktreeService] through its reconciliation logic by injecting a scripted [GitCommandRunner] and a
 * no-project [OpenProjectLocator], so the branch-collision classification can be verified without a platform fixture.
 */
@Tag("unit")
class BbCloudWorktreeServiceTest {
    private val sourceRoot: Path = Path.of("/tmp/clone")
    private val worktreePath: Path = Path.of("/tmp/.worktrees/clone--pr-9")
    private val prBranch = "feature-test1"

    /** A [GitCommandRunner] that returns scripted results keyed by working directory, git command, and parameters. */
    private class ScriptedGitRunner(private val script: (Path, GitCommand, List<String>) -> GitCommandResult) : GitCommandRunner {
        override fun run(project: Project?, workingDir: Path, command: GitCommand, parameters: List<String>, remoteUrl: String?): GitCommandResult {
            return script(workingDir, command, parameters)
        }
    }

    private val noProjectLocator = OpenProjectLocator { null }

    private fun success(output: String = ""): GitCommandResult = GitCommandResult(success = true, combinedOutput = output)

    private fun failure(output: String): GitCommandResult = GitCommandResult(success = false, combinedOutput = output)

    private fun serviceWith(script: (Path, GitCommand, List<String>) -> GitCommandResult): BbCloudWorktreeService {
        return BbCloudWorktreeService.forTesting(
            gitRunner = ScriptedGitRunner(script = script),
            openProjectLocator = noProjectLocator
        )
    }

    @Test
    fun `ensureWorktree - should return BranchCheckedOutInSource when the fresh add fails and the source clone is on the PR branch`() {
        // GIVEN a fresh add that fails, and the source clone currently sitting on the PR branch
        val service = serviceWith { _, command, parameters ->
            when {
                command == GitCommand.FETCH -> {
                    success()
                }
                command == GitCommand.WORKTREE && parameters.firstOrNull() == "prune" -> {
                    success()
                }
                command == GitCommand.WORKTREE && parameters.firstOrNull() == "list" -> {
                    success()
                }
                command == GitCommand.REV_PARSE && parameters.contains("--verify") -> {
                    success("refs/heads/$prBranch")
                }
                command == GitCommand.WORKTREE && parameters.firstOrNull() == "add" -> {
                    failure("'$prBranch' is already used by worktree at '/tmp/clone'")
                }
                command == GitCommand.REV_PARSE && parameters.contains("--abbrev-ref") -> {
                    success(prBranch)
                }
                else -> {
                    failure("unexpected command $command $parameters")
                }
            }
        }

        // WHEN
        val result = runBlocking {
            service.ensureWorktree(
                sourceCheckoutRoot = sourceRoot,
                worktreePath = worktreePath,
                remoteUrl = "https://example.invalid/clone.git",
                prNumber = 9,
                sourceBranch = prBranch
            )
        }

        // THEN
        val collision = result.shouldBeInstanceOf<WorktreeSetupResult.BranchCheckedOutInSource>()
        assertSoftly(collision) {
            branchName shouldBe prBranch
            sourceCheckoutPath shouldBe sourceRoot.toAbsolutePath().normalize()
        }
    }

    @Test
    fun `ensureWorktree - should return Failed for a fresh add failure when the source clone is on a different branch`() {
        // GIVEN a fresh add that fails, but the source clone is on a different branch (not a collision)
        val service = serviceWith { _, command, parameters ->
            when {
                command == GitCommand.FETCH -> success()
                command == GitCommand.WORKTREE && parameters.firstOrNull() == "prune" -> success()
                command == GitCommand.WORKTREE && parameters.firstOrNull() == "list" -> success()
                command == GitCommand.REV_PARSE && parameters.contains("--verify") -> success("refs/heads/$prBranch")
                command == GitCommand.WORKTREE && parameters.firstOrNull() == "add" -> failure("some other git error")
                command == GitCommand.REV_PARSE && parameters.contains("--abbrev-ref") -> success("main")
                else -> failure("unexpected command $command $parameters")
            }
        }

        // WHEN
        val result = runBlocking {
            service.ensureWorktree(
                sourceCheckoutRoot = sourceRoot,
                worktreePath = worktreePath,
                remoteUrl = "https://example.invalid/clone.git",
                prNumber = 9,
                sourceBranch = prBranch
            )
        }

        // THEN
        val failed = result.shouldBeInstanceOf<WorktreeSetupResult.Failed>()
        failed.output shouldBe "some other git error"
    }

    @Test
    fun `ensureWorktree - should return Failed when the add fails for a branch that does not yet exist`() {
        // GIVEN a fresh -b creation path (branch does not exist) that fails even though the source is on the PR branch
        val service = serviceWith { _, command, parameters ->
            when {
                command == GitCommand.FETCH -> success()
                command == GitCommand.WORKTREE && parameters.firstOrNull() == "prune" -> success()
                command == GitCommand.WORKTREE && parameters.firstOrNull() == "list" -> success()
                command == GitCommand.REV_PARSE && parameters.contains("--verify") -> failure("not a branch")
                command == GitCommand.WORKTREE && parameters.firstOrNull() == "add" -> failure("fatal: could not create")
                command == GitCommand.REV_PARSE && parameters.contains("--abbrev-ref") -> success(prBranch)
                else -> failure("unexpected command $command $parameters")
            }
        }

        // WHEN
        val result = runBlocking {
            service.ensureWorktree(
                sourceCheckoutRoot = sourceRoot,
                worktreePath = worktreePath,
                remoteUrl = "https://example.invalid/clone.git",
                prNumber = 9,
                sourceBranch = prBranch
            )
        }

        // THEN a non-existent branch cannot collide, so the generic failure is returned
        val failed = result.shouldBeInstanceOf<WorktreeSetupResult.Failed>()
        failed.output shouldBe "fatal: could not create"
    }

    @Test
    fun `ensureWorktree - should return BranchCheckedOutInSource on the reuse path when the checkout fails and the source is on the PR branch`(
        @TempDir tempDir: Path
    ) {
        // GIVEN an already-registered worktree folder that exists on disk, whose checkout onto the PR branch fails
        // because the source clone is on that same branch (decision D4: the reuse-checkout path also collides).
        val source = tempDir.resolve("clone").also { Files.createDirectories(it) }
        val existingWorktree = tempDir.resolve(".worktrees/clone--pr-9").also { Files.createDirectories(it) }
        val worktreeLine = "worktree ${existingWorktree.toAbsolutePath().normalize()}"
        val porcelain = "$ git worktree list --porcelain\n$worktreeLine\nHEAD abc123\nbranch refs/heads/main"

        val service = serviceWith { workingDir, command, parameters ->
            val inWorktree = workingDir.toAbsolutePath().normalize() == existingWorktree.toAbsolutePath().normalize()
            when {
                command == GitCommand.FETCH -> success()
                command == GitCommand.WORKTREE && parameters.firstOrNull() == "prune" -> success()
                command == GitCommand.WORKTREE && parameters.firstOrNull() == "list" -> success(porcelain)
                // The reused worktree reports a different current branch, so a checkout onto the PR branch is attempted.
                command == GitCommand.REV_PARSE && parameters.contains("--abbrev-ref") && inWorktree -> success("main")
                // A clean worktree (no uncommitted changes) so the checkout is attempted.
                command == GitCommand.STATUS -> success()
                command == GitCommand.CHECKOUT -> failure("'$prBranch' is already used by worktree at '$source'")
                // The source clone is on the PR branch, which is the collision.
                command == GitCommand.REV_PARSE && parameters.contains("--abbrev-ref") -> success(prBranch)
                else -> failure("unexpected command $command $parameters in $workingDir")
            }
        }

        // WHEN
        val result = runBlocking {
            service.ensureWorktree(
                sourceCheckoutRoot = source,
                worktreePath = existingWorktree,
                remoteUrl = "https://example.invalid/clone.git",
                prNumber = 9,
                sourceBranch = prBranch
            )
        }

        // THEN
        val collision = result.shouldBeInstanceOf<WorktreeSetupResult.BranchCheckedOutInSource>()
        assertSoftly(collision) {
            branchName shouldBe prBranch
            sourceCheckoutPath shouldBe source.toAbsolutePath().normalize()
        }
    }
}