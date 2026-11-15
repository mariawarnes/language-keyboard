package io.github.mariawarnes.languagekeyboard

enum class KeyStyle { NORMAL, FUNCTION, ACCENT, SPACER }

enum class ShiftState { OFF, ONE_SHOT, LOCKED }

object Codes {
    const val NONE = 0
    const val SHIFT = -1
    const val DELETE = -2
    const val ENTER = -3
    const val SPACE = -4
    const val MODE_LETTERS = -5
    const val MODE_SYMBOLS = -6
    const val MODE_SYMBOLS_2 = -7

    /** Japanese: make the last kana small / voiced (は → ば → ぱ, つ → っ → づ). */
    const val KANA_MODIFY = -8
}

/**
 * One key. Keys that type something have [text]; special keys have a [code] from [Codes].
 * [alternates] pop up on long-press (accents, digits, extra punctuation).
 */
data class Key(
    val label: String,
    val text: String? = label,
    val code: Int = Codes.NONE,
    val width: Float = 1f,
    val alternates: List<String> = emptyList(),
    val hint: String? = null,
    val style: KeyStyle = KeyStyle.NORMAL,
    val repeatable: Boolean = false,
    /** Japanese flick keys: [center, left, up, right, down] ("" = nothing that way). */
    val flicks: List<String>? = null,
) {
    /** Letters change case with shift; everything else stays as it is. */
    val isLetter: Boolean get() = text != null && text.length == 1 && text[0].isLetter()
}

enum class KeyboardMode { LETTERS, SYMBOLS, SYMBOLS_2 }

/**
 * Builds the key rows. Rows narrower than the widest one are centred by [KeyboardView],
 * so a 9-key middle row sits between the keys above it, like on a normal phone keyboard.
 */
object KeyboardLayouts {

    private fun split(s: String) = s.split(' ').filter { it.isNotEmpty() }

    private fun sym(c: String, alternates: String = "") = Key(c, alternates = split(alternates))

    private fun shift(width: Float) =
        Key("⇧", text = null, code = Codes.SHIFT, width = width, style = KeyStyle.FUNCTION)

    private fun delete(width: Float) =
        Key("⌫", text = null, code = Codes.DELETE, width = width, style = KeyStyle.FUNCTION, repeatable = true)

    private fun bottomRow(total: Float, modeKey: Key, comma: String, commaAlternates: String, periodAlternates: String) =
        listOf(
            modeKey,
            sym(comma, commaAlternates),
            Key("", text = null, code = Codes.SPACE, width = total - 5f),
            sym(".", periodAlternates),
            Key("⏎", text = null, code = Codes.ENTER, width = 1.5f, style = KeyStyle.ACCENT),
        )

    private fun modeKey(label: String, code: Int) =
        Key(label, text = null, code = code, width = 1.5f, style = KeyStyle.FUNCTION)

    // --- Letters ----------------------------------------------------------------------------

    private fun letters(spec: LayoutSpec, shifted: Boolean): List<List<Key>> {
        if (spec.kana12) return japaneseKana()
        val rows = (if (shifted && spec.shiftRows != null) spec.shiftRows else spec.rows).map(::split)
        val hasShift = spec.cased || spec.shiftRows != null
        val fnCount = if (hasShift) 2 else 1
        val last = rows.last()
        val total = maxOf(rows.dropLast(1).maxOfOrNull { it.size } ?: 0, last.size + fnCount, 10).toFloat()
        val fnWidth = ((total - last.size) / fnCount).coerceIn(1f, 1.5f)

        fun letter(c: String, digitIndex: Int? = null): Key {
            val own = spec.alternates[c] ?: if (spec.latin) LanguageLayouts.LATIN_ALTERNATES[c] else null
            val alternates = split(own.orEmpty())
            if (digitIndex == null || digitIndex >= spec.digits.length) return Key(c, alternates = alternates)
            val digit = spec.digits[digitIndex].toString()
            val western = "1234567890"[digitIndex].toString()
            val digits = if (digit == western) listOf(digit) else listOf(digit, western)
            return Key(c, alternates = digits + alternates, hint = digit)
        }

        val result = ArrayList<List<Key>>()
        rows.dropLast(1).forEachIndexed { r, row ->
            result.add(row.mapIndexed { i, c -> letter(c, if (r == 0) i else null) })
        }
        val lastRow = ArrayList<Key>()
        if (hasShift) lastRow.add(shift(fnWidth))
        last.forEachIndexed { i, c -> lastRow.add(letter(c, if (rows.size == 1) i else null)) }
        lastRow.add(delete(fnWidth))
        result.add(lastRow)
        result.add(
            bottomRow(
                total, modeKey("?123", Codes.MODE_SYMBOLS),
                spec.comma, spec.commaAlternates, spec.periodAlternates,
            ),
        )
        return result
    }

