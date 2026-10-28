// Kalell — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalell.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Testes de regressão das correções da auditoria (seção H). */
class AuditTest {
    private val today = "2026-10-03"
    private fun d(s: String) = Dates.parse(s)
    private fun sch(blocks: List<Block>, anchor: String = "2026-01-01", changes: List<Change> = emptyList()) =
        Schedule("", "Escala A", "#65558f", anchor, blocks, changes)

    // ---------- C1: remapChanges respeita trabalho × folga ----------
    @Test fun `C1 encolher o trabalho mantem a mudanca em dia de trabalho`() {
        val ob = listOf(Block(4, 1))
        val nb = listOf(Block(2, 1))
        val r = Engine.remapChanges(ob, nb, listOf(Change("2026-03-01", 2)), listOf(0)) // 3º trabalho
        val p = Engine.positions(nb)[r.changes.single().pos]
        assertTrue(p.work)
        assertEquals(2, p.n)          // último dia de trabalho do bloco novo
        assertEquals(1, r.moved)      // o rótulo mudou ("3º trabalho" → "2º trabalho"): conta como movida
    }

    @Test fun `C1 encolher a folga mantem folga`() {
        val ob = listOf(Block(4, 3))
        val nb = listOf(Block(4, 1))
        val r = Engine.remapChanges(ob, nb, listOf(Change("2026-03-01", 6)), listOf(0)) // 3º folga
        val p = Engine.positions(nb)[r.changes.single().pos]
        assertFalse(p.work)
        assertEquals(1, p.n)
    }

    @Test fun `C1 aumentar o trabalho nao transforma folga em trabalho`() {
        val ob = listOf(Block(2, 2))
        val nb = listOf(Block(5, 2))
        val r = Engine.remapChanges(ob, nb, listOf(Change("2026-03-01", 2), Change("2026-03-05", 1)), listOf(0))
        val ps = Engine.positions(nb)
        assertEquals(listOf(false to 1, true to 2), r.changes.map { ps[it.pos].work to ps[it.pos].n })
    }

    @Test fun `C1 blocos reordenados e bloco novo`() {
        val ob = listOf(Block(4, 1), Block(2, 2))
        val nb = listOf(Block(3, 3), Block(2, 2), Block(4, 1))  // novo, depois os dois antigos trocados
        val ps0 = Engine.positions(ob)
        val c = listOf(Change("2026-03-01", ps0.indexOfFirst { it.bi == 1 && !it.work }))  // 1º folga do 2×2
        val r = Engine.remapChanges(ob, nb, c, listOf(-1, 1, 0))
        val p = Engine.positions(nb)[r.changes.single().pos]
        assertEquals(1, p.bi)
        assertFalse(p.work)
        assertEquals(1, p.n)
        assertEquals(0, r.dropped)
    }

    @Test fun `C1 mudanca sem alteracao real nao conta como movida`() {
        val b = listOf(Block(4, 1))
        val r = Engine.remapChanges(b, listOf(Block(4, 1, "Dia")), listOf(Change("2026-03-01", 3)), listOf(0))
        assertEquals(0, r.moved)
        assertEquals(3, r.changes.single().pos)
    }

    // ---------- C2: calendário pessoal corrompido ----------
    @Test fun `C2 JSON truncado vazio ou de outro tipo avisa e devolve o original`() {
        listOf("""{"events":[""", "", "   ", "[1,2]", """{"events":{}}""", "42").forEach { raw ->
            val l = Personal.loadChecked(raw)
            assertNotNull("sem aviso para $raw", l.warning)
            assertEquals(raw, l.corruptRaw)
            assertTrue(l.data.events.isEmpty())
        }
    }

    @Test fun `C2 itens invalidos sao contados e o original e guardado`() {
        val raw = """{"events":[{"id":"a","date":"2026-10-03","title":"Ok"},{"id":"b","date":"x","title":"Ruim"}],"notes":{"2026-10-03":{"text":"n"},"zz":{"text":"m"}}}"""
        val l = Personal.loadChecked(raw)
        assertEquals(1, l.data.events.size)
        assertEquals(1, l.data.notes.size)
        assertTrue(l.warning!!.contains("1 evento(s)") && l.warning!!.contains("1 nota(s)"))
        assertEquals(raw, l.corruptRaw)
    }

