package io.github.mariawarnes.languagelens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import kotlin.math.hypot

/**
 * Draws the translations over the messages. Each message gets its own small overlay window, so
 * touches anywhere else go straight through to WhatsApp / Messenger.
 *
 * Tap a translation to see the original, tap again for the translation. A drag that starts on a
 * translation is handed to [drags], which replays it on the chat so it still scrolls.
 */
class LensOverlay(private val context: Context, private val drags: DragListener) {

    /** Receives drags (screen coordinates) that should scroll the app underneath. */
    interface DragListener {
        fun dragStart(fromX: Float, fromY: Float, x: Float, y: Float)
        fun dragMove(x: Float, y: Float)
        fun dragEnd(x: Float, y: Float)
    }

    class Item(val original: String, val translation: String, val bounds: Rect)

    private class Label(val view: TextView, val params: WindowManager.LayoutParams) {
        var original = ""
        var translation = ""
    }

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val labels = ArrayList<Label>()
    private val revealed = HashSet<String>()
    private var visible = true
    private var touchable = true

    private val density = context.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

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
                update(label)
            }
        }
        setTouchable(true)
        setVisible(true)
    }

    /** While scrolling or switching apps the labels hide (they'd be out of place) and come back after. */
    fun setVisible(show: Boolean) {
        if (visible == show) return
        visible = show
        for (l in labels) l.view.alpha = if (show) 1f else 0f
    }

    /**
     * Off while a drag is being replayed, so the replayed touches reach the app, not the labels.
     * The label under the finger is left alone ([keep]): changing it mid-drag could cancel the drag.
     */
    private fun setTouchable(on: Boolean, keep: Label? = null) {
        if (touchable == on) return
        touchable = on
        for (l in labels) {
            if (l === keep) continue
            l.params.flags = if (on) {
                l.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                l.params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            update(l)
        }
    }

    fun clear() {
        for (l in labels) remove(l)
        labels.clear()
    }

    val isShowing: Boolean get() = labels.isNotEmpty()

    @SuppressLint("ClickableViewAccessibility")
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

        // Tap = flip this message; drag = scroll the chat underneath. The drag is replayed in the
        // gap beside this label (not under it, or it would land on the label itself), towards the
        // middle of the screen and away from the edges where the system back gesture lives.
        var downX = 0f
        var downY = 0f
        var dragging = false
        var replayX = 0f
        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && hypot(e.rawX - downX, e.rawY - downY) > touchSlop) {
                        dragging = true
                        setVisible(false)
                        setTouchable(false, keep = label)
                        val screen = context.resources.displayMetrics.widthPixels
                        val gapLeft = label.params.x.toFloat()
                        val gapRight = (screen - label.params.x - label.params.width).toFloat()
                        val reach = 24 * density
                        replayX = if (gapRight >= gapLeft) {
                            label.params.x + label.params.width + minOf(gapRight / 2, reach)
                        } else {
                            label.params.x - minOf(gapLeft / 2, reach)
                        }
                        drags.dragStart(replayX, downY, replayX, e.rawY)
                    } else if (dragging) {
                        drags.dragMove(replayX, e.rawY)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        drags.dragEnd(replayX, e.rawY)
                    } else {
                        if (!revealed.add(label.original)) revealed.remove(label.original)
                        style(label)
                    }
                    dragging = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) drags.dragEnd(replayX, e.rawY)
                    dragging = false
                }
            }
            true
        }
        params.flags = if (touchable) params.flags else params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        view.alpha = if (visible) 1f else 0f
        windowManager.addView(view, params)
        return label
    }

    private fun style(label: Label) {
        val showOriginal = label.original in revealed
        label.view.text = if (showOriginal) label.original else label.translation
        label.view.setTextColor(color(if (showOriginal) R.color.lens_original_text else R.color.lens_text))
        label.view.background = GradientDrawable().apply {
            cornerRadius = 8 * density
            setColor(color(if (showOriginal) R.color.lens_original_bg else R.color.lens_bg))
            if (!showOriginal) setStroke((1 * density).toInt(), color(R.color.lens_border))
        }
    }

    private fun color(id: Int) = ContextCompat.getColor(context, id)

    private fun update(label: Label) {
        try {
            windowManager.updateViewLayout(label.view, label.params)
        } catch (e: IllegalArgumentException) {
            // window already gone
        }
    }

    private fun remove(label: Label) {
        try {
            windowManager.removeView(label.view)
        } catch (e: IllegalArgumentException) {
            // already gone
        }
    }
}