    // --- Japanese 12-key kana pad ---------------------------------------------------------------

    /** Tap for the first kana; flick left / up / right / down for the others in its row. */
    private fun flick(center: String, others: String): Key {
        val rest = split(others).map { if (it == "_") "" else it }
        return Key(center, flicks = listOf(center) + rest + List(4 - rest.size) { "" })
    }

    private fun japaneseKana(): List<List<Key>> = listOf(
        listOf(
            flick("ー", "～ … ・ _"), flick("あ", "い う え お"), flick("か", "き く け こ"),
            flick("さ", "し す せ そ"), delete(1f),
        ),
        listOf(
            flick("「", "『 （ 」 』"), flick("た", "ち つ て と"), flick("な", "に ぬ ね の"),
            flick("は", "ひ ふ へ ほ"), flick("？", "！ ～ … _"),
        ),
        listOf(
            Key("?123", text = null, code = Codes.MODE_SYMBOLS, style = KeyStyle.FUNCTION),
            flick("ま", "み む め も"), flick("や", "「 ゆ 」 よ"), flick("ら", "り る れ ろ"),
            Key("", text = null, code = Codes.SPACE),
        ),
        listOf(
            flick("、", "， ・ _ _"),
            Key("小゛゜", text = null, code = Codes.KANA_MODIFY, style = KeyStyle.FUNCTION),
            flick("わ", "を ん ー _"), flick("。", "． _ _ _"),
            Key("⏎", text = null, code = Codes.ENTER, style = KeyStyle.ACCENT),
        ),
    )

    // --- Symbols ----------------------------------------------------------------------------

    private fun symbols(spec: LayoutSpec): List<List<Key>> = listOf(
        listOf(
            sym("1", "¹ ½ ⅓ ¼"), sym("2", "² ⅔"), sym("3", "³ ¾"), sym("4"), sym("5"),
            sym("6"), sym("7"), sym("8"), sym("9"), sym("0", "°"),
        ),
        listOf(
            sym("@"), sym("#"), sym("$", "€ £ ¥ ¢ ₹"), sym("_"), sym("&"),
            sym("-", "– — ·"), sym("+", "±"), sym("("), sym(")"), sym("/"),
        ),
        listOf(
            modeKey("=\\<", Codes.MODE_SYMBOLS_2),
            sym("*", "★ †"), sym("\"", "“ ” „ « »"), sym("'", "‘ ’ ‚ ‹ ›"), sym(":"), sym(";"),
            sym("!", "¡"), sym("?", "¿ ؟"),
            delete(1.5f),
        ),
        bottomRow(10f, modeKey(spec.lettersLabel, Codes.MODE_LETTERS), spec.comma, spec.commaAlternates, spec.periodAlternates),
    )

    private fun symbols2(spec: LayoutSpec): List<List<Key>> = listOf(
        listOf(
            sym("~"), sym("`"), sym("|"), sym("•"), sym("√"),
            sym("π"), sym("÷"), sym("×"), sym("¶"), sym("∆"),
        ),
        listOf(
            sym("£"), sym("€"), sym("¥"), sym("¢"), sym("^"),
            sym("°"), sym("="), sym("{"), sym("}"), sym("\\"),
        ),
        listOf(
            modeKey("?123", Codes.MODE_SYMBOLS),
            sym("%", "‰"), sym("©"), sym("®"), sym("™"), sym("✓"), sym("["), sym("]"),
            delete(1.5f),
        ),
        bottomRow(10f, modeKey(spec.lettersLabel, Codes.MODE_LETTERS), spec.comma, spec.commaAlternates, spec.periodAlternates),
    )

    // Built once per language and mode, so the keyboard view can tell when the layout really changed.
    private val cache = HashMap<String, List<List<Key>>>()

    /** [shifted] picks the Shift page for scripts that have one (Thai, Indian scripts, Korean). */
    fun forMode(mode: KeyboardMode, language: String, shifted: Boolean = false): List<List<Key>> {
        val spec = LanguageLayouts.forLanguage(language)
        val page = shifted && mode == KeyboardMode.LETTERS && spec.shiftRows != null
        return cache.getOrPut("$mode/$language/$page") {
            when (mode) {
                KeyboardMode.LETTERS -> letters(spec, page)
                KeyboardMode.SYMBOLS -> symbols(spec)
                KeyboardMode.SYMBOLS_2 -> symbols2(spec)
            }
        }
    }
}
