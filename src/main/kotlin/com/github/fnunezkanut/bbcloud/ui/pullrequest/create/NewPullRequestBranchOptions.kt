package com.github.fnunezkanut.bbcloud.ui.pullrequest.create

/**
 * Pure branch-selection logic for the new pull request form: computing the source and destination option lists, the
 * defaults, and the main branch the current checkout switches to after a create. It has no IntelliJ or Compose
 * dependencies, so it is unit-testable on its own.
 */
object NewPullRequestBranchOptions {
    /** The destination fallbacks when the repository has no Bitbucket `mainbranch`, in preference order. */
    val DEFAULT_DESTINATION_CANDIDATES = listOf("main", "master")

    /**
     * Returns every branch as a destination option, with [mainBranchName] first when present, then `main`, then
     * `master`, then the remaining branches in API order (most recently updated first). Duplicates are removed.
     */
    fun destinationOptions(branches: List<String>, mainBranchName: String?): List<String> {
        val preferred = listOfNotNull(mainBranchName?.takeIf { it.isNotBlank() }) + DEFAULT_DESTINATION_CANDIDATES
        val preferredPresent = preferred.filter { candidate -> candidate in branches }
        return (preferredPresent + branches).distinct()
    }

    /**
     * Returns the default destination: [mainBranchName] when it is a branch, else `main`, else `master`, else the first
     * branch. Null when there are no branches.
     */
    fun defaultDestination(branches: List<String>, mainBranchName: String?): String? {
        mainBranchName?.takeIf { it.isNotBlank() && it in branches }?.let { return it }
        DEFAULT_DESTINATION_CANDIDATES.firstOrNull { candidate -> candidate in branches }?.let { return it }
        return branches.firstOrNull()
    }

    /**
     * Returns the source options: every branch minus `main`, `master`, [mainBranchName] and [destinationBranch],
     * keeping API order so the most recently updated branch stays first.
     */
    fun sourceOptions(branches: List<String>, mainBranchName: String?, destinationBranch: String?): List<String> {
        val excluded = (
            DEFAULT_DESTINATION_CANDIDATES +
                listOfNotNull(mainBranchName?.takeIf { it.isNotBlank() }) +
                listOfNotNull(destinationBranch)
        ).toSet()
        return branches.filterNot { branch -> branch in excluded }
    }

    /** Returns the default source: [currentLocalBranch] when it is an option, else the first option. */
    fun defaultSource(options: List<String>, currentLocalBranch: String?): String? {
        currentLocalBranch?.takeIf { it in options }?.let { return it }
        return options.firstOrNull()
    }

    /** Keeps [selectedSource] when it is still an option, otherwise falls back to [defaultSource]. */
    fun reconcileSource(selectedSource: String?, options: List<String>, currentLocalBranch: String?): String? {
        selectedSource?.takeIf { it in options }?.let { return it }
        return defaultSource(options = options, currentLocalBranch = currentLocalBranch)
    }

    /**
     * The branch the current checkout switches to after a create: [mainBranchName] when non-blank, else `main` if it is
     * a branch, else `master` if it is a branch, else null.
     */
    fun localMainBranch(branches: List<String>, mainBranchName: String?): String? {
        mainBranchName?.takeIf { it.isNotBlank() }?.let { return it }
        return DEFAULT_DESTINATION_CANDIDATES.firstOrNull { candidate -> candidate in branches }
    }

    /** The set of names treated as the main branch, used when deciding whether an uncommitted-changes warning applies. */
    fun mainBranchNames(mainBranchName: String?): Set<String> {
        return setOfNotNull(mainBranchName?.takeIf { it.isNotBlank() }, "main", "master")
    }
}