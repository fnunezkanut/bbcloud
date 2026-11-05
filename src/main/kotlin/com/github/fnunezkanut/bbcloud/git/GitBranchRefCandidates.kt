package com.github.fnunezkanut.bbcloud.git

import git4idea.repo.GitRemote

/** Ordered Git ref names to try when resolving a Bitbucket branch name to a local repository ref. */
object GitBranchRefCandidates {
    /**
     * Returns the candidates in the order to try them. By default the local branch name comes first; with
     * [preferRemote] the remote tracking name (`<remote>/<branch>`) comes first.
     */
    fun candidates(branchName: String, remoteName: String = GitRemote.ORIGIN, preferRemote: Boolean = false): List<String> {
        val trimmed = branchName.trim()
        if (trimmed.isEmpty()) {
            return emptyList()
        }
        val remotePrefix = "$remoteName/"
        val ordered = buildList {
            add(trimmed)
            if (!trimmed.startsWith(remotePrefix)) {
                add(remotePrefix + trimmed)
            } else {
                add(trimmed.removePrefix(remotePrefix))
            }
        }.distinct()
        return if (preferRemote) {
            ordered.sortedByDescending { candidate -> candidate.startsWith(remotePrefix) }
        } else {
            ordered
        }
    }
}