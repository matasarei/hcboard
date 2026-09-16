package net.matasar.keyboard.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import net.matasar.keyboard.input.AndroidEditorPort
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.ui.KeyboardScreen
import net.matasar.keyboard.ui.PopupMetrics
import net.matasar.keyboard.ui.theme.KeyboardTheme

/**
 * The input method. Hosts the Compose keyboard inside the IME window.
 *
 * A [ComposeView] needs a lifecycle, a view-model store and a saved-state registry on its view
 * tree; a service has none of those, so this class owns all three and drives the lifecycle from
 * the IME callbacks: created in [onCreate], started while an input view exists, resumed while it
 * is shown, destroyed in [onDestroy].
 */
class KeyboardService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val controller = KeyboardController(InputDispatcher(AndroidEditorPort { currentInputConnection }))
    private var inputView: View? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        return ComposeView(this).also { inputView = it }.apply {
            setViewTreeLifecycleOwner(this@KeyboardService)
            setViewTreeViewModelStoreOwner(this@KeyboardService)
            setViewTreeSavedStateRegistryOwner(this@KeyboardService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                KeyboardTheme {
                    KeyboardScreen(controller)
                }
            }
        }
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        controller.onStartInput(editorInfo)
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
}
