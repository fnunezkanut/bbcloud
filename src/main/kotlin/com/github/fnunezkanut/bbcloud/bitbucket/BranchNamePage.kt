package com.github.fnunezkanut.bbcloud.bitbucket

/** One page of branch names and the URL of the next page, mirroring [PullRequestPage]. */
data class BranchNamePage(val names: List<String>, val nextUrl: String?)