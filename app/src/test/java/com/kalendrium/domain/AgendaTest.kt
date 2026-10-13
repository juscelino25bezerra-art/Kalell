// Kalendrium — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalendrium.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Regras do redesign 6.0: repetição, turno, conflitos, estatísticas, simulação, busca e compatibilidade do formato. */
class AgendaTest {
    @Before fun pt() { I18n.lang = Lang.PT }

    // 4×1 começando em 01/10/2026: 01–04 trabalho, 05 folga, 06–09 trabalho, 10 folga…
    private val sch = Schedule("", "Escala A", "#65558f", "2026-10-01", listOf(Block(4, 1)), emptyList(), shiftStart = "07:00", shiftEnd = "15:00")
    private val off: (LocalDate) -> Boolean = { Agenda.isOff(it, sch) }
    private fun d(s: String) = Dates.parse(s)
    private fun ev(date: String, repeat: Repeat = Repeat.NONE, until: String = "", remind: Int = -1, title: String = "Academia") =
        Event("e1", date, title, allDay = false, start = "18:00", end = "19:00", remind = remind, repeat = repeat, until = until)

    @Test fun `folga do ciclo e periodo contam como dia livre`() {
        assertFalse(Agenda.isOff(d("2026-10-01"), sch))
        assertTrue(Agenda.isOff(d("2026-10-05"), sch))
        val ferias = sch.copy(periods = listOf(Period("p1", "2026-10-06", "2026-10-07", "Férias", "#1a73e8")))
        assertTrue(Agenda.isOff(d("2026-10-06"), ferias))
        assertFalse(Agenda.isOff(d("2026-10-05"), null))
    }

    @Test fun `repeticoes semanal mensal diaria e ate`() {
        val w = ev("2026-10-01", Repeat.WEEKLY)
        assertTrue(Agenda.occursOn(w, d("2026-10-01"), off))
        assertTrue(Agenda.occursOn(w, d("2026-10-08"), off))
        assertFalse(Agenda.occursOn(w, d("2026-10-09"), off))
        assertFalse(Agenda.occursOn(w, d("2026-09-24"), off))
        val m = ev("2026-01-31", Repeat.MONTHLY)
        assertTrue(Agenda.occursOn(m, d("2026-03-31"), off))
        assertFalse(Agenda.occursOn(m, d("2026-02-28"), off))
        val until = ev("2026-10-01", Repeat.DAILY, until = "2026-10-03")
        assertTrue(Agenda.occursOn(until, d("2026-10-03"), off))
        assertFalse(Agenda.occursOn(until, d("2026-10-04"), off))
    }

    @Test fun `nas minhas folgas segue a escala`() {
        val e = ev("2026-10-01", Repeat.OFF_DAYS)
        val p = PersonalData(listOf(e))
        assertEquals(1, Agenda.eventsOn(p, d("2026-10-01"), off).size) // data inicial sempre conta
        assertTrue(Agenda.eventsOn(p, d("2026-10-02"), off).isEmpty())
        val o = Agenda.eventsOn(p, d("2026-10-05"), off).single()
        assertEquals("2026-10-05", o.date)
        assertEquals("e1", o.id)
        assertEquals(listOf("2026-10-01", "2026-10-05", "2026-10-10"), Agenda.occurrences(p, d("2026-10-01"), d("2026-10-12"), off).map { it.date })
    }

    @Test fun `lembretes expandem as repeticoes com id proprio`() {
        val p = PersonalData(listOf(ev("2026-10-01", Repeat.WEEKLY, remind = 10), ev("2026-10-02").copy(id = "e2")))
        val r = Agenda.reminderEvents(p, d("2026-10-03"), off, days = 14)
        assertEquals(listOf("e1@2026-10-08", "e1@2026-10-15"), r.map { it.id })
        assertEquals(LocalDateTime.of(2026, 10, 8, 17, 50), r.first().reminderAt())
    }

    @Test fun `turno proximo turno e conflito`() {
        assertEquals(480L, Agenda.shiftMinutes(sch))
        assertEquals("07:00 – 15:00", Agenda.shiftText(sch))
        assertNull(Agenda.shiftOn(d("2026-10-05"), sch))
        // durante o turno: devolve o turno em andamento
        assertEquals(LocalDateTime.of(2026, 10, 2, 7, 0), Agenda.nextShift(LocalDateTime.of(2026, 10, 2, 10, 0), sch)!!.start)
        // depois do último turno do bloco: pula a folga
        assertEquals(LocalDateTime.of(2026, 10, 6, 7, 0), Agenda.nextShift(LocalDateTime.of(2026, 10, 4, 16, 0), sch)!!.start)
        assertNotNull(Agenda.conflict("2026-10-02", "14:00", "16:00", sch))
        assertNull(Agenda.conflict("2026-10-02", "15:00", "16:00", sch))
        assertNull(Agenda.conflict("2026-10-05", "08:00", "", sch))
        assertNull(Agenda.nextShift(LocalDateTime.of(2026, 10, 2, 10, 0), sch.copy(shiftStart = "", shiftEnd = "")))
    }

    @Test fun `turno noturno passa da meia noite e conflita na manha seguinte`() {
        val night = sch.copy(shiftStart = "19:00", shiftEnd = "07:00")
        assertEquals(720L, Agenda.shiftMinutes(night))
        // 04/10 é trabalho, 05/10 folga: o turno de 04 avança até 05 às 07:00
        assertNotNull(Agenda.conflict("2026-10-05", "06:00", "06:30", night))
        assertNull(Agenda.conflict("2026-10-05", "08:00", "09:00", night))
    }

