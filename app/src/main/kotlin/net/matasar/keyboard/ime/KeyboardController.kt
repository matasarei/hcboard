package net.matasar.keyboard.ime

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.matasar.keyboard.input.EditingAction
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.input.KeyStroke
import net.matasar.keyboard.input.Latch
import net.matasar.keyboard.input.LatchState
import net.matasar.keyboard.input.Modifiers
import net.matasar.keyboard.input.TrackpadGesture
import net.matasar.keyboard.input.glide.GlideEngine
import net.matasar.keyboard.input.glide.GlideKey
import net.matasar.keyboard.input.glide.GlidePoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.matasar.keyboard.input.keyStrokeFor
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.KeyIcon
import net.matasar.keyboard.layout.KeyboardLayout
import net.matasar.keyboard.layout.Language
import net.matasar.keyboard.layout.Languages
import net.matasar.keyboard.layout.phoneLayout
import net.matasar.keyboard.layout.LayerId
import net.matasar.keyboard.layout.ModifierKey
import net.matasar.keyboard.layout.PhoneLayout
import net.matasar.keyboard.layout.wideLayout
import net.matasar.keyboard.nlp.Candidates
import net.matasar.keyboard.nlp.WordCandidates

/** The two things a key can ask of the service rather than the editor. */
interface SystemActions {
    fun hideKeyboard()
    fun switchToNextInputMethod()
}

/**
 * Keyboard state and the meaning of every key tap. Pure Kotlin over Compose snapshot state, so
 * the UI observes it and the tests drive it without a device.
 */
