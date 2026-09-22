package net.matasar.keyboard.debug

import android.app.Activity
import android.os.Bundle
import android.widget.EditText

/**
 * A single platform [EditText] with no IME flags of its own, for the connected tests. The
 * settings screen's fields are Compose ones, which always ask for no fullscreen, so only a field
 * like this one shows what the keyboard itself decides. [EXTRA_INPUT_TYPE] gives the field a
 * declaration of its own, so a test can meet the flags a real app sends.
 */
class PlainFieldActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            EditText(this).apply {
                intent.getIntExtra(EXTRA_INPUT_TYPE, 0).takeIf { it != 0 }?.let { inputType = it }
                requestFocus()
            },
        )
    }

    companion object {
        const val EXTRA_INPUT_TYPE = "inputType"
    }
}
