package com.github.fnunezkanut.bbcloud.ui.main

/** Description operations of the displayed pull request; implementations call back on the event dispatch thread. */
interface DescriptionEditor {
    /** [content] is raw Markdown; [onResult] receives true when Bitbucket accepted the update. */
    fun updateDescription(content: String, onResult: (Boolean) -> Unit)
}