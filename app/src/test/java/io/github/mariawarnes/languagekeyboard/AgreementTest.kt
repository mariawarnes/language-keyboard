package io.github.mariawarnes.languagekeyboard

import io.github.mariawarnes.languagekeyboard.Agreement.Kind
import io.github.mariawarnes.languagekeyboard.Agreement.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgreementTest {

    private fun fixes(text: String, language: String = "es") = Agreement.check(text, language).map { it.word to it.fix }

    @Test
    fun theNounDecidesNotTheArticle() {
        // The screenshot: wrong article, wrong demonstrative, and "cuidad" for "ciudad".
        val hits = Agreement.check("Este es el cuidad", "es")
        assertEquals(listOf("Este" to "Esta", "el" to "la", "cuidad" to "ciudad"), hits.map { it.word to it.fix })
        assertEquals(Kind.NOT_A_NOUN, hits[2].kind)
        val article = hits[1]
        assertEquals(Reason.ENDING_ALWAYS, article.reason)
        assertEquals("dad", article.ending)
        assertEquals("ciudad", article.evidence)
        assertTrue(article.feminine)
        assertEquals(Reason.ENDING_ALWAYS, hits[0].reason)
        assertEquals("este/esta", hits[0].pair)

        assertEquals(listOf("la" to "el"), fixes("la problema"))
        assertEquals(Reason.EXCEPTION, Agreement.check("la problema", "es").single().reason)
        assertEquals(listOf("Esta" to "Este", "una" to "un"), fixes("Esta es una coche"))
        assertEquals(listOf("este" to "esta"), fixes("este ciudad"))
        assertEquals(listOf("los" to "las"), fixes("los canciones"))
    }

    @Test
    fun demonstrativeAfterIs() {
        assertEquals(listOf("este" to "esta"), fixes("Hola este es una prueba"))
        assertEquals(listOf("Esta" to "Este"), fixes("Esta es un problema"))
        assertEquals(listOf("este" to "esta"), fixes("este no es la casa"))
        assertEquals(listOf("Estos" to "Estas"), fixes("Estos son unas ideas"))
        assertEquals(listOf("ese" to "esa"), fixes("ese fue una buena idea"))
        // Not typed yet: the article is the only clue.
        val early = Agreement.check("este es una", "es").single()
        assertEquals(Reason.ARTICLE, early.reason)
        assertEquals("una", early.evidence)
    }

    @Test
    fun stressedA() {
        assertEquals(listOf("la" to "el"), fixes("la agua"))
        assertEquals(Reason.STRESSED_A, Agreement.check("la agua", "es").single().reason)
        assertEquals(listOf("este" to "esta"), fixes("este agua"))
        for (ok in listOf("el agua", "un arma", "las aguas", "esta agua", "esta es el agua", "esta es un arma")) {
            assertTrue(ok, fixes(ok).isEmpty())
        }
    }

    @Test
    fun correctOrUnclearSpanishIsLeftAlone() {
        for (ok in listOf(
            "esta es una prueba", "este es un problema", "esto es una prueba", "este es mi libro", "estas son las mías",
            "este es", "la ciudad", "el día", "una mano", "un idioma", "el mapa", "la flor",
            // Pronouns and verbs, not articles and nouns:
            "la trabajo", "la cambio", "el trabaja mucho", "ese trabaja aquí", "esta lloviendo", "la vino a buscar",
            // Either gender:
            "la artista", "el estudiante", "un pirata", "la radio", "el radio",
        )) {
            assertTrue(ok, fixes(ok).isEmpty())
        }
    }

    @Test
    fun genderLookup() {
        assertTrue(Agreement.gender("ciudades", "es")!!.feminine)
        assertTrue(Agreement.gender("ciudades", "es")!!.plural)
        assertTrue(!Agreement.gender("coches", "es")!!.feminine)
        assertTrue(Agreement.gender("luces", "es")!!.feminine)
        assertTrue(Agreement.gender("canciones", "es")!!.feminine)
        assertNull(Agreement.gender("artista", "es"))
    }

    @Test
    fun portugueseAndItalian() {
        assertEquals(listOf("este" to "esta"), fixes("este é uma casa", "pt"))
        assertTrue(fixes("esta é uma casa", "pt").isEmpty())
        assertEquals(listOf("um" to "uma"), fixes("um cidade", "pt"))
        assertTrue(fixes("vou a pé", "pt").isEmpty())
        assertEquals(listOf("questo" to "questa"), fixes("questo è una prova", "it"))
        assertEquals(listOf("questo" to "questa"), fixes("questo è un'amica", "it"))
        assertTrue(fixes("questa è un'amica", "it").isEmpty())
        assertTrue(fixes("questo è l'albero", "it").isEmpty())
        assertEquals(listOf("la" to "il"), fixes("la problema", "it"))
        assertEquals(listOf("una" to "uno"), fixes("una zaino", "it"))
        assertEquals(listOf("il" to "la"), fixes("il città", "it"))
    }

    @Test
    fun otherLanguagesAreNotChecked() {
        assertTrue(fixes("this is a test", "en").isEmpty())
    }
}
