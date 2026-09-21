package net.matasar.keyboard.input

import android.view.KeyEvent
import net.matasar.keyboard.layout.ModifierKey

/**
 * Every modifier's latch plus the ones a finger is holding right now. Immutable; the controller
 * replaces it on each event, and the chip and the strip render from it.
 */
data class Modifiers(
    val latches: Map<ModifierKey, Latch> = emptyMap(),
    val held: Set<ModifierKey> = emptySet(),
) {
    fun latch(key: ModifierKey): Latch = latches[key] ?: Latch()

    fun state(key: ModifierKey): LatchState = latch(key).state

    fun isActive(key: ModifierKey): Boolean = latch(key).active || key in held

    /** The active modifiers in display order: Ctrl, Alt, Shift, Meta, Fn. */
    val active: List<ModifierKey> get() = ModifierKey.entries.filter { isActive(it) }

    /** Whether a key sent now would carry any modifier (Fn counts: it changes the key). */
    val anyActive: Boolean get() = active.isNotEmpty()

    /** Ctrl, Alt, Shift or Meta active — the ones that become meta state on a key event. */
    val anyMetaActive: Boolean get() = active.any { it != ModifierKey.FN }

    fun tap(key: ModifierKey, nowMs: Long, doubleTapWindowMs: Long = Latch.DOUBLE_TAP_WINDOW_MS): Modifiers =
        copy(latches = latches + (key to latch(key).tap(nowMs, doubleTapWindowMs)))

    fun longPress(key: ModifierKey): Modifiers = copy(latches = latches + (key to latch(key).longPress()))

    fun hold(key: ModifierKey): Modifiers = copy(held = held + key)

    fun releaseHold(key: ModifierKey): Modifiers = copy(held = held - key)

    /** A key went out with these modifiers: one-shots release, locked and held stay. */
    fun consume(): Modifiers = copy(latches = latches.mapValues { it.value.consume() })

    fun releaseAll(): Modifiers = Modifiers()

    /** The KeyEvent meta flags for the active modifiers. Fn has none: it is translated instead. */
    fun metaState(): Int = metaStateOf(active)

    /**
     * What the toolbar chip says: "Ctrl + Alt · next key", "Ctrl locked",
     * "Ctrl locked + Shift · next key"; null when nothing is active.
     */
    fun chipText(): String? {
        val locked = ModifierKey.entries.filter { state(it) == LatchState.LOCKED }
        val oneShot = ModifierKey.entries.filter { isActive(it) && state(it) != LatchState.LOCKED }
        val parts = buildList {
            if (locked.isNotEmpty()) add(locked.joinToString(" + ") { it.label } + " locked")
            if (oneShot.isNotEmpty()) add(oneShot.joinToString(" + ") { it.label } + " · next key")
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" + ")
    }
}

/** The KeyEvent meta flags for [modifiers]: the left-hand key of each. Fn has none. */
fun metaStateOf(modifiers: Collection<ModifierKey>): Int = modifiers.fold(0) { acc, key ->
    acc or when (key) {
        ModifierKey.CTRL -> KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        ModifierKey.ALT -> KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        ModifierKey.SHIFT -> KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        ModifierKey.META -> KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        ModifierKey.FN -> 0
    }
}

val ModifierKey.label: String
    get() = when (this) {
        ModifierKey.CTRL -> "Ctrl"
        ModifierKey.ALT -> "Alt"
        ModifierKey.SHIFT -> "Shift"
        ModifierKey.META -> "Meta"
        ModifierKey.FN -> "Fn"
    }
