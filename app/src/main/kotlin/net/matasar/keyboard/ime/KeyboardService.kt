package net.matasar.keyboard.ime

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import androidx.compose.ui.graphics.toArgb
import net.matasar.keyboard.autofill.AndroidAutofillActions
import net.matasar.keyboard.autofill.InlineSuggestions
import net.matasar.keyboard.autofill.SuggestionColors
import net.matasar.keyboard.ui.theme.LocalKeyboardColors
import androidx.compose.runtime.SideEffect
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodSubtype
import net.matasar.keyboard.layout.Languages
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.matasar.keyboard.input.AndroidEditorPort
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.input.glide.GlideEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.matasar.keyboard.settings.Prefs
import net.matasar.keyboard.settings.Settings
import net.matasar.keyboard.settings.SettingsActivity
import net.matasar.keyboard.settings.asDarkTheme
import net.matasar.keyboard.ui.KeyboardFeel
import net.matasar.keyboard.ui.KeyboardScreen
import net.matasar.keyboard.ui.PopupMetrics
import net.matasar.keyboard.ui.ToolbarActions
import net.matasar.keyboard.ui.theme.KeyboardTheme

/**
 * The input method. Hosts the Compose keyboard inside the IME window.
 *
 * A [ComposeView] needs a lifecycle, a view-model store and a saved-state registry on its view
 * tree; a service has none of those, so this class owns all three and drives the lifecycle from
 * the IME callbacks: created in [onCreate], started while an input view exists, resumed while it
 * is shown, destroyed in [onDestroy].
 */
class KeyboardService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, ToolbarActions, SystemActions {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val controller = KeyboardController(InputDispatcher(AndroidEditorPort { currentInputConnection }))

    /** One glide engine per language, built on first use and kept; the word lists are small. */
    private val glideEngines = HashMap<String, GlideEngine>()

    /** Points the controller at [tag]'s engine, loading the word list off the main thread if needed. */
    private fun loadGlideEngine(tag: String) {
        glideEngines[tag]?.let { controller.glideEngine = it; return }
        controller.glideEngine = null
        lifecycleScope.launch(Dispatchers.IO) {
            val engine = GlideEngine.load(applicationContext, tag)
            withContext(Dispatchers.Main) {
                glideEngines[tag] = engine
                if (controller.language.tag == tag) controller.glideEngine = engine
            }
        }
    }
    private lateinit var prefs: Prefs
    private val autofillActions by lazy { AndroidAutofillActions(this) }
    private var inputView: View? = null

    /**
     * How far the system's bottom bar (navigation bar or gesture area) reaches into the input
     * view, in px. The IME window always extends under that bar; AOSP either pads the decor for
     * it or hands the insets to the content, but some skins (Samsung's One UI) do neither and the
     * pill lands on the bottom row. Measured after every layout, so the keys are padded by
     * exactly what is still uncovered and never twice.
     */
    private var bottomBarOverlapPx by mutableIntStateOf(0)
    private var currentPackage: String? = null

