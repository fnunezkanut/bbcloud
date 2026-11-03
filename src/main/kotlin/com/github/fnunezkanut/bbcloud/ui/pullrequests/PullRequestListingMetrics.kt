package com.github.fnunezkanut.bbcloud.ui.pullrequests

import androidx.compose.ui.unit.dp

/*
 * Sizes of the pull-request listing table. The listing has no header row, so every row uses these same widths to keep
 * its Branches, Activity, and Reviewers columns aligned.
 */

/** The author avatar size on a listing row, in dp. The shared avatar cache of the listing decodes at this size. */
internal const val LISTING_AUTHOR_AVATAR_SIZE_DP = 28

/** The branches-and-created column: the source/destination branch chips over the relative created time. */
internal val LISTING_BRANCHES_COLUMN_WIDTH = 220.dp
internal val LISTING_ACTIVITY_COLUMN_WIDTH = 56.dp
internal val LISTING_REVIEWERS_COLUMN_WIDTH = 84.dp

/** Separates the parts of a listing row subtitle: author, id, updated time, and repository. */
internal const val LISTING_SUBTITLE_SEPARATOR = " \u00B7 "