package com.github.fnunezkanut.bbcloud.ui.diff

import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentSide
import com.intellij.ui.scale.JBUIScale

internal const val ADD_COMMENT_GUTTER_ICON_WIDTH = 16

/** Returns the scaled width reserved for the custom add-comment gutter icon. */
internal fun addCommentGutterReserveWidthPx(): Int {
    return JBUIScale.scale(ADD_COMMENT_GUTTER_ICON_WIDTH)
}

/** Returns whether the custom add-comment affordance belongs on this editor side. */
internal fun shouldOfferAddCommentGutter(side: InlineCommentSide, isFileEditor: Boolean): Boolean {
    if (isFileEditor) {
        return true
    }
    return side == InlineCommentSide.AFTER
}