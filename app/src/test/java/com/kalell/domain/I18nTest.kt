// Kalell — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalell.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class I18nTest {
    private val d = LocalDate.of(2026, 10, 6)

    @After
    fun reset() { I18n.lang = Lang.PT }

    @Test
    fun systemLanguageIsFollowedWithEnglishFallback() {
        assertEquals(Lang.PT, I18n.resolve(null, Locale("pt", "BR")))
        assertEquals(Lang.PT, I18n.resolve("system", Locale("pt", "PT")))
        assertEquals(Lang.ES, I18n.resolve("system", Locale("es", "MX")))
        assertEquals(Lang.EN, I18n.resolve("system", Locale("en", "GB")))
        assertEquals(Lang.EN, I18n.resolve("system", Locale.FRANCE))
        assertEquals(Lang.EN, I18n.resolve("system", Locale.JAPAN))
    }

    @Test
    fun explicitChoiceWinsOverTheDevice() {
        assertEquals(Lang.ES, I18n.resolve("es", Locale("pt", "BR")))
        assertEquals(Lang.PT, I18n.resolve("pt", Locale.US))
        assertEquals(Lang.EN, I18n.resolve("en", Locale("es", "ES")))
        // arquivo de preferência estranho = segue o aparelho
        assertEquals("system", I18n.pref("xx"))
        assertEquals("system", I18n.pref(null))
        assertEquals("en", I18n.pref(" en\n"))
        assertEquals(Lang.PT, I18n.resolve("lixo", Locale("pt", "BR")))
    }

    @Test
    fun datesFollowTheLanguage() {
        I18n.lang = Lang.PT
        assertEquals("6 de outubro", Dates.dayMonth(d))
        assertEquals("06/10/2026", Dates.fmt(d))
        assertEquals("Outubro de 2026", Dates.monthYear(d))
        assertEquals("terça-feira, 6 de outubro", Dates.weekdayDayMonth(d))
        I18n.lang = Lang.EN
        assertEquals("October 6", Dates.dayMonth(d))
        assertEquals("10/06/2026", Dates.fmt(d))
        assertEquals("October 2026", Dates.monthYear(d))
        assertEquals("Tuesday, October 6", Dates.weekdayDayMonth(d))
        assertEquals("Tuesday, October 6, 2026", Dates.longDate(d))
        I18n.lang = Lang.ES
        assertEquals("6 de octubre", Dates.dayMonth(d))
        assertEquals("06/10/2026", Dates.fmt(d))
        assertEquals("Octubre de 2026", Dates.monthYear(d))
        assertEquals("martes, 6 de octubre", Dates.weekdayDayMonth(d))
    }

    @Test
    fun cycleLabelsFollowTheLanguage() {
        val b = Block(4, 2, "Noite")
        val ps = Engine.positions(listOf(b))
        I18n.lang = Lang.PT
        assertEquals("2º trabalho · bloco 4×2 · Noite", ps[1].label)
        assertEquals("1º folga · bloco 4×2 · Noite", ps[4].label)
        I18n.lang = Lang.EN
        assertEquals("2nd work day · block 4×2 · Noite", ps[1].label)
        assertEquals("1st day off · block 4×2 · Noite", ps[4].label)
        I18n.lang = Lang.ES
        assertEquals("2º trabajo · bloque 4×2 · Noite", ps[1].label)
        assertEquals("1º libre · bloque 4×2 · Noite", ps[4].label)
    }

    @Test
    fun englishOrdinals() {
        I18n.lang = Lang.EN
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd", "101st", "111th"),
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 101, 111).map { ordinal(it) })
    }

    @Test
    fun eventTextsFollowTheLanguage() {
        val e = Event("e1", "2026-10-06", "Dentista", EventKind.APPOINTMENT, allDay = true)
        I18n.lang = Lang.EN
        assertEquals("All day", e.timeText)
        assertEquals("Appointment", e.kind.label)
        assertEquals("1 hour before", Personal.REMINDERS.first { it.first == 60 }.second)
        assertEquals("Personal", CalendarMode.PERSONAL.label)
        I18n.lang = Lang.ES
        assertEquals("Todo el día", e.timeText)
        assertEquals("Cita", e.kind.label)
        assertEquals("Trabajo", CalendarMode.WORK.label)
        assertEquals(listOf("DOM", "LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB"), weekHeader())
        // o idioma não muda o que é gravado
        assertEquals(Personal.encode(PersonalData(listOf(e))), run { I18n.lang = Lang.PT; Personal.encode(PersonalData(listOf(e))) })
    }

    @Test
    fun loadWarningsFollowTheLanguage() {
        I18n.lang = Lang.EN
        assertEquals("The saved schedule data was empty and could not be read.", Codec.load(" ", "2026-10-06").warning)
        assertEquals("Schedule A", AppData.default("2026-10-06").main.name)
    }
}
