package io.github.mariawarnes.languagelens

/** Which pieces of on-screen text are worth translating (messages, not times or names). */
object TextFilter {
    private val TIME = Regex("""^\s*\d{1,2}[:.]\d{2}(\s*[AaPp]\.?[Mm]\.?)?\s*$""")
    private val DATE_OR_NUMBER = Regex("""^[\d\s/.,:\-+()%]*$""")
    private val URL = Regex("""^\s*(https?://|www\.)\S+\s*$""")

    fun worthTranslating(text: String): Boolean {
        val t = text.trim()
        if (t.length < 3) return false
        if (t.count { it.isLetter() } < 3) return false // emoji, "ok", numbers
        if (TIME.matches(t) || DATE_OR_NUMBER.matches(t) || URL.matches(t)) return false
        return true
    }
}
