// Kalendrium — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalendrium.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Port dos testes de regressão do PWA (tests/run.js) + compatibilidade de backup. */
class EngineTest {
    private val today = "2026-10-03"
    private fun j(s: String): JsonElement = Json.parseToJsonElement(s)
    private val base = """"initialized":true,"anchor":"2026-01-01","blocks":[{"w":4,"o":1}],"changes":[],"history":[],"notes":{},"periods":[]"""

    @Test fun `remap - remover bloco do meio nao reatribui mudanca a outro bloco`() {
        val ob = listOf(Block(4, 2), Block(4, 1), Block(5, 1))
        val nb = listOf(Block(4, 2), Block(5, 1))
        val ps = Engine.positions(ob)
        val inB1 = ps.first { it.bi == 1 }.idx
        val inB2 = ps.first { it.bi == 2 }.idx
        val r = Engine.remapChanges(ob, nb, listOf(Change("2026-10-10", inB1), Change("2026-10-11", inB2)), listOf(0, 2))
        assertEquals(1, r.dropped)
        assertEquals(1, r.changes.size)
        assertEquals(1, Engine.positions(nb)[r.changes[0].pos].bi)
    }

    @Test fun `remap - sem origs mantem indice`() {
        val b = listOf(Block(4, 1))
        assertEquals(listOf(Change("2026-01-01", 2)), Engine.remapChanges(b, b, listOf(Change("2026-01-01", 2))).changes)
    }

    @Test fun `num e cleanBlocks rejeitam hex, expoente e ciclo maior que 1000 dias`() {
        assertTrue(Sanitize.num(j("\"0x10\"")).isNaN())
        assertTrue(Sanitize.num(j("\"1e1\"")).isNaN())
        assertEquals(4.0, Sanitize.num(j("\" 4 \"")), 0.0)
        assertNull(Sanitize.cleanBlocks(j("""[{"w":"0x10","o":1}]""")))
        assertNull(Sanitize.cleanBlocks(j("[" + List(100) { """{"w":99,"o":99}""" }.joinToString(",") + "]")))
        assertNotNull(Sanitize.cleanBlocks(j("""[{"w":99,"o":99}]""")))
    }

    @Test fun `import estrito - limites de grupos e de notas`() {
        fun g(i: Int) = """{"id":"g$i","name":"x","color":"#00ff00","anchor":"2026-01-01","blocks":[{"w":4,"o":1}]}"""
        assertNotNull(Sanitize.sanitize(j("{$base,\"groups\":[${(0 until 19).joinToString(",") { g(it) }}]}"), true, today))
        assertNull(Sanitize.sanitize(j("{$base,\"groups\":[${(0 until 20).joinToString(",") { g(it) }}]}"), true, today))
        val notes = (0 until 5001).joinToString(",") { "\"${Dates.iso(LocalDate.of(2000, 1, 1).plusDays(it.toLong()))}\":{\"text\":\"x\"}" }
        assertNull(Sanitize.sanitize(j("""{"initialized":true,"anchor":"2026-01-01","blocks":[{"w":4,"o":1}],"notes":{$notes}}"""), true, today))
    }

    @Test fun `seguranca - __proto__ e datas invalidas`() {
        assertNull(Sanitize.sanitize(j("""{"initialized":true,"anchor":"2026-01-01","blocks":[{"w":4,"o":1}],"notes":{"__proto__":{"text":"x"}}}"""), true, today))
        assertFalse(Dates.isDate("0050-01-01"))
        assertFalse(Dates.isDate("275760-09-13"))
        assertFalse(Dates.isDate("2026-02-30"))
    }

    @Test fun `padrao novo e neutro e valido`() {
        val d = AppData.default(today)
        assertFalse(d.initialized)
        assertNotEquals("2027-10-01", d.main.anchor)
        assertTrue(Sanitize.validStored(Codec.toJson(d)))
    }

    @Test fun `status - era preservada continua valendo antes do limite`() {
        val s = Schedule(
            id = "", name = "A", color = "#65558f", anchor = "2026-06-01", blocks = listOf(Block(4, 1)), changes = emptyList(),
            history = listOf(Era("2026-09-01", "2026-01-01", listOf(Block(5, 2)), emptyList())),
        )
        assertEquals("2026-09-01", Engine.lastTo(s))
        assertEquals(5, Engine.status(Dates.parse("2026-01-06"), s).block.w)
        assertEquals(4, Engine.status(Dates.parse("2026-09-01"), s).block.w)
    }

