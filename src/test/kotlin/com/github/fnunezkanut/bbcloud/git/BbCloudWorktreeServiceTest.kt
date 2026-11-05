package com.github.fnunezkanut.bbcloud.git

import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeRecentProjects
import com.github.fnunezkanut.bbcloud.project.RecentProjectsGateway
import com.intellij.ide.ProjectGroup
import com.intellij.openapi.project.Project
import git4idea.commands.GitCommand
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

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
                sourceBranch = prBranch,
                destinationBranch = "main"
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
                sourceBranch = prBranch,
                destinationBranch = "main"
            )
        }

        // THEN
        val failed = result.shouldBeInstanceOf<WorktreeSetupResult.Failed>()
        failed.output shouldBe "some other git error"
    }

    @Test
    fun `ensureWorktree - should still return Success when the branch sync after a successful add fails`() {
        // GIVEN a fresh add that succeeds, while every git command of the branch sync fails or throws
        val service = serviceWith { _, command, parameters ->
            when {
                command == GitCommand.FETCH && parameters.any { parameter -> parameter.startsWith("+refs/heads/") } -> {
                    throw IllegalStateException("network down")
                }
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
                    failure("not a branch")
                }
                command == GitCommand.WORKTREE && parameters.firstOrNull() == "add" -> {
                    success()
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
                sourceBranch = prBranch,
                destinationBranch = "main"
            )
        }

        // THEN the sync is best-effort and does not change the result
        result.shouldBeInstanceOf<WorktreeSetupResult.Success>()
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
                sourceBranch = prBranch,
                destinationBranch = "main"
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
                sourceBranch = prBranch,
                destinationBranch = "main"
            )
        }

        // THEN
        val collision = result.shouldBeInstanceOf<WorktreeSetupResult.BranchCheckedOutInSource>()
        assertSoftly(collision) {
            branchName shouldBe prBranch
            sourceCheckoutPath shouldBe source.toAbsolutePath().normalize()
        }
    }

    @Test
    fun `removeWorktree - should forget the worktree in recent projects after a successful removal`(
        @TempDir tempDir: Path
    ) {
        // GIVEN an existing pull request worktree folder, and git remove and prune that both succeed
        val workTreeDirectory = tempDir.resolve(".worktrees")
        val worktree = workTreeDirectory.resolve("clone--pr-9").also { Files.createDirectories(it) }
        val gateway = RecordingRecentProjectsGateway()
        val service = serviceWithRecentProjects(gateway = gateway) { _, command, parameters ->
            if (command == GitCommand.WORKTREE && parameters.firstOrNull() in setOf("remove", "prune")) {
                success()
            } else {
                failure("unexpected command $command $parameters")
            }
        }

        // WHEN
        val result = runBlocking {
            service.removeWorktree(sourceCheckoutRoot = sourceRoot, worktreePath = worktree, workTreeDirectory = workTreeDirectory)
        }

        // THEN the removed worktree is forgotten
        assertSoftly {
            result shouldBe WorktreeRemovalResult.Success
            gateway.removedPaths shouldContainExactly listOf(worktree.toAbsolutePath().normalize().invariantSeparatorsPathString)
        }
    }

    @Test
    fun `removeWorktree - should forget the worktree when the folder no longer exists`(
        @TempDir tempDir: Path
    ) {
        // GIVEN a worktree folder that was already deleted by hand, and a prune that succeeds
        val workTreeDirectory = tempDir.resolve(".worktrees")
        val worktree = workTreeDirectory.resolve("clone--pr-9")
        val gateway = RecordingRecentProjectsGateway()
        val service = serviceWithRecentProjects(gateway = gateway) { _, command, parameters ->
            if (command == GitCommand.WORKTREE && parameters.firstOrNull() == "prune") {
                success()
            } else {
                failure("unexpected command $command $parameters")
            }
        }

        // WHEN
        val result = runBlocking {
            service.removeWorktree(sourceCheckoutRoot = sourceRoot, worktreePath = worktree, workTreeDirectory = workTreeDirectory)
        }

        // THEN the stale entry is still forgotten
        assertSoftly {
            result shouldBe WorktreeRemovalResult.Success
            gateway.removedPaths shouldContainExactly listOf(worktree.toAbsolutePath().normalize().invariantSeparatorsPathString)
        }
    }

    @Test
    fun `removeWorktree - should not forget the worktree when git removal fails`(
        @TempDir tempDir: Path
    ) {
        // GIVEN an existing worktree folder where git remove fails
        val workTreeDirectory = tempDir.resolve(".worktrees")
        val worktree = workTreeDirectory.resolve("clone--pr-9").also { Files.createDirectories(it) }
        val gateway = RecordingRecentProjectsGateway()
        val service = serviceWithRecentProjects(gateway = gateway) { _, command, parameters ->
            if (command == GitCommand.WORKTREE && parameters.firstOrNull() == "remove") {
                failure("fatal: locked worktree")
            } else {
                success()
            }
        }

        // WHEN
        val result = runBlocking {
            service.removeWorktree(sourceCheckoutRoot = sourceRoot, worktreePath = worktree, workTreeDirectory = workTreeDirectory)
        }

        // THEN the entry stays so the user can still reach the worktree
        assertSoftly {
            result.shouldBeInstanceOf<WorktreeRemovalResult.Failed>()
            gateway.removedPaths.shouldBeEmpty()
        }
    }

    @Test
    fun `removeWorktree - should not forget anything for a refused unexpected path`(
        @TempDir tempDir: Path
    ) {
        // GIVEN a folder that is not directly under the work tree root
        val workTreeDirectory = tempDir.resolve(".worktrees")
        val unexpectedWorktree = tempDir.resolve("elsewhere/clone--pr-9").also { Files.createDirectories(it) }
        val gateway = RecordingRecentProjectsGateway()
        val service = serviceWithRecentProjects(gateway = gateway) { _, _, _ -> success() }

        // WHEN
        val result = runBlocking {
            service.removeWorktree(sourceCheckoutRoot = sourceRoot, worktreePath = unexpectedWorktree, workTreeDirectory = workTreeDirectory)
        }

        // THEN the removal is refused and Recent Projects is untouched
        assertSoftly {
            result.shouldBeInstanceOf<WorktreeRemovalResult.Failed>()
            gateway.removedPaths.shouldBeEmpty()
        }
    }

    private fun serviceWithRecentProjects(
        gateway: RecentProjectsGateway,
        script: (Path, GitCommand, List<String>) -> GitCommandResult
    ): BbCloudWorktreeService {
        return BbCloudWorktreeService.forTesting(
            gitRunner = ScriptedGitRunner(script = script),
            openProjectLocator = noProjectLocator,
            recentProjects = PullRequestWorktreeRecentProjects(gateway = gateway)
        )
    }

    /** Records the paths removed from Recent Projects. These tests never create groups, so group lookups find nothing. */
    private class RecordingRecentProjectsGateway : RecentProjectsGateway {
        val removedPaths = mutableListOf<String>()

        override fun findGroup(name: String): ProjectGroup? {
            return null
        }

        override fun addGroup(group: ProjectGroup) {
            throw IllegalStateException("addGroup is not expected in removal tests")
        }

        override fun removeGroup(group: ProjectGroup) {
            throw IllegalStateException("removeGroup is not expected without a group")
        }

        override fun moveProjectToGroup(path: String, group: ProjectGroup) {
            throw IllegalStateException("moveProjectToGroup is not expected in removal tests")
        }

        override fun removePath(path: String) {
            removedPaths.add(path)
        }
    }

    @Test
    fun `registeredWorktrees - should parse registered paths from porcelain output`() {
        // GIVEN
        val service = serviceWith { _, command, parameters ->
            if (command == GitCommand.WORKTREE && parameters == listOf("list", "--porcelain")) {
                success("worktree /tmp/clone\nHEAD abc\nbranch refs/heads/main\n\nworktree /tmp/.worktrees/clone--pr-9\nHEAD def\n")
            } else {
                failure("unexpected command $command $parameters")
            }
        }

        // WHEN
        val paths = runBlocking { service.registeredWorktrees(sourceCheckoutRoot = sourceRoot) }

        // THEN
        paths shouldContainExactly listOf(Path.of("/tmp/clone"), Path.of("/tmp/.worktrees/clone--pr-9"))
    }

    @Test
    fun `registeredWorktrees - should return an empty list when git fails`() {
        // GIVEN
        val service = serviceWith { _, _, _ -> failure("fatal: not a git repository") }

        // WHEN
        val paths = runBlocking { service.registeredWorktrees(sourceCheckoutRoot = sourceRoot) }

        // THEN
        paths.shouldBeEmpty()
    }
}