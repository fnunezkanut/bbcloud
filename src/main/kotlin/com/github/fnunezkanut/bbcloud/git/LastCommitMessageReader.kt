package com.github.fnunezkanut.bbcloud.git

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.logger
import git4idea.config.GitExecutableManager
import java.nio.file.Path
import kotlin.time.TimeSource

/**
 * Reads the raw message of the most recent commit of a git checkout, used to prefill the new pull request form.
 *
 * Runs `git log -1 --format=%B [<ref>]` via a direct process invocation, like [WorktreeCurrentBranchReader]. Never
 * throws: a non-zero exit, timeout, or blank output yields null so the caller can leave the form unprefilled.
 */
object LastCommitMessageReader {
    private val LOG = logger<LastCommitMessageReader>()

    /** Reads the last commit of HEAD in the checkout at [worktreePath]. Callers should run it on `Dispatchers.IO`. */
    suspend fun readLastCommitMessage(worktreePath: Path): String? {
        return readRaw(worktreePath = worktreePath, ref = null)
    }

    /**
     * Reads the last commit of [branchName], trying each ref from [GitBranchRefCandidates] in order and returning the
     * first message found. Returns null when no candidate resolves. Callers should run it on `Dispatchers.IO`.
     */
    suspend fun readLastCommitMessageForBranch(worktreePath: Path, branchName: String): String? {
        for (candidate in GitBranchRefCandidates.candidates(branchName = branchName)) {
            val message = readRaw(worktreePath = worktreePath, ref = candidate)
            if (message != null) {
                return message
            }
        }
        return null
    }

    private suspend fun readRaw(worktreePath: Path, ref: String?): String? {
        val gitExecutable = GitExecutableManager.getInstance().pathToGit
        val parameters = listOf("-1", "--format=%B") + listOfNotNull(ref)
        val commandLine = GeneralCommandLine(gitExecutable, "log")
            .withParameters(parameters)
            .withWorkDirectory(worktreePath.toFile())

        LOG.debug("Running ${commandLine.commandLineString} in $worktreePath")
        val started = TimeSource.Monotonic.markNow()
        return try {
            val processOutput = CapturingProcessHandler(commandLine).runProcess(PROCESS_TIMEOUT_MS)
            val exitCode = if (processOutput.isTimeout) -1 else processOutput.exitCode
            LOG.debug(
                "Finished ${commandLine.commandLineString}: exitCode=$exitCode in ${started.elapsedNow().inWholeMilliseconds} ms"
            )
            val succeeded = !processOutput.isTimeout && processOutput.exitCode == 0
            if (succeeded && processOutput.stdout.isNotBlank()) processOutput.stdout else null
        } catch (exception: Exception) {
            LOG.debug("Last commit read failed for worktree $worktreePath", exception)
            null
        }
    }

    private const val PROCESS_TIMEOUT_MS = 120_000
}