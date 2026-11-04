package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The Swing component that owns popups opened from a composer. Null means "use Jewel's LocalComponent". Editor inlays
 * host a bare ComposePanel whose Jewel wrapper is not showing, so they must provide the ComposePanel itself.
 */
val LocalCommentPopupOwner = staticCompositionLocalOf<(() -> java.awt.Component?)?> { null }