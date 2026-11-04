package com.github.fnunezkanut.bbcloud.settings

/**
 * The filter state of one pull request tab: the three Exclude checkboxes and the user scope dropdown. Defaults
 * match a fresh install. [excludeResolved] is only offered on the Current Project tab; the Workspace filter always
 * carries false. [userScope] restricts the list to the pull requests matching the selected [PullRequestUserScope].
 */
data class PullRequestListFilter(
    val excludeResolved: Boolean = true,
    val excludeApprovedByMe: Boolean = false,
    val excludeDrafts: Boolean = true,
    val userScope: PullRequestUserScope = PullRequestUserScope.AnyUser
)

/**
 * The user scope dropdown selection for a pull request tab. [AnyUser] is the default and applies no restriction.
 * [Me] keeps the pull requests the current user authored. [AssignedToMe] keeps the pull requests where the current user
 * is a reviewer. The entry names are persisted in settings, so do not rename them without a migration.
 */
enum class PullRequestUserScope {
    AnyUser,
    Me,
    AssignedToMe
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