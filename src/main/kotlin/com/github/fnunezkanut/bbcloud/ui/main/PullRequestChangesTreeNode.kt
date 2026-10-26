package com.github.fnunezkanut.bbcloud.ui.main

/** The payload of a node of the Jewel file tree: either a directory or a changed file. */
sealed interface PullRequestChangesTreeNode {
    data class Directory(val name: String, val path: String) : PullRequestChangesTreeNode

    data class File(val row: PullRequestFileChangeRow) : PullRequestChangesTreeNode
}