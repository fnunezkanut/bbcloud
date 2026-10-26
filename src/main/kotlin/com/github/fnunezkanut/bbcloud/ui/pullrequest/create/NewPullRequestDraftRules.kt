package com.github.fnunezkanut.bbcloud.ui.pullrequest.create

/** Pure rule for whether the new pull request form holds an unsaved draft worth confirming before discarding. */
object NewPullRequestDraftRules {
    /** A draft exists when the title or the description is non-blank; reviewer and branch selections alone do not count. */
    fun hasDraft(title: String, description: String): Boolean {
        return title.isNotBlank() || description.isNotBlank()
    }
}