package com.github.fnunezkanut.bbcloud.review

/**
 * Tells whether the editor notifications have to be re-queried after the blue bar state changed.
 *
 * The editor notification providers decide whether an editor shows the blue bar when the editor is opened. When the
 * pull request state resolves later, the editors that were already open keep their decision, so every transition of
 * `isWorktree` requires a refresh. Other state changes (line totals, avatar, branches) do not.
 */
internal fun shouldRefreshEditorNotifications(previousIsWorktree: Boolean, nextIsWorktree: Boolean): Boolean {
    return previousIsWorktree != nextIsWorktree
}