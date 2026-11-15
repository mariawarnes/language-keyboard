package io.github.mariawarnes.languagekeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

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
        /** A swipe across the letters (gesture typing), as the finger's x/y positions. */
        fun onGesture(xs: FloatArray, ys: FloatArray)

        /**
         * Sliding along the space bar: put the cursor at [fraction] of the way through the text
         * (0 = left edge of the keyboard = start, 1 = right edge = end). Returns true if it moved.
         */
        fun onCursorSlide(fraction: Float): Boolean
    }

    /** Swiping from a letter key types a word instead of sliding between keys. */
    var gestureEnabled = false

    var listener: Listener? = null

    var rows: List<List<Key>> = KeyboardLayouts.forMode(KeyboardMode.LETTERS, "en")
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

    /** Language used for capitals, so Turkish i becomes İ rather than I. */
    var caseLocale: Locale = Locale.ROOT

    private val isShifted get() = shiftState != ShiftState.OFF

    private fun cased(s: String) = if (isShifted) s.uppercase(caseLocale) else s

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
    private var unitWidth = 0f

    /** Centres of the letter keys and the key width, for decoding gestures. */
    fun keyMap(): GestureDecoder.KeyMap {
        val centers = HashMap<Char, FloatArray>()
        for (pk in placed) {
            if (pk.key.isLetter) centers[pk.key.text!!.lowercase(caseLocale)[0]] = floatArrayOf(pk.face.centerX(), pk.face.centerY())
        }
        return GestureDecoder.KeyMap(centers, unitWidth)
    }

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
        // Every key in a layout has the same unit width; shorter rows are centred.
        val unit = available / rows.maxOf { row -> row.sumOf { it.width.toDouble() } }.toFloat()
        unitWidth = unit
        rows.forEachIndexed { r, row ->
            val rowWidth = row.sumOf { it.width.toDouble() }.toFloat() * unit
            var x = padSide + (available - rowWidth) / 2
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
        if (gesturing && gestureXs.size > 1) {
            trail.rewind()
            trail.moveTo(gestureXs[0], gestureYs[0])
            for (i in 1 until gestureXs.size) trail.lineTo(gestureXs[i], gestureYs[i])
            canvas.drawPath(trail, trailPaint)
        }
    }

    private val trail = Path()
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(6f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = colorAccent
        alpha = 170
    }

    private fun displayLabel(key: Key): String = when (key.code) {
        Codes.SHIFT -> if (shiftState == ShiftState.LOCKED) "⇪" else "⇧"
        Codes.ENTER -> enterLabel
        Codes.SPACE -> spaceLabel
        else -> if (key.isLetter) cased(key.label) else key.label
    }

    private fun drawKey(canvas: Canvas, pk: PlacedKey) {
        val key = pk.key
        val pressed = pressedKey === pk && popup == null && !gesturing
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

    /**
     * Options laid out in rows of up to [cols], growing upwards from just above the key:
     * row 0 (nearest your finger) holds the first options.
     */
    private class Popup(
        val options: List<String>,
        val bounds: RectF,
        val cellWidth: Float,
        val cellHeight: Float,
        val cols: Int,
    ) {
        var selected = 0
        val rowCount get() = (options.size + cols - 1) / cols
    }

    private var popup: Popup? = null

    private fun showPopup(pk: PlacedKey) {
        val options = pk.key.alternates.map { if (it.length == 1 && it[0].isLetter()) cased(it) else it }
        val cellWidth = maxOf(pk.face.width(), dp(36f))
        val cellHeight = rowHeight - gapY
        val maxCols = floor((width - 2 * padSide) / cellWidth).toInt().coerceIn(1, 8)
        val cols = minOf(options.size, maxCols)
        val rowCount = ceil(options.size / cols.toFloat()).toInt()
        val totalWidth = cellWidth * cols
        val totalHeight = cellHeight * rowCount

        var left = pk.face.centerX() - cellWidth / 2
        left = left.coerceAtMost(width - padSide - totalWidth).coerceAtLeast(padSide)
        val top = (pk.face.top - totalHeight).coerceAtLeast(0f)
        popup = Popup(options, RectF(left, top, left + totalWidth, top + totalHeight), cellWidth, cellHeight, cols).also {
            it.selected = ((pk.face.centerX() - left) / cellWidth).toInt().coerceIn(0, cols - 1)
        }
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
    }

    private fun updatePopupSelection(x: Float, y: Float) {
        val p = popup ?: return
        val col = ((x - p.bounds.left) / p.cellWidth).toInt().coerceIn(0, p.cols - 1)
        val row = ((p.bounds.bottom - y) / p.cellHeight).toInt().coerceIn(0, p.rowCount - 1)
        val index = (row * p.cols + col).coerceAtMost(p.options.size - 1)
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
            val cellLeft = b.left + (i % p.cols) * p.cellWidth
            val cellBottom = b.bottom - (i / p.cols) * p.cellHeight
            val cellTop = cellBottom - p.cellHeight
            if (i == p.selected) {
                keyPaint.color = colorPopupSelected
                canvas.drawRoundRect(
                    cellLeft + dp(2f), cellTop + dp(2f), cellLeft + p.cellWidth - dp(2f), cellBottom - dp(2f),
                    corner, corner, keyPaint,
                )
            }
            textPaint.color = if (i == p.selected) colorTextOnAccent else colorText
            val baseline = (cellTop + cellBottom) / 2 - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(option, cellLeft + p.cellWidth / 2, baseline, textPaint)
        }
    }

    // --- Touch handling ---------------------------------------------------------------------

    private val keyHandler = Handler(Looper.getMainLooper())
    private var pressedKey: PlacedKey? = null
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var longPressFired = false
    private var repeating = false

    // Gesture typing
    private var downX = 0f
    private var downY = 0f
    private var gesturing = false
    private val gestureXs = ArrayList<Float>()
    private val gestureYs = ArrayList<Float>()

    // Space-bar cursor control
    private var cursorSliding = false

    private val longPressRunnable = Runnable {
        val pk = pressedKey ?: return@Runnable
        when {
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
                if (popup == null && !gesturing) {
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
        downX = x
        downY = y
        gesturing = false
        gestureXs.clear(); gestureYs.clear()
        gestureXs += x; gestureYs += y
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
            updatePopupSelection(x, y)
            return
        }
        if (pressedKey?.key?.code == Codes.SPACE && !longPressFired) {
            slideOnSpace(x)
            return
        }
        if (repeating || longPressFired) return
        if (gesturing) {
            val lastX = gestureXs.last()
            val lastY = gestureYs.last()
            if (hypot(x - lastX, y - lastY) >= dp(3f)) {
                gestureXs += x; gestureYs += y
                invalidate()
            }
            return
        }
        if (gestureEnabled && pressedKey?.key?.isLetter == true && hypot(x - downX, y - downY) > unitWidth * 0.75f) {
            // Moved well away from the starting letter: this is a swipe, not a tap.
            keyHandler.removeCallbacks(longPressRunnable)
            gesturing = true
            gestureXs += x; gestureYs += y
            invalidate()
            return
        }
        val pk = keyAt(x, y) ?: return
        if (pk !== pressedKey) {
            // Slid onto a neighbouring key: that's the one that gets typed.
            keyHandler.removeCallbacks(longPressRunnable)
            pressedKey = pk
            if (!pk.key.repeatable) keyHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
            invalidate()
        }
    }

    /**
     * Slide along the space bar to place the cursor: the finger's position across the whole
     * keyboard maps straight onto the text, left edge = start, right edge = end.
     */
    private fun slideOnSpace(x: Float) {
        if (!cursorSliding) {
            if (kotlin.math.abs(x - downX) < unitWidth * 0.5f) return // a tap types a space
            cursorSliding = true
            keyHandler.removeCallbacks(longPressRunnable)
            invalidate()
        }
        // A small margin at each edge so the very start and end are easy to reach.
        val margin = unitWidth * 0.5f
        val fraction = ((x - margin) / (width - 2 * margin)).coerceIn(0f, 1f)
        if (listener?.onCursorSlide(fraction) == true) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun release(commit: Boolean) {
        if (cursorSliding) {
            resetTouch() // the slide moved the cursor; don't type a space
            return
        }
        keyHandler.removeCallbacks(longPressRunnable)
        keyHandler.removeCallbacks(repeatRunnable)
        if (gesturing) {
            if (commit && gestureXs.size > 1) listener?.onGesture(gestureXs.toFloatArray(), gestureYs.toFloatArray())
            resetTouch()
            return
        }
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
        gesturing = false
        gestureXs.clear(); gestureYs.clear()
        cursorSliding = false
        invalidate()
    }

    private fun fire(key: Key) {
        val text = key.text
        if (text != null) {
            listener?.onText(if (key.isLetter) cased(text) else text)
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
