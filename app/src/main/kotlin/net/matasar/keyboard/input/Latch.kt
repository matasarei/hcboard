package net.matasar.keyboard.input

/**
 * The three states shared by shift and by every modifier: idle, armed for the next key, locked
 * until tapped again. Pure and immutable so it can be tested without Android.
 */
enum class LatchState { IDLE, ARMED, LOCKED }

data class Latch(
    val state: LatchState = LatchState.IDLE,
    val lastTapMs: Long = Long.MIN_VALUE,
) {
    val active: Boolean get() = state != LatchState.IDLE

    /**
     * A tap. Idle arms; a second tap inside [doubleTapWindowMs] locks, a later one cancels;
     * a tap on a locked latch releases it.
     */
    fun tap(nowMs: Long, doubleTapWindowMs: Long = DOUBLE_TAP_WINDOW_MS): Latch = when (state) {
        LatchState.IDLE -> Latch(LatchState.ARMED, nowMs)
        LatchState.ARMED ->
            if (nowMs - lastTapMs <= doubleTapWindowMs) Latch(LatchState.LOCKED, nowMs)
            else Latch(LatchState.IDLE, nowMs)
        LatchState.LOCKED -> Latch(LatchState.IDLE, nowMs)
    }

    /** A long press locks from any state. */
    fun longPress(): Latch = copy(state = LatchState.LOCKED)

    /** A key was sent with this latch: one-shot releases, locked stays. */
    fun consume(): Latch = if (state == LatchState.ARMED) copy(state = LatchState.IDLE) else this

    fun release(): Latch = copy(state = LatchState.IDLE)

    companion object {
        const val DOUBLE_TAP_WINDOW_MS = 300L
    }
}
