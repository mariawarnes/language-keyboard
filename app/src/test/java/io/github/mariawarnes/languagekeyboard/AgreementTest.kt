package io.github.mariawarnes.languagekeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgreementTest {

    private fun fixes(text: String, language: String = "es") = Agreement.check(text, language).map { it.word to it.fix }

    @Test
    fun spanishDemonstrativeMustAgree() {
        assertEquals(listOf("este" to "esta"), fixes("Hola este es una prueba"))
        assertEquals(listOf("Esta" to "Este"), fixes("Esta es un problema"))
        assertEquals(listOf("este" to "esta"), fixes("este no es la casa"))
        assertEquals(listOf("Estos" to "Estas"), fixes("Estos son unas ideas"))
        assertEquals(listOf("ese" to "esa"), fixes("ese fue una buena idea"))
    }

    @Test
    fun correctSpanishIsLeftAlone() {
        for (ok in listOf(
            "esta es una prueba", "este es un problema", "esto es una prueba", "esta es el agua que bebo",
            "esta es un arma", "este es mi libro", "estas son las mías", "este es", "esta es una",
        )) {
            assertTrue(ok, fixes(ok).isEmpty())
        }
    }

    @Test
    fun portugueseAndItalian() {
        assertEquals(listOf("este" to "esta"), fixes("este é uma casa", "pt"))
        assertTrue(fixes("esta é uma casa", "pt").isEmpty())
        assertEquals(listOf("questo" to "questa"), fixes("questo è una prova", "it"))
        assertEquals(listOf("questo" to "questa"), fixes("questo è un'amica", "it"))
        assertTrue(fixes("questa è un'amica", "it").isEmpty())
        assertTrue(fixes("questo è l'albero", "it").isEmpty())
    }

    @Test
    fun findsWhatGivesTheGenderAway() {
        val hit = Agreement.check("Hola este es una prueba", "es").single()
        assertEquals(5, hit.start)
        assertEquals(9, hit.end)
        assertEquals("una", hit.article)
        assertEquals("prueba", hit.noun)
        assertEquals("este/esta", hit.pair)
        assertTrue(hit.feminine)
        // An adjective before the noun is skipped.
        assertEquals("idea", Agreement.check("ese fue una buena idea", "es").single().noun)
        // Not typed yet.
        assertEquals(null, Agreement.check("este es una", "es").single().noun)
        // Masculine, and Italian elision.
        val masc = Agreement.check("Esta es un problema", "es").single()
        assertEquals("problema", masc.noun)
        assertTrue(!masc.feminine)
        val it = Agreement.check("questo è un'amica", "it").single()
        assertEquals("un'", it.article)
        assertEquals("amica", it.noun)
    }

    @Test
    fun nounIsNotConsumed() {
        // The look-ahead for the noun mustn't swallow the next sentence's demonstrative.
        assertEquals(2, Agreement.check("esta es un libro este es una casa", "es").size)
    }

    @Test
    fun otherLanguagesAreNotChecked() {
        assertTrue(fixes("this is a test", "en").isEmpty())
    }
}