    @Test fun `bloco atual e estatisticas do mes`() {
        val b = Agenda.blockInfo(d("2026-10-03"), sch)
        assertEquals(d("2026-10-01"), b.start)
        assertEquals(d("2026-10-05"), b.end)
        assertEquals(2, b.workLeft)
        assertEquals(0, Agenda.blockInfo(d("2026-10-05"), sch).workLeft)
        val st = Agenda.monthStats(d("2026-10-15"), sch)
        // outubro/2026 em 4×1 a partir do dia 1: folgas nos dias 5, 10, 15, 20, 25 e 30
        assertEquals(25, st.work)
        assertEquals(6, st.off)
        assertEquals(25 * 480L, st.minutes)
    }

    @Test fun `simulacao preserva o passado e descarta mudancas futuras`() {
        val withChanges = sch.copy(changes = listOf(Change("2026-10-02", 1), Change("2026-10-20", 3)))
        val from = Agenda.simulationStart(d("2026-10-12"), withChanges)
        assertEquals(d("2026-10-13"), from)
        val s = Agenda.simulate(withChanges, listOf(Block(5, 2)), from)
        assertEquals(listOf(Block(5, 2)), s.blocks)
        assertEquals("2026-10-13", s.anchor)
        assertTrue(s.changes.isEmpty())
        assertEquals("2026-10-13", s.history.single().to)
        // antes de 13/10 nada muda; a partir dele, 5 dias de trabalho e 2 de folga
        for (i in 1..12) assertEquals(Engine.status(d("2026-10-%02d".format(i)), withChanges).work, Engine.status(d("2026-10-%02d".format(i)), s).work)
        assertEquals(listOf(true, true, true, true, true, false, false), (13..19).map { Engine.status(d("2026-10-$it"), s).work })
        // com um período preservado depois de amanhã, a simulação começa no dia seguinte a ele
        assertEquals(d("2026-11-02"), Agenda.simulationStart(d("2026-10-12"), s.copy(history = listOf(Era("2026-11-01", "2026-01-01", listOf(Block(4, 1)), emptyList())))))
    }

    @Test fun `busca ignora acento e mostra a proxima ocorrencia`() {
        val p = PersonalData(
            listOf(ev("2026-09-01", Repeat.WEEKLY, title = "Reunião de equipe"), ev("2026-10-20", title = "Dentista").copy(id = "e2", location = "Clínica Central")),
            mapOf("2026-10-04" to DayNote("Levar documentos da reuniao", "#f9ab00")),
        )
        val hits = Agenda.search("reuniao", p, sch, d("2026-10-03"), off)
        assertEquals(2, hits.size)
        assertEquals("2026-10-04", hits[0].date)
        // 01/09/2026 é terça: a próxima terça a partir de 03/10 é 06/10
        assertEquals("2026-10-06", (hits[1] as SearchHit.EventHit).event.date)
        assertEquals("e2", (Agenda.search("clinica", p, sch, d("2026-10-03"), off).single() as SearchHit.EventHit).event.id)
        assertTrue(Agenda.search("  ", p, sch, d("2026-10-03"), off).isEmpty())
    }

    @Test fun `evento novo grava categoria repeticao e local e versoes antigas leem so o basico`() {
        val e = Event("e9", "2026-10-01", "Curso", category = Category.STUDY, repeat = Repeat.OFF_DAYS, until = "2026-12-31", location = "Online")
        val json = Personal.encode(PersonalData(listOf(e)))
        val back = Personal.load(json).events.single()
        assertEquals(e, back)
        // evento salvo antes da 6.0 (sem as chaves novas): Pessoal, sem repetição
        val old = Personal.load("""{"version":1,"events":[{"id":"a","date":"2026-10-01","title":"X","kind":"event","allDay":true,"color":"#1a73e8"}],"notes":{}}""").events.single()
        assertEquals(Category.PERSONAL, old.category)
        assertEquals(Repeat.NONE, old.repeat)
        // "until" antes da data ou repetição desconhecida são descartados
        val bad = Personal.load("""{"events":[{"id":"a","date":"2026-10-05","title":"X","repeat":"hourly","until":"2026-10-01"}]}""").events.single()
        assertEquals(Repeat.NONE, bad.repeat)
        assertEquals("", bad.until)
    }

    @Test fun `paleta nova e horario do turno ficam em chaves que o PWA ignora`() {
        val d = AppData.default("2026-10-01").copy(initialized = true, palette = "kalendrium").let { it.replace(it.main.copy(shiftStart = "07:00", shiftEnd = "15:00")) }
        val raw = Codec.encodeBackup(d)
        assertTrue(raw.contains("\"palette\": \"material\""))
        assertTrue(raw.contains("\"paletteX\": \"kalendrium\""))
        val back = Codec.importBackup(raw, "2026-10-01")!!
        assertEquals("kalendrium", back.palette)
        assertEquals("07:00", back.main.shiftStart)
        assertEquals("15:00", back.main.shiftEnd)
        // sem "paletteX" (PWA, versões antigas) vale "palette"; horário inválido é ignorado sem recusar o backup
        val pwa = raw.replace("\"paletteX\": \"kalendrium\",", "").replace("\"15:00\"", "\"25:00\"")
        val old = Codec.importBackup(pwa, "2026-10-01")!!
        assertEquals("material", old.palette)
        assertEquals("", old.main.shiftStart)
        // paletteX desconhecida reprova o backup na importação estrita
        assertNull(Codec.importBackup(raw.replace("\"paletteX\": \"kalendrium\"", "\"paletteX\": \"neon\""), "2026-10-01"))
    }
}
