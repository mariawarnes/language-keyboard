package io.github.mariawarnes.languagekeyboard

import kotlin.math.hypot
import kotlin.math.ln

/**
 * Turns a swipe across the keyboard into likely words.
 *
 * For each dictionary word that starts near where the finger went down and ends near where it
 * lifted, we draw the word's "ideal" path through its key centres, check the finger passed close
 * to every letter in order, and compare the two paths' shapes. Common words get a small bonus.
 */
object GestureDecoder {

    private const val SAMPLES = 48

    class KeyMap(val centers: Map<Char, FloatArray>, val keyWidth: Float)

    fun decode(xs: FloatArray, ys: FloatArray, keys: KeyMap, dict: Dictionary, limit: Int): List<String> {
        if (xs.size < 2 || keys.centers.isEmpty()) return emptyList()
        val kw = keys.keyWidth
        val (px, py) = resample(xs, ys, SAMPLES)

        val startKeys = nearKeys(xs.first(), ys.first(), keys, kw * 1.3f)
        val endKeys = nearKeys(xs.last(), ys.last(), keys, kw * 1.3f)
        if (startKeys.isEmpty() || endKeys.isEmpty()) return emptyList()

        val baseCache = HashMap<Char, Char?>()
        fun keyFor(c: Char): Char? = when {
            keys.centers.containsKey(c) -> c
            else -> baseCache.getOrPut(c) {
                Dictionary.fold(c.toString(), dict.locale).firstOrNull()?.takeIf { keys.centers.containsKey(it) }
            }
        }

        class Scored(val rank: Int, val cost: Double)
        val best = ArrayList<Scored>(limit + 1)
        val threshold = kw * 1.1f

        for (rank in 0 until dict.size) {
            val word = dict.word(rank).lowercase(dict.locale)
            // Apostrophes and hyphens aren't swiped through.
            val first = word.firstOrNull { it.isLetter() } ?: continue
            val last = word.lastOrNull { it.isLetter() } ?: continue
            val firstKey = keyFor(first) ?: continue
            val lastKey = keyFor(last) ?: continue
            if (firstKey !in startKeys || lastKey !in endKeys) continue

            // The word's key sequence, without repeats ("hello" swipes h-e-l-o).
            val seq = ArrayList<FloatArray>(word.length)
            var ok = true
            var prev: Char? = null
            for (c in word) {
                if (!c.isLetter()) continue
                val k = keyFor(c)
                if (k == null) { ok = false; break }
                if (k != prev) seq += keys.centers.getValue(k)
                prev = k
            }
            if (!ok || seq.isEmpty()) continue
            if (!passesInOrder(seq, px, py, threshold)) continue

            val ix = FloatArray(seq.size) { seq[it][0] }
            val iy = FloatArray(seq.size) { seq[it][1] }
            val (qx, qy) = resample(ix, iy, SAMPLES)
            var total = 0.0
            for (i in 0 until SAMPLES) total += hypot((px[i] - qx[i]).toDouble(), (py[i] - qy[i]).toDouble())
            val cost = total / SAMPLES / kw + 0.08 * ln(rank + 1.0) + if (seq.size == 1) 0.3 else 0.0

            if (best.size == limit && cost >= best.last().cost) continue
            val at = best.indexOfFirst { it.cost > cost }.let { if (it < 0) best.size else it }
            best.add(at, Scored(rank, cost))
            if (best.size > limit) best.removeAt(best.size - 1)
        }
        return best.map { dict.word(it.rank) }
    }

    private fun nearKeys(x: Float, y: Float, keys: KeyMap, radius: Float): Set<Char> =
        keys.centers.filterValues { hypot(it[0] - x, it[1] - y) <= radius }.keys

    /** Every key in [seq] must be passed within [threshold], in order. */
    private fun passesInOrder(seq: List<FloatArray>, px: FloatArray, py: FloatArray, threshold: Float): Boolean {
        var j = 0
        for (center in seq) {
            var k = j
            while (k < px.size && hypot(px[k] - center[0], py[k] - center[1]) > threshold) k++
            if (k == px.size) return false
            // Move on to the closest point of this pass by the key.
            while (k + 1 < px.size &&
                hypot(px[k + 1] - center[0], py[k + 1] - center[1]) <= hypot(px[k] - center[0], py[k] - center[1])
            ) k++
            j = k
        }
        return true
    }

    /** [n] points evenly spaced along the polyline (all at the same spot if it has no length). */
    private fun resample(xs: FloatArray, ys: FloatArray, n: Int): Pair<FloatArray, FloatArray> {
        val ox = FloatArray(n)
        val oy = FloatArray(n)
        var length = 0f
        for (i in 1 until xs.size) length += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        if (xs.size == 1 || length == 0f) {
            ox.fill(xs[0]); oy.fill(ys[0])
            return ox to oy
        }
        val step = length / (n - 1)
        ox[0] = xs[0]; oy[0] = ys[0]
        var out = 1
        var acc = 0f
        var i = 1
        var x0 = xs[0]
        var y0 = ys[0]
        while (i < xs.size && out < n) {
            val seg = hypot(xs[i] - x0, ys[i] - y0)
            if (acc + seg >= step && seg > 0f) {
                val t = (step - acc) / seg
                x0 += t * (xs[i] - x0)
                y0 += t * (ys[i] - y0)
                ox[out] = x0; oy[out] = y0
                out++
                acc = 0f
            } else {
                acc += seg
                x0 = xs[i]; y0 = ys[i]
                i++
            }
        }
        while (out < n) { ox[out] = xs.last(); oy[out] = ys.last(); out++ }
        return ox to oy
    }
}
