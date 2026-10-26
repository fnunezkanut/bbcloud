package com.github.fnunezkanut.bbcloud.bitbucket

/** One repository's answer: a null [pullRequests] means the request failed and that repository contributes nothing. */
data class RepositoryReviewQueue(val repoSlug: String, val pullRequests: List<PullRequestSummary>?)

/** Turns the per-repository answers into the single review queue the Other Projects tab renders. */
object ReviewingPullRequestAssembler {
    /**
     * The repositories to ask: [excludedRepoSlug] dropped, because the Current Project tab already lists it, and
     * capped, so a workspace with hundreds of repositories cannot fan out without bound.
     *
     * The exclusion is matched without case: it is derived from the local directory name, while Bitbucket slugs
     * are lowercase, so `MyRepo` on disk has to match `myrepo` remotely.
     */
    fun repositoryTargets(repoSlugs: List<String>, excludedRepoSlug: String?, maxRepositories: Int): List<String> {
        val excluded = excludedRepoSlug?.trim()?.lowercase()?.takeIf { slug -> slug.isNotEmpty() }
        return repoSlugs
            .filter { slug -> slug.isNotBlank() }
            .filterNot { slug -> excluded != null && slug.lowercase() == excluded }
            .distinct()
            .take(maxRepositories)
    }

    /**
     * Merges the repositories newest-updated first and caps the result, reporting through
     * [ReviewingPullRequestListResult.Success.isTruncated] whenever the queue could not be complete.
     *
     * Capping is still correct: each repository was read newest-updated first up to [maxPullRequests], so a pull
     * request left out of its own repository page has at least that many newer ones behind it in that repository
     * and cannot belong to the merged head either.
     */
    fun assemble(
        workspace: String,
        queues: List<RepositoryReviewQueue>,
        isRepositoryListTruncated: Boolean,
        maxPullRequests: Int
    ): ReviewingPullRequestListResult {
        val failedCount = queues.count { queue -> queue.pullRequests == null }
        if (queues.isNotEmpty() && failedCount == queues.size) {
            return ReviewingPullRequestListResult.Failed()
        }
        val merged = queues.flatMap { queue ->
            queue.pullRequests.orEmpty().map { summary ->
                ReviewingPullRequest(summary = summary, workspace = workspace, repoSlug = queue.repoSlug)
            }
        }
        val newestFirst = merged.sortedByDescending { pullRequest -> pullRequest.summary.updatedOn }
        return ReviewingPullRequestListResult.Success(
            pullRequests = newestFirst.take(maxPullRequests),
            isTruncated = isRepositoryListTruncated || failedCount > 0 || newestFirst.size > maxPullRequests
        )
    }
}