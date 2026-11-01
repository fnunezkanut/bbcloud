package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment

/**
 * Keeps the view model (the draft, caret and selection) of the open reply or edit composer alive across editor inlay
 * rebuilds and Activity-card recompositions, and tracks a one-shot request to restore keyboard focus into a composer
 * after a rebuild.
 *
 * A composer view model is cached by the pair of its [ConversationComposerTarget] and a surface [scope]. The scope
 * identifies the surface that shows the card, for example the host `EditorEx`, so two editors that show the same file
 * keep separate drafts of the same comment instead of sharing one `TextFieldState`.
 *
 * All access is on the EDT; the cache is not synchronized.
 */
internal class ComposerDraftCache(private val createViewModel: (target: ConversationComposerTarget, comment: PullRequestComment) -> CommentComposerViewModel) {
    private val viewModels = HashMap<Pair<ConversationComposerTarget, Any>, CommentComposerViewModel>()
    private var pendingFocusRestore: Pair<Any, Long>? = null

    /**
     * Returns the view model of the open composer identified by [target] and [scope]. The same instance is returned for
     * the same [target] and [scope] until the composer closes, so a rebuilt card keeps the draft, caret and selection.
     * The draft is kept even when the comment text changes remotely, so the user's text is never discarded silently.
     */
    fun composerViewModel(target: ConversationComposerTarget, comment: PullRequestComment, scope: Any): CommentComposerViewModel {
        return viewModels.getOrPut(target to scope) { createViewModel(target, comment) }
    }

    /** Drops the cached drafts after the open composer changes target or closes. Only one composer is open per host. */
    fun clear() {
        viewModels.clear()
    }

    /** Drops the cached drafts whose surface scope is [scope]; used when an editor that showed the card is released. */
    fun forgetScope(scope: Any) {
        viewModels.keys.removeAll { key -> key.second == scope }
    }

    /** Records that the composer of [commentId] in [editor] should take keyboard focus after the next rebuild. */
    fun markFocusRestore(editor: Any, commentId: Long) {
        pendingFocusRestore = editor to commentId
    }

    /** Clears any pending focus-restore request, for example when the composer closes. */
    fun clearFocusRestore() {
        pendingFocusRestore = null
    }

    /**
     * Returns whether the composer of a rebuilt card should take keyboard focus, and consumes the request so it fires
     * once. True only when a focus restore was marked for the same [editor] (by identity), the pending comment id is in
     * [threadCommentIds], and [openCommentId] is still that comment.
     */
    fun consumeFocusRestore(editor: Any, threadCommentIds: Set<Long>, openCommentId: Long?): Boolean {
        val pending = pendingFocusRestore ?: return false
        val matches = pending.first === editor && pending.second in threadCommentIds && openCommentId == pending.second
        if (matches) {
            pendingFocusRestore = null
        }
        return matches
    }

    companion object {
        /**
         * Whether the open composer of [openCommentId] no longer has a comment on screen, so the host should close it.
         * False when no composer is open or when the comment is still among [shownIds].
         */
        fun isOrphaned(openCommentId: Long?, shownIds: Set<Long>): Boolean {
            if (openCommentId == null) {
                return false
            }
            return openCommentId !in shownIds
        }
    }
}