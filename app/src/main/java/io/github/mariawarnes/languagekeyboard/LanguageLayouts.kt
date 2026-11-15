package io.github.mariawarnes.languagekeyboard

/**
 * How one language's letter keys are arranged, modelled on that language's usual phone keyboard.
 */
class LayoutSpec(
    /** Shown in settings, e.g. "QWERTZ". */
    val name: String,
    /** Three rows of letters, space-separated. Shift/delete are added automatically. */
    val rows: List<String>,
    /** Long-press options per key (space-separated), shown after the top-row digit. */
    val alternates: Map<String, String> = emptyMap(),
    /** Latin layouts fall back to a general list of accents for keys without their own. */
    val latin: Boolean = true,
    /** Scripts without upper/lower case (Arabic, Hebrew, Georgian, …) get no shift key. */
    val cased: Boolean = true,
    /** Digits shown on the top row (long-press). */
    val digits: String = "1234567890",
    val comma: String = ",",
    val commaAlternates: String = "' \" : ; - _",
    val periodAlternates: String = "? ! … ¿ ¡ - :",
    /** Label on the key that returns from symbols to letters. */
    val lettersLabel: String = "ABC",
    /** False when we don't have a layout for this language yet and fall back to QWERTY. */
    val dedicated: Boolean = true,
)

object LanguageLayouts {

    private const val QWERTY_1 = "q w e r t y u i o p"
    private const val QWERTY_2 = "a s d f g h j k l"
    private const val QWERTY_3 = "z x c v b n m"
    private const val QWERTZ_1 = "q w e r t z u i o p"
    private const val QWERTZ_3 = "y x c v b n m"

    /** Accents offered on long-press for Latin layouts, unless a language lists its own. */
    val LATIN_ALTERNATES: Map<String, String> = mapOf(
        "a" to "á à â ä ã å ā æ ą",
        "c" to "ç ć č",
        "d" to "ð ď đ",
        "e" to "é è ê ë ē ę ě ė",
        "g" to "ğ ġ ģ",
        "h" to "ħ",
        "i" to "í ì î ï ī į ı",
        "k" to "ķ",
        "l" to "ł ľ ļ ĺ",
        "n" to "ñ ń ň ņ",
        "o" to "ó ò ô ö õ ø ō œ ő",
        "r" to "ř ŕ",
        "s" to "ß ś š ş ș",
        "t" to "ť ț þ",
        "u" to "ú ù û ü ū ů ű ų",
        "y" to "ý ÿ",
        "z" to "ž ź ż",
    )

    private fun qwerty(alternates: Map<String, String> = emptyMap(), row2: String = QWERTY_2, name: String = "QWERTY") =
        LayoutSpec(name, listOf(QWERTY_1, row2, QWERTY_3), alternates)

    private fun qwertz(alternates: Map<String, String> = emptyMap()) =
        LayoutSpec("QWERTZ", listOf(QWERTZ_1, QWERTY_2, QWERTZ_3), alternates)

