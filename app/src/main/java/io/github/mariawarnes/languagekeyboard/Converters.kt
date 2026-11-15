package io.github.mariawarnes.languagekeyboard

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

/**
 * A conversion offered in the suggestion bar: replace the last [remove] characters before the
 * cursor with [insert]. [label] is what the bar shows.
 */
class Candidate(val label: String, val remove: Int, val insert: String)

/** Turns what was just typed (kana, pinyin) into the characters people actually write. */
interface Converter {
    fun candidates(before: String, limit: Int): List<Candidate>
}

/**
 * Japanese: the hiragana just typed → kanji (from the SKK dictionary) or katakana.
 * Looks up the longest ending of the kana run that the dictionary knows, so in
 * "わたしはがくせい" the bar offers 学生 for がくせい.
 */
class JapaneseConverter(private val readings: Map<String, List<String>>) : Converter {

    override fun candidates(before: String, limit: Int): List<Candidate> {
        val run = before.takeLastWhile { Kana.isHiragana(it) }
        if (run.isEmpty()) return emptyList()
        val out = ArrayList<Candidate>()
        for (length in run.length downTo 1) {
            val words = readings[run.takeLast(length)] ?: continue
            for (w in words) {
                if (out.size >= limit) break
                out += Candidate(w, length, w)
            }
            break // only the longest ending that matches
        }
        // Katakana for the whole run (loanwords, names) as the second option.
        val katakana = Kana.toKatakana(run)
        if (katakana != run) out.add(minOf(1, out.size), Candidate(katakana, run.length, katakana))
        return out.take(limit)
    }

    companion object {
        /** Parses the SKK dictionary's okuri-nasi entries: "がくせい /学生/" (annotations after ';'). */
        fun parseSkk(lines: Sequence<String>): Map<String, List<String>> {
            val map = HashMap<String, List<String>>()
            for (line in lines) {
                if (line.startsWith(";") || line.isBlank()) continue
                val space = line.indexOf(' ')
                if (space <= 0) continue
                val reading = line.substring(0, space)
                // Entries ending in a Latin letter are verbs/adjectives with okurigana; skip those.
                if (reading.last().isLetter() && reading.last().code < 0x80) continue
                if (!reading.all { Kana.isHiragana(it) }) continue
                val words = line.substring(space + 1).split('/')
                    .map { it.substringBefore(';').trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("(") }
                if (words.isNotEmpty()) map[reading] = words
            }
            return map
        }
    }
}

/**
 * Chinese: pinyin just typed → words from the Chinese word list, most common first.
 * "nihao" offers 你好; a longer run like "nihaoma" also offers its first word (你好 + "ma" left to
 * convert next).
 */
class PinyinConverter(private val dict: Dictionary) : Converter {

    /** Pinyin (no tones, ü as v, syllables joined) → word ranks, most common first. */
    private val byPinyin = HashMap<String, MutableList<Int>>()
    private val keys: List<String>

    init {
        val format = HanyuPinyinOutputFormat().apply {
            toneType = HanyuPinyinToneType.WITHOUT_TONE
            vCharType = HanyuPinyinVCharType.WITH_V
            caseType = HanyuPinyinCaseType.LOWERCASE
        }
        val syllable = HashMap<Char, String?>()
        for (rank in 0 until dict.size) {
            val word = dict.word(rank)
            val key = StringBuilder()
            var ok = true
            for (c in word) {
                val p = syllable.getOrPut(c) {
                    try {
                        PinyinHelper.toHanyuPinyinStringArray(c, format)?.firstOrNull()
                    } catch (e: Exception) {
                        null
                    }
                }
                if (p == null) { ok = false; break }
                key.append(p)
            }
            if (ok && key.isNotEmpty()) byPinyin.getOrPut(key.toString()) { ArrayList() } += rank
        }
        keys = byPinyin.keys.sorted()
    }

    override fun candidates(before: String, limit: Int): List<Candidate> {
        val run = before.takeLastWhile { it in 'a'..'z' }
        if (run.isEmpty()) return emptyList()
        val out = LinkedHashMap<String, Candidate>()
        fun add(word: String, remove: Int, insert: String) {
            if (out.size < limit && word !in out) out[word] = Candidate(word, remove, insert)
        }
        // 1. Words spelled exactly like the whole run.
        byPinyin[run]?.forEach { add(dict.word(it), run.length, dict.word(it)) }
        // 2. The longest word at the start of the run; the rest stays as pinyin.
        for (length in run.length - 1 downTo 1) {
            val ranks = byPinyin[run.substring(0, length)] ?: continue
            val word = dict.word(ranks[0])
            add(word, run.length, word + run.substring(length))
            break
        }
        // 3. Words whose pinyin starts with what's typed so far (still typing).
        if (out.size < limit) {
            var i = keys.binarySearch(run).let { if (it < 0) -it - 1 else it + 1 }
            val more = ArrayList<Int>()
            while (i < keys.size && keys[i].startsWith(run) && more.size < 200) {
                more += byPinyin.getValue(keys[i]).first()
                i++
            }
            more.sorted().forEach { add(dict.word(it), run.length, dict.word(it)) }
        }
        return out.values.toList()
    }
}
