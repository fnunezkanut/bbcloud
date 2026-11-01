package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

/** The direction of a previous or next unviewed-file search over the files of a diff chain. */
internal enum class ReviewBarFileNavigationDirection {
    /** The closest file before the current file in chain order. */
    Previous,

    /** The closest file after the current file in chain order. */
    Next
}

/** Finds the unviewed file that the previous or next navigation of the review bar moves to. */
internal object ReviewBarFileNavigation {
    /**
     * The index of the closest file in [direction] from [currentIndex] that is not viewed, or null when there is none.
     *
     * The current file itself is never a target. The search wraps around the ends of the list, so every other file is
     * visited once before the search gives up.
     */
    fun targetIndex(fileCount: Int, currentIndex: Int, direction: ReviewBarFileNavigationDirection, isViewed: (index: Int) -> Boolean): Int? {
        if (currentIndex !in 0 until fileCount) {
            return null
        }
        for (offset in 1 until fileCount) {
            val candidate = when (direction) {
                ReviewBarFileNavigationDirection.Next -> (currentIndex + offset).mod(fileCount)
                ReviewBarFileNavigationDirection.Previous -> (currentIndex - offset).mod(fileCount)
            }
            if (!isViewed(candidate)) {
                return candidate
            }
        }
        return null
    }
}