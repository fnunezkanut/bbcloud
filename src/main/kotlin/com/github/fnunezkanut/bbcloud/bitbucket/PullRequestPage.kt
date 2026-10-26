package com.github.fnunezkanut.bbcloud.bitbucket

data class PullRequestPage(val pullRequests: List<PullRequestSummary>, val nextUrl: String?)