package com.klin.read.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Toggles the reading chrome on tap.
 *
 * Tapping the left or right third used to turn a chapter. That was removed: it
 * fired on any stray touch while scrolling, and in the paged modes it duplicated
 * the swipe gesture. Chapter changes now come from swiping or the toolbar
 * buttons, which are unambiguous.
 *
 * The handler is attached to the container rather than an overlay, and
 * `detectTapGestures` does not consume drags, so scrolling and swiping still
 * work underneath.
 */
fun Modifier.readerTaps(onTap: () -> Unit): Modifier = this.pointerInput(Unit) {
    detectTapGestures { onTap() }
}
