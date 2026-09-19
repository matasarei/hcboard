package net.matasar.keyboard.autofill

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import net.matasar.keyboard.ime.FieldKind
import java.nio.CharBuffer

/**
 * The field a fill was started from: its app, and the view id the app reported for it
 * (`EditorInfo.fieldId`). Fields without an id of their own share one, so the id narrows the
 * target within an app rather than pinning it.
 */
data class FillTarget(val packageName: String, val fieldId: Int)

/**
 * A field that just got the keyboard, as a fill sees it: where it is, what kind it is, whether it
 * says another field follows it, and — asked only when it matters — whether it holds any text.
 */
class FocusedField(
    val target: FillTarget,
    val kind: FieldKind,
    val canGoNext: Boolean,
    val isEmpty: () -> Boolean,
)

/** What a fill does to the field that has the keyboard. */
interface FillTyper {
    fun typeUsername(text: CharSequence)
    fun typePassword(text: CharSequence)

    /** Moves on to the field the current one says follows it. */
    fun goNext()
}

/**
 * A login the fill screen got from the password manager, on its way to the app the keyboard was
 * on when the fill started.
 *
 * Started from a text field with a username to go with it, the fill is two stages: the username
 * goes into that field (unless it already holds text), the keyboard moves on to the next field
 * when the field says there is one, and the password then waits for the first password field of
 * the same app — which may come a page later, on a two-step login. Started from anything else (a
 * password field, a terminal, a PIN), or without a username, the password goes into the field
 * the fill started from, as a single fill.
 *
 * It lives in this process only — never logged, stored, shown, or put in an `Intent` — and is
 * handed out at most once: to the app it was asked for, before it expires. Another app taking
 * the keyboard wipes it, and the characters are overwritten once they have been used, so a login
 * that went nowhere does not linger either.
 *
 * @param clock uptime in milliseconds (`SystemClock.uptimeMillis`), never wall-clock time.
 * @param schedule runs a task after a delay in milliseconds; it wipes a login nobody took when it
 *   expires, rather than whenever the next field happens to connect.
 */
class PendingFill(private val clock: () -> Long, private val schedule: (Long, () -> Unit) -> Unit) {

    private var username: CharArray? = null
    private var password: CharArray? = null
    private var target: FillTarget? = null
    private var expiresAt = 0L

    /** The username is done with, and the password waits for a password field of the same app. */
    private var awaitingPasswordField = false

    /** Whether a password is waiting, whoever it is for. */
    val waiting: Boolean
        @Synchronized get() = password != null

    /**
     * Keeps [password], and [username] when the manager gave one, for [target] for [ttlMs]; any
     * login already waiting is wiped.
     */
    @Synchronized
    fun offer(target: FillTarget, password: CharArray, username: CharArray? = null, ttlMs: Long = TTL_MS) {
        clear()
        this.password = password
        this.username = username
        this.target = target
        expiresAt = clock() + ttlMs
        schedule(ttlMs, ::clearIfExpired)
    }

    /** Wipes the login once it has expired; a newer one, with a later expiry, is left alone. */
    @Synchronized
    fun clearIfExpired() {
        if (clock() >= expiresAt) clear()
    }

    /**
     * A field got the keyboard: types what belongs in it through [typer], if anything does.
     *
     * Another app, an unknown field or an expired login wipes everything. While the password waits
     * for a password field, other fields of the same app leave it waiting. Whatever [typer] was
     * given is wiped after it returns, and everything is wiped when it throws.
     */
    @Synchronized
    fun deliverTo(field: FocusedField?, typer: FillTyper) {
        if (password == null) return
        if (field == null || field.target.packageName != target?.packageName || clock() >= expiresAt) {
            clear()
            return
        }
        when {
            awaitingPasswordField -> if (field.kind == FieldKind.PASSWORD) typePassword(typer)
            field.target != target -> clear()
            field.kind == FieldKind.TEXT && username != null -> typeUsername(field, typer)
            else -> typePassword(typer)
        }
    }

    /** The single fill, or the second stage: the password goes in, and nothing is left waiting. */
    private fun typePassword(typer: FillTyper) {
        try {
            password?.let { typer.typePassword(CharBuffer.wrap(it)) }
        } finally {
            clear()
        }
    }

    /** The first stage: the username goes into an empty field, and the keyboard moves on. */
    private fun typeUsername(field: FocusedField, typer: FillTyper) {
        val chars = username ?: return
        username = null
        try {
            if (field.isEmpty()) typer.typeUsername(CharBuffer.wrap(chars))
            awaitingPasswordField = true
            if (field.canGoNext) typer.goNext()
        } catch (failure: Throwable) {
            clear()
            throw failure
        } finally {
            chars.fill(WIPED)
        }
    }

    /** Drops and wipes whatever is waiting. */
    @Synchronized
    fun clear() {
        username?.fill(WIPED)
        password?.fill(WIPED)
        username = null
        password = null
        target = null
        awaitingPasswordField = false
    }

    companion object {
        /** Long enough to pick a login and come back; short enough not to outstay the moment. */
        const val TTL_MS = 30_000L

        /** What a used or dropped login's characters are overwritten with. */
        val WIPED = Char(0)

        /** The one the fill screen and the keyboard share. */
        val shared: PendingFill by lazy {
            val main = Handler(Looper.getMainLooper())
            PendingFill(SystemClock::uptimeMillis) { delayMs, task -> main.postDelayed(task, delayMs) }
        }
    }
}
