package net.matasar.keyboard.autofill

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * The field a fill was started from: its app, and the view id the app reported for it
 * (`EditorInfo.fieldId`). Fields without an id of their own share one, so the id narrows the
 * target within an app rather than pinning it.
 */
data class FillTarget(val packageName: String, val fieldId: Int)

/**
 * A password the fill screen got from the password manager, on its way to the field the keyboard
 * was on when the fill started.
 *
 * It lives in this process only — never logged, stored, shown, or put in an `Intent` — and is
 * handed out at most once: to the field it was asked for, before it expires. Every [takeFor]
 * clears it whatever the answer, and the characters are overwritten once they have been used, so
 * a password that went nowhere does not linger either.
 *
 * @param clock uptime in milliseconds (`SystemClock.uptimeMillis`), never wall-clock time.
 * @param schedule runs a task after a delay in milliseconds; it wipes a password nobody took
 *   when it expires, rather than whenever the next field happens to connect.
 */
class PendingFill(private val clock: () -> Long, private val schedule: (Long, () -> Unit) -> Unit) {

    private var password: CharArray? = null
    private var target: FillTarget? = null
    private var expiresAt = 0L

    /** Whether a password is waiting, whoever it is for. */
    val waiting: Boolean
        @Synchronized get() = password != null

    /** Keeps [password] for [target] for [ttlMs]; any password already waiting is wiped. */
    @Synchronized
    fun offer(target: FillTarget, password: CharArray, ttlMs: Long = TTL_MS) {
        clear()
        this.password = password
        this.target = target
        expiresAt = clock() + ttlMs
        schedule(ttlMs, ::clearIfExpired)
    }

    /** Wipes the password once it has expired; a newer one, with a later expiry, is left alone. */
    @Synchronized
    fun clearIfExpired() {
        if (clock() >= expiresAt) clear()
    }

    /**
     * Runs [use] with the password when it was asked for [field] and has not expired, then
     * wipes it; returns null, having wiped it, in every other case.
     */
    @Synchronized
    fun <T> takeFor(field: FillTarget?, use: (CharArray) -> T): T? {
        val chars = password ?: return null
        val deliver = field != null && field == target && clock() < expiresAt
        password = null
        target = null
        return try {
            if (deliver) use(chars) else null
        } finally {
            chars.fill(WIPED)
        }
    }

    /** Drops and wipes whatever is waiting. */
    @Synchronized
    fun clear() {
        password?.fill(WIPED)
        password = null
        target = null
    }

    companion object {
        /** Long enough to pick a login and come back; short enough not to outstay the moment. */
        const val TTL_MS = 30_000L

        /** What a used or dropped password's characters are overwritten with. */
        val WIPED = Char(0)

        /** The one the fill screen and the keyboard share. */
        val shared: PendingFill by lazy {
            val main = Handler(Looper.getMainLooper())
            PendingFill(SystemClock::uptimeMillis) { delayMs, task -> main.postDelayed(task, delayMs) }
        }
    }
}
