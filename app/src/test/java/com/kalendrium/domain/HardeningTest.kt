package com.kalendrium.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Auditoria 5.3: a importação é uma fronteira de confiança (arquivo escolhido pelo usuário, vindo de qualquer lugar). */
class HardeningTest {
    private val today = "2026-10-03"

    @Test fun `JSON muito aninhado e recusado sem estourar a pilha`() {
        // antes: StackOverflowError (Error, não Exception) já com ~10 mil níveis = 20 KB, e o app fechava
        for (n in listOf(Codec.MAX_DEPTH + 1, 10_000, 1_000_000)) {
            val deep = "[".repeat(n) + "]".repeat(n)
            assertNull(Codec.parseOrNull(deep))
            assertNull(Codec.importBackup(deep, today))
            assertNull(Codec.personalFromBackup("""{"personal":$deep}"""))
        }
        val deepObj = """{"a":""".repeat(5_000) + "1" + "}".repeat(5_000)
        assertNull(Codec.parseOrNull(deepObj))
    }

    @Test fun `dados salvos muito aninhados avisam em vez de fechar o app`() {
        val deep = "[".repeat(50_000) + "]".repeat(50_000)
        val w = Codec.load(deep, today)
        assertNotNull(w.warning)
        assertEquals(deep, w.corruptRaw)
        val p = Personal.loadChecked("""{"events":${"[".repeat(50_000)}}""")
        assertNotNull(p.warning)
        assertTrue(p.data.events.isEmpty())
    }

    @Test fun `colchetes dentro de textos nao contam como aninhamento`() {
        val title = "[[[{{{\\\"]]]".repeat(100)
        val raw = """{"events":[{"id":"a","date":"$today","title":"$title"}]}"""
        val p = Personal.loadChecked(raw)
        assertNull(p.warning)
        assertEquals(1, p.data.events.size)
        assertTrue(Codec.depthWithin(raw, 3))
        assertFalse(Codec.depthWithin(raw, 2))
    }

    @Test fun `backup real mais fundo cabe no limite`() {
        val sch = AppData.default(today).main.copy(
            blocks = listOf(Block(4, 1, "Dia")),
            history = listOf(Era("2026-09-01", "2026-01-01", listOf(Block(2, 2)), listOf(Change("2026-02-01", 1)))),
        )
        val d = AppData.default(today).copy(initialized = true, schedules = listOf(sch, sch.copy(id = "g1")))
        val raw = Codec.encodeBackup(d, PersonalData(listOf(Event("e", today, "x"))))
        assertEquals(d.copy(hideRecents = false), Codec.importBackup(raw, today))
        assertTrue(Codec.depthWithin(raw, 7))
        assertFalse(Codec.depthWithin(raw, 6))
    }
}
