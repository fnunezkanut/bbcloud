package com.github.fnunezkanut.bbcloud.settings

/**
 * The filter state of one pull request tab: the three Exclude checkboxes and the "Created by" dropdown. Defaults
 * match a fresh install. [excludeResolved] is only offered on the Current Project tab; the Workspace filter always
 * carries false. [authorScope] restricts the list to the current user's pull requests when set to
 * [PullRequestAuthorScope.Me].
 */
data class PullRequestListFilter(
    val excludeResolved: Boolean = true,
    val excludeApprovedByMe: Boolean = false,
    val excludeDrafts: Boolean = true,
    val authorScope: PullRequestAuthorScope = PullRequestAuthorScope.AnyUser
)

/** The "Created by" dropdown selection for a pull request tab. [AnyUser] is the default and applies no author restriction. */
enum class PullRequestAuthorScope {
    AnyUser,
    Me
}

/**
 * The tab a [PullRequestListFilter] belongs to. [offersResolved] says whether its Exclude: checkboxes include Resolved.
 * [openFirst] says whether open rows are listed before resolved rows (Current Project) or all rows are purely
 * newest first (Workspace).
 */
enum class PullRequestListScope(val offersResolved: Boolean, val openFirst: Boolean) {
    CurrentProject(offersResolved = true, openFirst = true),
    Workspace(offersResolved = false, openFirst = false)
}