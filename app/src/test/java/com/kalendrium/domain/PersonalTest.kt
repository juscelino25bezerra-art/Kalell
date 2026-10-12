package com.kalendrium.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PersonalTest {
    private val sample = PersonalData(
        events = listOf(
            Event("e1", "2026-10-06", "Dentista", EventKind.APPOINTMENT, allDay = false, start = "14:00", end = "15:00", description = "Sala 4", color = "#9334e6", remind = 30),
            Event("e2", "2026-10-06", "Aniversário", EventKind.EVENT, color = "#1a73e8"),
            Event("e3", "2026-10-06", "Academia", allDay = false, start = "07:00"),
        ),
        notes = mapOf("2026-10-06" to DayNote("Levar documentos", "#f9ab00")),
    )

    @Test fun roundTrip() {
        assertEquals(sample.events.toSet(), Personal.load(Personal.encode(sample)).events.toSet())
        assertEquals(sample.notes, Personal.load(Personal.encode(sample)).notes)
    }

    @Test fun eventsOfDaySortedAllDayFirstThenByTime() {
        assertEquals(listOf("e2", "e3", "e1"), sample.eventsOn("2026-10-06").map { it.id })
        assertTrue(sample.eventsOn("2026-10-07").isEmpty())
    }

    @Test fun invalidItemsAreDropped() {
        val raw = """{"events":[
            {"id":"a","date":"2026-02-30","title":"data inválida"},
            {"id":"b","date":"2026-10-01","title":"   "},
            {"id":"c","date":"2026-10-01","title":"sem hora","allDay":false},
            {"id":"d","date":"2026-10-01","title":"ok","allDay":false,"start":"10:00","end":"09:00","remind":7,"color":"red"},
            {"id":"d","date":"2026-10-02","title":"id repetido"}],
            "notes":{"2026-10-01":{"text":"  "},"x":{"text":"a"},"2026-10-03":{"text":"nota"}}}"""
        val p = Personal.load(raw)
        assertEquals(1, p.events.size)
        val d = p.events[0]
        assertEquals("", d.end)        // fim antes do início é descartado
        assertEquals(-1, d.remind)     // antecedência fora da lista
        assertEquals("#1a73e8", d.color)
        assertEquals(setOf("2026-10-03"), p.notes.keys)
        assertTrue(Personal.load("lixo").events.isEmpty())
    }

    @Test fun reminderTimes() {
        assertEquals(LocalDateTime.of(2026, 10, 6, 13, 30), sample.events[0].reminderAt())
        assertNull(sample.events[1].reminderAt())
        assertEquals(LocalDateTime.of(2026, 10, 5, 9, 0), Event("x", "2026-10-06", "t", remind = 1440).reminderAt())
    }

    @Test fun backupCarriesPersonalDataAndPwaBackupsStillImport() {
        val work = AppData.default("2026-10-06")
        val raw = Codec.encodeBackup(work, sample)
        assertEquals(sample.events.toSet(), Codec.personalFromBackup(raw)!!.events.toSet())
        assertTrue(Codec.importBackup(raw, "2026-10-06") != null)
        assertNull(Codec.personalFromBackup(Codec.encodeBackup(work)))
    }

    @Test fun modeKeys() {
        assertEquals(CalendarMode.WORK, CalendarMode.of("work"))
        assertNull(CalendarMode.of("outro"))
    }
}
