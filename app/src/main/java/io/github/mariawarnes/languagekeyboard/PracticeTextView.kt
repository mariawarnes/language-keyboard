package io.github.mariawarnes.languagekeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.widget.TextView

/**
 * The practice bar's text. Draws a thin cursor line and word-processor style squiggly underlines
 * on top of the text, so neither takes up any space between the letters.
 */
@SuppressLint("AppCompatCustomView") // lives in the keyboard window, which has no AppCompat theme
class PracticeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextView(context, attrs) {

    class Mark(val start: Int, val end: Int, val color: Int, val highlighted: Boolean)

    /** Character offset to draw the cursor before, or -1 for none. */
    var cursorOffset: Int = -1
        set(value) {
            field = value
            invalidate()
        }

    var marks: List<Mark> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var cursorColor: Int = 0
    var markHighlightColor: Int = 0

    private val density = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.3f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val wave = Path()

    private fun x(layout: android.text.Layout, offset: Int) =
        layout.getPrimaryHorizontal(offset.coerceIn(0, layout.text.length))

    override fun onDraw(canvas: Canvas) {
        val layout = layout
        if (layout == null || layout.lineCount == 0) {
            super.onDraw(canvas)
            return
        }
        val dx = totalPaddingLeft.toFloat()
        val dy = totalPaddingTop.toFloat() // includes the centre-vertical offset
        val lineTop = layout.getLineTop(0) + dy
        val lineBottom = layout.getLineBottom(0) + dy

        // Behind the text: highlight the selected mistake.
        for (m in marks) {
            if (!m.highlighted) continue
            fillPaint.color = markHighlightColor
            canvas.drawRect(dx + x(layout, m.start), lineTop, dx + x(layout, m.end), lineBottom, fillPaint)
        }

        super.onDraw(canvas)

        // Squiggly underlines: a zig-zag point every 2dp just under the baseline.
        val amplitude = 1.4f * density
        val step = 2f * density
        val baseline = layout.getLineBaseline(0) + dy + paint.descent() * 0.6f
        for (m in marks) {
            val left = dx + x(layout, m.start)
            val right = dx + x(layout, m.end)
            if (right <= left) continue
            wave.rewind()
            wave.moveTo(left, baseline)
            var px = left
            var up = true
            while (px < right) {
                px = minOf(px + step, right)
                wave.lineTo(px, if (up) baseline - amplitude else baseline + amplitude)
                up = !up
            }
            wavePaint.color = m.color
            canvas.drawPath(wave, wavePaint)
        }

        // The cursor: a thin line between two letters.
        if (cursorOffset >= 0) {
            val cx = dx + x(layout, cursorOffset)
            fillPaint.color = cursorColor
            val half = density
            canvas.drawRect(cx - half, lineTop + 3 * density, cx + half, lineBottom - 3 * density, fillPaint)
        }
    }
}
