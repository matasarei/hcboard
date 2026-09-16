package net.matasar.keyboard.ime

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
class KeyboardService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, ToolbarActions {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val controller = KeyboardController(InputDispatcher(AndroidEditorPort { currentInputConnection }))
    private lateinit var prefs: Prefs
    private var inputView: View? = null
    private var currentPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(applicationContext)
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleScope.launch {
            prefs.settings.collect { settings ->
                controller.editingShortcutsInTextFields = settings.editingShortcuts
                controller.doubleTapLock = settings.doubleTapLock
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
            setViewTreeLifecycleOwner(this@KeyboardService)
            setViewTreeViewModelStoreOwner(this@KeyboardService)
            setViewTreeSavedStateRegistryOwner(this@KeyboardService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val settings by prefs.settings.collectAsState(initial = Settings())
                KeyboardTheme(darkTheme = settings.theme.asDarkTheme()) {
                    KeyboardScreen(
                        controller = controller,
                        actions = this@KeyboardService,
                        feel = KeyboardFeel(
                            haptics = settings.haptics,
                            previews = settings.previews,
                            keyBorders = settings.keyBorders,
                            heightScale = settings.heightScale,
                        ),
                    )
                }
            }
        }
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

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }

    // ---- toolbar ----

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
}
