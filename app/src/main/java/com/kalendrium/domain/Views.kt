// Kalendrium — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalendrium.domain

import java.time.LocalDate

/** Modos de visualização do calendário (botão de grade ao lado do seletor Pessoal | Work). Valem para os dois contextos. */
enum class ViewMode(val key: String) {
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    MONTH_DAY("month-day"),
    YEAR("year"),
    LIST("list");

    val label: String get() = when (this) {
        DAY -> L("Diária", "Day", "Diaria")
        WEEK -> L("Semanal", "Week", "Semanal")
        MONTH -> L("Mensal", "Month", "Mensual")
        MONTH_DAY -> L("Mensal + diária", "Month + day", "Mensual + diaria")
        YEAR -> L("Anual", "Year", "Anual")
        LIST -> L("Lista de eventos", "Event list", "Lista de eventos")
    }

    companion object {
        const val KEY = "kalendrium.view"
        /** Mensal é o padrão (instalações novas e valor gravado inválido). */
        val DEFAULT = MONTH
        fun of(key: String?): ViewMode = entries.firstOrNull { it.key == key?.trim() } ?: DEFAULT
    }
}

/** Item da lista da escala de trabalho: nota/feriado, período ou mudança, em ordem de data. */
sealed interface WorkItem {
    val date: String
    data class NoteItem(override val date: String, val note: Note) : WorkItem
    data class PeriodItem(val period: Period) : WorkItem { override val date get() = period.start }
    data class ChangeItem(val change: Change) : WorkItem { override val date get() = change.date }
}

object Views {
    /** Domingo da semana de `d` (o calendário começa no domingo). */
    fun weekStart(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value % 7).toLong())

    /** Os 7 dias da semana de `d`, de domingo a sábado. */
    fun weekOf(d: LocalDate): List<LocalDate> = weekStart(d).let { s -> List(7) { s.plusDays(it.toLong()) } }

    /** Hora (0–23) em que o evento começa; null para dia inteiro. */
    fun startHour(e: Event): Int? = if (e.allDay) null else Personal.time(e.start)?.hour

    /**
     * Eventos para a lista: próximos (a partir de hoje, do mais perto ao mais longe)
     * ou anteriores (antes de hoje, do mais recente ao mais antigo).
     */
    fun eventList(p: PersonalData, today: String, past: Boolean): List<Event> {
        val order = compareBy<Event>({ it.date }, { !it.allDay }, { it.start }, { it.title.lowercase() })
        return if (past) p.events.filter { it.date < today }.sortedWith(order.reversed())
        else p.events.filter { it.date >= today }.sortedWith(order)
    }

    /**
     * Notas, períodos e mudanças da escala para a lista. Períodos em andamento contam como próximos.
     * Próximos em ordem crescente de data; anteriores em ordem decrescente.
     */
    fun workList(sch: Schedule, today: String, past: Boolean): List<WorkItem> {
        val all = sch.notes.map { (d, n) -> WorkItem.NoteItem(d, n) } +
            sch.periods.map { WorkItem.PeriodItem(it) } +
            sch.changes.map { WorkItem.ChangeItem(it) }
        fun upcoming(i: WorkItem) = if (i is WorkItem.PeriodItem) i.period.end >= today else i.date >= today
        val rank: (WorkItem) -> Int = { when (it) { is WorkItem.ChangeItem -> 0; is WorkItem.PeriodItem -> 1; is WorkItem.NoteItem -> 2 } }
        val order = compareBy<WorkItem>({ it.date }, rank)
        return if (past) all.filter { !upcoming(it) }.sortedWith(order.reversed()) else all.filter { upcoming(it) }.sortedWith(order)
    }
}
