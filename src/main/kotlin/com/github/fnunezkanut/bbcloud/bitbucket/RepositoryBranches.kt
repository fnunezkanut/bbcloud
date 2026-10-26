package com.github.fnunezkanut.bbcloud.bitbucket

/**
 * The remote branch names of a repository, newest-updated first. [isTruncated] is true when the branch listing stopped
 * at the page limit before reaching the last page, so not every branch is included.
 */
data class RepositoryBranches(val names: List<String>, val isTruncated: Boolean)