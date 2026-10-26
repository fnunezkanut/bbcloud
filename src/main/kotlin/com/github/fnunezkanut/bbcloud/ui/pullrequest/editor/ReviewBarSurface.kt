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