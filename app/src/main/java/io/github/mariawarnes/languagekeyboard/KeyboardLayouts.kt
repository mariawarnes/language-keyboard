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

object KeyboardLayouts {

    private fun letter(c: String, alternates: String = "", hint: String? = null) =
        Key(c, alternates = alternates.split(' ').filter { it.isNotEmpty() }, hint = hint)

    private fun sym(c: String, alternates: String = "") =
        Key(c, alternates = alternates.split(' ').filter { it.isNotEmpty() })

    private fun spacer(width: Float) = Key("", text = null, width = width, style = KeyStyle.SPACER)

    private val shift = Key("⇧", text = null, code = Codes.SHIFT, width = 1.5f, style = KeyStyle.FUNCTION)
    private val delete = Key("⌫", text = null, code = Codes.DELETE, width = 1.5f, style = KeyStyle.FUNCTION, repeatable = true)

    private fun bottomRow(modeKey: Key) = listOf(
        modeKey,
        sym(",", "' \" : ; - _"),
        Key("", text = null, code = Codes.SPACE, width = 5f),
        sym(".", "? ! … ¿ ¡ - :"),
        Key("⏎", text = null, code = Codes.ENTER, width = 1.5f, style = KeyStyle.ACCENT),
    )

    private val toSymbols = Key("?123", text = null, code = Codes.MODE_SYMBOLS, width = 1.5f, style = KeyStyle.FUNCTION)
    private val toLetters = Key("ABC", text = null, code = Codes.MODE_LETTERS, width = 1.5f, style = KeyStyle.FUNCTION)

    val LETTERS: List<List<Key>> = listOf(
        listOf(
            letter("q", "1", "1"),
            letter("w", "2", "2"),
            letter("e", "3 é è ê ë ē ę ě", "3"),
            letter("r", "4 ř", "4"),
            letter("t", "5 ť þ", "5"),
            letter("y", "6 ý ÿ", "6"),
            letter("u", "7 ú ù û ü ū ů ű", "7"),
            letter("i", "8 í ì î ï ī ı", "8"),
            letter("o", "9 ó ò ô ö õ ø ō œ ő", "9"),
            letter("p", "0", "0"),
        ),
        listOf(
            spacer(0.5f),
            letter("a", "á à â ä ã å ā æ ą"),
            letter("s", "ß ś š ş"),
            letter("d", "ð ď"),
            letter("f"),
            letter("g", "ğ"),
            letter("h"),
            letter("j"),
            letter("k"),
            letter("l", "ł ľ"),
            spacer(0.5f),
        ),
        listOf(
            shift,
            letter("z", "ž ź ż"),
            letter("x"),
            letter("c", "ç ć č"),
            letter("v"),
            letter("b"),
            letter("n", "ñ ń ň"),
            letter("m"),
            delete,
        ),
        bottomRow(toSymbols),
    )

    val SYMBOLS: List<List<Key>> = listOf(
        listOf(
            sym("1", "¹ ½ ⅓ ¼"), sym("2", "² ⅔"), sym("3", "³ ¾"), sym("4"), sym("5"),
            sym("6"), sym("7"), sym("8"), sym("9"), sym("0", "°"),
        ),
        listOf(
            sym("@"), sym("#"), sym("$", "€ £ ¥ ¢ ₹"), sym("_"), sym("&"),
            sym("-", "– — ·"), sym("+", "±"), sym("("), sym(")"), sym("/"),
        ),
        listOf(
            Key("=\\<", text = null, code = Codes.MODE_SYMBOLS_2, width = 1.5f, style = KeyStyle.FUNCTION),
            sym("*", "★ †"), sym("\"", "“ ” „ « »"), sym("'", "‘ ’ ‚ ‹ ›"), sym(":"), sym(";"),
            sym("!", "¡"), sym("?", "¿"),
            delete,
        ),
        bottomRow(toLetters),
    )

    val SYMBOLS_2: List<List<Key>> = listOf(
        listOf(
            sym("~"), sym("`"), sym("|"), sym("•"), sym("√"),
            sym("π"), sym("÷"), sym("×"), sym("¶"), sym("∆"),
        ),
        listOf(
            sym("£"), sym("€"), sym("¥"), sym("¢"), sym("^"),
            sym("°"), sym("="), sym("{"), sym("}"), sym("\\"),
        ),
        listOf(
            Key("?123", text = null, code = Codes.MODE_SYMBOLS, width = 1.5f, style = KeyStyle.FUNCTION),
            sym("%", "‰"), sym("©"), sym("®"), sym("™"), sym("✓"), sym("["), sym("]"),
            delete,
        ),
        bottomRow(toLetters),
    )

    fun forMode(mode: KeyboardMode): List<List<Key>> = when (mode) {
        KeyboardMode.LETTERS -> LETTERS
        KeyboardMode.SYMBOLS -> SYMBOLS
        KeyboardMode.SYMBOLS_2 -> SYMBOLS_2
    }
}
