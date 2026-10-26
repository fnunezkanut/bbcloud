package com.github.fnunezkanut.bbcloud.git

import git4idea.repo.GitRemote

/** Ordered Git ref names to try when resolving a Bitbucket branch name to a local repository ref. */
object GitBranchRefCandidates {
    fun candidates(branchName: String, remoteName: String = GitRemote.ORIGIN): List<String> {
        val trimmed = branchName.trim()
        if (trimmed.isEmpty()) {
            return emptyList()
        }
        val remotePrefix = "$remoteName/"
        return buildList {
            add(trimmed)
            if (!trimmed.startsWith(remotePrefix)) {
                add(remotePrefix + trimmed)
            } else {
                add(trimmed.removePrefix(remotePrefix))
            }
        }.distinct()
    }
}