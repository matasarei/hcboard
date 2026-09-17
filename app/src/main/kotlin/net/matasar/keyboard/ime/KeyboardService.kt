package net.matasar.keyboard.ime

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import net.matasar.keyboard.autofill.AndroidAutofillActions
import net.matasar.keyboard.autofill.InlineSuggestions
import net.matasar.keyboard.autofill.SuggestionColors
import net.matasar.keyboard.ui.theme.LocalKeyboardColors
import androidx.compose.runtime.SideEffect
import android.annotation.SuppressLint
import android.view.View
import android.view.inputmethod.EditorInfo
import net.matasar.keyboard.R
import net.matasar.keyboard.layout.Languages
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.FileDescriptor
import java.io.PrintWriter
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
import net.matasar.keyboard.nlp.Candidates
import net.matasar.keyboard.nlp.WordList
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

    init {
        // Before onCreate, as InputMethodService.setTheme requires: see res/values/themes.xml.
        setTheme(R.style.Theme_Hcboard_Ime)
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    internal val controller = KeyboardController(InputDispatcher(AndroidEditorPort { currentInputConnection }))

    /** A language's glide engine and candidate engine, over one shared word list. */
    private class LanguageEngines(val glide: GlideEngine, val candidates: Candidates)

    /** One pair of engines per language, built on first use and kept; the word lists are small. */
    private val engines = HashMap<String, LanguageEngines>()

    /** Points the controller at [tag]'s engines, loading the word list off the main thread if needed. */
    private fun loadLanguage(tag: String) {
        engines[tag]?.let { use(it); return }
        controller.glideEngine = null
        controller.candidateEngine = null
        lifecycleScope.launch(Dispatchers.IO) {
            val list = WordList.load(applicationContext, tag)
            val loaded = LanguageEngines(GlideEngine(list), Candidates(list).apply { warmUp() })
            withContext(Dispatchers.Main) {
                engines[tag] = loaded
                if (controller.language.tag == tag) use(loaded)
            }
        }
    }

    private fun use(loaded: LanguageEngines) {
        controller.glideEngine = loaded.glide
        controller.candidateEngine = loaded.candidates
    }
    private lateinit var prefs: Prefs
    private val autofillActions by lazy { AndroidAutofillActions(this) }
    internal var inputView: View? = null
        private set

    /**
     * How far the system's bottom bar (navigation bar or gesture area) reaches into the input
     * view, in px. The IME window always extends under that bar; AOSP either pads the decor for
     * it or hands the insets to the content, but some skins (Samsung's One UI) do neither and the
     * pill lands on the bottom row. Measured after every layout, so the keys are padded by
     * exactly what is still uncovered and never twice.
     */
    private var bottomBarOverlapPx by mutableIntStateOf(0)

    /** The last configuration seen, so a change can be told apart from a change that matters. */
    private lateinit var lastConfiguration: Configuration

    /** How many times the display's shape made us rebuild the input view; for the dump. */
    private var inputViewRebuilds = 0

    /** Settings: follow the measured bar, or use the user's own room under the keys instead. */
    private var autoBottomPadding by mutableStateOf(true)
    private var manualBottomPaddingDp by mutableIntStateOf(0)

    /** The last measurement, kept for `dumpsys activity service`. */
    private var lastMeasurement: String = "not measured yet"
    private var currentPackage: String? = null

    /** The chip colours, captured from the theme so the inline request can style the chips. */
    private var suggestionColors = SuggestionColors(0xFFFFFFFF.toInt(), 0xFF1B1C1F.toInt(), 0xFF5C5F66.toInt())

    override fun onCreate() {
        super.onCreate()
        instance = this
        lastConfiguration = Configuration(resources.configuration)
        prefs = Prefs(applicationContext)
        controller.systemActions = this
        controller.scope = lifecycleScope
        savedStateController.performRestore(null)
        controller.onLanguageChanged = { language ->
            lifecycleScope.launch { prefs.setCurrentLanguage(language.tag) }
            loadLanguage(language.tag)
        }
        loadLanguage(controller.language.tag)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleScope.launch {
            prefs.settings.collect { settings ->
                controller.editingShortcutsInTextFields = settings.editingShortcuts
                controller.doubleTapLock = settings.doubleTapLock
                controller.glideEnabled = settings.glide
                controller.suggestionsEnabled = settings.suggestions
                controller.autoCorrect = settings.autoCorrect
                autoBottomPadding = settings.bottomPaddingAuto
                manualBottomPaddingDp = settings.bottomPaddingDp
                controller.enabledLanguages = settings.enabledLanguages
                // The persisted choice is authoritative: follow it when it changes under us, and
                // fall back to the first enabled language when the current one was switched off.
                val wanted = Languages.byTag(settings.currentLanguage)?.takeIf { it.tag in settings.enabledLanguages }
                    ?: Languages.byTag(settings.enabledLanguages.first())
                if (wanted != null && wanted != controller.language) {
                    controller.restoreLanguage(wanted)
                    loadLanguage(wanted.tag)
                }
            }
        }
    }

    /**
     * The display changed shape: a fold, a rotation, a resized window. The input view outlives
     * that, and the window goes on measuring the app's room from it, so the keyboard is laid out
     * for a screen that is no longer there — the app keeps the old keyboard's space and the keys
     * sit in the wrong part of the screen. Rebuilding the view is what the framework does for an
     * activity and does not do for us.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val previous = lastConfiguration
        lastConfiguration = Configuration(newConfig)
        if (!rebuildsInputView(previous.diff(newConfig))) return
        inputViewRebuilds++
        // onCreateInputView drops the lifecycle back to STARTED; a keyboard that is up right now
        // is still resumed, and its composition must not be told otherwise.
        val resumed = lifecycleRegistry.currentState == Lifecycle.State.RESUMED
        setInputView(onCreateInputView())
        if (resumed) lifecycleRegistry.currentState = Lifecycle.State.RESUMED
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
                        paintBottomBar(colors.background.toArgb(), light = colors.background.luminance() > 0.5f)
                    }
                    KeyboardScreen(
                        controller = controller,
                        actions = this@KeyboardService,
                        autofill = autofillActions,
                        bottomInset = if (autoBottomPadding) with(LocalDensity.current) { bottomBarOverlapPx.toDp() } else manualBottomPaddingDp.dp,
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
        if (decor.height == 0) return // not laid out yet; the next pass has real numbers
        val insets = ViewCompat.getRootWindowInsets(decor) ?: return
        // The bar as the framework sizes its own nav-bar frame. Unlike the framework, which
        // uses the visible inset, this also counts a bar reported hidden: a skin that hides
        // the bar for the IME and still draws its pill is the case this exists for, at the
        // cost of a padded strip under a bar an immersive app has genuinely hidden. Only a
        // skin that reports no navigation bar at all falls back to the gesture area, which
        // is taller than the bar on stock Android and would leave a dead strip under the keys.
        val navigationBar = maxOf(
            insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom,
            insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars()).bottom,
        )
        val reported = reportedBottomBar(
            navigationBarPx = navigationBar,
            tappablePx = insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom,
            gestureAreaPx = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures()).bottom,
        )
        val bar = bottomBarHeight(reported, gestureNavigation(), systemNavigationBarHeightPx())
        val location = IntArray(2).also { view.getLocationInWindow(it) }
        val spaceBelowView = decor.height - (location[1] + view.height)
        val overlap = bottomBarOverlap(bar, spaceBelowView)
        val measurement = "reported=$reported bar=$bar decorHeight=${decor.height} viewTop=${location[1]} viewHeight=${view.height} " +
            "spaceBelow=$spaceBelowView overlap=$overlap gestureNav=${gestureNavigation()} systemBarHeight=${systemNavigationBarHeightPx()}"
        if (overlap != bottomBarOverlapPx) bottomBarOverlapPx = overlap
        // Layout passes are frequent; the report is only worth rebuilding when the numbers moved.
        if (measurement != lastMeasurement) {
            lastMeasurement = measurement
            BottomBarDiagnostics.report = insetReport()
        }
    }

    /**
     * The window's bottom bar as the framework's decor paints it, in the keyboard's own colour
     * so the strip under the keys reads as part of the keyboard rather than a black band.
     */
    private fun paintBottomBar(color: Int, light: Boolean) {
        val w = window?.window ?: return
        @Suppress("DEPRECATION")
        w.navigationBarColor = color
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            w.isNavigationBarContrastEnforced = false
        }
        androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightNavigationBars = light
    }

    /** Every inset type the decor reports, its padding, and the last measurement, one line each. */
    private fun insetReport(): String {
        val lines = mutableListOf("measurement: $lastMeasurement auto=$autoBottomPadding manualPaddingDp=$manualBottomPaddingDp")
        lines += "configuration: ${resources.configuration.screenWidthDp}x${resources.configuration.screenHeightDp}dp density=${resources.configuration.densityDpi} rebuilds=$inputViewRebuilds"
        lines += "device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}), navigation_mode=${runCatching { android.provider.Settings.Secure.getInt(contentResolver, "navigation_mode", -1) }.getOrDefault(-1)}, locales=${resources.configuration.locales.toLanguageTags()}"
        val decor = window?.window?.decorView ?: return (lines + "window: none").joinToString("\n")
        lines += "decor: padding=[${decor.paddingLeft},${decor.paddingTop},${decor.paddingRight},${decor.paddingBottom}] size=${decor.width}x${decor.height}"
        val insets = ViewCompat.getRootWindowInsets(decor) ?: return (lines + "insets: none on the decor").joinToString("\n")
        val types = listOf(
            "navigationBars" to WindowInsetsCompat.Type.navigationBars(), "statusBars" to WindowInsetsCompat.Type.statusBars(),
            "systemBars" to WindowInsetsCompat.Type.systemBars(), "mandatorySystemGestures" to WindowInsetsCompat.Type.mandatorySystemGestures(),
            "systemGestures" to WindowInsetsCompat.Type.systemGestures(), "tappableElement" to WindowInsetsCompat.Type.tappableElement(),
            "displayCutout" to WindowInsetsCompat.Type.displayCutout(), "captionBar" to WindowInsetsCompat.Type.captionBar(),
        )
        for ((name, type) in types) {
            val visible = insets.getInsets(type)
            val stable = runCatching { insets.getInsetsIgnoringVisibility(type) }.getOrNull()
            lines += "$name: bottom=${visible.bottom} stableBottom=${stable?.bottom} visible=${insets.isVisible(type)}"
        }
        return lines.joinToString("\n")
    }

    /** Whether the device is on gesture navigation (`navigation_mode` 2), the mode that draws a pill over the IME. */
    private fun gestureNavigation(): Boolean =
        runCatching { android.provider.Settings.Secure.getInt(contentResolver, "navigation_mode", 0) }.getOrDefault(0) == 2

    /**
     * The system's own navigation-bar height, the value it uses for the bar it draws; 0 when the
     * resource is missing. Lint discourages the internal resource, rightly: it is a last resort,
     * read only when the window reports no inset at all under gesture navigation.
     */
    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    private fun systemNavigationBarHeightPx(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id != 0) resources.getDimensionPixelSize(id) else 0
    }

    /** `adb shell dumpsys activity service net.matasar.keyboard/.ime.KeyboardService`: the inset picture on this device. */
    override fun dump(fd: FileDescriptor, fout: PrintWriter, args: Array<String>) {
        super.dump(fd, fout, args)
        for (line in insetReport().lines()) fout.println("hcboard $line")
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

    /** The cursor moved, by us or by the user: the word under it decides the candidates. */
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        controller.onSelectionChanged()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
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

    companion object {
        /**
         * The running service, so the connected tests can drive its input view and read its
         * state; null when none runs. Lint's leak warning is answered by onDestroy clearing it.
         */
        @SuppressLint("StaticFieldLeak")
        internal var instance: KeyboardService? = null
            private set
    }
}
