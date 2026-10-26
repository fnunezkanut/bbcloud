package com.github.fnunezkanut.bbcloud.ui.diff

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.ActivityUser

/**
 * The text of the inline diff comment thread, kept apart from the drawing so it can be tested without a Compose
 * runtime.
 */
internal object DiffCommentThreadText {
    /** Bitbucket omits the resolver on some resolved threads, so the notice falls back to a nameless phrasing. */
    fun resolutionText(resolvedBy: ActivityUser?): String {
        val name = resolvedBy?.displayName?.takeIf { it.isNotBlank() }
        return if (name == null) {
            BbCloudBundle.message("main.branch.diffComments.threadResolvedUnknown")
        } else {
            BbCloudBundle.message("main.branch.diffComments.threadResolved", name)
        }
    }
}