package net.matasar.keyboard.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import net.matasar.keyboard.input.glide.GlideGesture
import net.matasar.keyboard.input.glide.GlidePoint

/** What the layer grid tells the screen while a finger glides. Paths are in root coordinates. */
interface GlideListener {
    fun onGlideStart()
    fun onGlideMove(path: List<GlidePoint>)
    fun onGlideEnd(path: List<GlidePoint>)
}

/**
 * Watches every finger that lands on a letter key, on the same pointer pass as the keys but
 * earlier, since a parent sees the Initial pass first. Once [GlideGesture] says the press is a
 * glide, the changes are consumed so the key's own gesture releases without a tap, and the path
 * is reported until the finger lifts. Presses that stay taps or long presses are left alone.
 */
fun Modifier.glideDetector(
    letterBounds: () -> Map<Char, Rect>,
    gridOriginInRoot: () -> Offset,
    keyWidthPx: () -> Float,
    longPressMs: Long,
    listener: GlideListener,
): Modifier = pointerInput(listener) {
    fun keyAt(root: Offset): Char? = letterBounds().entries.firstOrNull { it.value.contains(root) }?.key

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val origin = gridOriginInRoot()
        val start = down.position + origin
        val startKey = keyAt(start) ?: return@awaitEachGesture
        val gesture = GlideGesture(startKey, keyWidthPx(), longPressMs, down.uptimeMillis, start.x, start.y)
        var gliding = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
            val position = change.position + origin
            if (gliding) {
                gesture.add(position.x, position.y, change.uptimeMillis, keyAt(position))
                change.consume()
                listener.onGlideMove(gesture.path)
            } else {
                when (gesture.add(position.x, position.y, change.uptimeMillis, keyAt(position))) {
                    GlideGesture.State.GLIDING -> {
                        gliding = true
                        change.consume()
                        listener.onGlideStart()
                        listener.onGlideMove(gesture.path)
                    }
                    GlideGesture.State.ABANDONED -> return@awaitEachGesture
                    GlideGesture.State.PENDING -> Unit
                }
            }
            if (!change.pressed) {
                if (gliding) listener.onGlideEnd(gesture.path)
                return@awaitEachGesture
            }
        }
    }
}
