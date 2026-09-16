package net.matasar.keyboard.autofill

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Icon
import net.matasar.keyboard.R
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.common.TextViewStyle
import androidx.autofill.inline.common.ViewStyle
import androidx.autofill.inline.v1.InlineSuggestionUi
import java.util.concurrent.Executor

/** One suggestion the password manager sent, inflated into a view it draws itself. */
class SuggestionEntry(val view: View, val pinned: Boolean)

/** The chip sizes the keyboard asks for, in pixels. Pure so the request maths is testable. */
data class InlineSizes(val minWidth: Int, val maxWidth: Int, val height: Int)

/** Chips are 32 dp tall, at least 48 dp wide and at most 60% of the screen. */
fun inlineSizes(screenWidthPx: Int, density: Float): InlineSizes = InlineSizes(
    minWidth = (48 * density).toInt(),
    maxWidth = (screenWidthPx * 0.6f).toInt().coerceAtLeast((48 * density).toInt()),
    height = (32 * density).toInt(),
)

/**
 * Android 11 inline autofill: the keyboard describes the chips it can host, the manager sends
 * suggestions, and each one inflates into a view the manager renders. The keyboard never sees
 * what the chips contain.
 */
@RequiresApi(Build.VERSION_CODES.R)
object InlineSuggestions {

    const val MAX_SUGGESTIONS = 6

    /**
     * Builds the request the system forwards to the autofill service.
     *
     * RestrictedApi is suppressed on purpose: ViewStyle.Builder's setPadding/setBackground are
     * public, but lint resolves them to the library-restricted BaseBuilder they override. The
     * Android autofill sample carries the same suppression.
     */
    @SuppressLint("RestrictedApi")
    fun createRequest(context: Context, colors: SuggestionColors): InlineSuggestionsRequest {
        val metrics = context.resources.displayMetrics
        val sizes = inlineSizes(metrics.widthPixels, metrics.density)
        val padding = (10 * metrics.density).toInt()
        // The chip background is a drawable (with a night variant): the public style API takes
        // an Icon, and the manager's process renders it, so it cannot follow dynamic colour.
        val style = InlineSuggestionUi.newStyleBuilder()
            .setChipStyle(
                ViewStyle.Builder()
                    .setPadding(padding, 0, padding, 0)
                    .setBackground(Icon.createWithResource(context, R.drawable.bg_suggestion_chip))
                    .build(),
            )
            .setTitleStyle(TextViewStyle.Builder().setTextColor(colors.chipText).setTextSize(13f).build())
            .setSubtitleStyle(TextViewStyle.Builder().setTextColor(colors.chipSubtext).setTextSize(11f).build())
            .build()
        val styles: Bundle = UiVersions.newStylesBuilder().addStyle(style).build()
        val spec = InlinePresentationSpec.Builder(
            Size(sizes.minWidth, sizes.height),
            Size(sizes.maxWidth, sizes.height),
        ).setStyle(styles).build()
        return InlineSuggestionsRequest.Builder(listOf(spec))
            .setMaxSuggestionCount(MAX_SUGGESTIONS)
            .build()
    }

    /**
     * Inflates every suggestion; [onReady] gets the list once all views exist, pinned entries
     * first, so the manager's own icon sits at a fixed spot.
     */
    fun inflate(
        context: Context,
        response: InlineSuggestionsResponse,
        executor: Executor,
        onReady: (List<SuggestionEntry>) -> Unit,
    ) {
        val suggestions: List<InlineSuggestion> = response.inlineSuggestions
        if (suggestions.isEmpty()) {
            onReady(emptyList())
            return
        }
        val height = inlineSizes(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.density).height
        val results = arrayOfNulls<SuggestionEntry>(suggestions.size)
        var remaining = suggestions.size
        suggestions.forEachIndexed { index, suggestion ->
            suggestion.inflate(context, Size(ViewGroup.LayoutParams.WRAP_CONTENT, height), executor) { view ->
                if (view != null) results[index] = SuggestionEntry(view, suggestion.info.isPinned)
                remaining--
                if (remaining == 0) {
                    onReady(results.filterNotNull().sortedByDescending { it.pinned })
                }
            }
        }
    }
}

/** The colours the chips are styled with, as ARGB ints. */
data class SuggestionColors(val chipBackground: Int, val chipText: Int, val chipSubtext: Int)
