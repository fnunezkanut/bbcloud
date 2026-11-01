package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

/**
 * The kind of editor the review bar is shown over.
 *
 * The surface decides the action the bar offers: over a normal text editor the bar offers to show the diff of the
 * file, and over a diff view it offers to open the file in a normal editor. The enum keeps the Compose content free
 * of the IntelliJ editor types, which decide the surface outside of the composable.
 */
internal enum class ReviewBarSurface {
    /** The bar is shown over a normal text editor, so the action opens the diff of the file. */
    NormalFile,

    /** The bar is shown over a diff view, so the action opens the file in a normal editor. */
    Diff
}

/**
 * The single action the review bar offers for the surface it is shown over.
 *
 * The action is resolved before the bar is built: when there is nothing to open, the action is absent and the bar
 * shows no button at all, instead of a button that does nothing when it is clicked.
 */
internal class ReviewBarAction(val onClick: () -> Unit)

/**
 * The previous and next unviewed-file navigation the review bar offers over a diff chain.
 *
 * [paths] are the relative paths of the files of the chain, in chain order. A null entry is a request that is not a file
 * of the pull request, and it is never a target. [currentIndex] is the index of the file the bar is shown for.
 * [isViewed] reads the viewed state of a file, and [onNavigate] moves the chain to the file at an index.
 *
 * The class is not a data class, to match [ReviewBarAction], because it holds lambdas.
 */
internal class ReviewBarFileNavigationState(
    val paths: List<String?>,
    val currentIndex: Int,
    val isViewed: (relativePath: String) -> Boolean,
    val onNavigate: (index: Int, direction: ReviewBarFileNavigationDirection) -> Unit
) {
    /** The index of the closest unviewed file in [direction], or null when every other file is viewed. */
    fun unviewedTargetIndex(direction: ReviewBarFileNavigationDirection): Int? {
        return ReviewBarFileNavigation.targetIndex(
            fileCount = paths.size,
            currentIndex = currentIndex,
            direction = direction,
            isViewed = { index -> isViewedAt(index = index) }
        )
    }

    /** Whether the file at [index] counts as viewed. A null path is never a target, so it counts as viewed. */
    fun isViewedAt(index: Int): Boolean {
        val path = paths[index] ?: return true
        return isViewed(path)
    }
}