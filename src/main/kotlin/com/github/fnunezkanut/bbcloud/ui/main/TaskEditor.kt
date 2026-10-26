package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.TaskEdits

/** Task operations of the displayed pull request; implementations call back on the event dispatch thread. */
interface TaskEditor {
    fun createTask(content: String)

    fun setTasksResolved(taskIds: List<Long>, resolved: Boolean)

    fun saveTaskEdits(edits: TaskEdits)
}