package com.github.fnunezkanut.bbcloud.settings

/**
 * The Exclude: checkbox states of one pull request tab. Defaults match a fresh install.
 * [excludeResolved] is only offered on the Current Project - PRs tab; the Workspace - PRs filter always carries false.
 */
data class PullRequestListFilter(
    val excludeResolved: Boolean = true,
    // UI label "Mine": hides the PRs the current user authored.
    val excludeMine: Boolean = false,
    val excludeApprovedByMe: Boolean = false,
    val excludeDrafts: Boolean = true
)

/**
 * The tab a [PullRequestListFilter] belongs to. [offersResolved] says whether its Exclude: checkboxes include Resolved.
 * [openFirst] says whether open rows are listed before resolved rows (Current Project - PRs) or all rows are purely
 * newest first (Workspace - PRs).
 */
enum class PullRequestListScope(val offersResolved: Boolean, val openFirst: Boolean) {
    CurrentProject(offersResolved = true, openFirst = true),
    Workspace(offersResolved = false, openFirst = false)
}