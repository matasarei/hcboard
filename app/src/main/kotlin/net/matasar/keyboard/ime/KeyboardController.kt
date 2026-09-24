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
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import net.matasar.keyboard.input.keyStrokeFor
import net.matasar.keyboard.layout.BulgarianLayout
import net.matasar.keyboard.layout.FieldMarks
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
import net.matasar.keyboard.macro.Macro
import net.matasar.keyboard.macro.MacroField
import net.matasar.keyboard.macro.MacroRunner
import net.matasar.keyboard.macro.MacroTooLong
import net.matasar.keyboard.macro.typesSecrets
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom
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

    /** Setting: which board Bulgarian is typed on. */
    var bulgarianLayout: BulgarianLayout by mutableStateOf(BulgarianLayout.PHONETIC)

    /** The current language as its keys are laid out: Bulgarian's standard board when the setting asks for it. */
    private val keysLanguage: Language get() = Languages.resolve(language, bulgarianLayout)

    /** The marks the focused field keeps beside the space bar: `@` or `/` and `.` in an address field. */
    var fieldMarks: FieldMarks by mutableStateOf(FieldMarks.NONE)
        private set

    private data class PhoneLayoutKey(val language: Language, val withGlobe: Boolean, val numberRow: Boolean, val marks: FieldMarks)

    private val layoutCache = HashMap<PhoneLayoutKey, KeyboardLayout>()
    private val wideLayoutCache = HashMap<Pair<Language, Boolean>, KeyboardLayout>()

    /** The phone layout for the current language, built once per language, layout, globe, number row and field marks. */
    val phoneLayout: KeyboardLayout
        get() = PhoneLayoutKey(keysLanguage, withGlobe, numberRowShown, fieldMarks).let { key ->
            layoutCache.getOrPut(key) { phoneLayout(key.language, key.withGlobe, key.numberRow, key.marks) }
        }

    /** The 60% board for the current language, built the same way. */
    val wideLayout: KeyboardLayout
        get() = keysLanguage.let { keys -> wideLayoutCache.getOrPut(keys to withGlobe) { wideLayout(keys, withGlobe) } }

    /** The enabled languages in cycling order. */
    val enabledLanguageList: List<Language>
        get() = Languages.all.filter { it.tag in enabledLanguages }.ifEmpty { listOf(Languages.english) }

    fun switchLanguage(to: Language) {
        languageSheetOpen = false
        if (to == language) return
        language = to
        // The symbols and code pages are the same in every language, so a language picked there
        // is a request for its letters. A number field, which opens on symbols, keeps its page.
        if (layer != LayerId.LETTERS && fieldKind.initialLayer() == LayerId.LETTERS) layer = LayerId.LETTERS
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

    /**
     * The field wants a capital at the cursor (a sentence starts) and Shift is idle: the next
     * letter goes in uppercase. Letters only — never a digit's shifted symbol, a combination or an
     * Fn meaning — so it is kept apart from [shift] and [shiftActive].
     */
    var autoCapital: Boolean by mutableStateOf(false)
        private set

    /** Setting: a capital at the start of a sentence, in fields that ask for one. */
    var autoCapitalize: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            refreshAutoCapital()
        }

    /** The capitalization the focused field asks for ([capsModesOf]); 0 asks for none. */
    private var capsModes = 0

    /**
     * When a Shift tap cancelled the automatic capital: a second quick tap locks caps, and until
     * the next key goes out the field is not asked again (the app's report of the previous key's
     * cursor move can arrive after the tap and would bring the capital back).
     */
    private var autoCancelledAtMs: Long? = null

    /** Ctrl, Alt, Shift, Meta, Fn: armed, locked or held. Survives layer switches. */
    var modifiers: Modifiers by mutableStateOf(Modifiers())
        private set

    /** Whether the modifier strip shows above the layers. */
    var developerMode: Boolean by mutableStateOf(false)
        private set

    /** Setting: the digits across the top of the phone letters page. */
    var numberRow: Boolean by mutableStateOf(false)

    /**
     * The number row shows where it is switched on, and in every password field regardless; never
     * in a number field, which opens on the digits page and needs that page to keep its digits.
     */
    val numberRowShown: Boolean get() = (numberRow || passwordField) && fieldKind != FieldKind.NUMBER

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

    /** Setting: the mic button in the strip, for handing dictation to a voice keyboard. */
    var voiceInputEnabled: Boolean by mutableStateOf(true)

    /** Whether an enabled keyboard offers a voice mode to hand off to; the service checks per field. */
    var voiceAvailable: Boolean by mutableStateOf(false)

    /** Whether the focused field may offer the mic: not a password, not an app that asked for none. */
    private var fieldAllowsMic: Boolean by mutableStateOf(true)

    /**
     * A restart: focus moved to another field of the same screen (or the app restarted input), and
     * onStartInput is skipped. Everything onStartInput reads from the field is read again here,
     * and the strip's words go: they were the last field's, and a password field reached from a
     * text field kept showing them. Only the words: an app can restart input without the user
     * leaving the field, and the correction's undo, the pick's space and the glide's undo each
     * check that the text still ends as they left it before they act.
     */
    fun onRestartInput(info: EditorInfo?) {
        updateFieldKind(info)
        updateFieldMic(info)
        updateFieldSuggestions(info)
        candidates = null
        candidatesCollapsed = false
    }

    /**
     * What kind of field this is, which decides the number row in a password field among other
     * things. A restart re-reads it too, or a text field reached from a password field on the
     * same screen keeps the password's digits. The page stays where the user left it.
     */
    fun updateFieldKind(info: EditorInfo?) {
        fieldKind = info?.let { fieldKindOf(it.inputType) } ?: FieldKind.TEXT
        fieldMarks = info?.let { fieldMarksOf(it.inputType) } ?: FieldMarks.NONE
    }

    /**
     * Reads whether [info]'s field may offer the mic. The service calls it on a restart too: moving
     * between fields of one Compose screen restarts input without a new start, and a password field
     * reached that way must not keep the text field's mic.
     */
    fun updateFieldMic(info: EditorInfo?) {
        fieldAllowsMic = info?.let { micAllowed(it.inputType, it.privateImeOptions) } ?: true
    }

    /** Whether the strip shows the mic now. */
    val showVoiceKey: Boolean
        get() = voiceInputEnabled && voiceAvailable && fieldAllowsMic

    /** The candidate engine for the current language, once the service has loaded its word list. */
    var candidateEngine: Candidates? by mutableStateOf(null)

    /** Setting: word candidates in the toolbar while typing. */
    var suggestionsEnabled: Boolean by mutableStateOf(true)

    /** Setting: a separator applies the strip's correction. */
    var autoCorrect: Boolean by mutableStateOf(true)

    /** Whether the field's input type lets candidates be read and shown. */
    private var fieldAllowsSuggestions = true

    /** Setting: a quick second Space after a word types ". ", as on the iPhone. */
    var doubleSpacePeriod: Boolean = true

    /** When the last key, if it was Space, went out ([clock]); any other key clears it. */
    private var lastSpaceAt: Long? = null

    /**
     * The focused field's input type, kept so the rule can be applied again when the stored answer
     * for this app arrives — it is read asynchronously — and when the user flips the row mid-field.
     * Null is a field that gave no [EditorInfo], which has always been allowed to suggest.
     */
    private var fieldInputType: Int? = null

    /** Whether the user has allowed this app to suggest though its fields ask for none. */
    var suggestInApp: Boolean by mutableStateOf(false)
        private set

    /** Whether this field's app asked for no suggestions and nothing else is in the way. */
    private var fieldOverridable: Boolean by mutableStateOf(false)

    /**
     * Whether the gear sheet offers its row: allowing the app must change something here, and
     * suggestions must be on at all — with the setting off, the row would promise what the
     * keyboard would not then do.
     */
    val suggestInAppOffered: Boolean get() = fieldOverridable && suggestionsEnabled

    /** Whether the word before the cursor may be read and candidates shown right now. */
    val suggestionsAvailable: Boolean
        get() = suggestionsEnabled && candidateEngine != null && fieldAllowsSuggestions && !modifiers.anyMetaActive && !trackpad && !passwordTyped &&
            runningMacro == null

    /**
     * A filled password was just typed into this field: nothing is read back from it for the strip
     * until the user presses a key or leaves the field, whatever kind of field it is.
     */
    private var passwordTyped = false

    /** What the strip shows: candidates for the word being typed, or the alternatives of the last glide. */
    var candidates: WordCandidates? by mutableStateOf(null)
        private set

    /** The chevron folded the strip away; the next key brings it back. */
    var candidatesCollapsed: Boolean by mutableStateOf(false)
        private set

    /**
     * Whether the phone strip shows its settings, passwords and macros buttons rather than the
     * chevron that opens them. It opens on a tap and stays open across fields; hiding the keyboard
     * folds it again. The wide board ignores it and always shows them.
     */
    var toolbarExpanded: Boolean by mutableStateOf(false)
        private set

    /** Setting: whether the phone strip folds its buttons at all; off keeps them always in sight. */
    var foldToolbar: Boolean by mutableStateOf(true)

    /**
     * The same setting as the screens show it, "Always show toolbar buttons": on means no folding.
     * Both the settings screen and the gear sheet read it here, so the flip is written once.
     */
    val toolbarAlwaysShown: Boolean
        get() = !foldToolbar

    /** Whether the strip may fold its buttons: on the phone board, while the setting allows it. */
    fun toolbarFolds(wide: Boolean): Boolean = foldToolbar && !wide

    /** The strip's chevron: show its buttons until the keyboard hides. */
    fun expandToolbar() {
        toolbarExpanded = true
    }

    /** The keyboard went away: the next time it shows, the strip is folded. */
    fun onKeyboardHidden() {
        toolbarExpanded = false
    }

    private var lastGlideWord: String? = null
    private var lastGlideCommit: String? = null

    /** The correction the last separator applied, so one backspace right after can take it back. */
    private var lastAutocorrect: Autocorrect? = null

    private data class Autocorrect(val typed: String, val correction: String, val separator: String)

    /**
     * The picked word and the space the pick put after it, until the next key: punctuation
     * then takes the space's place, and Space finds it already there.
     */
    private var autoSpace: String? = null

    /**
     * Words whose correction the user refused in this field (lowercased): an undone correction,
     * or the typed word tapped in the strip. Only a new field forgets them, never a read of the
     * field: some apps answer from a copy that lags our own edits, and a single stale read used
     * to drop the refusal so the next space corrected the word again. Memory only.
     */
    private val declined = LinkedHashSet<String>()

    private fun decline(word: String) {
        declined.remove(word.lowercase())
        declined.add(word.lowercase())
        if (declined.size > MAX_DECLINED) declined.remove(declined.first())
    }

    private fun isDeclined(word: String): Boolean = word.lowercase() in declined

    /** The password manager's chips for the current field, pinned first. */
    var suggestions: List<net.matasar.keyboard.autofill.SuggestionEntry> by mutableStateOf(emptyList())

    /** Whether the key button's sheet is open. */
    var managerSheetOpen: Boolean by mutableStateOf(false)

    /** Whether the macro sheet is open. */
    var macroSheetOpen: Boolean by mutableStateOf(false)

    /** Whether the gear's sheet (developer mode, settings) is open. */
    var settingsSheetOpen: Boolean by mutableStateOf(false)

    /** The id of the macro playing now, for the sheet's Stop; null when none is. */
    var runningMacro: String? by mutableStateOf(null)
        private set

    private var macroJob: Job? = null

    /** The app the running macro was started in; a field in another app stops it. */
    private var macroPackage: String? = null

    /** Whether the running macro types random keys or a secret text, so a field it moves into is not read back either. */
    private var macroTypesSecrets = false

    /** The app of the focused field, as the last [onStartInput] reported it. */
    private var fieldPackage: String? = null

    /** Counts the fields started, so a macro can wait for the next one after a Tab. */
    private val fieldStarts = MutableStateFlow(0)

    /** A macro pressed Tab or Enter and is waiting for the app to move focus: the old field finishing is expected. */
    private var awaitingFocusMove = false

    /** The clipboard's text for a macro's paste block; the service plugs it in. */
    var clipboardText: () -> String? = { null }

    /** Puts a macro's copy on the clipboard, marked sensitive when asked; the service plugs it in. */
    var copyToClipboard: (text: String, sensitive: Boolean) -> Unit = { _, _ -> }

    /** Where a run's random keys come from: a fresh SecureRandom per run. Tests seed it. */
    internal var macroRandom: () -> Random = { SecureRandom().asKotlinRandom() }

    /** Modifiers whose hold was used by another key, so the release must not count as a tap. */
    private val usedHolds = mutableSetOf<ModifierKey>()

    /** Held modifiers whose long press wants to lock them; decided when the finger lifts. */
    private val pendingLocks = mutableSetOf<ModifierKey>()

    /** Shift is on, by the latch or the strip's modifier: the key would type its shifted symbol. */
    val shiftActive: Boolean get() = shift.active || modifiers.isActive(ModifierKey.SHIFT)

    private val fnActive: Boolean get() = modifiers.isActive(ModifierKey.FN)

    /** A letter tapped now goes in uppercase: Shift, or the field's capital outside a combination. */
    private val letterUpper: Boolean get() = letterUpper(shiftActive)

    private fun letterUpper(shift: Boolean): Boolean = shift || (autoCapital && !modifiers.anyMetaActive)

    /**
     * What a key shows right now, which is always what it would type if it were tapped. Shift
     * shows the shifted symbol (`1` reads `!`), Fn the key's Fn meaning (`1` reads `F1`, `х` reads
     * `[`, backspace reads `Del`) and Fn+Shift the shifted one of those (`{`). While a combination
     * modifier is active (Ctrl, Alt, Meta, or the strip's Shift, which all send a key event) a
     * letter shows the US letter of its slot, so a Cyrillic board reads Q W E R T Y and Ctrl+С is
     * visibly Ctrl+C.
     */
    fun displayLabel(key: Key): String = labelFor(key, shiftActive, fnActive)

    /**
     * Whether Shift is what makes this key's glyph what it is, so its legend is the live one.
     * Shift is armed but changes nothing on a digit under Fn — the key types F1 either way — and
     * a legend tinted there says the opposite of the truth.
     */
    fun shiftLive(key: Key): Boolean = shiftActive && labelFor(key, shift = false, fn = fnActive) != displayLabel(key)

    /** The same question for Fn. */
    fun fnLive(key: Key): Boolean = fnActive && labelFor(key, shift = shiftActive, fn = false) != displayLabel(key)

    private fun labelFor(key: Key, shift: Boolean, fn: Boolean): String {
        // With English alone there is nothing to tell apart, so space stays blank. The key keeps
        // its label, which is its id and what TalkBack reads.
        if (key.action == KeyAction.Space && !withGlobe) return ""
        if (modifiers.anyMetaActive && key.action is KeyAction.Letter && key.slot != null) return key.slot.uppercase()
        fnLabel(key, shift, fn)?.let { return it }
        return when (val action = key.action) {
            is KeyAction.Letter -> if (letterUpper(shift)) action.upper else action.lower
            is KeyAction.Text -> if (shift && action.shifted != null) action.shifted else key.label
            else -> key.label
        }
    }

    /** What Fn makes of a key, or null when Fn leaves it alone. */
    private fun fnLabel(key: Key, shift: Boolean, fn: Boolean): String? {
        if (!fn) return null
        val action = key.fnAction
        if (action is KeyAction.Text) return if (shift && action.shifted != null) action.shifted else action.text
        return key.fnLegend
    }

    /** An icon steps aside while Fn is active and the key's Fn meaning has a name of its own. */
    fun showsIcon(key: Key): Boolean = fnLabel(key, shiftActive, fnActive) == null

    fun onStartInput(info: EditorInfo?) {
        fieldPackage = info?.packageName
        // A macro follows its own Tab into the next field of the same app, never into another app.
        if (macroJob != null && fieldPackage != macroPackage) stopMacro()
        fieldStarts.value++
        updateFieldKind(info)
        layer = fieldKind.initialLayer()
        shift = Latch()
        lastSpaceAt = null
        modifiers = Modifiers()
        usedHolds.clear()
        pendingLocks.clear()
        editorActionId = info?.let { editorActionFor(it.imeOptions, it.inputType) }
        // A new field, so the previous app's answer does not carry: the service restores this
        // one's a moment later, and until it does the app's own request stands.
        suggestInApp = false
        updateFieldSuggestions(info)
        updateFieldMic(info)
        capsModes = info?.let { capsModesOf(it.inputType) } ?: 0
        autoCancelledAtMs = null
        suggestions = emptyList()
        managerSheetOpen = false
        macroSheetOpen = false
        settingsSheetOpen = false
        languageSheetOpen = false
        clearCandidates()
        declined.clear()
        refreshAutoCapital()
        if (macroJob != null && macroTypesSecrets) passwordTyped = true
    }

    fun onFinishInput() {
        // A macro never plays on into another field, unless it moved there itself with Tab or Enter.
        if (!awaitingFocusMove) stopMacro()
        passwordTyped = false
        capsModes = 0
        autoCapital = false
        autoCancelledAtMs = null
        shift = Latch()
        modifiers = Modifiers()
        usedHolds.clear()
        pendingLocks.clear()
        endTrackpad()
        suggestions = emptyList()
        managerSheetOpen = false
        macroSheetOpen = false
        settingsSheetOpen = false
        // No field at all: TYPE_NULL, which no override reaches, so a stored answer landing late
        // cannot turn the strip back on after the field has gone.
        fieldInputType = InputType.TYPE_NULL
        suggestInApp = false
        fieldOverridable = false
        fieldAllowsSuggestions = false
        clearCandidates()
        declined.clear()
    }

    // The toolbar's three sheets share the space under it: opening one closes the others.

    fun toggleManagerSheet() {
        val open = !managerSheetOpen
        closeToolbarSheets()
        managerSheetOpen = open
    }

    fun toggleMacroSheet() {
        val open = !macroSheetOpen
        closeToolbarSheets()
        macroSheetOpen = open
    }

    fun toggleSettingsSheet() {
        val open = !settingsSheetOpen
        closeToolbarSheets()
        settingsSheetOpen = open
    }

    private fun closeToolbarSheets() {
        managerSheetOpen = false
        macroSheetOpen = false
        settingsSheetOpen = false
    }

    /**
     * Plays [macro] into the field; one at a time, a new one replaces a running one. What it types
     * feeds no candidates, and after random keys or a secret text nothing is read back, as after a filled password.
     */
    fun runMacro(macro: Macro) {
        val scope = scope ?: return
        stopMacro()
        macroSheetOpen = false
        clearCandidates()
        val runner = MacroRunner(dispatcher, clipboardText, macroRandom, awaitFocusMove = ::awaitFocusMove, copyToClipboard = copyToClipboard)
        runningMacro = macro.id
        macroPackage = fieldPackage
        macroTypesSecrets = macro.typesSecrets()
        if (macroTypesSecrets) passwordTyped = true
        val job = scope.launch(main ?: Dispatchers.Main.immediate) {
            try {
                runner.run(macro) { MacroField(terminal = terminalField, editingShortcuts = editingShortcutsInTextFields, password = passwordField) }
            } catch (_: MacroTooLong) {
                // The editor warns about it; the keyboard plays nothing.
            }
        }
        macroJob = job
        job.invokeOnCompletion {
            if (macroJob === job) {
                macroJob = null
                runningMacro = null
                refreshAutoCapital()
            }
        }
    }

    fun stopMacro() {
        macroJob?.cancel()
        macroJob = null
        runningMacro = null
        awaitingFocusMove = false
    }

    /** Waits until the app starts another field, or [FOCUS_MOVE_TIMEOUT_MS] pass when the key moved nothing. */
    private suspend fun awaitFocusMove() {
        val before = fieldStarts.value
        awaitingFocusMove = true
        try {
            withTimeoutOrNull(FOCUS_MOVE_TIMEOUT_MS) { fieldStarts.first { it != before } }
        } finally {
            awaitingFocusMove = false
        }
    }

    /** Flips developer mode from the gear's sheet and closes it, so the strip shows at once. */
    fun toggleDeveloperMode() {
        developerMode = !developerMode
        settingsSheetOpen = false
    }

    /** Restores the remembered developer mode for the app that just got focus. */
    fun restoreDeveloperMode(on: Boolean) {
        developerMode = on
    }

    /**
     * Re-reads what the field says about suggestions. Called for a new field and again when one
     * restarts, because a restart skips [onStartInput] and would otherwise leave this per-field
     * state describing the field before it.
     */
    fun updateFieldSuggestions(info: EditorInfo?) {
        fieldInputType = info?.inputType
        fieldOverridable = info?.let { noSuggestionsOverridable(it.inputType) } ?: false
        applySuggestionRules()
    }

    /** Restores the remembered answer for the app that just got focus, as developer mode's is. */
    fun restoreSuggestInApp(on: Boolean) {
        suggestInApp = on
        applySuggestionRules()
    }

    /** The gear sheet's row: the digits come and go at once, and the sheet closes as Developer mode's does. */
    fun toggleNumberRow() {
        numberRow = !numberRow
        settingsSheetOpen = false
    }

    /**
     * The gear sheet's row: this app may suggest though it asked not to. It takes effect in the
     * field that is open, not only at the next one, and the sheet closes as Developer mode's does.
     */
    fun toggleSuggestInApp() {
        suggestInApp = !suggestInApp
        applySuggestionRules()
        // Turned off, the words already on the strip would stay there until the next key, in a
        // field whose app asked for none and where the user has just agreed.
        if (!fieldAllowsSuggestions) clearCandidates()
        settingsSheetOpen = false
    }

    private fun applySuggestionRules() {
        fieldAllowsSuggestions = fieldInputType?.let { suggestionsAllowed(it, suggestInApp) } ?: true
    }

    /** Setting: whether a second quick tap locks a modifier or shift. */
    var doubleTapLock: Boolean = true

    private val doubleTapWindowMs: Long get() = if (doubleTapLock) Latch.DOUBLE_TAP_WINDOW_MS else 0L

    fun onKey(key: Key) {
        passwordTyped = false
        candidatesCollapsed = false
        val undo = lastAutocorrect
        lastAutocorrect = null
        val spaced = autoSpace
        autoSpace = null
        val glidedWord = lastGlideWord
        val glideCommit = lastGlideCommit
        if (glidedWord != null) clearCandidates()
        if (glidedWord != null && key.action == KeyAction.Backspace && !modifiers.anyActive) {
            undoGlide(glideCommit ?: glidedWord)
            refreshAutoCapital()
            return
        }
        if (undo != null && key.action == KeyAction.Backspace && !modifiers.anyActive) {
            undoAutocorrect(undo)
            refreshAutoCapital()
            return
        }
        if (key.action != KeyAction.Space) lastSpaceAt = null
        if (spaced != null && !modifiers.anyActive && takeBackAutoSpace(key, spaced)) {
            // Space that finds a pick's space there counts as the first of a double space.
            if (key.action == KeyAction.Space) lastSpaceAt = clock()
            afterKey()
            return
        }
        val fnAction = key.fnAction
        if (modifiers.isActive(ModifierKey.FN) && fnAction != null) perform(key, fnAction) else perform(key, key.action)
        // A page switch or Shift types nothing: on a phone ! and ) are behind ?123, and the
        // pick's space must still be ours when they arrive.
        if (spaced != null && (key.action is KeyAction.SwitchLayer || key.action == KeyAction.Shift)) autoSpace = spaced
        // A combination modifier or the trackpad takes the strip away; the chip has the toolbar then.
        if (modifiers.anyMetaActive || trackpad) candidates = null
    }

    private fun perform(key: Key, action: KeyAction) {
        when (action) {
            is KeyAction.Letter -> {
                val text = if (letterUpper) action.upper else action.lower
                if (modifiers.anyMetaActive) sendCombo(text, key) else { dispatcher.commitText(text); refreshCandidates() }
                afterKey()
            }
            is KeyAction.Text -> {
                val text = if (shiftActive && action.shifted != null) action.shifted else action.text
                when {
                    modifiers.anyMetaActive -> sendCombo(text, key)
                    text in SEPARATORS -> commitSeparator(text)
                    else -> { dispatcher.commitText(text); if (text.any { it.isLetter() }) refreshCandidates() else candidates = null }
                }
                afterKey()
            }
            KeyAction.CapsLock -> {
                shift = if (shift.state == LatchState.LOCKED) Latch() else shift.longPress()
                refreshAutoCapital()
            }
            KeyAction.Space -> {
                val previous = lastSpaceAt
                lastSpaceAt = null
                when {
                    modifiers.anyMetaActive -> sendCombo(" ", key)
                    periodShortcut(previous) -> { dispatcher.replaceWordBeforeCursor(" ", ". "); candidates = null }
                    else -> { commitSeparator(" "); lastSpaceAt = clock() }
                }
                afterKey()
            }
            KeyAction.Backspace -> {
                when {
                    modifiers.isActive(ModifierKey.FN) -> dispatcher.forwardDelete(terminalField)
                    modifiers.anyMetaActive -> dispatcher.sendKey(KeyEvent.KEYCODE_DEL, modifiers.metaState())
                    else -> { dispatcher.backspace(); refreshCandidates() }
                }
                afterKey()
            }
            KeyAction.Enter -> {
                if (modifiers.anyMetaActive) dispatcher.sendKey(KeyEvent.KEYCODE_ENTER, modifiers.metaState())
                else dispatcher.enter(editorActionId)
                candidates = null
                afterKey()
            }
            KeyAction.Shift -> onShiftTap()
            is KeyAction.SwitchLayer -> layer = action.layer
            is KeyAction.KeyCode -> {
                val code = if (modifiers.isActive(ModifierKey.FN) && action.fnKeyCode != null) action.fnKeyCode else action.keyCode
                dispatcher.sendKey(code, modifiers.metaState())
                candidates = null
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
        val physical = key.slot?.let { if (shiftActive) it.uppercase() else it.toString() } ?: text
        val onlyCtrl = modifiers.active.filter { it != ModifierKey.FN } == listOf(ModifierKey.CTRL)
        if (onlyCtrl && editingShortcutsInTextFields && !terminalField) {
            EditingAction.forLetter(physical)?.let { action ->
                if (dispatcher.sendEditingAction(action)) return
            }
        }
        val stroke: KeyStroke = keyStrokeFor(physical) ?: keyStrokeFor(text) ?: keyStrokeFor(key.label) ?: return
        // A letter on a punctuation slot (ї on `]`) has no shifted twin to look up; Shift+Ctrl+ї is still Shift+Ctrl+].
        val shiftedSlot = shiftActive && key.slot?.isLetter() == false
        val meta = if (shiftedSlot) modifiers.metaState() or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else modifiers.metaState()
        dispatcher.sendCombo(stroke, meta)
    }

    /** A key went out: one-shot shift and modifiers release; held ones remember they were used. */
    private fun afterKey() {
        if (modifiers.held.isNotEmpty()) usedHolds += modifiers.held
        shift = shift.consume()
        modifiers = modifiers.consume()
        autoCancelledAtMs = null
        refreshAutoCapital()
    }

    /**
     * Shift tapped. On an automatic capital the tap cancels it, as on a phone keyboard, and a
     * second quick tap locks caps the way a double tap does from idle.
     */
    private fun onShiftTap() {
        val now = clock()
        val cancelledAt = autoCancelledAtMs
        autoCancelledAtMs = null
        when {
            autoCapital -> {
                autoCapital = false
                autoCancelledAtMs = now
            }
            cancelledAt != null && shift.state == LatchState.IDLE && now - cancelledAt <= doubleTapWindowMs ->
                shift = Latch(LatchState.LOCKED, now)
            else -> shift = shift.tap(now, doubleTapWindowMs)
        }
    }

    /**
     * Asks the field whether the next letter should be a capital. Only when it could matter: the
     * setting is on, the field asks for capitals, Shift is idle and did not just cancel one, and no
     * combination or trackpad gesture is under way; otherwise the field is not asked at all.
     */
    private fun refreshAutoCapital() {
        autoCapital = autoCapitalize && capsModes != 0 && autoCancelledAtMs == null && shift.state == LatchState.IDLE &&
            !modifiers.anyMetaActive && !trackpad && dispatcher.capitalAtCursor(capsModes)
    }

    private fun onModifierTap(modifier: ModifierKey) {
        if (usedHolds.remove(modifier)) return
        modifiers = modifiers.tap(modifier, clock(), doubleTapWindowMs)
        refreshAutoCapital()
    }

    /** A finger lands on a modifier: it is held until the finger lifts (chording). */
    fun onModifierPressStart(modifier: ModifierKey) {
        modifiers = modifiers.hold(modifier)
        refreshAutoCapital()
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
        refreshAutoCapital()
    }

    /** Whether [key] repeats while held down in the current modifier state. */
    fun repeats(key: Key): Boolean {
        if (key.action == KeyAction.Backspace) return true
        val action = if (fnActive && key.fnAction != null) key.fnAction else key.action
        val code = if (fnActive && action is KeyAction.KeyCode && action.fnKeyCode != null) {
            action.fnKeyCode
        } else {
            (action as? KeyAction.KeyCode)?.keyCode
        }
        if (code != null && code.isDpadArrow()) return true
        return key.repeats && !fnActive
    }

    /** Backspace or arrow held down: repeat one step per tick. */
    fun onKeyRepeat(key: Key) {
        if (key.action == KeyAction.Backspace) {
            if (fnActive) dispatcher.forwardDelete(terminalField) else dispatcher.backspace()
            return
        }
        val action = if (fnActive && key.fnAction != null) key.fnAction else key.action
        if (action is KeyAction.KeyCode) {
            val code = if (fnActive && action.fnKeyCode != null) action.fnKeyCode else action.keyCode
            if (code.isDpadArrow()) {
                dispatcher.sendKey(code, modifiers.metaState())
                candidates = null
            }
        }
    }

    fun onKeyLongPress(key: Key) {
        when (val action = key.action) {
            KeyAction.SwitchLanguage -> languageSheetOpen = withGlobe
            KeyAction.Shift -> {
                shift = shift.longPress()
                autoCancelledAtMs = null
                refreshAutoCapital()
            }
            // A held modifier waits for the finger to lift: the hold may still be a chord.
            is KeyAction.Modifier ->
                if (action.modifier in modifiers.held) pendingLocks += action.modifier
                else {
                    modifiers = modifiers.longPress(action.modifier)
                    refreshAutoCapital()
                }
            else -> Unit
        }
    }

    /** A glide ended over the letter keys [keys]: classify off the main thread, then commit the best word. */
    fun onGlideEnd(path: List<GlidePoint>, keys: List<GlideKey>) {
        val engine = glideEngine ?: return
        val scope = scope ?: return
        if (!glideAvailable || path.size < 2) return
        val capitalize = letterUpper
        scope.launch(background) {
            engine.setLayout(keys)
            val words = engine.classify(path)
            withContext(main ?: Dispatchers.Main.immediate) { commitGlide(words, capitalize) }
        }
    }

    /**
     * Commits the best word and keeps the rest as alternatives. No trailing space: what follows a
     * word is the user's to choose, and a comma after one should not arrive as ` ,`. The space
     * goes in front instead, and only when the text already there ends a word, so two glides in a
     * row still read as two words. A word glided in front of another one still gets a space after
     * it, or the two would join.
     */
    internal fun commitGlide(words: List<String>, capitalize: Boolean) {
        if (words.isEmpty()) return
        // Classification is asynchronous, so the field can have become another one since the
        // gesture: a word must not land in a password field, nor its text be read there.
        if (passwordField || terminalField) return
        val cased = words.map { if (capitalize) it.replaceFirstChar(Char::uppercase) else it }
        val before = if (dispatcher.needsSpaceBefore()) " " else ""
        val after = if (dispatcher.needsSpaceAfter()) " " else ""
        val committed = before + cased.first() + after
        dispatcher.commitText(committed)
        lastGlideWord = cased.first()
        lastGlideCommit = committed
        lastAutocorrect = null
        candidates = WordCandidates(cased.first(), cased.take(Candidates.MAX_WORDS))
        shift = shift.consume()
        refreshAutoCapital()
    }

    /**
     * The user tapped a word in the strip: after a glide it swaps the glided word (and the
     * alternatives stay, and glide keeps its own spacing); while typing it replaces the word
     * being typed and puts a space after it, unless the field already has one there or a mark
     * that takes none. The typed word itself is already in the field, so tapping it keeps it,
     * with its space, and the field corrects it no more.
     */
    fun pickCandidate(word: String) {
        val current = candidates ?: return
        val glided = lastGlideWord
        val glideCommit = lastGlideCommit
        if (glided != null) {
            if (word == glided) return
            dispatcher.replaceWordBeforeCursor(glided, word)
            lastGlideWord = word
            lastGlideCommit = when (glideCommit) {
                " $glided " -> " $word "
                " $glided" -> " $word"
                "$glided " -> "$word "
                glided -> word
                else -> word
            }
            refreshAutoCapital()
            return
        }
        // Keeping the word as typed: the next separator must not correct it after all.
        if (word == current.typed) decline(word)
        // The field may have changed under the strip; replace only what is still there.
        if (dispatcher.textEndsWith(current.typed)) {
            // The word and its space are one change to the app.
            dispatcher.batch {
                if (word != current.typed) dispatcher.replaceWordBeforeCursor(current.typed, word)
                if (!dispatcher.nextCharAvoidsSpace()) {
                    dispatcher.commitText(" ")
                    autoSpace = "$word "
                }
            }
        }
        candidates = null
        refreshAutoCapital()
    }

    /**
     * The key after a pick's space: a mark that ends a word swaps places with the space
     * ("hello! "), a closing bracket takes its place ("hello)"), and Space finds one already
     * there. False for any other key, and when the text no longer ends with the pick (the cursor
     * moved), so a space the user typed is never taken.
     */
    private fun takeBackAutoSpace(key: Key, spaced: String): Boolean {
        val text = when (val action = key.action) {
            KeyAction.Space -> " "
            is KeyAction.Text -> if (shiftActive && action.shifted != null) action.shifted else action.text
            else -> return false
        }
        if (text != " " && text !in SWAP_BEFORE_SPACE && text !in CLOSE_ON_WORD) return false
        if (!dispatcher.textEndsWith(spaced)) return false
        when (text) {
            " " -> Unit
            in SWAP_BEFORE_SPACE -> dispatcher.replaceWordBeforeCursor(" ", "$text ")
            else -> dispatcher.replaceWordBeforeCursor(" ", text)
        }
        candidates = null
        return true
    }

    /**
     * Types a password the fill screen got from the manager. It is committed as it is, and the
     * strip never shows a word of it: the field decides whether the password is visible, not us.
     */
    fun typeFilledPassword(password: CharSequence) {
        dispatcher.commitText(password)
        passwordTyped = true
        clearCandidates()
    }

    /**
     * Types a username the fill screen got from the manager, the first half of a login. Like a
     * filled password, the strip never reads it back.
     */
    fun typeFilledUsername(username: CharSequence) {
        dispatcher.commitText(username)
        passwordTyped = true
        clearCandidates()
    }

    /** Moves on to the field this one says follows it, the password after a username. */
    fun goToNextField() {
        dispatcher.performEditorAction(EditorInfo.IME_ACTION_NEXT)
    }

    /** Whether the field has no text of its own, so a filled username overwrites nothing. */
    fun fieldIsEmpty(): Boolean = dispatcher.fieldIsEmpty()

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
        lastGlideCommit = null
        val word = dispatcher.wordBeforeCursor()
        val found = engine.forWord(word)
        candidates = if (found != null && isDeclined(word)) found.copy(correction = null) else found
    }

    /** The cursor moved (the service's onUpdateSelection): the word under it may be another one. */
    fun onSelectionChanged() {
        refreshAutoCapital()
        // A glide's own commit moves the cursor too; its alternatives stay until the next key.
        if (lastGlideWord != null) return
        refreshCandidates()
    }

    /**
     * Whether this Space turns the one before it into ". ": it came within
     * [DOUBLE_SPACE_WINDOW_MS] of a Space typed after a letter or digit, in a field of prose.
     */
    private fun periodShortcut(previousSpaceAt: Long?): Boolean =
        doubleSpacePeriod && previousSpaceAt != null && clock() - previousSpaceAt <= DOUBLE_SPACE_WINDOW_MS &&
            (fieldInputType?.let(::periodShortcutAllowed) ?: true) && dispatcher.endsWithSpaceAfterWord()

    /** A separator: apply the strip's correction first when there is one, then the separator itself. */
    private fun commitSeparator(separator: String) {
        val current = candidates
        val correction = current?.correction
        // The correction and the separator after it are one change to the app.
        dispatcher.batch {
            if (autoCorrect && current != null && correction != null && lastGlideWord == null && !isDeclined(current.typed) &&
                dispatcher.textEndsWith(current.typed)
            ) {
                dispatcher.replaceWordBeforeCursor(current.typed, correction)
                lastAutocorrect = Autocorrect(current.typed, correction, separator)
            }
            dispatcher.commitText(separator)
        }
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
        decline(undo.typed)
        refreshCandidates()
    }

    /**
     * Backspace right after a glide removes the entire swiped word and any auto-inserted spaces
     * around it, so the effect is a full revert. If the text no longer ends with what was
     * committed (the cursor was moved), it is an ordinary backspace.
     */
    private fun undoGlide(committed: String) {
        if (!dispatcher.textEndsWith(committed)) {
            dispatcher.backspace()
            refreshCandidates()
            return
        }
        dispatcher.replaceWordBeforeCursor(committed, "")
    }

    private fun clearCandidates() {
        candidates = null
        candidatesCollapsed = false
        lastGlideWord = null
        lastGlideCommit = null
        lastAutocorrect = null
        autoSpace = null
    }

    /** The accent candidates a long press on [key] offers, in the current case; none in passwords. */
    fun accentsFor(key: Key): List<String> = when {
        passwordField -> emptyList()
        letterUpper -> key.longPress.map { it.uppercase() }
        else -> key.longPress
    }

    /**
     * What a long press on [key] types: the shifted symbol printed on it, which is otherwise only
     * reachable through Shift. Caps Lock inverts it, because a tap there already gives the shifted
     * symbol. Null where the rule does not apply — a letter, a key with no shifted symbol, or any
     * key while Fn is active, which gives the key another meaning. Accents are not checked here:
     * the caller offers [accentsFor] first, and a key with accents never reaches this.
     */
    fun longPressText(key: Key): String? {
        if (fnActive) return null
        val action = key.action as? KeyAction.Text ?: return null
        val shifted = action.shifted ?: return null
        return if (shift.state == LatchState.LOCKED) action.text else shifted
    }

    /**
     * Types what [longPressText] says, exactly as a tap on the shifted key would: separators
     * apply a pending correction, a held Ctrl or Alt still sends a combination. False when the
     * key has nothing to offer, which leaves the long press to whatever the caller does next.
     */
    fun onKeyLongPressShift(key: Key): Boolean {
        val text = longPressText(key) ?: return false
        perform(key, KeyAction.Text(text))
        return true
    }

    /**
     * The toolbar's paste: the clipboard's text goes in through the dispatcher like typed text,
     * and the strip and the automatic capital follow what is now before the cursor. It is not a
     * key, so a one-shot Shift or modifier stays armed; nothing when the clipboard holds no text.
     */
    fun paste() {
        val text = clipboardText()?.takeIf { it.isNotEmpty() } ?: return
        dispatcher.commitText(text)
        lastAutocorrect = null
        refreshCandidates()
        refreshAutoCapital()
    }

    /** A chosen accent goes in like a letter: it consumes a one-shot shift. */
    fun commitAccent(text: String) {
        dispatcher.commitText(text)
        lastAutocorrect = null
        refreshCandidates()
        afterKey()
    }

    /**
     * Moves the cursor by [steps] characters, or words with [byWord], as the trackpad's slide
     * does: the accessibility action on Space, for a screen reader that never gets a long press.
     * By word is Ctrl and an arrow, which is how Android's own text fields move by word.
     */
    fun moveCursor(steps: Int, byWord: Boolean) {
        if (byWord) {
            val key = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            repeat(kotlin.math.abs(steps)) { dispatcher.sendKey(key, KeyEvent.META_CTRL_ON) }
        } else {
            dispatcher.moveCursor(steps)
        }
    }

    fun startTrackpad(stepPx: Float) {
        trackpadGesture = TrackpadGesture(stepPx)
        trackpad = true
        autoCapital = false
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
        refreshAutoCapital()
    }

    companion object {
        /** How long a macro waits after its Tab or Enter for the app to start the next field. */
        const val FOCUS_MOVE_TIMEOUT_MS = 500L

        /** How soon a second Space has to follow the first to type ". ". */
        const val DOUBLE_SPACE_WINDOW_MS = 400L

        /** The characters that end a word and apply its correction. */
        private val SEPARATORS = setOf(" ", ".", ",", "!", "?")

        /** Marks that follow a picked word before its space: "hello " and "!" make "hello! ". */
        private val SWAP_BEFORE_SPACE = setOf(".", ",", "!", "?", ";", ":")

        /** Closing brackets, which replace a picked word's space: "hello " and ")" make "hello)". */
        private val CLOSE_ON_WORD = setOf(")", "]", "}")

        /** How many refused words a field remembers; the oldest goes first. */
        private const val MAX_DECLINED = 32

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

private fun Int.isDpadArrow(): Boolean = when (this) {
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT -> true
    else -> false
}
