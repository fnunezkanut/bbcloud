package com.github.fnunezkanut.bbcloud.git

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.logger
import git4idea.config.GitExecutableManager
import java.nio.file.Path
import kotlin.time.TimeSource

/**
 * Reads the current branch name of a git worktree without requiring an open IntelliJ project.
 *
 * Runs `git rev-parse --abbrev-ref HEAD` against the worktree directory via a direct process invocation
 * (no `GitLineHandler`, since the watched-projects table has no open `Project` per worktree). Never throws:
 * a non-zero exit, timeout, blank output, or the literal `HEAD` (detached HEAD) all yield null so callers can
 * render icon + `#<prNum>` only (edge case E-2).
 */
object WorktreeCurrentBranchReader {
    private val LOG = logger<WorktreeCurrentBranchReader>()

    /**
     * Returns the current branch name of the worktree at [worktreePath], or null when it cannot be derived
     * (git error, timeout, blank output, or detached HEAD). Safe to call off the EDT; callers should run it on
     * [kotlinx.coroutines.Dispatchers.IO].
     */
    suspend fun readCurrentBranch(worktreePath: Path): String? {
        val gitExecutable = GitExecutableManager.getInstance().pathToGit
        val commandLine = GeneralCommandLine(gitExecutable, "rev-parse")
            .withParameters(listOf("--abbrev-ref", "HEAD"))
            .withWorkDirectory(worktreePath.toFile())

        LOG.debug("Running ${commandLine.commandLineString} in $worktreePath")
        val started = TimeSource.Monotonic.markNow()
        return try {
            val processOutput = CapturingProcessHandler(commandLine).runProcess(PROCESS_TIMEOUT_MS)
            val exitCode = if (processOutput.isTimeout) -1 else processOutput.exitCode
            LOG.debug(
                "Finished ${commandLine.commandLineString}: exitCode=$exitCode in ${started.elapsedNow().inWholeMilliseconds} ms"
            )
            if (!processOutput.isTimeout && processOutput.exitCode == 0) {
                val branch = processOutput.stdout.trim()
                when {
                    branch.isEmpty() -> null
                    branch == "HEAD" -> null // detached HEAD
                    else -> branch
                }
            } else {
                null
            }
        } catch (exception: Exception) {
            LOG.debug("Git branch read failed for worktree $worktreePath", exception)
            null
        }
    }

    private const val PROCESS_TIMEOUT_MS = 120_000
}