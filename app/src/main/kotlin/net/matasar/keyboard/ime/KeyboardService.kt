package net.matasar.keyboard.ime

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import net.matasar.keyboard.autofill.AndroidAutofillActions
import net.matasar.keyboard.autofill.FILL_SCREEN_IME_OPTION
import net.matasar.keyboard.autofill.FillActivity
import net.matasar.keyboard.autofill.FillTarget
import net.matasar.keyboard.autofill.FillTyper
import net.matasar.keyboard.autofill.FocusedField
import net.matasar.keyboard.autofill.InlineSuggestions
import net.matasar.keyboard.autofill.PendingFill
import net.matasar.keyboard.autofill.SuggestionColors
import net.matasar.keyboard.ui.theme.LocalKeyboardColors
import androidx.compose.runtime.SideEffect
import android.annotation.SuppressLint
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodSubtype
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
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.matasar.keyboard.input.AndroidEditorPort
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.input.glide.GlideEngine
import net.matasar.keyboard.nlp.Candidates
import net.matasar.keyboard.nlp.CustomWordStore
import net.matasar.keyboard.nlp.CustomWords
import net.matasar.keyboard.nlp.WordList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.matasar.keyboard.macro.MacroStore
import net.matasar.keyboard.macro.MacrosActivity
import net.matasar.keyboard.settings.Prefs
import net.matasar.keyboard.settings.Settings
import net.matasar.keyboard.settings.SettingsActivity
import net.matasar.keyboard.settings.KeyboardThemeFor
import net.matasar.keyboard.ui.KeyboardFeel
import net.matasar.keyboard.ui.KeyboardScreen
import net.matasar.keyboard.ui.PopupMetrics
import net.matasar.keyboard.ui.ToolbarActions

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

    private var ruBulgarianVocabulary = false

    /** The voice keyboard picked in settings, or null for the automatic choice. */
    private var preferredVoiceKeyboard: String? = null

    /** The user's own words, by language tag; applied to every word list as it loads. */
    private var customWords: CustomWords = emptyMap()

    /** Bumped when [customWords] change, so a list still loading with the old words is not kept. */
    private var customWordsVersion = 0

    /** Points the controller at [tag]'s engines, loading the word list off the main thread if needed. */
    private fun loadLanguage(tag: String) {
        val assetTag = if (tag == "ru" && ruBulgarianVocabulary) "ru_bg" else tag
        engines[assetTag]?.let { use(it); return }
        controller.glideEngine = null
        controller.candidateEngine = null
        // Keyed by the language, not the asset: words added for Russian apply to RU+BG too.
        val overrides = customWords[tag].orEmpty()
        val version = customWordsVersion
        lifecycleScope.launch(Dispatchers.IO) {
            val list = WordList.load(applicationContext, assetTag).withOverrides(overrides)
            val loaded = LanguageEngines(GlideEngine(list), Candidates(list).apply { warmUp() })
            withContext(Dispatchers.Main) {
                if (version != customWordsVersion) return@withContext
                engines[assetTag] = loaded
                if (controller.language.tag == tag) use(loaded)
            }
        }
    }

    /** New custom words: every cached list is stale, so the current language loads again. */
    private fun onCustomWordsChanged(words: CustomWords) {
        if (words == customWords) return
        customWords = words
        customWordsVersion++
        engines.clear()
        loadLanguage(controller.language.tag)
    }

    private fun use(loaded: LanguageEngines) {
        controller.glideEngine = loaded.glide
        controller.candidateEngine = loaded.candidates
    }
    private lateinit var prefs: Prefs
    private lateinit var macroStore: MacroStore
    private lateinit var wordStore: CustomWordStore
    private val autofillActions by lazy { AndroidAutofillActions(this, canFill = ::canFillHere, onFillPassword = ::fillPassword) }
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

    /**
     * How far a display cutout reaches into the input view on the left or right, in px. In
     * landscape mode, corner or side camera cutouts extend into the keyboard area; this measures
     * the needed padding so keys are not obscured by the physical camera hole.
     */
    private var sideCutoutOverlapPx by mutableIntStateOf(0)

    /** A separating vertical fold's bounds in the IME window, as the window manager reports it; null for none. */
    private var hingeInWindow: android.graphics.Rect? = null

    /** Where the hinge answer came from, for the diagnostics: the window, or why it is unavailable. */
    private var hingeSource = "not reported yet"

    /** The hinge in px from the keyboard view's left edge; the wide board splits around it. */
    private var hingePx by mutableStateOf<ClosedFloatingPointRange<Float>?>(null)

    /** The last configuration seen, so a change can be told apart from a change that matters. */
    private lateinit var lastConfiguration: Configuration

    /** How many times the display's shape made us rebuild the input view; for the dump. */
    private var inputViewRebuilds = 0

    /** Settings: follow the measured bar, or use the user's own room under the keys instead. */
    private var autoBottomPadding by mutableStateOf(true)
    private var manualBottomPaddingDp by mutableIntStateOf(0)

    /** The last measurement, kept for `dumpsys activity service`. */
    private var lastMeasurement: String = "not measured yet"

    /** The last insets handed to the window manager, kept for the dump. */
    private var lastComputedInsets: String = "not computed yet"
    private var currentPackage: String? = null
    private var currentFieldId = View.NO_ID

    /** The enabled languages last mirrored into Android's subtypes; null before the first push. */
    private var pushedLanguages: Set<String>? = null

    /** The chip colours, captured from the theme so the inline request can style the chips. */
    private var suggestionColors = SuggestionColors(0xFFFFFFFF.toInt(), 0xFF1B1C1F.toInt(), 0xFF5C5F66.toInt())

    override fun onCreate() {
        super.onCreate()
        instance = this
        lastConfiguration = Configuration(resources.configuration)
        prefs = Prefs(applicationContext)
        controller.systemActions = this
        controller.scope = lifecycleScope
        controller.clipboardText = ::clipboardText
        controller.copyToClipboard = ::copyToClipboard
        macroStore = MacroStore(applicationContext)
        wordStore = CustomWordStore(applicationContext)
        savedStateController.performRestore(null)
        controller.onLanguageChanged = { language ->
            lifecycleScope.launch { prefs.setCurrentLanguage(language.tag) }
            loadLanguage(language.tag)
            reportCurrentSubtype(this, language)
        }
        loadLanguage(controller.language.tag)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        watchHinge()
        lifecycleScope.launch { wordStore.words.collect(::onCustomWordsChanged) }
        lifecycleScope.launch {
            prefs.settings.collect { settings ->
                val ruBgChanged = ruBulgarianVocabulary != settings.ruBulgarianVocabulary
                ruBulgarianVocabulary = settings.ruBulgarianVocabulary
                controller.editingShortcutsInTextFields = settings.editingShortcuts
                controller.doubleTapLock = settings.doubleTapLock
                controller.glideEnabled = settings.glide
                controller.foldToolbar = settings.foldToolbar
                controller.voiceInputEnabled = settings.voiceInput
                preferredVoiceKeyboard = settings.voiceKeyboard
                controller.suggestionsEnabled = settings.suggestions
                controller.autoCorrect = settings.autoCorrect
                controller.autoCapitalize = settings.autoCapitalize
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
                } else if (ruBgChanged && controller.language.tag == "ru") {
                    loadLanguage("ru")
                }
                // Android's keyboard list names the enabled subtypes: mirror ours into it, then
                // point its current subtype at the language on the keys.
                if (settings.enabledLanguages != pushedLanguages) {
                    pushEnabledSubtypes(this@KeyboardService, settings.enabledLanguages)
                    pushedLanguages = settings.enabledLanguages
                }
                reportCurrentSubtype(this@KeyboardService, controller.language)
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

    /**
     * Never the fullscreen extract editor, which Android otherwise puts over the app on a phone in
     * landscape: the keyboard has its own landscape boards (split and 60%), and the extract view
     * would hide the field the user is typing into.
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onConfigureWindow(win: Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val params = win.attributes
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            win.attributes = params
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = win.attributes
            @Suppress("DEPRECATION")
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            win.attributes = params
        }
    }

    /** Measures the bars after every layout of the current input view; removed with that view. */
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { measureBottomBarOverlap() }

    override fun onCreateInputView(): View {
        // A rebuild replaces the view, but the lifecycle it was composed under is the service's
        // and lives on: without this the old composition keeps collecting and recomposing.
        // The listener removal only reaches the old window's observer while the old view is still
        // attached; after the framework rebuilt the window itself, that observer went with it, and
        // a listener left behind would only re-measure the current inputView anyway.
        (inputView as? ComposeView)?.let { old ->
            old.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
            old.disposeComposition()
        }
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        // Compose resolves its window recomposer from the window's root view, so the owners
        // have to be on the IME window's decor view as well as on the ComposeView itself.
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        return ComposeView(this).also { inputView = it }.apply {
            viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
            setViewTreeLifecycleOwner(this@KeyboardService)
            setViewTreeViewModelStoreOwner(this@KeyboardService)
            setViewTreeSavedStateRegistryOwner(this@KeyboardService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val settings by prefs.settings.collectAsState(initial = Settings())
                val macros by macroStore.macros.collectAsState(initial = emptyList())
                KeyboardThemeFor(settings.theme) {
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
                        sideInset = with(LocalDensity.current) { sideCutoutOverlapPx.toDp() },
                        hinge = hingePx,
                        feel = KeyboardFeel(
                            haptics = settings.haptics,
                            previews = settings.previews,
                            keyBorders = settings.keyBorders,
                            heightScale = settings.heightScale,
                            widthScale = settings.widthScale,
                            glide = settings.glide,
                            glideTrail = settings.glideTrail,
                            split = settings.splitKeyboard,
                        ),
                        macros = macros,
                    )
                }
            }
        }
    }

    /**
     * Follows the window's folds: a fold or hinge that separates the screen top to bottom is where
     * no key may sit. The IME's service is a UI context, which is what the window library reports
     * folds to; where it will not, the keyboard carries on as if there were no hinge, and the
     * diagnostics say why.
     */
    private fun watchHinge() {
        val layoutInfo = try {
            WindowInfoTracker.getOrCreate(this).windowLayoutInfo(this)
        } catch (e: RuntimeException) {
            hingeSource = "unavailable: ${e.javaClass.simpleName}"
            KeyboardDiagnostics.insets = insetReport()
            return
        }
        lifecycleScope.launch {
            layoutInfo
                .catch { e ->
                    hingeSource = "unavailable: ${e.javaClass.simpleName}"
                    KeyboardDiagnostics.insets = insetReport()
                }
                .collect { info ->
                    val fold = info.displayFeatures.filterIsInstance<FoldingFeature>()
                        .firstOrNull { it.isSeparating && it.orientation == FoldingFeature.Orientation.VERTICAL }
                    hingeInWindow = fold?.bounds
                    hingeSource = "window, ${info.displayFeatures.size} feature(s)"
                    // The source changed even when the hinge did not (none before, none now).
                    KeyboardDiagnostics.insets = insetReport()
                    updateHinge()
                }
        }
    }

    /** Re-reads the hinge against where the input view sits now; the view moves on rotation and unfolding. */
    private fun updateHinge() {
        val bounds = hingeInWindow
        val view = inputView
        val hinge = if (bounds == null || view == null) {
            null
        } else {
            val location = IntArray(2).also { view.getLocationInWindow(it) }
            hingeInView(bounds.left, bounds.right, location[0], view.width)
        }
        if (hinge != hingePx) {
            hingePx = hinge
            KeyboardDiagnostics.insets = insetReport()
        }
    }

    private fun measureBottomBarOverlap() {
        val view = inputView ?: return
        val decor = window?.window?.decorView ?: return
        // Neither laid out yet; the next pass has real numbers. A view measured at zero would
        // read as leaving the whole decor free below it, and the keys would lose their padding.
        if (decor.height == 0 || view.height == 0) return
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
        val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val spaceLeft = location[0]
        val spaceRight = decor.width - (location[0] + view.width)
        val leftCutout = sideInsetOverlap(cutout.left, spaceLeft)
        val rightCutout = sideInsetOverlap(cutout.right, spaceRight)
        val sideOverlap = maxOf(leftCutout, rightCutout)
        val measurement = "reported=$reported bar=$bar decorHeight=${decor.height} viewTop=${location[1]} viewHeight=${view.height} " +
            "spaceBelow=$spaceBelowView overlap=$overlap sideOverlap=$sideOverlap gestureNav=${gestureNavigation()} systemBarHeight=${systemNavigationBarHeightPx()}"
        if (overlap != bottomBarOverlapPx) bottomBarOverlapPx = overlap
        if (sideOverlap != sideCutoutOverlapPx) sideCutoutOverlapPx = sideOverlap
        updateHinge()
        // Layout passes are frequent; the report is only worth rebuilding when the numbers moved.
        if (measurement != lastMeasurement) {
            lastMeasurement = measurement
            KeyboardDiagnostics.insets = insetReport()
        }
    }

    /**
     * The window's bottom bar as the framework's decor paints it, in the keyboard's own colour
     * so the strip under the keys reads as part of the keyboard rather than a black band. Up to
     * Android 15, where the theme's edge-to-edge opt-out holds and the decor paints that strip.
     * From Android 16 the opt-out is ignored at this target and the colour call does nothing:
     * the keyboard draws under the bar itself, so the strip is already its own background.
     */
    private fun paintBottomBar(color: Int, light: Boolean) {
        val w = window?.window ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            @Suppress("DEPRECATION")
            w.navigationBarColor = color
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            w.isNavigationBarContrastEnforced = false
        }
        androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightNavigationBars = light
    }

    /** Every inset type the decor reports, its padding, and the last measurement, one line each. */
    private fun insetReport(): String {
        val lines = mutableListOf("measurement: $lastMeasurement auto=$autoBottomPadding manualPaddingDp=$manualBottomPaddingDp")
        lines += "computed insets: $lastComputedInsets"
        lines += hingeLine(hingePx, hingeSource)
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

    /** `adb shell dumpsys activity service net.matasar.keyboard/.ime.KeyboardService`: the build, the field and the insets. */
    override fun dump(fd: FileDescriptor, fout: PrintWriter, args: Array<String>) {
        super.dump(fd, fout, args)
        fout.println(KeyboardDiagnostics.report())
    }

    /**
     * A field got the keyboard's connection, shown or not: if it belongs to the app a login was
     * filled for, what belongs in it — the username, or the password — is typed now, once. The
     * fill screen's own form never takes either.
     */
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        // Our own screens are skipped: the report is read on the settings screen, and walking
        // there must not overwrite what the app being diagnosed reported.
        if (attribute != null && attribute.packageName != packageName) {
            KeyboardDiagnostics.field = fieldReport(attribute)
        }
        if (attribute == null || attribute.privateImeOptions == FILL_SCREEN_IME_OPTION) return
        val field = attribute.packageName?.let {
            FocusedField(FillTarget(it, attribute.fieldId), fieldKindOf(attribute.inputType), canNavigateNext(attribute.imeOptions), controller::fieldIsEmpty)
        }
        PendingFill.shared.deliverTo(field, fillTyper)
    }

    /** How a fill reaches the field: through the controller, like every other keystroke. */
    private val fillTyper = object : FillTyper {
        override fun typeUsername(text: CharSequence) = controller.typeFilledUsername(text)
        override fun typePassword(text: CharSequence) = controller.typeFilledPassword(text)
        override fun goNext() = controller.goToNextField()
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        if (!restarting) controller.onStartInput(editorInfo) else controller.updateFieldMic(editorInfo)
        currentPackage = editorInfo?.packageName
        currentFieldId = editorInfo?.fieldId ?: View.NO_ID
        // Keyboards can be enabled or disabled between fields, so the mic's target is looked up per field.
        controller.voiceAvailable = findVoiceTarget(inputMethodManager(), packageName) != null
        // The first report, from onCreate, comes before the service is attached and does nothing;
        // here it reaches Android. Only once the settings have loaded, or it would report the
        // default language and Android's echo would switch the keys to it.
        if (pushedLanguages != null) reportCurrentSubtype(this, controller.language)
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
        // What the app is told the keyboard takes, next to where the window and the keys really
        // are on screen: the report an app pushed too far up has to be diagnosed from.
        val decorOnScreen = IntArray(2).also { window?.window?.decorView?.getLocationOnScreen(it) }
        val viewOnScreen = IntArray(2).also { view.getLocationOnScreen(it) }
        val reported = "contentTop=${outInsets.contentTopInsets} visibleTop=${outInsets.visibleTopInsets} overhang=$overhang " +
            "decorScreenTop=${decorOnScreen[1]} viewScreenTop=${viewOnScreen[1]} fullscreen=$isFullscreenMode extractShown=$isExtractViewShown"
        if (reported != lastComputedInsets) {
            lastComputedInsets = reported
            KeyboardDiagnostics.insets = insetReport()
        }
    }

    /**
     * Android's switcher picked one of the keyboard's subtypes: follow it when that language is
     * enabled here, otherwise put Android back on ours. The keyboard's settings stay authoritative.
     */
    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        val picked = languageForPick(newSubtype.hashCode(), controller.enabledLanguages)
        if (picked != null) controller.switchLanguage(picked) else reportCurrentSubtype(this, controller.language)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        controller.onFinishInput()
    }

    /** The keyboard went away: a macro stops with it, whatever it was waiting for, and the strip folds. */
    override fun onWindowHidden() {
        super.onWindowHidden()
        controller.stopMacro()
        controller.onKeyboardHidden()
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
        controller.toggleManagerSheet()
    }

    override fun toggleMacroSheet() {
        controller.toggleMacroSheet()
    }

    override fun toggleSettingsSheet() {
        controller.toggleSettingsSheet()
    }

    override fun openMacros() {
        controller.macroSheetOpen = false
        runCatching { startActivity(MacrosActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun toggleDeveloperMode() {
        controller.toggleDeveloperMode()
        val pkg = currentPackage ?: return
        lifecycleScope.launch { prefs.setDeveloperMode(pkg, controller.developerMode) }
    }

    /** The gear sheet's switch: the strip follows at once, and the sheet closes as Developer mode's does. */
    override fun toggleToolbarAlwaysShown() {
        controller.foldToolbar = !controller.foldToolbar
        controller.settingsSheetOpen = false
        val fold = controller.foldToolbar
        lifecycleScope.launch { prefs.setFoldToolbar(fold) }
    }

    override fun pasteClipboard() {
        controller.paste()
    }

    /** The clipboard's first item as text, for the paste button and a macro's paste block. */
    private fun clipboardText(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
    }

    /** A macro's copy of the field; a sensitive one is hidden in Android's clipboard preview (13 and later). */
    private fun copyToClipboard(text: String, sensitive: Boolean) {
        val clip = ClipData.newPlainText("", text)
        if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    }

    /** The fill screen's own form never asks for another fill screen. */
    private fun canFillHere(): Boolean = currentInputEditorInfo?.privateImeOptions != FILL_SCREEN_IME_OPTION

    /** Opens the fill screen for the field that has the keyboard now. */
    private fun fillPassword() {
        if (!canFillHere()) return
        val target = FillTarget(currentPackage ?: return, currentFieldId)
        runCatching { startActivity(FillActivity.intent(this, target)) }
    }

    override fun openSettings() {
        controller.settingsSheetOpen = false
        startActivity(
            Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun hideKeyboard() {
        requestHideSelf(0)
    }

    /**
     * Hands dictation to the voice keyboard: it takes the field, and switches back here when it is
     * done. The keyboard itself never hears or records anything.
     */
    override fun startVoiceInput() {
        if (!controller.showVoiceKey) return
        val imm = inputMethodManager()
        val target = findVoiceTarget(imm, packageName, preferredVoiceKeyboard)
        if (target == null) {
            controller.voiceAvailable = false
            return
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                switchInputMethod(target.imeId, target.subtype)
            } else {
                val token = window.window?.attributes?.token ?: return
                @Suppress("DEPRECATION")
                imm.setInputMethodAndSubtype(token, target.imeId, target.subtype)
            }
        }.onFailure {
            // The keyboard went away since the field opened: a mic that does nothing is worse than none.
            controller.voiceAvailable = false
        }
    }

    private fun inputMethodManager() = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager

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