    private val specs: Map<String, LayoutSpec> = buildMap {
        // --- Latin, QWERTY ---
        for (code in listOf("en", "id", "ms", "sw", "tl")) put(code, qwerty())
        put("es", qwerty(mapOf("a" to "á", "e" to "é", "i" to "í", "o" to "ó", "u" to "ú ü", "n" to "ñ"), "$QWERTY_2 ñ", "QWERTY (Español)"))
        put("gl", qwerty(mapOf("a" to "á", "e" to "é", "i" to "í", "o" to "ó", "u" to "ú ü"), "$QWERTY_2 ñ", "QWERTY (Galego)"))
        put("ca", qwerty(mapOf("a" to "à", "e" to "è é", "i" to "í ï", "o" to "ò ó", "u" to "ú ü", "l" to "l·l ·"), "$QWERTY_2 ç", "QWERTY (Català)"))
        put("pt", qwerty(mapOf("a" to "á à â ã", "e" to "é ê", "i" to "í", "o" to "ó ô õ", "u" to "ú ü", "c" to "ç")))
        put("it", qwerty(mapOf("a" to "à á", "e" to "è é", "i" to "ì í", "o" to "ò ó", "u" to "ù ú")))
        put("nl", qwerty(mapOf("e" to "é ë è ê", "i" to "ï í", "o" to "ó ö ô", "u" to "ü ú", "a" to "á ä", "c" to "ç")))
        put("af", qwerty(mapOf("e" to "ê é è ë", "i" to "î ï", "o" to "ô ö", "u" to "û ü", "n" to "ŉ")))
        put("pl", qwerty(mapOf("a" to "ą", "c" to "ć", "e" to "ę", "l" to "ł", "n" to "ń", "o" to "ó", "s" to "ś", "z" to "ż ź")))
        put("ro", qwerty(mapOf("a" to "ă â", "i" to "î", "s" to "ș ş", "t" to "ț ţ")))
        put("lt", qwerty(mapOf("a" to "ą", "c" to "č", "e" to "ę ė", "i" to "į", "s" to "š", "u" to "ų ū", "z" to "ž")))
        put("lv", qwerty(mapOf("a" to "ā", "c" to "č", "e" to "ē", "g" to "ģ", "i" to "ī", "k" to "ķ", "l" to "ļ", "n" to "ņ", "s" to "š", "u" to "ū", "z" to "ž")))
        put("ga", qwerty(mapOf("a" to "á", "e" to "é", "i" to "í", "o" to "ó", "u" to "ú")))
        put("cy", qwerty(mapOf("a" to "â á à ä", "e" to "ê é è ë", "i" to "î í ì ï", "o" to "ô ó ò ö", "u" to "û ú ù ü", "w" to "ŵ ẃ ẁ ẅ", "y" to "ŷ ý ỳ ÿ")))
        put("ht", qwerty(mapOf("a" to "à", "e" to "è", "o" to "ò")))
        put("eo", qwerty(mapOf("c" to "ĉ", "g" to "ĝ", "h" to "ĥ", "j" to "ĵ", "s" to "ŝ", "u" to "ŭ")))
        put("mt", qwerty(mapOf("c" to "ċ", "g" to "ġ", "h" to "ħ", "z" to "ż", "a" to "à", "e" to "è", "i" to "ì î", "o" to "ò", "u" to "ù")))
        put("vi", qwerty(mapOf(
            "a" to "à á ả ã ạ ă ằ ắ ẳ ẵ ặ â ầ ấ ẩ ẫ ậ",
            "e" to "è é ẻ ẽ ẹ ê ề ế ể ễ ệ",
            "i" to "ì í ỉ ĩ ị",
            "o" to "ò ó ỏ õ ọ ô ồ ố ổ ỗ ộ ơ ờ ớ ở ỡ ợ",
            "u" to "ù ú ủ ũ ụ ư ừ ứ ử ữ ự",
            "y" to "ỳ ý ỷ ỹ ỵ",
            "d" to "đ",
        )))

        // --- Latin, Nordic and Baltic ---
        val nordic = mapOf("a" to "å ä æ", "o" to "ö ø", "e" to "é")
        put("sv", LayoutSpec("QWERTY (Svenska)", listOf("$QWERTY_1 å", "$QWERTY_2 ö ä", QWERTY_3), nordic))
        put("fi", LayoutSpec("QWERTY (Suomi)", listOf("$QWERTY_1 å", "$QWERTY_2 ö ä", QWERTY_3), mapOf("s" to "š", "z" to "ž") + nordic))
        put("da", LayoutSpec("QWERTY (Dansk)", listOf("$QWERTY_1 å", "$QWERTY_2 æ ø", QWERTY_3), nordic))
        put("no", LayoutSpec("QWERTY (Norsk)", listOf("$QWERTY_1 å", "$QWERTY_2 ø æ", QWERTY_3), nordic))
        put("is", LayoutSpec("QWERTY (Íslenska)", listOf("$QWERTY_1 ð", "$QWERTY_2 æ", "$QWERTY_3 þ"),
            mapOf("a" to "á", "e" to "é", "i" to "í", "o" to "ó ö", "u" to "ú", "y" to "ý")))
        put("et", LayoutSpec("QWERTY (Eesti)", listOf("$QWERTY_1 ü õ", "$QWERTY_2 ö ä", QWERTY_3), mapOf("s" to "š", "z" to "ž")))

        // --- Latin, QWERTZ / AZERTY / Turkish Q ---
        put("de", LayoutSpec("QWERTZ", listOf("$QWERTZ_1 ü", "$QWERTY_2 ö ä", QWERTZ_3), mapOf("a" to "ä", "o" to "ö", "u" to "ü", "s" to "ß")))
        put("cs", LayoutSpec("QWERTZ (Čeština)", listOf("$QWERTZ_1 ú", "$QWERTY_2 ů", QWERTZ_3), mapOf(
            "a" to "á", "c" to "č", "d" to "ď", "e" to "é ě", "i" to "í", "n" to "ň", "o" to "ó",
            "r" to "ř", "s" to "š", "t" to "ť", "u" to "ú ů", "z" to "ž", "y" to "ý",
        )))
        put("sk", qwertz(mapOf(
            "a" to "á ä", "c" to "č", "d" to "ď", "e" to "é", "i" to "í", "l" to "ľ ĺ", "n" to "ň",
            "o" to "ó ô", "r" to "ŕ", "s" to "š", "t" to "ť", "u" to "ú", "z" to "ž", "y" to "ý",
        )))
        put("hu", qwertz(mapOf("a" to "á", "e" to "é", "i" to "í", "o" to "ó ö ő", "u" to "ú ü ű")))
        val southSlavic = mapOf("c" to "č ć", "d" to "đ", "s" to "š", "z" to "ž")
        put("hr", qwertz(southSlavic))
        put("sl", qwertz(southSlavic))
        put("sq", LayoutSpec("QWERTZ (Shqip)", listOf("$QWERTZ_1 ç", "$QWERTY_2 ë", QWERTZ_3), mapOf("e" to "ë", "c" to "ç")))
        put("fr", LayoutSpec("AZERTY", listOf("a z e r t y u i o p", "q s d f g h j k l m", "w x c v b n '"), mapOf(
            "a" to "à â æ", "e" to "é è ê ë", "i" to "î ï", "o" to "ô œ", "u" to "ù û ü", "c" to "ç", "y" to "ÿ",
            "'" to "’ \" « »",
        )))
        put("tr", LayoutSpec("Türkçe Q", listOf("q w e r t y u ı o p ğ ü", "a s d f g h j k l ş i", "z x c v b n m ö ç"),
            mapOf("a" to "â", "u" to "û", "i" to "î")))

        // --- Cyrillic ---
        put("ru", LayoutSpec("ЙЦУКЕН", listOf("й ц у к е н г ш щ з х", "ф ы в а п р о л д ж э", "я ч с м и т ь б ю"),
            mapOf("е" to "ё", "ь" to "ъ"), latin = false, lettersLabel = "АБВ", periodAlternates = "? ! … - :"))
        put("uk", LayoutSpec("ЙЦУКЕН (Українська)", listOf("й ц у к е н г ш щ з х ї", "ф і в а п р о л д ж є", "я ч с м и т ь б ю"),
            mapOf("г" to "ґ", "ь" to "ʼ"), latin = false, lettersLabel = "АБВ", periodAlternates = "? ! … - :"))
        put("be", LayoutSpec("ЙЦУКЕН (Беларуская)", listOf("й ц у к е н г ш ў з х", "ф ы в а п р о л д ж э", "я ч с м і т ь б ю"),
            mapOf("е" to "ё", "ь" to "ʼ"), latin = false, lettersLabel = "АБВ", periodAlternates = "? ! … - :"))
        put("bg", LayoutSpec("Фонетична", listOf("я в е р т ъ у и о п ч", "а с д ф г х й к л ш щ", "з ь ц ж б н м ю"),
            mapOf("и" to "ѝ"), latin = false, lettersLabel = "АБВ", periodAlternates = "? ! … - :"))
        put("mk", LayoutSpec("Македонска", listOf("љ њ е р т ѕ у и о п ш ѓ", "а с д ф г х ј к л ч ќ ж", "з џ ц в б н м"),
            mapOf("е" to "ѐ", "и" to "ѝ"), latin = false, lettersLabel = "АБВ", periodAlternates = "? ! … - :"))

        // --- Greek ---
        put("el", LayoutSpec("Ελληνικά", listOf("; ς ε ρ τ υ θ ι ο π", "α σ δ φ γ η ξ κ λ", "ζ χ ψ ω β ν μ"), mapOf(
            "α" to "ά", "ε" to "έ", "η" to "ή", "ι" to "ί ϊ ΐ", "ο" to "ό", "υ" to "ύ ϋ ΰ", "ω" to "ώ",
        ), latin = false, lettersLabel = "ΑΒΓ", periodAlternates = "; ! … - : ?"))

        // --- Right-to-left scripts (no letter case) ---
        put("he", LayoutSpec("עברית", listOf("ק ר א ט ו ן ם פ", "ש ד ג כ ע י ח ל ך ף", "ז ס ב ה נ מ צ ת ץ"),
            latin = false, cased = false, lettersLabel = "אבג", periodAlternates = "? ! … - : ״ ׳"))
        val arabicDigits = "١٢٣٤٥٦٧٨٩٠"
        val persianDigits = "۱۲۳۴۵۶۷۸۹۰"
        put("ar", LayoutSpec("العربية", listOf("ض ص ث ق ف غ ع ه خ ح ج", "ش س ي ب ل ا ت ن م ك ط", "ذ ء ؤ ر ى ة و ز ظ د"), mapOf(
            "ا" to "أ إ آ ٱ", "ي" to "ئ ى", "و" to "ؤ", "ه" to "ة", "ء" to "ئ أ إ ؤ", "ل" to "لا", "ت" to "ة",
        ), latin = false, cased = false, digits = arabicDigits, comma = "،", commaAlternates = "؛ : \" ' -",
            periodAlternates = "؟ ! … - :", lettersLabel = "أبت"))
        put("fa", LayoutSpec("فارسی", listOf("ض ص ث ق ف غ ع ه خ ح ج چ", "ش س ی ب ل ا ت ن م ک گ", "ظ ط ژ ز ر ذ د پ و"), mapOf(
            "ا" to "آ أ ء", "ی" to "ئ ي", "ه" to "ۀ ة", "ک" to "ك", "و" to "ؤ",
        ), latin = false, cased = false, digits = persianDigits, comma = "،", commaAlternates = "؛ : \" ' -",
            periodAlternates = "؟ ! … - :", lettersLabel = "الف"))
        put("ur", LayoutSpec("اردو (صوتی)", listOf("ق و ع ر ت ے ء ی ہ پ", "ا س د ف گ ح ج ک ل", "ز ش چ ط ب ن م"), mapOf(
            "ا" to "آ أ", "ت" to "ٹ ث ۃ", "د" to "ڈ ذ", "ر" to "ڑ", "ن" to "ں", "ہ" to "ھ ۃ ۂ", "ی" to "ئ ي",
            "س" to "ص ث", "ح" to "خ ھ", "ز" to "ذ ض ظ ژ", "ط" to "ظ", "ے" to "ۓ", "ک" to "ق", "ع" to "غ",
        ), latin = false, cased = false, digits = persianDigits, comma = "،", commaAlternates = "؛ : \" ' -",
            periodAlternates = "؟ ! … - : ۔", lettersLabel = "ابپ"))

        // --- Georgian (QWERTY-mapped; capitals don't exist in everyday writing) ---
        put("ka", LayoutSpec("ქართული", listOf("ქ წ ე რ ტ ყ უ ი ო პ", "ა ს დ ფ გ ჰ ჯ კ ლ", "ზ ხ ც ვ ბ ნ მ"), mapOf(
            "წ" to "ჭ", "რ" to "ღ", "ტ" to "თ", "ს" to "შ", "ჯ" to "ჟ", "ზ" to "ძ", "ც" to "ჩ",
        ), latin = false, cased = false, lettersLabel = "აბგ", periodAlternates = "? ! … - :"))
    }

    private val fallback = qwerty()

    /** The layout for [code], or plain QWERTY (with [LayoutSpec.dedicated] = false) if we don't have one yet. */
    fun forLanguage(code: String): LayoutSpec =
        specs[code] ?: LayoutSpec(fallback.name, fallback.rows, dedicated = false)
}
