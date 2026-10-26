package com.github.fnunezkanut.bbcloud.bitbucket

data class ItemCount(val value: Int, val isTruncated: Boolean)

/**
 * Counts shown as tab badges; a null field means the corresponding request failed.
 */
data class PullRequestCounts(val filesChanged: ItemCount?, val commits: ItemCount?)

data class CountPage(val size: Int?, val valueCount: Int, val nextUrl: String?)