    @Test fun `C2 arquivo inexistente ou valido nao avisa`() {
        assertNull(Personal.loadChecked(null).warning)
        val ok = Personal.encode(PersonalData(listOf(Event("a", "2026-10-03", "T"))))
        val l = Personal.loadChecked(ok)
        assertNull(l.warning)
        assertNull(l.corruptRaw)
    }

    // ---------- C3: arquivo vazio não é instalação nova ----------
    @Test fun `C3 texto vazio avisa em vez de iniciar como instalacao nova`() {
        val l = Codec.load("", today)
        assertNotNull(l.warning)
        assertEquals("", l.corruptRaw)
        assertNull(Codec.load(null, today).warning)
    }

    // ---------- backup: ida e volta completa ----------
    private fun fullData(): AppData {
        val main = Schedule(
            "", "Principal", "#123456", "2026-02-01", listOf(Block(5, 2, "Dia"), Block(3, 3)), listOf(Change("2026-05-01", 4)),
            history = listOf(Era("2026-02-01", "2025-01-01", listOf(Block(4, 1)), listOf(Change("2025-06-01", 2)))),
            notes = mapOf("2026-12-25" to Note("Natal", true, "#d93025", "left"), "2026-03-03" to Note("Consulta", false)),
            periods = listOf(Period("p1", "2026-07-01", "2026-07-30", "Férias", "#1a73e8")),
        )
        val groups = (1..19).map { i -> Schedule("g$i", "Escala $i", "#0b8043", "2026-01-0${1 + i % 9}", listOf(Block(1 + i % 5, 1 + i % 3)), emptyList()) }
        return AppData(true, "dark", "nord", "glass", listOf(main) + groups, "g3", dynamicColor = true, hideRecents = true)
    }

    private fun fullPersonal() = PersonalData(
        listOf(
            Event("e1", "2026-10-03", "Dentista", EventKind.APPOINTMENT, false, "14:00", "15:00", "Rua X", "#9334e6", 30),
            Event("e2", "2026-10-03", "Aniversário", EventKind.EVENT, true, color = "#1a73e8", remind = 1440),
            Event("e3", "2026-10-04", "Tomar remédio", EventKind.REMINDER, false, "08:00", remind = 0),
        ),
        mapOf("2026-10-03" to DayNote("Levar documentos", "#f9ab00")),
    )

    @Test fun `backup ida e volta com os dois modos sem perdas`() {
        val data = fullData()
        val pers = fullPersonal()
        val raw = Codec.encodeBackup(data, pers)
        assertEquals(data, Codec.importBackup(raw, today))
        // a ordem dos eventos no arquivo segue data/horário; o conteúdo é o mesmo
        val back = Codec.personalFromBackup(raw)!!
        assertEquals(pers.events.sortedBy { it.id }, back.events.sortedBy { it.id })
        assertEquals(pers.notes, back.notes)
        // o armazenamento local também
        assertEquals(data, Codec.load(Codec.encode(data), today).data)
    }

    @Test fun `backup do PWA sem personal importa e nao traz dados pessoais`() {
        val pwa = """{"app":"minha-escala","version":3,"initialized":true,"anchor":"2026-01-01","blocks":[{"w":4,"o":1}],"changes":[],"history":[],"notes":{},"periods":[],"theme":"system","palette":"material","uiStyle":"material","name":"Escala A","color":"#65558f","groups":[],"active":""}"""
        assertNotNull(Codec.importBackup(pwa, today))
        assertNull(Codec.personalFromBackup(pwa))
    }

    @Test fun `chaves novas tem tipo validado e o padrao e falso`() {
        val ok = Codec.encode(AppData.default(today).copy(initialized = true))
        assertFalse(Codec.load(ok, today).data.dynamicColor)
        val bad = ok.dropLast(1) + ""","dynamicColor":"sim"}"""
        assertNull(Codec.importBackup(bad, today))
    }

