// Kalell — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalell.domain

import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Categoria de um evento (redesign 6.0). A cor padrão do evento segue a categoria. */
enum class Category(val key: String, val color: String) {
    PERSONAL("pessoal", "#3dd6a8"),
    WORK("trabalho", "#7ea2ff"),
    HEALTH("saude", "#f07ab8"),
    STUDY("estudo", "#f5b544"),
    OTHER("outro", "#a3acc2");

    val label: String get() = when (this) {
        PERSONAL -> L("Pessoal", "Personal", "Personal")
        WORK -> L("Trabalho", "Work", "Trabajo")
        HEALTH -> L("Saúde", "Health", "Salud")
        STUDY -> L("Estudo", "Study", "Estudio")
        OTHER -> L("Outro", "Other", "Otro")
    }

    companion object {
        /** Ausente ou desconhecida = Pessoal (eventos criados antes da 6.0 vinham do calendário pessoal). */
        fun of(key: String?): Category = entries.firstOrNull { it.key == key } ?: PERSONAL
    }
}

/** Repetição de um evento. "Nas minhas folgas" segue a escala ativa (folgas do ciclo e períodos como férias). */
enum class Repeat(val key: String) {
    NONE("none"),
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    OFF_DAYS("offdays");

    val label: String get() = when (this) {
        NONE -> L("Não repete", "Doesn't repeat", "No se repite")
        DAILY -> L("Todo dia", "Every day", "Cada día")
        WEEKLY -> L("Toda semana", "Every week", "Cada semana")
        MONTHLY -> L("Todo mês", "Every month", "Cada mes")
        OFF_DAYS -> L("Nas minhas folgas", "On my days off", "En mis días libres")
    }

    companion object {
        fun of(key: String?): Repeat = entries.firstOrNull { it.key == key } ?: NONE
    }
}

/** Horário de um turno num dia: início e fim (o fim pode cair no dia seguinte, em turnos noturnos). */
data class ShiftWindow(val start: LocalDateTime, val end: LocalDateTime)

/** Turnos, folgas e horas de um mês. [minutes] = null quando a escala não tem horário de turno. */
data class MonthStats(val work: Int, val off: Int, val minutes: Long?)

/** Posição de hoje no bloco atual: dias do bloco, quantos são de trabalho e o primeiro dia do bloco. */
data class BlockInfo(val block: Block, val start: LocalDate, val index: Int, val work: Boolean) {
    val total get() = block.w + block.o
    val end: LocalDate get() = start.plusDays((total - 1).toLong())
    /** Dias de trabalho que ainda faltam no bloco, contando hoje. */
    val workLeft: Int get() = if (work) block.w - index else 0
}

/** Resultado da busca: um evento (já na data da ocorrência) ou uma nota. */
sealed interface SearchHit {
    val date: String
    data class EventHit(val event: Event) : SearchHit { override val date get() = event.date }
    data class PersonalNoteHit(override val date: String, val note: DayNote) : SearchHit
    data class WorkNoteHit(override val date: String, val note: Note) : SearchHit
}

/**
 * Regras do redesign que juntam o calendário pessoal e a escala: ocorrências de eventos que se repetem,
 * horário do turno, conflitos, estatísticas do mês, simulação de outro padrão e busca. Kotlin puro (testável na JVM).
 */
object Agenda {
    // ---------- folgas ----------
    /** Dia livre para "Nas minhas folgas": folga no ciclo ou dentro de um período (férias, licença…). Sem escala = nunca. */
    fun isOff(d: LocalDate, sch: Schedule?): Boolean =
        sch != null && (Engine.periodOn(Dates.iso(d), sch) != null || !Engine.status(d, sch).work)

    /** Dia de trabalho de fato: trabalho no ciclo e fora de períodos. */
    fun isWork(d: LocalDate, sch: Schedule): Boolean = Engine.periodOn(Dates.iso(d), sch) == null && Engine.status(d, sch).work

    // ---------- repetição ----------
    /** O evento acontece em `d`? A data inicial sempre conta; depois dela, segue a regra de repetição até [Event.until]. */
    fun occursOn(e: Event, d: LocalDate, off: (LocalDate) -> Boolean): Boolean {
        val s = Dates.iso(d)
        if (s < e.date || (e.until.isNotEmpty() && s > e.until)) return false
        if (s == e.date) return true
        val start = Dates.parse(e.date)
        return when (e.repeat) {
            Repeat.NONE -> false
            Repeat.DAILY -> true
            Repeat.WEEKLY -> d.dayOfWeek == start.dayOfWeek
            Repeat.MONTHLY -> d.dayOfMonth == start.dayOfMonth
            Repeat.OFF_DAYS -> off(d)
        }
    }

