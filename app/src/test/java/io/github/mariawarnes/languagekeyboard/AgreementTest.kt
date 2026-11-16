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
    fun explainsWithTheCorrectPhrase() {
        val hit = Agreement.check("Hola este es una prueba", "es").single()
        assertEquals(5, hit.start)
        assertEquals(9, hit.end)
        assertTrue(hit.message, hit.message.contains("«esta es una prueba»"))
    }

    @Test
    fun otherLanguagesAreNotChecked() {
        assertTrue(fixes("this is a test", "en").isEmpty())
    }
}
