package com.github.fnunezkanut.bbcloud.project

import com.intellij.openapi.diagnostic.logger
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException

/** Totals of one background cleanup run. */
data class ResolvedWorktreeCleanupSummary(val removed: Int, val skippedInUse: Int, val closeFailed: Int, val failed: Int)

/**
 * Removes the work trees of merged or declined pull requests without any prompt. An IDE window open on a work tree is
 * closed first and uncommitted changes are discarded, both through [WatchedWorktreeRemover]. A work tree that is being
 * opened right now is skipped. One failing candidate never stops the others.
 */
internal class ResolvedWorktreeCleaner(private val isOpenInProgress: (Path) -> Boolean, private val remover: WatchedWorktreeRemover) {
    suspend fun clean(sourceCheckoutRoot: Path, workTreeDirectory: Path, candidates: List<ResolvedWorktreeCandidate>): ResolvedWorktreeCleanupSummary {
        var removed = 0
        var skippedInUse = 0
        var closeFailed = 0
        var failed = 0
        for (candidate in candidates) {
            if (isOpenInProgress(candidate.worktreePath)) {
                skippedInUse++
                LOG.info("Skipping cleanup of the work tree of pull request #${candidate.prNumber}: it is being opened")
                continue
            }
            when (val outcome = removeOne(sourceCheckoutRoot, workTreeDirectory, candidate)) {
                WatchedWorktreeRemovalOutcome.Removed -> {
                    removed++
                }
                WatchedWorktreeRemovalOutcome.CloseFailed -> {
                    closeFailed++
                    LOG.info("Kept the work tree of pull request #${candidate.prNumber}: its IDE window could not be closed")
                }
                is WatchedWorktreeRemovalOutcome.RemovalFailed -> {
                    failed++
                    LOG.warn("Could not remove the work tree of pull request #${candidate.prNumber}: ${outcome.output}", outcome.cause)
                }
            }
        }
        LOG.debug("Resolved work tree cleanup: removed=$removed, skippedInUse=$skippedInUse, closeFailed=$closeFailed, failed=$failed")
        return ResolvedWorktreeCleanupSummary(removed = removed, skippedInUse = skippedInUse, closeFailed = closeFailed, failed = failed)
    }

    private suspend fun removeOne(sourceCheckoutRoot: Path, workTreeDirectory: Path, candidate: ResolvedWorktreeCandidate): WatchedWorktreeRemovalOutcome {
        return try {
            remover.remove(
                sourceCheckoutRoot = sourceCheckoutRoot,
                worktreePath = candidate.worktreePath,
                workTreeDirectory = workTreeDirectory
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            WatchedWorktreeRemovalOutcome.RemovalFailed(output = exception.message.orEmpty(), cause = exception)
        }
    }

    private companion object {
        val LOG = logger<ResolvedWorktreeCleaner>()
    }
}