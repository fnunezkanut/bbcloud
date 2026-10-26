package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary

/**
 * The visible subtitle text and its tooltip for the author date line on the pull-request detail card.
 *
 * [visibleText] collapses to a single "updated: …" line when created and updated resolve to the same moment or the
 * same relative display string, and otherwise keeps the "updated: … created: …" line; [tooltipText] always carries the
 * full absolute created and updated timestamps so the tooltip stays informative even when the visible line is collapsed.
 */
data class PullRequestAuthorDates(val visibleText: String, val tooltipText: String)

/**
 * Builds the visible subtitle and tooltip from a [summary] and its already-formatted relative strings.
 *
 * Collapse rule (decision 4b): when the created and updated instants are the same moment, or their relative display
 * strings are identical, the visible line drops the redundant "created:" half and shows only the updated time. The
 * tooltip (decision 3b) always shows both absolutes regardless of the collapse.
 */
internal fun pullRequestAuthorDates(summary: PullRequestSummary, updatedRelative: String, createdRelative: String): PullRequestAuthorDates {
    val isSameMoment = summary.createdOn == summary.updatedOn || updatedRelative == createdRelative
    val visibleText = if (isSameMoment) {
        BbCloudBundle.message("main.pullRequests.updatedOnly", updatedRelative)
    } else {
        BbCloudBundle.message("main.pullRequests.updatedCreated", updatedRelative, createdRelative)
    }
    val tooltipText = BbCloudBundle.message(
        "main.pullRequests.updatedCreated.tooltip",
        PullRequestRelativeTime.format(summary.createdOn),
        PullRequestRelativeTime.format(summary.updatedOn)
    )
    return PullRequestAuthorDates(visibleText = visibleText, tooltipText = tooltipText)
}