// Kalendrium — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalendrium.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Utilitários de data equivalentes aos do PWA (datas sempre como texto "AAAA-MM-DD"). */
object Dates {
    private val ISO_RE = Regex("""^\d{4}-\d{2}-\d{2}$""")

    fun iso(d: LocalDate): String =
        d.year.toString().padStart(4, '0') + "-" + d.monthValue.toString().padStart(2, '0') + "-" + d.dayOfMonth.toString().padStart(2, '0')

    fun parse(s: String): LocalDate {
        val (y, m, d) = s.split("-").map { it.toInt() }
        return LocalDate.of(y, m, d)
    }

    /**
     * Data real no formato AAAA-MM-DD. Como no PWA (`new Date(y,m,d)` trata 0–99 como 19xx),
     * anos menores que 100 são recusados.
     */
    fun isDate(s: String?): Boolean {
        if (s == null || !ISO_RE.matches(s)) return false
        return try {
            val d = parse(s)
            d.year >= 100 && iso(d) == s
        } catch (_: Exception) {
            false
        }
    }

    /** Diferença em dias de calendário (b - a). */
    fun dd(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)

    fun mod(n: Long, m: Int): Int = (((n % m) + m) % m).toInt()

    /** Data curta: dd/mm/aaaa (pt, es) ou mm/dd/yyyy (en). */
    fun fmt(s: String): String = fmt(parse(s))
    fun fmt(d: LocalDate): String = d.format(pattern(shortPattern()))

    fun shortPattern(): String = if (I18n.lang == Lang.EN) "MM/dd/yyyy" else "dd/MM/yyyy"

    /** Texto de exemplo do formato curto, para campos vazios. */
    fun shortHint(): String = L("dd/mm/aaaa", "mm/dd/yyyy", "dd/mm/aaaa")

    private fun pattern(p: String) = DateTimeFormatter.ofPattern(p, I18n.locale)

    /** "3 de outubro" / "October 3" / "3 de octubre" */
    fun dayMonth(d: LocalDate): String = d.format(pattern(L("d 'de' MMMM", "MMMM d", "d 'de' MMMM")))

    /** "6 de out." / "Oct 6" / "6 oct" (semana na visualização semanal) */
    fun shortDayMonth(d: LocalDate): String = d.format(pattern(L("d MMM", "MMM d", "d MMM")))

    /** "terça-feira, 6 de outubro" / "Tuesday, October 6" / "martes, 6 de octubre" */
    fun weekdayDayMonth(d: LocalDate): String = d.format(pattern(L("EEEE, d 'de' MMMM", "EEEE, MMMM d", "EEEE, d 'de' MMMM")))

    /** "sábado, 03 de outubro de 2026" / "Saturday, October 3, 2026" / "sábado, 03 de octubre de 2026" */
    fun longDate(d: LocalDate): String = d.format(pattern(L("EEEE, dd 'de' MMMM 'de' yyyy", "EEEE, MMMM d, yyyy", "EEEE, dd 'de' MMMM 'de' yyyy")))

    /** "outubro" / "October" / "octubre" */
    fun monthName(d: LocalDate): String {
        val n = d.format(pattern("LLLL")).let {
            // alguns runtimes devolvem o número do mês para LLLL; garante o nome
            if (it.firstOrNull()?.isDigit() == true) d.format(pattern("MMMM")) else it
        }
        return if (I18n.lang == Lang.EN) capitalize(n) else n.lowercase(I18n.locale)
    }

    /** "Outubro de 2026" / "October 2026" / "Octubre de 2026" */
    fun monthYear(d: LocalDate): String = capitalize(monthName(d)) + L(" de ", " ", " de ") + d.year

    fun capitalize(s: String): String = s.replaceFirstChar { it.titlecase(I18n.locale) }
}
