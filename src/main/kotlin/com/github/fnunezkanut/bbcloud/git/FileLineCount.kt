package com.github.fnunezkanut.bbcloud.git

/** Added and removed line counts for one file in a Git diff. Binary files have unknown counts. */
data class FileLineCount(val linesAdded: Int, val linesRemoved: Int, val isBinary: Boolean)