    /** The chip colours, captured from the theme so the inline request can style the chips. */
    private var suggestionColors = SuggestionColors(0xFFFFFFFF.toInt(), 0xFF1B1C1F.toInt(), 0xFF5C5F66.toInt())

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(applicationContext)
        controller.systemActions = this
        controller.scope = lifecycleScope
        savedStateController.performRestore(null)
        controller.onLanguageChanged = { language ->
            lifecycleScope.launch { prefs.setCurrentLanguage(language.tag) }
            loadGlideEngine(language.tag)
        }
        loadGlideEngine(controller.language.tag)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleScope.launch {
            prefs.settings.collect { settings ->
                controller.editingShortcutsInTextFields = settings.editingShortcuts
                controller.doubleTapLock = settings.doubleTapLock
                controller.glideEnabled = settings.glide
                controller.enabledLanguages = settings.enabledLanguages
                // The persisted choice is authoritative: follow it when it changes under us, and
                // fall back to the first enabled language when the current one was switched off.
                val wanted = Languages.byTag(settings.currentLanguage)?.takeIf { it.tag in settings.enabledLanguages }
                    ?: Languages.byTag(settings.enabledLanguages.first())
                if (wanted != null && wanted != controller.language) {
                    controller.restoreLanguage(wanted)
                    loadGlideEngine(wanted.tag)
                }
            }
        }
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        // Compose resolves its window recomposer from the window's root view, so the owners
        // have to be on the IME window's decor view as well as on the ComposeView itself.
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        return ComposeView(this).also { inputView = it }.apply {
            viewTreeObserver.addOnGlobalLayoutListener { measureBottomBarOverlap() }
            setViewTreeLifecycleOwner(this@KeyboardService)
            setViewTreeViewModelStoreOwner(this@KeyboardService)
            setViewTreeSavedStateRegistryOwner(this@KeyboardService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val settings by prefs.settings.collectAsState(initial = Settings())
                KeyboardTheme(darkTheme = settings.theme.asDarkTheme()) {
                    val colors = LocalKeyboardColors.current
                    SideEffect {
                        suggestionColors = SuggestionColors(colors.key.toArgb(), colors.onKey.toArgb(), colors.subtle.toArgb())
                    }
                    KeyboardScreen(
                        controller = controller,
                        actions = this@KeyboardService,
                        autofill = autofillActions,
                        bottomInset = with(LocalDensity.current) { bottomBarOverlapPx.toDp() },
                        feel = KeyboardFeel(
                            haptics = settings.haptics,
                            previews = settings.previews,
                            keyBorders = settings.keyBorders,
                            heightScale = settings.heightScale,
                            glide = settings.glide,
                            glideTrail = settings.glideTrail,
                        ),
                    )
                }
            }
        }
    }

    private fun measureBottomBarOverlap() {
        val view = inputView ?: return
        val decor = window?.window?.decorView ?: return
        val insets = ViewCompat.getRootWindowInsets(decor) ?: return
        // The bar as the framework sizes its own nav-bar frame; only a skin that reports no
        // navigation bar at all falls back to the gesture area, which is taller than the bar
        // on stock Android and would otherwise leave a dead strip under the keys.
        val navigationBar = maxOf(
            insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom,
            insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars()).bottom,
        )
        val bar = if (navigationBar > 0) navigationBar else insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures()).bottom
        val location = IntArray(2).also { view.getLocationInWindow(it) }
        val spaceBelowView = decor.height - (location[1] + view.height)
        val overlap = (bar - spaceBelowView).coerceAtLeast(0)
        if (overlap != bottomBarOverlapPx) bottomBarOverlapPx = overlap
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        if (!restarting) controller.onStartInput(editorInfo)
        currentPackage = editorInfo?.packageName
        lifecycleScope.launch {
            val remembered = prefs.settings.first().developerModePackages
            controller.restoreDeveloperMode(currentPackage in remembered)
        }
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (lifecycleRegistry.currentState == Lifecycle.State.RESUMED) {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        }
    }

    /**
     * The top [PopupMetrics.overhang] of the input view is room for popups: the app keeps that
     * strip (content and visible insets move down) and touches there fall through to it.
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val view = inputView ?: return
        val overhang = (PopupMetrics.overhang.value * resources.displayMetrics.density).toInt()
        outInsets.contentTopInsets += overhang
        outInsets.visibleTopInsets += overhang
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(0, outInsets.contentTopInsets, view.width, outInsets.contentTopInsets + view.height)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        controller.onFinishInput()
    }

    /** Android's own language switcher picked a subtype: follow it. */
    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        val language = Languages.byTag(newSubtype.locale.replace('-', '_')) ?: Languages.byTag(newSubtype.languageTag.replace('-', '_')) ?: return
        if (language.tag !in controller.enabledLanguages) lifecycleScope.launch { prefs.setLanguageEnabled(language.tag, true) }
        controller.switchLanguage(language)
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }

    // ---- inline autofill (Android 11+) ----

    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return InlineSuggestions.createRequest(this, suggestionColors)
    }

    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        InlineSuggestions.inflate(this, response, mainExecutor) { entries -> controller.suggestions = entries }
        return true
    }

    // ---- toolbar ----

    override fun toggleManagerSheet() {
        controller.managerSheetOpen = !controller.managerSheetOpen
    }

    override fun toggleDeveloperMode() {
        controller.toggleDeveloperMode()
        val pkg = currentPackage ?: return
        lifecycleScope.launch { prefs.setDeveloperMode(pkg, controller.developerMode) }
    }

    override fun pasteClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: return
        currentInputConnection?.commitText(text, 1)
    }

    override fun openSettings() {
        startActivity(
            Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun hideKeyboard() {
        requestHideSelf(0)
    }

    override fun switchToNextInputMethod() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToNextInputMethod(false)
        } else {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showInputMethodPicker()
        }
    }
}
