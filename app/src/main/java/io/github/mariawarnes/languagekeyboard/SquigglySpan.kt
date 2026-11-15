package io.github.mariawarnes.languagekeyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.style.ReplacementSpan
import kotlin.math.ceil

/**
 * Draws its text normally with a wavy underline, like a word processor's spelling/grammar marks.
 * [highlight] (if set) fills the background, for the mistake that's currently selected.
 */
class SquigglySpan(
    private val color: Int,
    private val highlight: Int?,
    private val density: Float,
) : ReplacementSpan() {

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.3f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = this@SquigglySpan.color
    }
    private val path = Path()

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (fm != null) paint.getFontMetricsInt(fm)
        return ceil(paint.measureText(text, start, end)).toInt()
    }

    override fun draw(
        canvas: Canvas, text: CharSequence, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint,
    ) {
        val width = paint.measureText(text, start, end)
        if (highlight != null) {
            val old = paint.color
            paint.color = highlight
            canvas.drawRect(x, top.toFloat(), x + width, bottom.toFloat(), paint)
            paint.color = old
        }
        canvas.drawText(text, start, end, x, y.toFloat(), paint)

        // Zig-zag just under the baseline: a point every 2dp, alternating up and down.
        val amplitude = 1.4f * density
        val step = 2f * density
        val baseline = minOf(y + paint.descent() * 0.6f, bottom - amplitude - wavePaint.strokeWidth)
        path.rewind()
        path.moveTo(x, baseline)
        var px = x
        var up = true
        while (px < x + width) {
            px = minOf(px + step, x + width)
            path.lineTo(px, if (up) baseline - amplitude else baseline + amplitude)
            up = !up
        }
        canvas.drawPath(path, wavePaint)
    }
}
