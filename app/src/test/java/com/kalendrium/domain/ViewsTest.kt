// Kalendrium — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalendrium.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ViewsTest {
    @Test fun `semana comeca no domingo`() {
        val w = Views.weekOf(LocalDate.of(2026, 10, 7))    // quarta
        assertEquals(LocalDate.of(2026, 10, 4), w.first())
        assertEquals(LocalDate.of(2026, 10, 10), w.last())
        assertEquals(LocalDate.of(2026, 10, 4), Views.weekStart(LocalDate.of(2026, 10, 4)))
    }

    @Test fun `lista de eventos separa proximos e anteriores`() {
        val p = PersonalData(listOf(
            Event("a", "2026-10-03", "Hoje tarde", allDay = false, start = "15:00"),
            Event("b", "2026-10-03", "Hoje dia todo"),
            Event("c", "2026-10-01", "Antes"),
            Event("d", "2026-09-20", "Bem antes"),
            Event("e", "2026-11-01", "Depois"),
        ))
        assertEquals(listOf("b", "a", "e"), Views.eventList(p, "2026-10-03", past = false).map { it.id })
        assertEquals(listOf("c", "d"), Views.eventList(p, "2026-10-03", past = true).map { it.id })
    }

    @Test fun `lista do work inclui periodo em andamento`() {
        val s = Schedule(
            "", "X", "#000000", "2026-01-01", listOf(Block(4, 1)), listOf(Change("2026-10-05", 0), Change("2026-09-01", 0)),
            notes = mapOf("2026-10-12" to Note("Feriado", true)),
            periods = listOf(Period("p", "2026-09-28", "2026-10-08", "Férias", "#00838f")),
        )
        val next = Views.workList(s, "2026-10-03", past = false)
        assertEquals(listOf("2026-09-28", "2026-10-05", "2026-10-12"), next.map { it.date })
        assertEquals(listOf("2026-09-01"), Views.workList(s, "2026-10-03", past = true).map { it.date })
    }

    @Test fun `valor gravado invalido volta para mensal`() {
        assertEquals(ViewMode.MONTH, ViewMode.of(null))
        assertEquals(ViewMode.MONTH, ViewMode.of("x"))
        assertEquals(ViewMode.MONTH_DAY, ViewMode.of("month-day"))
    }
}
