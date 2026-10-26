package com.github.fnunezkanut.bbcloud.git

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.GitExecutableManager
import java.nio.file.Path
import kotlin.time.TimeSource

/** The outcome of a single git command: whether it succeeded, and its formatted combined output for logging. */
data class GitCommandResult(val success: Boolean, val combinedOutput: String)

/**
 * Resolves the open IntelliJ [Project] whose base path matches a checkout root, so [BbCloudWorktreeService] can run
 * git through that project's [GitLineHandler] when a window is already open for it. Injectable so the service can be
 * unit-tested without the platform's [com.intellij.openapi.project.ProjectManager]; the production implementation is
 * [OpenProjectLocatorImpl].
 */
fun interface OpenProjectLocator {
    fun findOpenProjectForPath(path: Path): Project?
}

/** The production [OpenProjectLocator] that matches a checkout root against the open projects' base paths. */
class OpenProjectLocatorImpl : OpenProjectLocator {
    override fun findOpenProjectForPath(path: Path): Project? {
        val target = path.toAbsolutePath().normalize()
        return com.intellij.openapi.project.ProjectManager.getInstance().openProjects.firstOrNull { project ->
            val basePath = project.basePath ?: return@firstOrNull false
            val projectPath = try {
                Path.of(basePath).toAbsolutePath().normalize()
            } catch (_: Exception) {
                return@firstOrNull false
            }
            projectPath == target
        }
    }
}

/**
 * The seam [BbCloudWorktreeService] uses to run git, so the service's reconciliation logic can be unit-tested against
 * stubbed command results without a platform fixture. The real implementation is [PlatformGitCommandRunner]; when a
 * project is already open for the working directory its [GitLineHandler] path is used for consistency with the rest of
 * the plugin, otherwise the command runs through a direct process invocation so worktree creation still works when no
 * source-clone project is open.
 */
interface GitCommandRunner {
    fun run(project: Project?, workingDir: Path, command: GitCommand, parameters: List<String>, remoteUrl: String? = null): GitCommandResult
}

/** The production [GitCommandRunner] that drives git4idea's [Git] or a direct [GeneralCommandLine]. */
class PlatformGitCommandRunner : GitCommandRunner {
    override fun run(project: Project?, workingDir: Path, command: GitCommand, parameters: List<String>, remoteUrl: String?): GitCommandResult {
        return if (project != null) {
            runViaGitLineHandler(project = project, workingDir = workingDir, command = command, parameters = parameters, remoteUrl = remoteUrl)
        } else {
            runViaProcess(workingDir = workingDir, command = command, parameters = parameters)
        }
    }

    private fun runViaGitLineHandler(project: Project, workingDir: Path, command: GitCommand, parameters: List<String>, remoteUrl: String?): GitCommandResult {
        val handler = GitLineHandler(project, workingDir, command).apply {
            if (remoteUrl != null) {
                setUrls(listOf(remoteUrl))
            }
            addParameters(parameters)
        }
        val commandLine = handler.printableCommandLine()
        LOG.debug("Running $commandLine in $workingDir")
        val started = TimeSource.Monotonic.markNow()
        val result = Git.getInstance().runCommand(handler)
        LOG.debug("Finished $commandLine: exitCode=${result.exitCode} in ${started.elapsedNow().inWholeMilliseconds} ms")
        return GitCommandResult(
            success = result.success(),
            combinedOutput = GitCommandOutput.format(
                commandLine = commandLine,
                output = result.outputAsJoinedString,
                errorOutput = result.errorOutputAsJoinedString
            )
        )
    }

    private fun runViaProcess(workingDir: Path, command: GitCommand, parameters: List<String>): GitCommandResult {
        val gitExecutable = GitExecutableManager.getInstance().pathToGit
        val commandLine = GeneralCommandLine(gitExecutable, command.name())
            .withParameters(parameters)
            .withWorkDirectory(workingDir.toFile())
        LOG.debug("Running ${commandLine.commandLineString} in $workingDir")
        val started = TimeSource.Monotonic.markNow()
        return try {
            val processOutput = CapturingProcessHandler(commandLine).runProcess(PROCESS_TIMEOUT_MS)
            val exitCode = if (processOutput.isTimeout) -1 else processOutput.exitCode
            LOG.debug("Finished ${commandLine.commandLineString}: exitCode=$exitCode in ${started.elapsedNow().inWholeMilliseconds} ms")
            GitCommandResult(
                success = !processOutput.isTimeout && processOutput.exitCode == 0,
                combinedOutput = GitCommandOutput.format(
                    commandLine = commandLine.commandLineString,
                    output = processOutput.stdout,
                    errorOutput = processOutput.stderr
                )
            )
        } catch (exception: Exception) {
            LOG.debug("Git process failed: ${commandLine.commandLineString}", exception)
            GitCommandResult(success = false, combinedOutput = exception.message.orEmpty())
        }
    }

    companion object {
        private val LOG = logger<PlatformGitCommandRunner>()
        private const val PROCESS_TIMEOUT_MS = 120_000
    }
}