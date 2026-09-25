package net.matasar.keyboard.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import net.matasar.keyboard.input.glide.GlideGesture
import net.matasar.keyboard.input.glide.GlideKey
import net.matasar.keyboard.input.glide.GlidePoint

/** What the layer grid tells the screen while a finger glides. Paths are in root coordinates. */
interface GlideListener {
    fun onGlideStart()
    fun onGlideMove(path: List<GlidePoint>)
    fun onGlideEnd(path: List<GlidePoint>)

    /** The system took the pointer mid-glide: drop the trail, commit nothing. */
    fun onGlideCancel()
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
    /**
     * Read when a finger lands. The modifier stays attached whether or not gliding is possible:
     * attaching and detaching it detaches every gesture under it, so a long press on Space that
     * turned glide off would cancel itself.
     */
    enabled: () -> Boolean,
): Modifier = pointerInput(listener) {
    fun keyAt(root: Offset): Char? = letterBounds().entries.firstOrNull { it.value.contains(root) }?.key

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!enabled()) return@awaitEachGesture
        val origin = gridOriginInRoot()
        val start = down.position + origin
        val startKey = keyAt(start) ?: return@awaitEachGesture
        val gesture = GlideGesture(startKey, keyWidthPx(), longPressMs, down.uptimeMillis, start.x, start.y)
        var gliding = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
            // Nothing runs before this detector on the Initial pass, so a change that arrives
            // already consumed is a cancellation: the pointer went to the system.
            if (change.isConsumed) {
                if (gliding) listener.onGlideCancel()
                return@awaitEachGesture
            }
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

/**
 * The keys a glide is classified against, from the grid's letter bounds: letters only. An
 * apostrophe key (Ukrainian, French) is not a glide stop, so розв'язок is glided as розвязок and
 * the classifier skips the apostrophe, which has no key.
 */
internal fun glideKeys(letterBounds: Map<Char, Rect>): List<GlideKey> =
    letterBounds.filterKeys { it.isLetter() }
        .map { (char, rect) -> GlideKey(char, rect.center.x, rect.center.y, rect.width, rect.height) }
