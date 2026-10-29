package com.github.fnunezkanut.bbcloud.ui.diff

import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentSide

/** Returns whether the custom add-comment affordance belongs on this editor side. */
internal fun shouldOfferAddCommentGutter(side: InlineCommentSide, isFileEditor: Boolean): Boolean {
    if (isFileEditor) {
        return true
    }
    return side == InlineCommentSide.AFTER
}