    // ---------- C7 / D2: limites ----------
    @Test fun `C7 backup com 5000 eventos e 5000 notas cabe no limite e importa`() {
        val events = (0 until Personal.MAX_EVENTS).map { i ->
            val day = LocalDate.of(2026, 1, 1).plusDays((i % 700).toLong())
            Event("e$i", Dates.iso(day), "Compromisso número $i com um título médio", EventKind.APPOINTMENT, false, "10:00", "11:00",
                "Descrição de tamanho médio para o evento $i, com algum texto a mais.", "#9334e6", 30)
        }
        val notes = (0 until AppData.MAX_NOTES).associate { i -> Dates.iso(LocalDate.of(2010, 1, 1).plusDays(i.toLong())) to Note("Nota $i", i % 7 == 0) }
        val data = AppData.default(today).copy(initialized = true).let { it.replace(it.main.copy(notes = notes)) }
        val raw = Codec.encodeBackup(data, PersonalData(events))
        assertTrue("backup com ${raw.length} bytes", raw.toByteArray().size < 10_000_000)
        assertEquals(data, Codec.importBackup(raw, today))
        assertEquals(Personal.MAX_EVENTS, Codec.personalFromBackup(raw)!!.events.size)
    }

    @Test fun `D2 cleanPeriods ordena antes de cortar`() {
        val many = (0 until 201).map { i -> Period("p$i", Dates.iso(LocalDate.of(2027, 1, 1).minusDays(i.toLong())), "2027-12-31", "", "#1a73e8") }
        val out = Sanitize.cleanPeriods(many)
        assertEquals(AppData.MAX_PERIODS, out.size)
        assertEquals(out.sortedBy { it.start }, out)
    }

    // ---------- C5: manter os dias anteriores ----------
    @Test fun `C5 dias anteriores ficam identicos e a nova escala comeca no 1o dia`() {
        val old = sch(listOf(Block(4, 1)), changes = listOf(Change("2026-06-10", 2), Change("2026-12-01", 0)))
        val from = "2026-11-01"
        val e = Engine.applyCycleEdit(old, listOf(Block(5, 2)), anchor = from, keep = true, from = from, origs = listOf(0))
        val s = e.schedule
        var day = d(from).minusYears(2)
        while (day < d(from)) {
            assertEquals(Engine.status(day, old).work, Engine.status(day, s).work)
            assertEquals(Engine.status(day, old).idx, Engine.status(day, s).idx)
            day = day.plusDays(1)
        }
        val first = Engine.status(d(from), s)
        assertTrue(first.work)
        assertEquals(1, first.n)
        // a era guarda só as mudanças anteriores a "from"; as posteriores foram para a escala nova
        assertEquals(listOf(Change("2026-06-10", 2)), s.history.single().changes)
        assertEquals(listOf("2026-12-01"), s.changes.map { it.date })
    }

    @Test fun `C5 sem manter recalcula tudo e limpa o historico`() {
        val old = sch(listOf(Block(4, 1))).copy(history = listOf(Era("2026-01-01", "2025-01-01", listOf(Block(2, 2)), emptyList())))
        val e = Engine.applyCycleEdit(old, listOf(Block(5, 2)), "2026-01-01", keep = false, from = "", origs = listOf(0))
        assertTrue(e.schedule.history.isEmpty())
    }

    // ---------- datas extremas ----------
    @Test fun `ancora em 29 de fevereiro e ciclos atravessando o ano`() {
        val s = sch(listOf(Block(4, 1)), anchor = "2028-02-29")
        assertTrue(Engine.status(d("2028-02-29"), s).work)
        assertFalse(Engine.status(d("2028-03-04"), s).work)           // 5º dia = folga
        assertTrue(Engine.status(d("2028-03-05"), s).work)
        // datas antes da âncora continuam o ciclo para trás
        assertFalse(Engine.status(d("2028-02-28"), s).work)
        // virada de ano: 365 dias depois de 01/01 cai na posição 365 % 5 = 0
        val y = sch(listOf(Block(4, 1)), anchor = "2026-01-01")
        assertEquals(0, Engine.status(d("2027-01-01"), y).idx)
    }

