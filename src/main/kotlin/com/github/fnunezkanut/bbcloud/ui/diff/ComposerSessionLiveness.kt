package com.github.fnunezkanut.bbcloud.ui.diff

/**
 * A composer session is live only while its editor is open and its inlay is still in that editor. A session that fails
 * either check shows nothing, so it must be replaced rather than focused.
 */
internal fun isComposerSessionLive(isEditorDisposed: Boolean, isInlayValid: Boolean): Boolean {
    return !isEditorDisposed && isInlayValid
}