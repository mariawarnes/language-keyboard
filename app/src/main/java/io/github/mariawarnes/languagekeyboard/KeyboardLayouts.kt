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

    private fun letters(spec: LayoutSpec): List<List<Key>> {
        val rows = spec.rows.map(::split)
        val fnCount = if (spec.cased) 2 else 1
        val third = rows[2]
        val total = maxOf(rows[0].size, rows[1].size, third.size + fnCount, 10).toFloat()
        val fnWidth = ((total - third.size) / fnCount).coerceIn(1f, 1.5f)

        fun letter(c: String, digitIndex: Int? = null): Key {
            val own = spec.alternates[c] ?: if (spec.latin) LanguageLayouts.LATIN_ALTERNATES[c] else null
            val alternates = split(own.orEmpty())
            if (digitIndex == null || digitIndex >= spec.digits.length) return Key(c, alternates = alternates)
            val digit = spec.digits[digitIndex].toString()
            val western = "1234567890"[digitIndex].toString()
            val digits = if (digit == western) listOf(digit) else listOf(digit, western)
            return Key(c, alternates = digits + alternates, hint = digit)
        }

        val row1 = rows[0].mapIndexed { i, c -> letter(c, i) }
        val row2 = rows[1].map { letter(it) }
        val row3 = buildList {
            if (spec.cased) add(shift(fnWidth))
            addAll(third.map { letter(it) })
            add(delete(fnWidth))
        }
        val bottom = bottomRow(
            total, modeKey("?123", Codes.MODE_SYMBOLS),
            spec.comma, spec.commaAlternates, spec.periodAlternates,
        )
        return listOf(row1, row2, row3, bottom)
    }

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

    fun forMode(mode: KeyboardMode, language: String): List<List<Key>> =
        cache.getOrPut("$mode/$language") {
            val spec = LanguageLayouts.forLanguage(language)
            when (mode) {
                KeyboardMode.LETTERS -> letters(spec)
                KeyboardMode.SYMBOLS -> symbols(spec)
                KeyboardMode.SYMBOLS_2 -> symbols2(spec)
            }
        }
}
