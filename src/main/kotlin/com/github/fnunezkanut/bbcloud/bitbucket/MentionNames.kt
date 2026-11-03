package com.github.fnunezkanut.bbcloud.bitbucket

/**
 * Display names of the `@{account-key}` mentions in a set of comments, keyed by normalized account key
 * (see [CommentMentionResolver.normalizeKey]). [isComplete] is false when some mentioned account still needs a
 * workspace-member or `/users` lookup before it can be named.
 */
data class MentionNames(val names: Map<String, String>, val isComplete: Boolean) {
    companion object {
        val NONE = MentionNames(names = emptyMap(), isComplete = true)
    }
}