    /** Eventos de `d`, incluindo as ocorrências dos que se repetem (com `date` = `d` e o mesmo id do original). */
    fun eventsOn(p: PersonalData, d: LocalDate, off: (LocalDate) -> Boolean): List<Event> {
        val s = Dates.iso(d)
        val base = p.eventsOn(s)
        val rep = p.repeating.filter { it.date != s && occursOn(it, d, off) }.map { it.copy(date = s) }
        return if (rep.isEmpty()) base else (base + rep).sortedWith(PersonalData.ORDER)
    }

    /** Ocorrências em [from, to], por data e horário. */
    fun occurrences(p: PersonalData, from: LocalDate, to: LocalDate, off: (LocalDate) -> Boolean): List<Event> {
        val out = ArrayList<Event>()
        var d = from
        while (!d.isAfter(to)) {
            out += eventsOn(p, d, off)
            d = d.plusDays(1)
        }
        return out
    }

    /**
     * Eventos para os lembretes: os que não se repetem como estão e, dos que se repetem, as ocorrências
     * de ontem até [days] dias à frente (id "original@data", para cada uma ter sua notificação).
     */
    fun reminderEvents(p: PersonalData, today: LocalDate, off: (LocalDate) -> Boolean, days: Long = 62): List<Event> {
        val single = p.events.filter { !it.recurring && it.remind >= 0 }
        val rep = p.repeating.filter { it.remind >= 0 }
        if (rep.isEmpty()) return single
        val out = ArrayList(single)
        var d = today.minusDays(1)
        val end = today.plusDays(days)
        while (!d.isAfter(end)) {
            val s = Dates.iso(d)
            rep.forEach { e -> if (occursOn(e, d, off)) out += e.copy(id = "${e.id}@$s", date = s) }
            d = d.plusDays(1)
        }
        return out
    }

    // ---------- turno ----------
    fun shiftTimes(sch: Schedule): Pair<LocalTime, LocalTime>? {
        val a = Personal.time(sch.shiftStart) ?: return null
        val b = Personal.time(sch.shiftEnd) ?: return null
        return a to b
    }

    /** Duração do turno em minutos (turno noturno passa da meia-noite; início = fim conta como 24 h). */
    fun shiftMinutes(sch: Schedule): Long? = shiftTimes(sch)?.let { (a, b) ->
        val m = (b.toSecondOfDay() - a.toSecondOfDay()) / 60L
        if (m <= 0) m + 24 * 60 else m
    }

    /** "07:00 – 15:00" (vazio sem horário). */
    fun shiftText(sch: Schedule): String = if (shiftTimes(sch) != null) "${sch.shiftStart} – ${sch.shiftEnd}" else ""

    /** Turno do dia `d` (null se for folga, período ou se a escala não tem horário). */
    fun shiftOn(d: LocalDate, sch: Schedule): ShiftWindow? {
        val (a, _) = shiftTimes(sch) ?: return null
        if (!isWork(d, sch)) return null
        val start = d.atTime(a)
        return ShiftWindow(start, start.plusMinutes(shiftMinutes(sch)!!))
    }

    /** Turno em andamento ou o próximo a começar depois de `now` (procura até [days] dias). */
    fun nextShift(now: LocalDateTime, sch: Schedule, days: Int = 120): ShiftWindow? {
        if (shiftTimes(sch) == null) return null
        for (i in -1..days) {
            val w = shiftOn(now.toLocalDate().plusDays(i.toLong()), sch) ?: continue
            if (w.end.isAfter(now)) return w
        }
        return null
    }

    /** "9 h 47 min", "35 min", "2 dias" até `to`. */
    fun durationText(from: LocalDateTime, to: LocalDateTime): String {
        val min = java.time.Duration.between(from, to).toMinutes().coerceAtLeast(0)
        val days = min / (24 * 60)
        return when {
            days >= 2 -> "$days " + L("dias", "days", "días")
            min >= 60 -> "${min / 60} h" + (if (min % 60 > 0) " ${min % 60} min" else "")
            else -> "$min min"
        }
    }

