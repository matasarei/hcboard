package net.matasar.keyboard.debug

import android.app.Activity
import android.os.Bundle
import android.widget.EditText

/**
 * A single platform [EditText] with no IME flags of its own, for the connected tests. The
 * settings screen's fields are Compose ones, which always ask for no fullscreen, so only a field
 * like this one shows what the keyboard itself decides.
 */
class PlainFieldActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(EditText(this).apply { requestFocus() })
    }
}
