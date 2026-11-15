package io.github.mariawarnes.languagekeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.abs

/**
 * A simple drawn keyboard: taps, sliding between keys, long-press for accents (slide to pick,
 * lift to type), key repeat for delete, and a fast second-finger tap for two-thumb typing.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        fun onText(text: String)
        fun onKey(code: Int)
        fun onSpaceLongPress()
    }

    var listener: Listener? = null

    var rows: List<List<Key>> = KeyboardLayouts.LETTERS
        set(value) {
            if (field === value) return
            field = value
            resetTouch()
            placeKeys()
            requestLayout()
            invalidate()
        }

    var shiftState: ShiftState = ShiftState.OFF
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var enterLabel: String = "⏎"
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Shown in small text on the space bar, e.g. "Español → English". */
    var spaceLabel: String = ""
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val isShifted get() = shiftState != ShiftState.OFF

    // --- Dimensions -------------------------------------------------------------------------

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val rowHeight = dp(54f)
    private val padTop = dp(4f)
    private val padBottom = dp(8f)
    private val padSide = dp(3f)
    private val gapX = dp(3f)
    private val gapY = dp(5f)
    private val corner = dp(7f)

    // --- Paints -----------------------------------------------------------------------------

    private fun color(id: Int) = ContextCompat.getColor(context, id)

    private val colorKey = color(R.color.kb_key)
    private val colorKeyPressed = color(R.color.kb_key_pressed)
    private val colorFunction = color(R.color.kb_key_function)
    private val colorAccent = color(R.color.kb_key_accent)
    private val colorText = color(R.color.kb_key_text)
    private val colorTextOnAccent = color(R.color.kb_key_text_on_accent)
    private val colorHint = color(R.color.kb_key_hint)
    private val colorPopup = color(R.color.kb_popup)
    private val colorPopupSelected = color(R.color.kb_popup_selected)

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = sp(10f)
        color = colorHint
    }

    // --- Layout -----------------------------------------------------------------------------

    private class PlacedKey(val key: Key, val row: Int, val cell: RectF, val face: RectF)

    private val placed = ArrayList<PlacedKey>()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (padTop + rows.size * rowHeight + padBottom).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        placeKeys()
    }

    private fun placeKeys() {
        placed.clear()
        if (width == 0) return
        val available = width - 2 * padSide
        rows.forEachIndexed { r, row ->
            val unit = available / row.sumOf { it.width.toDouble() }.toFloat()
            var x = padSide
            val top = padTop + r * rowHeight
            for (key in row) {
                val w = key.width * unit
                val cell = RectF(x, top, x + w, top + rowHeight)
                val face = RectF(cell.left + gapX / 2, cell.top + gapY / 2, cell.right - gapX / 2, cell.bottom - gapY / 2)
                if (key.style != KeyStyle.SPACER) placed += PlacedKey(key, r, cell, face)
                x += w
            }
        }
    }

    private fun keyAt(x: Float, y: Float): PlacedKey? {
        if (placed.isEmpty()) return null
        val row = ((y - padTop) / rowHeight).toInt().coerceIn(0, rows.size - 1)
        val inRow = placed.filter { it.row == row }
        inRow.firstOrNull { x >= it.cell.left && x < it.cell.right }?.let { return it }
        // In a spacer or the side padding: pick the nearest key in the row.
        return inRow.minByOrNull { abs(it.cell.centerX() - x) }
    }

    // --- Drawing ----------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        for (pk in placed) drawKey(canvas, pk)
        popup?.let { drawPopup(canvas, it) }
    }

    private fun displayLabel(key: Key): String = when (key.code) {
        Codes.SHIFT -> if (shiftState == ShiftState.LOCKED) "⇪" else "⇧"
        Codes.ENTER -> enterLabel
        Codes.SPACE -> spaceLabel
        else -> if (isShifted && key.isLetter) key.label.uppercase() else key.label
    }

    private fun drawKey(canvas: Canvas, pk: PlacedKey) {
        val key = pk.key
        val pressed = pressedKey === pk && popup == null
        val shiftActive = key.code == Codes.SHIFT && shiftState != ShiftState.OFF

        val fill = when {
            pressed -> colorKeyPressed
            key.style == KeyStyle.ACCENT || shiftActive -> colorAccent
            key.style == KeyStyle.FUNCTION -> colorFunction
            else -> colorKey
        }
        val face = pk.face
        canvas.drawRoundRect(face.left, face.top + dp(1f), face.right, face.bottom + dp(1f), corner, corner, shadowPaint)
        keyPaint.color = fill
        canvas.drawRoundRect(face, corner, corner, keyPaint)

        val label = displayLabel(key)
        textPaint.color = if (!pressed && (key.style == KeyStyle.ACCENT || shiftActive)) colorTextOnAccent else colorText
        textPaint.textSize = when {
            key.code == Codes.SPACE -> sp(12f)
            label.length == 1 && key.style == KeyStyle.NORMAL -> sp(22f)
            label.length <= 2 -> sp(20f)
            else -> sp(15f)
        }
        if (key.code == Codes.SPACE) textPaint.color = colorHint
        val baseline = face.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, face.centerX(), baseline, textPaint)

        key.hint?.let {
            canvas.drawText(it, face.right - dp(5f), face.top + hintPaint.textSize + dp(2f), hintPaint)
        }
    }

    // --- Long-press popup -------------------------------------------------------------------

    private class Popup(val options: List<String>, val bounds: RectF, val cellWidth: Float) {
        var selected = 0
    }

    private var popup: Popup? = null

    private fun showPopup(pk: PlacedKey) {
        val options = pk.key.alternates.map { if (isShifted && it.length == 1 && it[0].isLetter()) it.uppercase() else it }
        val cellWidth = maxOf(pk.face.width(), dp(36f))
        val total = cellWidth * options.size
        var left = pk.face.centerX() - cellWidth / 2
        left = left.coerceAtMost(width - padSide - total).coerceAtLeast(padSide)
        val top = (pk.face.top - rowHeight).coerceAtLeast(0f)
        popup = Popup(options, RectF(left, top, left + total, top + rowHeight - gapY), cellWidth).also {
            it.selected = (((pk.face.centerX() - left) / cellWidth).toInt()).coerceIn(0, options.size - 1)
        }
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
    }

    private fun updatePopupSelection(x: Float) {
        val p = popup ?: return
        val index = ((x - p.bounds.left) / p.cellWidth).toInt().coerceIn(0, p.options.size - 1)
        if (index != p.selected) {
            p.selected = index
            invalidate()
        }
    }

    private fun drawPopup(canvas: Canvas, p: Popup) {
        val b = p.bounds
        canvas.drawRoundRect(b.left, b.top + dp(2f), b.right, b.bottom + dp(2f), corner, corner, shadowPaint)
        keyPaint.color = colorPopup
        canvas.drawRoundRect(b, corner, corner, keyPaint)
        textPaint.textSize = sp(22f)
        p.options.forEachIndexed { i, option ->
            val cellLeft = b.left + i * p.cellWidth
            if (i == p.selected) {
                keyPaint.color = colorPopupSelected
                canvas.drawRoundRect(cellLeft + dp(2f), b.top + dp(2f), cellLeft + p.cellWidth - dp(2f), b.bottom - dp(2f), corner, corner, keyPaint)
            }
            textPaint.color = if (i == p.selected) colorTextOnAccent else colorText
            val baseline = b.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(option, cellLeft + p.cellWidth / 2, baseline, textPaint)
        }
    }

    // --- Touch handling ---------------------------------------------------------------------

    private val keyHandler = Handler(Looper.getMainLooper())
    private var pressedKey: PlacedKey? = null
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var longPressFired = false
    private var repeating = false

    private val longPressRunnable = Runnable {
        val pk = pressedKey ?: return@Runnable
        when {
            pk.key.code == Codes.SPACE -> {
                longPressFired = true
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                listener?.onSpaceLongPress()
                invalidate()
            }
            pk.key.alternates.isNotEmpty() -> {
                longPressFired = true
                showPopup(pk)
            }
        }
    }

    private val repeatRunnable = object : Runnable {
        override fun run() {
            val pk = pressedKey ?: return
            repeating = true
            fire(pk.key)
            keyHandler.postDelayed(this, REPEAT_INTERVAL_MS)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                press(event.x, event.y)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // A second finger landed: finish the first key straight away (two-thumb typing).
                if (popup == null) {
                    release(commit = true)
                    val index = event.actionIndex
                    activePointerId = event.getPointerId(index)
                    press(event.getX(index), event.getY(index))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index >= 0) move(event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    release(commit = true)
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                }
            }
            MotionEvent.ACTION_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) release(commit = true)
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
            MotionEvent.ACTION_CANCEL -> {
                release(commit = false)
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        return true
    }

    private fun press(x: Float, y: Float) {
        val pk = keyAt(x, y) ?: return
        pressedKey = pk
        longPressFired = false
        repeating = false
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (pk.key.repeatable) {
            fire(pk.key)
            repeating = true
            keyHandler.postDelayed(repeatRunnable, REPEAT_DELAY_MS)
        } else {
            keyHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
        }
        invalidate()
    }

    private fun move(x: Float, y: Float) {
        if (popup != null) {
            updatePopupSelection(x)
            return
        }
        if (repeating || longPressFired) return
        val pk = keyAt(x, y) ?: return
        if (pk !== pressedKey) {
            // Slid onto a neighbouring key: that's the one that gets typed.
            keyHandler.removeCallbacks(longPressRunnable)
            pressedKey = pk
            if (!pk.key.repeatable) keyHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
            invalidate()
        }
    }

    private fun release(commit: Boolean) {
        keyHandler.removeCallbacks(longPressRunnable)
        keyHandler.removeCallbacks(repeatRunnable)
        val pk = pressedKey
        val p = popup
        if (commit && pk != null) {
            when {
                p != null -> listener?.onText(p.options[p.selected])
                repeating || longPressFired -> Unit // already handled
                else -> fire(pk.key)
            }
        }
        resetTouch()
    }

    private fun resetTouch() {
        keyHandler.removeCallbacks(longPressRunnable)
        keyHandler.removeCallbacks(repeatRunnable)
        pressedKey = null
        popup = null
        longPressFired = false
        repeating = false
        invalidate()
    }

    private fun fire(key: Key) {
        val text = key.text
        if (text != null) {
            listener?.onText(if (isShifted && key.isLetter) text.uppercase() else text)
        } else if (key.code != Codes.NONE) {
            listener?.onKey(key.code)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        resetTouch()
    }

    private companion object {
        const val LONG_PRESS_MS = 350L
        const val REPEAT_DELAY_MS = 400L
        const val REPEAT_INTERVAL_MS = 50L
    }
}
