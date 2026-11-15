package io.github.mariawarnes.languagelens

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat

/**
 * Draws the translations over the messages. Each message gets its own small overlay window, so
 * touches anywhere else go straight through to WhatsApp / Messenger.
 *
 * Tap a message to see the original, tap again for the translation; long-press to do that for
 * every message on screen.
 */
class LensOverlay(private val context: Context) {

    class Item(val original: String, val translation: String, val bounds: Rect)

    private class Label(val view: TextView, val params: WindowManager.LayoutParams) {
        var original = ""
        var translation = ""
    }

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val labels = ArrayList<Label>()
    private val revealed = HashSet<String>()
    private var revealAll = false
    private var visible = true

    private val density = context.resources.displayMetrics.density

    fun show(items: List<Item>) {
        // Reuse windows where we can; add or remove the rest.
        while (labels.size > items.size) remove(labels.removeAt(labels.size - 1))
        items.forEachIndexed { i, item ->
            val label = labels.getOrNull(i) ?: create().also { labels += it }
            label.original = item.original
            label.translation = item.translation
            style(label)
            val p = label.params
            if (p.x != item.bounds.left || p.y != item.bounds.top ||
                p.width != item.bounds.width() || p.height != item.bounds.height()
            ) {
                p.x = item.bounds.left
                p.y = item.bounds.top
                p.width = item.bounds.width()
                p.height = item.bounds.height()
                windowManager.updateViewLayout(label.view, p)
            }
        }
        setVisible(true)
    }

    /** While scrolling, the labels hide (they'd lag behind) and come back when it stops. */
    fun setVisible(show: Boolean) {
        if (visible == show) return
        visible = show
        for (l in labels) l.view.visibility = if (show) View.VISIBLE else View.INVISIBLE
    }

    fun clear() {
        for (l in labels) remove(l)
        labels.clear()
    }

    val isShowing: Boolean get() = labels.isNotEmpty()

    private fun create(): Label {
        val view = TextView(context).apply {
            val pad = (4 * density).toInt()
            setPadding(pad * 2, pad, pad * 2, pad)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 9, 17, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        val params = WindowManager.LayoutParams(
            0, 0,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        val label = Label(view, params)
        view.setOnClickListener {
            if (!revealed.add(label.original)) revealed.remove(label.original)
            style(label)
        }
        view.setOnLongClickListener {
            revealAll = !revealAll
            revealed.clear()
            labels.forEach { style(it) }
            true
        }
        windowManager.addView(view, params)
        return label
    }

    private fun style(label: Label) {
        val showOriginal = revealAll != (label.original in revealed)
        label.view.text = if (showOriginal) label.original else label.translation
        label.view.setTextColor(color(if (showOriginal) R.color.lens_original_text else R.color.lens_text))
        label.view.background = GradientDrawable().apply {
            cornerRadius = 8 * density
            setColor(color(if (showOriginal) R.color.lens_original_bg else R.color.lens_bg))
            if (!showOriginal) setStroke((1 * density).toInt(), color(R.color.lens_border))
        }
    }

    private fun color(id: Int) = ContextCompat.getColor(context, id)

    private fun remove(label: Label) {
        try {
            windowManager.removeView(label.view)
        } catch (e: IllegalArgumentException) {
            // already gone
        }
    }
}
