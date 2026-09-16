package net.matasar.keyboard.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/** What a key reports about a finger on it. Positions are local to the key. */
interface KeyGestureListener {
    fun onPressStart()
    fun onPressEnd()
    fun onTap()
    /** Returns true when the long press opened something the finger now steers (accents, trackpad). */
    fun onLongPress(): Boolean
    fun onLongPressMove(position: Offset)
    fun onLongPressRelease(position: Offset)
}

/**
 * One finger on one key: press, then either a tap on release, or a long press followed by
 * steering until release. Each key handles only its own pointer, so a second finger on another
 * key is that key's own gesture — which is what chording needs.
 */
fun Modifier.keyGestures(key: Any, longPressMs: Long, listener: KeyGestureListener): Modifier =
    pointerInput(key, listener) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            down.consume()
            listener.onPressStart()

            var longPressed = false
            var up = false
            val deadline = down.uptimeMillis + longPressMs
            while (!up && !longPressed) {
                val remaining = deadline - System.currentTimeMillis()
                val event = withTimeoutOrNull(remaining.coerceAtLeast(1)) { awaitPointerEvent(PointerEventPass.Initial) }
                if (event == null) {
                    longPressed = true
                } else {
                    val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                    change.consume()
                    if (!change.pressed) up = true
                }
            }

            if (up) {
                listener.onPressEnd()
                listener.onTap()
                return@awaitEachGesture
            }

            val steering = listener.onLongPress()
            var last = down.position
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                change.consume()
                last = change.position
                if (!change.pressed) break
                if (steering) listener.onLongPressMove(last)
            }
            listener.onPressEnd()
            if (steering) listener.onLongPressRelease(last)
        }
    }
