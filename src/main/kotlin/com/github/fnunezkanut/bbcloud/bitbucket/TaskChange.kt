package com.github.fnunezkanut.bbcloud.bitbucket

sealed interface TaskChange {
    data class Create(val content: String) : TaskChange

    /** Resolves or reopens every task in [taskIds]; used for a single checkbox and for the header bulk checkbox. */
    data class SetResolved(val taskIds: List<Long>, val resolved: Boolean) : TaskChange

    data class Edit(val edits: TaskEdits) : TaskChange
}

/** Text changes and deletions made in the tasks edit mode; [contentUpdates] holds trimmed text keyed by task id. */
data class TaskEdits(val contentUpdates: Map<Long, String>, val deletedIds: Set<Long>) {
    val isEmpty: Boolean
        get() = contentUpdates.isEmpty() && deletedIds.isEmpty()

    val hasBlankContent: Boolean
        get() = contentUpdates.values.any { content -> content.isBlank() }

    companion object {
        /**
         * Keeps only real changes: text of resolved or deleted tasks is ignored because resolved tasks cannot be edited
         * and deleted tasks are removed anyway.
         */
        fun of(original: List<PullRequestTask>, editedContents: Map<Long, String>, deletedIds: Set<Long>): TaskEdits {
            val originalIds = original.map { task -> task.id }.toSet()
            val contentUpdates = original
                .filter { task -> !task.isResolved && task.id !in deletedIds }
                .mapNotNull { task ->
                    val edited = editedContents[task.id]?.trim() ?: return@mapNotNull null
                    if (edited == task.content.trim()) null else task.id to edited
                }
                .toMap()
            return TaskEdits(contentUpdates = contentUpdates, deletedIds = deletedIds.intersect(originalIds))
        }
    }
}