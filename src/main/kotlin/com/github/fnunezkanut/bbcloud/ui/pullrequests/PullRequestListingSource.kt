package com.github.fnunezkanut.bbcloud.ui.pullrequests

/** Where a row of a merged pull request tab came from; decides how its title link behaves. Declaration order is the de-duplication precedence. */
enum class PullRequestListingSource { ProjectOpen, ProjectResolved, WorkspaceAuthored, WorkspaceAssigned }