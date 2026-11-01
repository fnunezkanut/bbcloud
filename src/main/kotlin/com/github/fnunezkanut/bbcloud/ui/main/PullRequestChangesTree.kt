package com.github.fnunezkanut.bbcloud.ui.main

import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.lazy.tree.TreeGeneratorScope
import org.jetbrains.jewel.foundation.lazy.tree.buildTree

/**
 * Builds the directory tree of the changed files.
 *
 * The tree is built from the in-memory rows only: the platform's `Path.asTree`/`File.asTree` helpers walk the real
 * file system and cannot be used for a pull-request diff. Directories are sorted before the files of the same level,
 * which is the ordering the platform changes browser used.
 *
 * The ids are stable across refreshes, so the expansion state of the tree survives a reload of the changed files.
 */
internal fun buildChangesTree(rows: List<PullRequestFileChangeRow>): Tree<PullRequestChangesTreeNode> {
    val root = DirectoryNode(name = "", path = "")
    rows.forEach { row -> root.addRow(row) }
    return buildTree {
        root.directories.values.sortedBy { directory -> directory.name }.forEach { directory -> addDirectory(directory) }
        root.files.sortedBy { row -> row.fileName }.forEach { row ->
            addLeaf(data = PullRequestChangesTreeNode.File(row), id = fileId(row.relativePath))
        }
    }
}

/**
 * The ids of every directory node of the changed-files tree. Used to expand the whole tree; the ids match the ones
 * [buildChangesTree] assigns, so they can be handed straight to the tree state.
 */
internal fun changesTreeDirectoryIds(rows: List<PullRequestFileChangeRow>): List<String> {
    val root = DirectoryNode(name = "", path = "")
    rows.forEach { row -> root.addRow(row) }
    val ids = mutableListOf<String>()
    collectDirectoryIds(node = root, into = ids)
    return ids
}

private fun collectDirectoryIds(node: DirectoryNode, into: MutableList<String>) {
    node.directories.values.forEach { child ->
        into += directoryId(child.path)
        collectDirectoryIds(node = child, into = into)
    }
}

/** A directory of the file tree, with its sub-directories and the files it directly contains. */
private class DirectoryNode(val name: String, val path: String) {
    val directories: MutableMap<String, DirectoryNode> = linkedMapOf()
    val files: MutableList<PullRequestFileChangeRow> = mutableListOf()

    /** Files that live directly in the root of the project, or in the directory chain of [row]. */
    fun addRow(row: PullRequestFileChangeRow) {
        val segments = row.relativePath.split('/').filter { segment -> segment.isNotEmpty() }
        if (segments.isEmpty()) {
            return
        }
        if (segments.size == 1) {
            files += row
            return
        }
        var current = this
        val pathSegments = mutableListOf<String>()
        segments.dropLast(1).forEach { segment ->
            pathSegments += segment
            val path = pathSegments.joinToString(separator = "/")
            current = current.directories.getOrPut(segment) { DirectoryNode(name = segment, path = path) }
        }
        current.files += row
    }
}

private fun TreeGeneratorScope<PullRequestChangesTreeNode>.addDirectory(directory: DirectoryNode) {
    addNode(
        data = PullRequestChangesTreeNode.Directory(name = directory.name, path = directory.path),
        id = directoryId(directory.path)
    ) {
        directory.directories.values.sortedBy { child -> child.name }.forEach { child -> addDirectory(child) }
        directory.files.sortedBy { row -> row.fileName }.forEach { row ->
            addLeaf(data = PullRequestChangesTreeNode.File(row), id = fileId(row.relativePath))
        }
    }
}

/**
 * Groups every file row under each directory above it, keyed by the directory path [buildChangesTree] uses. A file in
 * `src/main/App.kt` is listed under `src` and under `src/main`. Build this once per row list and reuse it per folder,
 * so a folder does not rescan all rows during composition.
 */
internal fun fileRowsByDirectory(rows: List<PullRequestFileChangeRow>): Map<String, List<PullRequestFileChangeRow>> {
    val rowsByDirectory = mutableMapOf<String, MutableList<PullRequestFileChangeRow>>()
    rows.forEach { row ->
        val segments = row.relativePath.split('/').filter { segment -> segment.isNotEmpty() }
        segments.dropLast(1).indices.forEach { index ->
            val directoryPath = segments.subList(0, index + 1).joinToString(separator = "/")
            rowsByDirectory.getOrPut(directoryPath) { mutableListOf() } += row
        }
    }
    return rowsByDirectory
}

/**
 * Tells whether a folder shows the Viewed tick: it has at least one file below it and every one of those files is
 * viewed according to [isViewed]. A folder with no file below it is never ticked.
 */
internal fun directoryFullyViewed(descendantRows: List<PullRequestFileChangeRow>, isViewed: (PullRequestFileChangeRow) -> Boolean): Boolean {
    if (descendantRows.isEmpty()) {
        return false
    }
    return descendantRows.all { row -> isViewed(row) }
}

/** Directories and files live in different id spaces, so a directory and a file can never collide. */
internal fun directoryId(path: String): String = "dir:$path"

internal fun fileId(path: String): String = "file:$path"

/**
 * Resolves the file row the Enter key should open: the single selected tree key, when it is a file id present in
 * [rowsById]. Returns null for a directory id, for zero keys, or for several selected keys, so the caller leaves the
 * default tree key actions untouched.
 */
internal fun selectedFileRow(selectedKeys: Set<Any>, rowsById: Map<String, PullRequestFileChangeRow>): PullRequestFileChangeRow? {
    val key = selectedKeys.singleOrNull() ?: return null
    return rowsById[key]
}