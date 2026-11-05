package com.github.fnunezkanut.bbcloud.ui.recentprojects

import com.github.fnunezkanut.bbcloud.project.RecentProjectGroupSnapshot
import com.github.fnunezkanut.bbcloud.project.RecentProjectsSnapshot

/**
 * One recent project. [repoSlug] is the Bitbucket repository when known, [isPullRequestWorktree] is true for folders
 * directly under the PR work tree root, and [isCurrentProject] is true for the project hosting the tool window.
 */
data class RecentProjectRow(
    val path: String,
    val name: String,
    val displayPath: String,
    val branch: String?,
    val exists: Boolean,
    val isGrouped: Boolean,
    val repoSlug: String? = null,
    val isPullRequestWorktree: Boolean = false,
    val isCurrentProject: Boolean = false
)

data class RecentProjectGroupRow(val name: String, val isExpanded: Boolean, val projects: List<RecentProjectRow>)

data class RecentProjectsUiState(
    val isLoading: Boolean,
    val groups: List<RecentProjectGroupRow>,
    val ungrouped: List<RecentProjectRow>,
    val searchQuery: String,
    val loadFailed: Boolean,
    /** Bumped whenever a repository avatar finishes loading, so the screen recomposes. */
    val avatarRevision: Int = 0
) {
    val isEmpty: Boolean
        get() = groups.isEmpty() && ungrouped.isEmpty()

    companion object {
        val INITIAL = RecentProjectsUiState(
            isLoading = true,
            groups = emptyList(),
            ungrouped = emptyList(),
            searchQuery = "",
            loadFailed = false
        )
    }
}

/** The paths of the Recent Projects list arranged as groups first and the ungrouped paths after them. */
data class RecentProjectsTree(val groups: List<Pair<RecentProjectGroupSnapshot, List<String>>>, val ungroupedPaths: List<String>)

/**
 * Arranges [snapshot] the way the Welcome screen does: groups first, ordered by the lowest index of any of their paths in
 * the recent paths (groups without a recent path last, ties broken by name), then the ungrouped paths in recent order.
 * The platform breaks ties with a natural-order comparator; a case-insensitive comparison is close enough here.
 *
 * Group members are shown even when they are not in the recent paths, as the platform does. Duplicate members are
 * removed and empty groups are dropped.
 */
fun buildRecentProjectsTree(snapshot: RecentProjectsSnapshot): RecentProjectsTree {
    val recentIndex = snapshot.recentPaths.withIndex().associate { (index, path) -> path to index }
    val groups = snapshot.groups
        .map { group -> group to group.projectPaths.distinct() }
        .filter { (_, paths) -> paths.isNotEmpty() }
        .sortedWith(
            compareBy<Pair<RecentProjectGroupSnapshot, List<String>>> { (_, paths) ->
                paths.minOf { path -> recentIndex[path] ?: Int.MAX_VALUE }
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { (group, _) -> group.name }
        )
    val groupedPaths = groups.flatMapTo(hashSetOf()) { (_, paths) -> paths }
    val ungroupedPaths = snapshot.recentPaths.distinct().filter { path -> path !in groupedPaths }
    return RecentProjectsTree(
        groups = groups,
        ungroupedPaths = ungroupedPaths
    )
}

/**
 * Filters the tab content by [query], matching the trimmed query case-insensitively against the name, display path, or
 * branch. A group whose name matches keeps all its projects; other groups keep only matching projects and are dropped
 * when none match. Remaining groups are returned expanded, in the returned copy only. A blank query returns the input.
 */
fun filterRecentProjects(
    groups: List<RecentProjectGroupRow>,
    ungrouped: List<RecentProjectRow>,
    query: String
): Pair<List<RecentProjectGroupRow>, List<RecentProjectRow>> {
    val needle = query.trim()
    if (needle.isEmpty()) {
        return groups to ungrouped
    }
    val filteredGroups = groups.mapNotNull { group ->
        val projects = if (group.name.contains(needle, ignoreCase = true)) {
            group.projects
        } else {
            group.projects.filter { row -> row.matches(needle = needle) }
        }
        if (projects.isEmpty()) {
            null
        } else {
            group.copy(
                isExpanded = true,
                projects = projects
            )
        }
    }
    return filteredGroups to ungrouped.filter { row -> row.matches(needle = needle) }
}

private fun RecentProjectRow.matches(needle: String): Boolean {
    return name.contains(needle, ignoreCase = true) ||
        displayPath.contains(needle, ignoreCase = true) ||
        branch?.contains(needle, ignoreCase = true) == true
}

/**
 * Replaces a leading [userHome] in [path] with `~`. Only whole path segments match, so `/Users/fnunez` does not shorten
 * `/Users/fnunezka/app`. Both arguments use forward slashes.
 */
fun shortenHomePath(path: String, userHome: String): String {
    val home = userHome.trimEnd('/')
    if (home.isEmpty()) {
        return path
    }
    if (path == home) {
        return "~"
    }
    if (path.startsWith("$home/")) {
        return "~" + path.removePrefix(home)
    }
    return path
}