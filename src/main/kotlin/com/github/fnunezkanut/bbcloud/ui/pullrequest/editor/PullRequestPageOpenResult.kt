package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

/** The outcome of opening the pull request page by id. */
enum class PullRequestPageOpenResult {
    /** An already-open `<prNum>.pr` editor tab was selected; it still needs a reload to show the new activity. */
    SelectedExisting,

    /** A new `<prNum>.pr` editor tab was opened; its first load already includes the new activity. */
    OpenedNew,

    /** The page could not be opened, for example because repository metadata is missing. */
    Failed
}