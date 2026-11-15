package io.github.mariawarnes.languagekeyboard

import com.google.mlkit.nl.translate.TranslateLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every language gets its own keyboard, and the non-Latin input methods behave. */
class KeyboardsTest {

    // --- Layouts ----------------------------------------------------------------------------

    @Test
    fun everyTranslatableLanguageHasItsOwnLayout() {
        val missing = TranslateLanguage.getAllLanguages().filter { !LanguageLayouts.forLanguage(it).dedicated }
        assertEquals("languages still on the fallback QWERTY", emptyList<String>(), missing)
    }

    @Test
    fun everyLayoutBuildsWithPrintableKeys() {
        for (language in TranslateLanguage.getAllLanguages()) {
            for (mode in KeyboardMode.values()) {
                for (shifted in listOf(false, true)) {
                    val rows = KeyboardLayouts.forMode(mode, language, shifted)
                    assertTrue("$language $mode has no rows", rows.size >= 3)
                    for (key in rows.flatten()) {
                        val text = key.text ?: continue
                        assertTrue("$language: undefined character in key '$text'", text.all { Character.isDefined(it) })
                    }
                }
            }
        }
    }

    private fun letters(language: String, shifted: Boolean = false) =
        KeyboardLayouts.forMode(KeyboardMode.LETTERS, language, shifted).flatten().mapNotNull { it.text }.toSet()

    @Test
    fun scriptsUseTheirOwnLetters() {
        assertTrue("Hindi", "क" in letters("hi") && "ख" in letters("hi", shifted = true))
        assertTrue("Bengali", "ক" in letters("bn"))
        assertTrue("Gujarati", "ક" in letters("gu"))
        assertTrue("Tamil", "க" in letters("ta"))
        assertTrue("Telugu", "క" in letters("te"))
        assertTrue("Kannada", "ಕ" in letters("kn"))
        assertTrue("Thai", "ก" in letters("th") && "ฉ" in letters("th", shifted = true))
        assertTrue("Korean", "ㅎ" in letters("ko") && "ㅃ" in letters("ko", shifted = true))
        assertTrue("Japanese", "あ" in letters("ja"))
        assertNotEquals("Thai shift page", letters("th"), letters("th", shifted = true))
    }

    @Test
    fun japaneseKeysFlick() {
        val a = KeyboardLayouts.forMode(KeyboardMode.LETTERS, "ja").flatten().first { it.text == "あ" }
        assertEquals(listOf("あ", "い", "う", "え", "お"), a.flicks)
    }

    // --- Korean -----------------------------------------------------------------------------

    private fun typeKorean(jamo: String): String {
        var text = ""
        for (j in jamo) {
            val (replace, insert) = Hangul.type(text.lastOrNull(), j)
            text = text.dropLast(replace) + insert
        }
        return text
    }

    @Test
    fun hangulBuildsSyllables() {
        assertEquals("한글", typeKorean("ㅎㅏㄴㄱㅡㄹ"))
        assertEquals("안녕하세요", typeKorean("ㅇㅏㄴㄴㅕㅇㅎㅏㅅㅔㅇㅛ"))
        assertEquals("하나", typeKorean("ㅎㅏㄴㅏ")) // final consonant moves on
        assertEquals("달가", typeKorean("ㄷㅏㄹㄱㅏ")) // double final splits
        assertEquals("와", typeKorean("ㅇㅗㅏ")) // compound vowel
        assertEquals("삶", typeKorean("ㅅㅏㄹㅁ")) // compound final
    }

    @Test
    fun hangulBackspaceRemovesOneJamo() {
        assertEquals("하", Hangul.removeLast('한'))
        assertEquals("ㅎ", Hangul.removeLast('하'))
        assertEquals("오", Hangul.removeLast('와'))
        assertEquals("살", Hangul.removeLast('삶'))
        assertEquals(null, Hangul.removeLast('a'))
    }

    // --- Japanese ---------------------------------------------------------------------------

    @Test
    fun kanaModifierCycles() {
        assertEquals('ば', Kana.nextForm('は'))
        assertEquals('ぱ', Kana.nextForm('ば'))
        assertEquals('は', Kana.nextForm('ぱ'))
        assertEquals('っ', Kana.nextForm('つ'))
        assertEquals("カタカナ", Kana.toKatakana("かたかな"))
    }

    @Test
    fun kanaConvertsToKanji() {
        val readings = JapaneseConverter.parseSkk(
            sequenceOf(
                ";; okuri-nasi entries.",
                "がくせい /学生/",
                "にほん /日本/二本/",
                "かk /書/欠/",
                "わたし /私;first person/渡し/",
            ),
        )
        val converter = JapaneseConverter(readings)
        val found = converter.candidates("わたしはがくせい", 3)
        assertEquals("学生", found[0].insert)
        assertEquals(4, found[0].remove)
        assertEquals("ワタシハガクセイ", found[1].insert) // katakana of the whole run
        assertEquals(listOf("私", "渡し"), readings["わたし"])
        assertTrue("okurigana entries skipped", "かk" !in readings)
    }

    // --- Chinese ----------------------------------------------------------------------------

    @Test
    fun pinyinConvertsToCharacters() {
        val dict = Dictionary("zh", listOf("你好", "你", "好", "吗", "我们", "我", "们"))
        val converter = PinyinConverter(dict)
        val hello = converter.candidates("nihao", 3)
        assertEquals("你好", hello[0].insert)
        assertEquals(5, hello[0].remove)
        val question = converter.candidates("nihaoma", 3)
        assertTrue(question.any { it.insert == "你好ma" })
        assertTrue(converter.candidates("wom", 3).any { it.insert == "我们" }) // still typing
    }
}
