package net.matasar.keyboard.autofill

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import net.matasar.keyboard.R

/**
 * The fill screen: a login form of our own for the password manager to fill. The keyboard's
 * window cannot ask for autofill (only an activity has an autofill client), and a terminal never
 * asks, so the key button opens this instead. When the manager fills the password field the
 * value goes to [PendingFill] — not into the field, not into an Intent — and the screen closes;
 * the keyboard types it once the original field is back.
 *
 * The password is never shown: the field is masked, nothing is filled into it, there is no reveal
 * toggle, and the window is secure against screenshots and the recents thumbnail.
 */
class FillActivity : Activity() {

    private lateinit var password: FillField
    private var target: FillTarget? = null
    private var asked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        target = intent.getStringExtra(EXTRA_TARGET_PACKAGE)?.let { FillTarget(it, intent.getIntExtra(EXTRA_TARGET_FIELD, View.NO_ID)) }
        if (target == null) {
            finish()
            return
        }

        val login = FillField(this, onFilled = null).apply {
            hint = getString(R.string.fill_login_label)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setAutofillHints(View.AUTOFILL_HINT_USERNAME, View.AUTOFILL_HINT_EMAIL_ADDRESS)
        }
        password = FillField(this, onFilled = ::handOver).apply {
            hint = getString(R.string.fill_password_label)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
        }
        val autofill = getSystemService(AutofillManager::class.java)
        val enabled = autofill?.isEnabled == true

        val pad = dp(24)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, dp(12))
            addView(TextView(this@FillActivity).apply {
                text = getString(R.string.fill_title)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            })
            addView(TextView(this@FillActivity).apply {
                text = getString(if (enabled) R.string.fill_hint else R.string.fill_no_service)
                setPadding(0, dp(8), 0, dp(12))
            })
            addView(login, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(password, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(LinearLayout(this@FillActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(12), 0, 0)
                addView(textButton(R.string.fill_cancel) { finish() })
                addView(
                    if (enabled) textButton(R.string.fill_show_logins) { askForLogins() }
                    else textButton(R.string.fill_choose_manager) { chooseManager() },
                )
            })
        })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Once, when the form can take it: the manager's list attaches to a focused, laid-out field.
        if (hasFocus && !asked) {
            asked = true
            askForLogins()
        }
    }

    private fun askForLogins() {
        password.requestFocus()
        getSystemService(AutofillManager::class.java)?.requestAutofill(password)
    }

    private fun chooseManager() {
        runCatching { startActivity(changeManagerIntent(packageName)) }
    }

    /** The manager filled the password: hand it to the keyboard and get out of the way. */
    private fun handOver(value: CharSequence) {
        val target = this.target ?: return
        PendingFill.shared.offer(target, CharArray(value.length) { value[it] })
        finish()
    }

    override fun finish() {
        // Dropping the session keeps Android from offering to save the empty form on the way out.
        getSystemService(AutofillManager::class.java)?.cancel()
        super.finish()
    }

    private fun textButton(label: Int, onClick: () -> Unit) =
        Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = getString(label)
            setOnClickListener { onClick() }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_TARGET_PACKAGE = "net.matasar.keyboard.autofill.TARGET_PACKAGE"
        private const val EXTRA_TARGET_FIELD = "net.matasar.keyboard.autofill.TARGET_FIELD"

        /** Opens the fill screen for a password to be typed into [target]. */
        fun intent(context: Context, target: FillTarget): Intent =
            Intent(context, FillActivity::class.java)
                .putExtra(EXTRA_TARGET_PACKAGE, target.packageName)
                .putExtra(EXTRA_TARGET_FIELD, target.fieldId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** What the fill screen's fields tell the keyboard about themselves, so it never types into them. */
const val FILL_SCREEN_IME_OPTION = "net.matasar.keyboard.fillScreen"

/**
 * A field of the fill screen. With [onFilled], an autofilled text goes there and the field stays
 * empty; without it the field fills as usual.
 */
@SuppressLint("ViewConstructor") // built in code only, never inflated
private class FillField(context: Context, private val onFilled: ((CharSequence) -> Unit)?) : EditText(context) {

    init {
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_YES
        isSingleLine = true
    }

    override fun autofill(value: AutofillValue) {
        val filled = onFilled
        if (filled != null && value.isText) filled(value.textValue) else super.autofill(value)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs)
        outAttrs.privateImeOptions = FILL_SCREEN_IME_OPTION
        return connection
    }
}
