package io.github.mariawarnes.languagekeyboard

/**
 * Builds Hangul syllables from 2-set (Dubeolsik) jamo as you type, the way Korean keyboards do:
 * ㅎ + ㅏ → 하, + ㄴ → 한; a vowel after a final consonant moves it to a new syllable
 * (한 + ㅏ → 하나); double finals split (닭 + ㅏ → 달가).
 */
object Hangul {
    private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private const val JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    private const val JONG = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ" // index 0 = no final

    private val VOWEL_PAIRS = mapOf(
        "ㅗㅏ" to 'ㅘ', "ㅗㅐ" to 'ㅙ', "ㅗㅣ" to 'ㅚ', "ㅜㅓ" to 'ㅝ', "ㅜㅔ" to 'ㅞ', "ㅜㅣ" to 'ㅟ', "ㅡㅣ" to 'ㅢ',
    )
    private val FINAL_PAIRS = mapOf(
        "ㄱㅅ" to 'ㄳ', "ㄴㅈ" to 'ㄵ', "ㄴㅎ" to 'ㄶ', "ㄹㄱ" to 'ㄺ', "ㄹㅁ" to 'ㄻ', "ㄹㅂ" to 'ㄼ',
        "ㄹㅅ" to 'ㄽ', "ㄹㅌ" to 'ㄾ', "ㄹㅍ" to 'ㄿ', "ㄹㅎ" to 'ㅀ', "ㅂㅅ" to 'ㅄ',
    )

    fun isJamo(c: Char) = c in 'ㄱ'..'ㆎ'
    private fun isSyllable(c: Char) = c in '가'..'힣'

    private fun syllable(cho: Int, jung: Int, jong: Int) = (0xAC00 + (cho * 21 + jung) * 28 + jong).toChar()

    private class Parts(val cho: Int, val jung: Int, val jong: Int)

    private fun parts(c: Char): Parts {
        val i = c.code - 0xAC00
        return Parts(i / (21 * 28), (i % (21 * 28)) / 28, i % 28)
    }

    private fun split(compound: Char, pairs: Map<String, Char>) = pairs.entries.firstOrNull { it.value == compound }?.key

    /**
     * Typing jamo [j] right after [prev]: how many characters before the cursor to replace (0 or 1)
     * and the text to put there.
     */
    fun type(prev: Char?, j: Char): Pair<Int, String> {
        val vowel = JUNG.indexOf(j) >= 0
        if (prev != null && isSyllable(prev)) {
            val p = parts(prev)
            if (!vowel) {
                if (p.jong == 0) {
                    val f = JONG.indexOf(j)
                    if (f > 0) return 1 to syllable(p.cho, p.jung, f).toString()
                } else {
                    val pair = FINAL_PAIRS["${JONG[p.jong]}$j"]
                    if (pair != null) return 1 to syllable(p.cho, p.jung, JONG.indexOf(pair)).toString()
                }
                return 0 to j.toString()
            }
            if (p.jong == 0) {
                val v = VOWEL_PAIRS["${JUNG[p.jung]}$j"] ?: return 0 to j.toString()
                return 1 to syllable(p.cho, JUNG.indexOf(v), 0).toString()
            }
            // The final consonant becomes the start of a new syllable (a double final splits).
            val fin = JONG[p.jong]
            val pair = split(fin, FINAL_PAIRS)
            val keep = if (pair != null) JONG.indexOf(pair[0]) else 0
            val move = pair?.get(1) ?: fin
            val cho = CHO.indexOf(move)
            if (cho < 0) return 0 to j.toString()
            return 1 to "${syllable(p.cho, p.jung, keep)}${syllable(cho, JUNG.indexOf(j), 0)}"
        }
        if (prev != null && vowel) {
            val cho = CHO.indexOf(prev)
            if (cho >= 0) return 1 to syllable(cho, JUNG.indexOf(j), 0).toString()
            VOWEL_PAIRS["$prev$j"]?.let { return 1 to it.toString() }
        }
        return 0 to j.toString()
    }

    /** Backspace on syllable [c]: it loses its last jamo (한 → 하 → ㅎ). Null if [c] isn't one. */
    fun removeLast(c: Char): String? {
        if (!isSyllable(c)) return null
        val p = parts(c)
        if (p.jong != 0) {
            val pair = split(JONG[p.jong], FINAL_PAIRS)
            return syllable(p.cho, p.jung, if (pair != null) JONG.indexOf(pair[0]) else 0).toString()
        }
        val pair = split(JUNG[p.jung], VOWEL_PAIRS)
        if (pair != null) return syllable(p.cho, JUNG.indexOf(pair[0]), 0).toString()
        return CHO[p.cho].toString()
    }
}