    @Test fun `notas e periodos sao separados por escala`() {
        val g = """{"id":"g1","name":"Amor","color":"#00ff00","anchor":"2026-01-01","blocks":[{"w":2,"o":2}],"notes":{"2026-10-01":{"text":"só aqui","holiday":false}},"periods":[{"id":"p1","start":"2026-10-01","end":"2026-10-03","title":"Férias","color":"#1a73e8"}]}"""
        val d = Sanitize.sanitize(j("""{"initialized":true,"anchor":"2026-01-01","blocks":[{"w":4,"o":1}],"changes":[],"history":[],"periods":[],"notes":{"2026-10-02":{"text":"principal","holiday":false}},"groups":[$g]}"""), true, today)!!
        assertTrue(d.main.notes.containsKey("2026-10-02") && !d.main.notes.containsKey("2026-10-01"))
        assertTrue(d.groups[0].notes.containsKey("2026-10-01") && !d.groups[0].notes.containsKey("2026-10-02"))
        assertEquals(1, d.groups[0].periods.size)
        assertEquals(0, d.main.periods.size)
        val old = Sanitize.sanitize(j("""{$base,"groups":[{"id":"g1","name":"X","anchor":"2026-01-01","blocks":[{"w":4,"o":1}]}]}"""), true, today)!!
        assertEquals(emptyMap<String, Note>(), old.groups[0].notes)
        assertEquals(emptyList<Period>(), old.groups[0].periods)
        assertNull(Sanitize.sanitize(j(g.let { """{$base,"groups":[${it.replace("\"notes\":{\"2026-10-01\":{\"text\":\"só aqui\",\"holiday\":false}}", "\"notes\":{\"x\":1}")}]}""" }), true, today))
    }

    @Test fun `ciclo composto de 28 dias continua entre meses e anos`() {
        val blocks = listOf(Block(4, 2), Block(4, 1), Block(4, 2), Block(4, 1), Block(5, 1))
        assertEquals(28, Engine.cycleLen(blocks))
        val s = Schedule("", "A", "#65558f", "2026-01-01", blocks, emptyList())
        val d0 = Dates.parse("2026-01-01")
        for (i in 0L until 400L) {
            assertEquals(Engine.status(d0.plusDays(i), s).idx, Engine.status(d0.plusDays(i + 28), s).idx)
        }
        assertTrue(Engine.status(d0, s).work)
        assertFalse(Engine.status(d0.plusDays(4), s).work)
        // antes da âncora o ciclo também é projetado
        assertEquals(27, Engine.status(d0.minusDays(1), s).idx)
    }

    @Test fun `mudanca recalcula somente dali em diante`() {
        val s = Schedule("", "A", "#65558f", "2026-01-01", listOf(Block(4, 1)), listOf(Change("2026-01-10", 4)))
        assertEquals(Engine.status(Dates.parse("2026-01-09"), s.copy(changes = emptyList())).idx, Engine.status(Dates.parse("2026-01-09"), s).idx)
        assertEquals(4, Engine.status(Dates.parse("2026-01-10"), s).idx)
        assertEquals(0, Engine.status(Dates.parse("2026-01-11"), s).idx)
    }

    @Test fun `backup do PWA ida e volta sem perdas`() {
        val raw = """{"app":"minha-escala","version":3,"initialized":true,"anchor":"2027-10-01","blocks":[{"w":4,"o":2},{"w":4,"o":1,"name":"Noturno"},{"w":5,"o":1}],
            "changes":[{"date":"2026-10-17","pos":3}],"history":[{"to":"2026-01-01","anchor":"2025-01-01","blocks":[{"w":5,"o":2}],"changes":[]}],
            "notes":{"2026-10-17":{"text":"EXTRA","holiday":false,"color":"#f9ab00","align":"center"},"2026-12-25":{"text":"","holiday":true}},
            "periods":[{"id":"p1","start":"2026-12-01","end":"2026-12-30","title":"Férias","color":"#1a73e8"}],
            "theme":"light","palette":"nord","uiStyle":"glass","name":"Escala A","color":"#1a73e8",
            "groups":[{"id":"g1","name":"Escala B","color":"#d93025","anchor":"2026-01-01","blocks":[{"w":2,"o":2}],"changes":[],"history":[],"notes":{},"periods":[]}],"active":"g1"}"""
        val d = Codec.importBackup(raw, today)!!
        assertEquals("nord", d.palette)
        assertEquals("glass", d.uiStyle)
        assertEquals("g1", d.active)
        assertEquals("Escala B", d.current.name)
        assertEquals("Noturno", d.main.blocks[1].name)
        val again = Codec.importBackup(Codec.encodeBackup(d), today)!!
        assertEquals(d, again)
        assertEquals(d, Codec.load(Codec.encode(d), today).data)
        assertNull(Codec.load(Codec.encode(d), today).warning)
    }

    @Test fun `dados corrompidos voltam ao padrao com aviso`() {
        val l = Codec.load("{isso não é json", today)
        assertFalse(l.data.initialized)
        assertNotNull(l.warning)
        val l2 = Codec.load("""{$base,"groups":[{"id":"g1"}]}""", today)
        assertTrue(l2.warning!!.contains("1 escala(s)"))
    }

    @Test fun `datas em portugues`() {
        val d = Dates.parse("2026-10-06")
        assertEquals("6 de outubro", Dates.dayMonth(d))
        assertEquals("terça-feira, 6 de outubro", Dates.weekdayDayMonth(d))
        assertEquals("outubro", Dates.monthName(d))
        assertEquals("06/10/2026", Dates.fmt(d))
    }
}
