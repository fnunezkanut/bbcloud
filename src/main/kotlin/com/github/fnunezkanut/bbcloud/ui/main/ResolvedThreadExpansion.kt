package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which resolved threads show their body, indexed by root comment id: `expansion[rootId]` reads and
 * `expansion[rootId] = true` writes. Compose state, so cards recompose on a toggle. EDT-only.
 */
class ResolvedThreadExpansion {
    private var expandedRootIds: Set<Long> by mutableStateOf(emptySet())

    operator fun get(rootCommentId: Long): Boolean {
        return rootCommentId in expandedRootIds
    }

    operator fun set(rootCommentId: Long, expanded: Boolean) {
        expandedRootIds = if (expanded) expandedRootIds + rootCommentId else expandedRootIds - rootCommentId
    }
}