    /**
     * Conflito entre um evento com horário e o turno: o do próprio dia ou o noturno do dia anterior.
     * `end` vazio = evento sem fim definido (conta 1 minuto). null = sem conflito.
     */
    fun conflict(date: String, start: String, end: String, sch: Schedule): ShiftWindow? {
        val d = Dates.parse(date)
        val s = Personal.time(start) ?: return null
        val es = d.atTime(s)
        val ee = Personal.time(end)?.takeIf { end >= start }?.let { d.atTime(it) }?.takeIf { it.isAfter(es) } ?: es.plusMinutes(1)
        return listOf(d.minusDays(1), d).mapNotNull { shiftOn(it, sch) }.firstOrNull { w -> es.isBefore(w.end) && ee.isAfter(w.start) }
    }

    // ---------- escala ----------
    fun blockInfo(today: LocalDate, sch: Schedule): BlockInfo {
        val st = Engine.status(today, sch)
        val index = if (st.work) st.n - 1 else st.block.w + st.n - 1
        return BlockInfo(st.block, today.minusDays(index.toLong()), index, st.work)
    }

    /** Turnos, folgas e horas do mês de `month` (dias em período contam como folga). */
    fun monthStats(month: LocalDate, sch: Schedule): MonthStats {
        val first = month.withDayOfMonth(1)
        val n = first.lengthOfMonth()
        val work = (0 until n).count { isWork(first.plusDays(it.toLong()), sch) }
        return MonthStats(work, n - work, shiftMinutes(sch)?.let { it * work })
    }

    /** Primeira data em que um novo padrão pode começar: amanhã, ou o dia seguinte ao último período preservado. */
    fun simulationStart(today: LocalDate, sch: Schedule): LocalDate {
        val tomorrow = today.plusDays(1)
        val lastTo = Engine.lastTo(sch)
        return if (lastTo.isNotEmpty() && Dates.iso(tomorrow) <= lastTo) Dates.parse(lastTo).plusDays(1) else tomorrow
    }

    /**
     * Escala com outro padrão a partir de `from` (1º dia de trabalho em `from`). O que valia antes vira período
     * preservado; mudanças a partir de `from` são descartadas, porque apontavam para os blocos antigos.
     */
    fun simulate(sch: Schedule, blocks: List<Block>, from: LocalDate): Schedule {
        val f = Dates.iso(from)
        return Engine.applyCycleEdit(sch.copy(changes = sch.changes.filter { it.date < f }), blocks, f, keep = true, from = f, origs = null).schedule
    }

    /** Padrões oferecidos em "Simular outro padrão" (12×36 = 1 dia de trabalho e 1 de folga). */
    val PATTERNS: List<Pair<String, Block>> = listOf("4×1" to Block(4, 1), "4×2" to Block(4, 2), "5×2" to Block(5, 2), "6×1" to Block(6, 1), "12×36" to Block(1, 1))

    // ---------- busca ----------
    private val MARKS = Regex("\\p{Mn}+")

    /** Minúsculas e sem acento, para a busca achar "reuniao" em "Reunião". */
    fun fold(s: String): String = MARKS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")

    /**
     * Eventos (pelo título, local ou descrição) e notas que contêm `query`. Eventos que se repetem aparecem uma vez,
     * na próxima ocorrência a partir de hoje (ou na data inicial). Resultados de hoje em diante primeiro.
     */
    fun search(query: String, p: PersonalData, sch: Schedule?, today: LocalDate, off: (LocalDate) -> Boolean, limit: Int = 60): List<SearchHit> {
        val q = fold(query.trim())
        if (q.isEmpty()) return emptyList()
        val t = Dates.iso(today)
        val hits = ArrayList<SearchHit>()
        p.events.filter { e -> listOf(e.title, e.location, e.description).any { fold(it).contains(q) } }.forEach { e ->
            val date = if (e.recurring && e.date < t) nextOccurrence(e, today, off) ?: e.date else e.date
            hits += SearchHit.EventHit(e.copy(date = date))
        }
        p.notes.forEach { (d, n) -> if (fold(n.text).contains(q)) hits += SearchHit.PersonalNoteHit(d, n) }
        sch?.notes?.forEach { (d, n) -> if (fold(n.text).contains(q)) hits += SearchHit.WorkNoteHit(d, n) }
        val (next, past) = hits.partition { it.date >= t }
        return (next.sortedBy { it.date } + past.sortedByDescending { it.date }).take(limit)
    }

    /** Próxima ocorrência a partir de `from` (até 400 dias). */
    fun nextOccurrence(e: Event, from: LocalDate, off: (LocalDate) -> Boolean): String? {
        for (i in 0..400) {
            val d = from.plusDays(i.toLong())
            if (occursOn(e, d, off)) return Dates.iso(d)
        }
        return null
    }
}
