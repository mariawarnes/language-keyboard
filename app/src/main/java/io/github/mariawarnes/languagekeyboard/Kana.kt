package io.github.mariawarnes.languagekeyboard

/** Small helpers for Japanese kana input. */
object Kana {
    /** What the 小゛゜ key cycles through for the kana before the cursor. */
    private val CYCLES = listOf(
        "かが", "きぎ", "くぐ", "けげ", "こご", "さざ", "しじ", "すず", "せぜ", "そぞ",
        "ただ", "ちぢ", "つっづ", "てで", "とど", "はばぱ", "ひびぴ", "ふぶぷ", "へべぺ", "ほぼぽ",
        "あぁ", "いぃ", "うぅゔ", "えぇ", "おぉ", "やゃ", "ゆゅ", "よょ", "わゎ",
    )

    fun nextForm(c: Char): Char? {
        for (cycle in CYCLES) {
            val i = cycle.indexOf(c)
            if (i >= 0) return cycle[(i + 1) % cycle.length]
        }
        return null
    }

    fun isHiragana(c: Char) = c in 'ぁ'..'ゟ' || c == 'ー'

    fun toKatakana(s: String) = buildString {
        for (c in s) append(if (c in 'ぁ'..'ゖ') c + 0x60 else c)
    }
}