class KeyboardController(
    private val dispatcher: InputDispatcher,
    val layout: KeyboardLayout = PhoneLayout,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Where glide classification runs, and where its result comes back to (the main thread by default). */
    private val background: CoroutineDispatcher = Dispatchers.Default,
    private val main: CoroutineDispatcher? = null,
) {
    var layer: LayerId by mutableStateOf(LayerId.LETTERS)
        private set

    /** The language being typed; its letters layer, accents, space-bar name and word list. */
    var language: Language by mutableStateOf(Languages.english)
        private set

    /** Tags of the languages the user switched on; never empty. */
    var enabledLanguages: Set<String> by mutableStateOf(setOf(Languages.english.tag))

    /** Called when the language changes by a key, the picker or the system, so the service can persist and reload. */
    var onLanguageChanged: ((Language) -> Unit)? = null

    /** Whether the language picker sheet is open. */
    var languageSheetOpen: Boolean by mutableStateOf(false)

    /** The globe key exists only when there is something to switch to. */
    val withGlobe: Boolean get() = enabledLanguages.size > 1

    private val layoutCache = HashMap<Pair<String, Boolean>, KeyboardLayout>()
    private val wideLayoutCache = HashMap<Pair<String, Boolean>, KeyboardLayout>()

    /** The phone layout for the current language, built once per language and globe state. */
    val phoneLayout: KeyboardLayout
        get() = layoutCache.getOrPut(language.tag to withGlobe) { phoneLayout(language, withGlobe) }

    /** The 60% board for the current language, built the same way. */
    val wideLayout: KeyboardLayout
        get() = wideLayoutCache.getOrPut(language.tag to withGlobe) { wideLayout(language, withGlobe) }

    /** The enabled languages in cycling order. */
    val enabledLanguageList: List<Language>
        get() = Languages.all.filter { it.tag in enabledLanguages }.ifEmpty { listOf(Languages.english) }

    fun switchLanguage(to: Language) {
        languageSheetOpen = false
        if (to == language) return
        language = to
        clearCandidates()
        onLanguageChanged?.invoke(to)
    }

    /** Sets the language without reporting it back: for restoring the persisted choice or a system subtype change. */
    fun restoreLanguage(to: Language) {
        language = to
    }

    fun nextLanguage() = switchLanguage(Languages.next(language, enabledLanguages))

    var shift: Latch by mutableStateOf(Latch())
        private set

    /** Ctrl, Alt, Shift, Meta, Fn: armed, locked or held. Survives layer switches. */
    var modifiers: Modifiers by mutableStateOf(Modifiers())
        private set

    /** Whether the modifier strip shows above the layers. */
    var developerMode: Boolean by mutableStateOf(false)
        private set

    /** The editor action Enter performs, or null when Enter should be a real key. */
    var editorActionId: Int? by mutableStateOf(null)
        private set

    /** True while space is held and the finger moves the cursor. Keys hide their labels. */
    var trackpad: Boolean by mutableStateOf(false)
        private set

    /** What kind of field has focus; drives the opening layer, previews and Enter's icon. */
    var fieldKind: FieldKind by mutableStateOf(FieldKind.TEXT)
        private set

    /** A field with no input type at all: a terminal. Ctrl+C there must stay a key event. */
    val terminalField: Boolean get() = fieldKind == FieldKind.TERMINAL

    /** Password fields get no press preview and no accent popups. */
    val passwordField: Boolean get() = fieldKind == FieldKind.PASSWORD

    val enterIcon: KeyIcon get() = enterIconFor(editorActionId)

    /** Setting: Ctrl+A/C/V/X become the editor's own actions in ordinary text fields. */
    var editingShortcutsInTextFields: Boolean = true

    private var trackpadGesture: TrackpadGesture? = null

    /** Hide and language switching belong to the service; it plugs in here. */
    var systemActions: SystemActions? = null

    /** The glide engine, once the service has loaded the word list; null until then. */
    var glideEngine: GlideEngine? by mutableStateOf(null)

    /** The scope classification runs in; the service's lifecycle scope. */
    var scope: CoroutineScope? = null

    /** Setting: glide typing on the letters layer. */
    var glideEnabled: Boolean by mutableStateOf(true)

    /** Whether a finger on the letters may glide right now. */
    val glideAvailable: Boolean
        get() = glideEnabled && glideEngine != null && !passwordField && !terminalField && !modifiers.anyActive && !trackpad

    /** The candidate engine for the current language, once the service has loaded its word list. */
    var candidateEngine: Candidates? by mutableStateOf(null)

    /** Setting: word candidates in the toolbar while typing. */
    var suggestionsEnabled: Boolean by mutableStateOf(true)

    /** Setting: a separator applies the strip's correction. */
    var autoCorrect: Boolean by mutableStateOf(true)

    /** Whether the field's input type lets candidates be read and shown. */
    private var fieldAllowsSuggestions = true

    /** Whether the word before the cursor may be read and candidates shown right now. */
    val suggestionsAvailable: Boolean
        get() = suggestionsEnabled && candidateEngine != null && fieldAllowsSuggestions && !modifiers.anyMetaActive && !trackpad

    /** What the strip shows: candidates for the word being typed, or the alternatives of the last glide. */
    var candidates: WordCandidates? by mutableStateOf(null)
        private set

    /** The chevron folded the strip away; the next key brings it back. */
    var candidatesCollapsed: Boolean by mutableStateOf(false)
        private set

    private var lastGlideWord: String? = null

    /** The correction the last separator applied, so one backspace right after can take it back. */
    private var lastAutocorrect: Autocorrect? = null

    private data class Autocorrect(val typed: String, val correction: String, val separator: String)

    /** The password manager's chips for the current field, pinned first. */
    var suggestions: List<net.matasar.keyboard.autofill.SuggestionEntry> by mutableStateOf(emptyList())

    /** Whether the key button's sheet is open. */
    var managerSheetOpen: Boolean by mutableStateOf(false)

    /** Modifiers whose hold was used by another key, so the release must not count as a tap. */
    private val usedHolds = mutableSetOf<ModifierKey>()

    /** Held modifiers whose long press wants to lock them; decided when the finger lifts. */
    private val pendingLocks = mutableSetOf<ModifierKey>()

    private val uppercase: Boolean get() = shift.active || modifiers.isActive(ModifierKey.SHIFT)

    /**
     * What a letter key shows right now. While a combination modifier is active (Ctrl, Alt, Meta,
     * or the strip's Shift, which all send a key event) a letter shows the US letter of its slot,
     * so a Cyrillic board reads Q W E R T Y and Ctrl+С is visibly Ctrl+C. The Shift latch alone
     * keeps the language: it only changes case.
     */
    fun displayLabel(key: Key): String = when (val action = key.action) {
        is KeyAction.Letter -> when {
            modifiers.anyMetaActive && key.slot != null -> key.slot.uppercase()
            uppercase -> action.upper
            else -> action.lower
        }
        else -> key.label
    }

    fun onStartInput(info: EditorInfo?) {
        fieldKind = info?.let { fieldKindOf(it.inputType) } ?: FieldKind.TEXT
        layer = fieldKind.initialLayer()
        shift = Latch()
        modifiers = Modifiers()
        usedHolds.clear()
        pendingLocks.clear()
        editorActionId = info?.let { editorActionFor(it.imeOptions, it.inputType) }
        fieldAllowsSuggestions = info?.let { suggestionsAllowed(it.inputType) } ?: true
        suggestions = emptyList()
        managerSheetOpen = false
        languageSheetOpen = false
        clearCandidates()
    }

    fun onFinishInput() {
        shift = Latch()
        modifiers = Modifiers()
        usedHolds.clear()
        pendingLocks.clear()
        endTrackpad()
        suggestions = emptyList()
        managerSheetOpen = false
        clearCandidates()
    }

    fun toggleDeveloperMode() {
        developerMode = !developerMode
    }

    /** Restores the remembered developer mode for the app that just got focus. */
    fun restoreDeveloperMode(on: Boolean) {
        developerMode = on
    }

    /** Setting: whether a second quick tap locks a modifier or shift. */
    var doubleTapLock: Boolean = true

    private val doubleTapWindowMs: Long get() = if (doubleTapLock) Latch.DOUBLE_TAP_WINDOW_MS else 0L

    fun onKey(key: Key) {
        candidatesCollapsed = false
        val undo = lastAutocorrect
        lastAutocorrect = null
        if (lastGlideWord != null) clearCandidates()
        if (undo != null && key.action == KeyAction.Backspace && !modifiers.anyActive) {
            undoAutocorrect(undo)
            return
        }
        val fnAction = key.fnAction
        if (modifiers.isActive(ModifierKey.FN) && fnAction != null) perform(key, fnAction) else perform(key, key.action)
        // A combination modifier or the trackpad takes the strip away; the chip has the toolbar then.
        if (modifiers.anyMetaActive || trackpad) candidates = null
    }

    private fun perform(key: Key, action: KeyAction) {
        when (action) {
            is KeyAction.Letter -> {
                val text = if (uppercase) action.upper else action.lower
                if (modifiers.anyMetaActive) sendCombo(text, key) else { dispatcher.commitText(text); refreshCandidates() }
                afterKey()
            }
            is KeyAction.Text -> {
                val text = if (uppercase && action.shifted != null) action.shifted else action.text
                when {
                    modifiers.anyMetaActive -> sendCombo(text, key)
                    text in SEPARATORS -> commitSeparator(text)
                    else -> { dispatcher.commitText(text); if (text.any { it.isLetter() }) refreshCandidates() else candidates = null }
                }
                afterKey()
            }
            KeyAction.CapsLock -> shift = if (shift.state == LatchState.LOCKED) Latch() else shift.longPress()
            KeyAction.Space -> {
                if (modifiers.anyMetaActive) sendCombo(" ", key) else commitSeparator(" ")
                afterKey()
            }
            KeyAction.Backspace -> {
                when {
                    modifiers.isActive(ModifierKey.FN) -> dispatcher.forwardDelete()
                    modifiers.anyMetaActive -> dispatcher.sendKey(KeyEvent.KEYCODE_DEL, modifiers.metaState())
                    else -> { dispatcher.backspace(); refreshCandidates() }
                }
                afterKey()
            }
            KeyAction.Enter -> {
                if (modifiers.anyMetaActive) dispatcher.sendKey(KeyEvent.KEYCODE_ENTER, modifiers.metaState())
                else dispatcher.enter(editorActionId)
                afterKey()
            }
            KeyAction.Shift -> shift = shift.tap(clock(), doubleTapWindowMs)
            is KeyAction.SwitchLayer -> layer = action.layer
            is KeyAction.KeyCode -> {
                val code = if (modifiers.isActive(ModifierKey.FN) && action.fnKeyCode != null) action.fnKeyCode else action.keyCode
                dispatcher.sendKey(code, modifiers.metaState())
                afterKey()
            }
            is KeyAction.Modifier -> onModifierTap(action.modifier)
            KeyAction.HideKeyboard -> systemActions?.hideKeyboard()
            // With one language enabled the globe is absent; a switch action then goes to the system switcher.
            KeyAction.SwitchLanguage -> {
                if (withGlobe) nextLanguage() else systemActions?.switchToNextInputMethod()
                afterKey()
            }
        }
    }

    /**
     * A character with Ctrl/Alt/Shift/Meta active. Ctrl+A/C/V/X in an ordinary text field go
     * through the editor's own actions; everything else is a key event with meta state.
     */
    private fun sendCombo(text: String, key: Key) {
        // A letter sends the key of the slot it sits in: Ctrl+С on a Cyrillic board is Ctrl+C.
        val physical = key.slot?.let { if (uppercase) it.uppercase() else it.toString() } ?: text
        val onlyCtrl = modifiers.active.filter { it != ModifierKey.FN } == listOf(ModifierKey.CTRL)
        if (onlyCtrl && editingShortcutsInTextFields && !terminalField) {
            EditingAction.forLetter(physical)?.let { action ->
                if (dispatcher.sendEditingAction(action)) return
            }
        }
        val stroke: KeyStroke = keyStrokeFor(physical) ?: keyStrokeFor(text) ?: keyStrokeFor(key.label) ?: return
        // A letter on a punctuation slot (ї on `]`) has no shifted twin to look up; Shift+Ctrl+ї is still Shift+Ctrl+].
        val shiftedSlot = uppercase && key.slot?.isLetter() == false
        val meta = if (shiftedSlot) modifiers.metaState() or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else modifiers.metaState()
        dispatcher.sendCombo(stroke, meta)
    }

    /** A key went out: one-shot shift and modifiers release; held ones remember they were used. */
    private fun afterKey() {
        if (modifiers.held.isNotEmpty()) usedHolds += modifiers.held
        shift = shift.consume()
        modifiers = modifiers.consume()
    }

    private fun onModifierTap(modifier: ModifierKey) {
        if (usedHolds.remove(modifier)) return
        modifiers = modifiers.tap(modifier, clock(), doubleTapWindowMs)
    }

    /** A finger lands on a modifier: it is held until the finger lifts (chording). */
    fun onModifierPressStart(modifier: ModifierKey) {
        modifiers = modifiers.hold(modifier)
    }

    /**
     * The finger lifts. After a short press the gesture's tap follows (ignored if another key
     * used the hold). After a long press no tap follows: lock now, unless the hold was a chord.
     */
    fun onModifierPressEnd(modifier: ModifierKey) {
        modifiers = modifiers.releaseHold(modifier)
        if (pendingLocks.remove(modifier)) {
            val chorded = usedHolds.remove(modifier)
            if (!chorded) modifiers = modifiers.longPress(modifier)
        }
    }

    /** Backspace held down: one more deletion per repeat tick. */
    fun onKeyRepeat(key: Key) {
        if (key.action == KeyAction.Backspace) dispatcher.backspace()
    }

    fun onKeyLongPress(key: Key) {
        when (val action = key.action) {
            KeyAction.SwitchLanguage -> languageSheetOpen = withGlobe
            KeyAction.Shift -> shift = shift.longPress()
            // A held modifier waits for the finger to lift: the hold may still be a chord.
            is KeyAction.Modifier ->
                if (action.modifier in modifiers.held) pendingLocks += action.modifier
                else modifiers = modifiers.longPress(action.modifier)
            else -> Unit
        }
    }

    /** A glide ended over the letter keys [keys]: classify off the main thread, then commit the best word. */
    fun onGlideEnd(path: List<GlidePoint>, keys: List<GlideKey>) {
        val engine = glideEngine ?: return
        val scope = scope ?: return
        if (!glideAvailable || path.size < 2) return
        val capitalize = uppercase
        scope.launch(background) {
            engine.setLayout(keys)
            val words = engine.classify(path)
            withContext(main ?: Dispatchers.Main.immediate) { commitGlide(words, capitalize) }
        }
    }

    /** Commits the best word with a trailing space and keeps the rest as alternatives. */
    internal fun commitGlide(words: List<String>, capitalize: Boolean) {
        if (words.isEmpty()) return
        val cased = words.map { if (capitalize) it.replaceFirstChar(Char::uppercase) else it }
        dispatcher.commitText(cased.first() + " ")
        lastGlideWord = cased.first()
        lastAutocorrect = null
        candidates = WordCandidates(cased.first(), cased.take(Candidates.MAX_WORDS))
        shift = shift.consume()
    }

    /**
     * The user tapped a word in the strip: after a glide it swaps the glided word (and the
     * alternatives stay); while typing it replaces the word being typed, plus a space. The
     * typed word itself is already in the field, so tapping it does nothing.
     */
    fun pickCandidate(word: String) {
        val current = candidates ?: return
        val glided = lastGlideWord
        if (glided != null) {
            if (word == glided) return
            dispatcher.replaceLastWord(glided, word)
            lastGlideWord = word
            return
        }
        if (word == current.typed) return
        dispatcher.replaceWordBeforeCursor(current.typed, "$word ")
        candidates = null
    }

    /** The chevron: fold the strip away so the toolbar's buttons show until the next key. */
    fun collapseCandidates() {
        candidatesCollapsed = true
    }

    /**
     * Re-derives the candidates from the word before the cursor. Reads nothing from the field
     * when candidates are unavailable there (a password, a terminal, a modifier armed).
     */
    private fun refreshCandidates() {
        val engine = candidateEngine
        if (engine == null || !suggestionsAvailable) {
            candidates = null
            return
        }
        lastGlideWord = null
        candidates = engine.forWord(dispatcher.wordBeforeCursor())
    }

    /** The cursor moved (the service's onUpdateSelection): the word under it may be another one. */
    fun onSelectionChanged() {
        // A glide's own commit moves the cursor too; its alternatives stay until the next key.
        if (lastGlideWord != null) return
        refreshCandidates()
    }

    /** A separator: apply the strip's correction first when there is one, then the separator itself. */
    private fun commitSeparator(separator: String) {
        val current = candidates
        val correction = current?.correction
        if (autoCorrect && current != null && correction != null && lastGlideWord == null) {
            dispatcher.replaceWordBeforeCursor(current.typed, correction)
            lastAutocorrect = Autocorrect(current.typed, correction, separator)
        }
        dispatcher.commitText(separator)
        candidates = null
    }

    /**
     * Backspace right after a correction puts the typed word back, without the separator, and
     * offers no correction for it again. If the text no longer ends with what was applied (the
     * cursor was moved), it is an ordinary backspace.
     */
    private fun undoAutocorrect(undo: Autocorrect) {
        val applied = undo.correction + undo.separator
        if (!dispatcher.textEndsWith(applied)) {
            dispatcher.backspace()
            refreshCandidates()
            return
        }
        dispatcher.replaceWordBeforeCursor(applied, undo.typed)
        candidates = if (suggestionsAvailable) candidateEngine?.forWord(undo.typed)?.copy(correction = null) else null
    }

    private fun clearCandidates() {
        candidates = null
        candidatesCollapsed = false
        lastGlideWord = null
        lastAutocorrect = null
    }

    /** The accent candidates a long press on [key] offers, in the current case; none in passwords. */
    fun accentsFor(key: Key): List<String> = when {
        passwordField -> emptyList()
        uppercase -> key.longPress.map { it.uppercase() }
        else -> key.longPress
    }

    /** A chosen accent goes in like a letter: it consumes a one-shot shift. */
    fun commitAccent(text: String) {
        dispatcher.commitText(text)
        lastAutocorrect = null
        refreshCandidates()
        afterKey()
    }

    fun startTrackpad(stepPx: Float) {
        trackpadGesture = TrackpadGesture(stepPx)
        trackpad = true
        candidates = null
        lastAutocorrect = null
    }

    /** More horizontal travel while space is held. */
    fun trackpadMove(dxPx: Float) {
        val steps = trackpadGesture?.move(dxPx) ?: return
        if (steps != 0) dispatcher.moveCursor(steps)
    }

    fun endTrackpad() {
        trackpadGesture = null
        trackpad = false
    }

    companion object {
        /** The characters that end a word and apply its correction. */
        private val SEPARATORS = setOf(" ", ".", ",", "!", "?")

        /**
         * The action Enter should perform for a field: the field's own IME action when it has
         * one and allows it, otherwise null so a real Enter key goes through (newline in
         * multi-line fields, return in terminals).
         */
        fun editorActionFor(imeOptions: Int, inputType: Int): Int? {
            val multiLine = inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
            val noEnterAction = imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
            val action = imeOptions and EditorInfo.IME_MASK_ACTION
            val hasAction = action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED
            return if (hasAction && !noEnterAction && !multiLine) action else null
        }
    }
}