    @Test fun `mudanca no mesmo dia da ancora e eras seguidas`() {
        val s = sch(listOf(Block(4, 1)), anchor = "2026-01-01", changes = listOf(Change("2026-01-01", 4)))
        assertFalse(Engine.status(d("2026-01-01"), s).work)
        val eras = s.copy(
            anchor = "2026-03-01",
            history = listOf(
                Era("2026-02-01", "2026-01-01", listOf(Block(1, 1)), emptyList()),
                Era("2026-03-01", "2026-02-01", listOf(Block(2, 2)), emptyList()),
            ),
            changes = emptyList(),
        )
        assertTrue(Engine.status(d("2026-01-01"), eras).work)
        assertFalse(Engine.status(d("2026-01-02"), eras).work)        // 1×1
        assertTrue(Engine.status(d("2026-02-02"), eras).work)         // 2×2: 2º trabalho
        assertFalse(Engine.status(d("2026-02-03"), eras).work)
        assertTrue(Engine.status(d("2026-03-01"), eras).work)         // 4×1 começa na âncora
        assertEquals(listOf("1×1", "2×2", "4×1"), Engine.cyclesIn("2026-01-15", "2026-03-15", eras).map { Engine.cycleText(it) })
        assertEquals(listOf("4×1"), Engine.cyclesIn("2026-04-01", "2026-12-31", eras).map { Engine.cycleText(it) })
        assertEquals("1×1 → 2×2", Engine.cyclesText("2026-01-15", "2026-02-15", eras))
    }

    // ---------- C6: períodos ----------
    @Test fun `C6 periodo de hoje e volta ao trabalho`() {
        val s = sch(listOf(Block(4, 1)), anchor = "2026-10-01").copy(periods = listOf(Period("p", "2026-10-01", "2026-10-20", "Férias", "#1a73e8")))
        assertEquals("p", Engine.periodOn("2026-10-05", s)?.id)
        assertNull(Engine.periodOn("2026-10-21", s))
        // 20/10 é o 20º dia: posição 19 % 5 = 4 (folga); 21/10 volta ao trabalho
        assertEquals(d("2026-10-21"), Engine.nextWorkAfter(d("2026-10-20"), s))
        assertEquals("p", Engine.upcomingPeriod(d("2026-09-25"), s)?.id)
        assertNull(Engine.upcomingPeriod(d("2026-09-01"), s))
    }

    // ---------- lembretes ----------
    @Test fun `lembrete de dia inteiro um dia antes e antes da meia-noite`() {
        assertEquals(LocalDateTime.of(2026, 10, 2, 9, 0), Event("a", "2026-10-03", "T", allDay = true, remind = 1440).reminderAt())
        assertEquals(LocalDateTime.of(2026, 10, 2, 23, 40), Event("b", "2026-10-03", "T", allDay = false, start = "00:10", remind = 30).reminderAt())
        // horário que não existe na mudança de horário de verão: LocalDateTime não depende de fuso;
        // o AlarmManager converte com ZoneId.systemDefault() (ver Reminders.sync)
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), Event("c", "2026-11-01", "T", allDay = false, start = "00:00", remind = 0).reminderAt())
    }

    @Test fun `ids novos nao colidem`() {
        val seen = HashSet<String>()
        repeat(500) { seen.add(Ids.newId("p", 24, now = 0L) { it in seen }) }
        assertEquals(500, seen.size)
        assertTrue(seen.all { it.length <= 24 && it.startsWith("p") })
    }

    @Test fun `eventsOn usa o indice por data`() {
        val p = fullPersonal()
        assertEquals(listOf("e2", "e1"), p.eventsOn("2026-10-03").map { it.id })
        assertTrue(p.eventsOn("2030-01-01").isEmpty())
    }
}
