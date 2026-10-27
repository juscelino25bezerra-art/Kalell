// Kalell — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalell.ui

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalell.domain.Agenda
import com.kalell.domain.Dates
import com.kalell.domain.Engine
import com.kalell.domain.L
import com.kalell.domain.P
import com.kalell.domain.Schedule
import com.kalell.ui.theme.Display
import com.kalell.ui.theme.Look
import com.kalell.ui.theme.fgOn
import com.kalell.ui.theme.hexColor
import com.kalell.ui.theme.toHex
import java.time.LocalDate

@Composable
fun SchedulesScreen(ctl: AppController) {
    var history by rememberSaveable { mutableStateOf(false) }
    ScreenScroll {
        ScreenTitle(L("Escalas", "Schedules", "Horarios")) {
            IconAction(KIcons.Plus, L("Nova escala", "New schedule", "Nuevo horario"), { if (ctl.ready) ctl.newSchedule() else ctl.firstSchedule() }, filled = true)
        }
        if (!ctl.ready) {
            EmptySetupCard(ctl::firstSchedule)
            return@ScreenScroll
        }
        Segmented(listOf(false, true), history, { if (it) L("Histórico", "History", "Historial") else L("Minhas escalas", "My schedules", "Mis horarios") }, { history = it })
        if (history) HistoryList(ctl) else {
            ActiveCard(ctl)
            SimulateCard(ctl)
            ScheduleList(ctl)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(L("Ações", "Actions", "Acciones"))
                val c = Look.colors
                ActionRow(KIcons.Edit, hexColor("#3dd6a8"), L("Editar escala", "Edit schedule", "Editar horario"), L("Padrão, nome, cor, horário do turno e datas", "Pattern, name, color, shift hours and dates", "Patrón, nombre, color, horario del turno y fechas"), ctl::openCycle)
                ActionRow(KIcons.Swap, c.primary, L("Mudança na escala", "Schedule change", "Cambio en el horario"), L("Recomeçar o ciclo numa data (troca de turno)", "Restart the cycle on a date (shift swap)", "Reiniciar el ciclo en una fecha (cambio de turno)"), { ctl.openChange() })
                ActionRow(KIcons.Umbrella, hexColor("#f5b544"), L("Férias e períodos", "Vacations and periods", "Vacaciones y períodos"), L("Marcar férias, licenças ou afastamentos", "Mark vacations, leaves or time off", "Marcar vacaciones, permisos o bajas"), { ctl.openPeriod() })
                ActionRow(KIcons.Share, hexColor("#f07ab8"), L("Compartilhar período", "Share period", "Compartir período"), L("Imagem da escala para enviar", "Image of the schedule to send", "Imagen del horario para enviar"), ctl::openShare)
                ActionRow(KIcons.File, c.offStrong, L("PDF do ano", "Year PDF", "PDF del año"), L("Calendário anual da escala para imprimir", "Yearly calendar of the schedule to print", "Calendario anual del horario para imprimir") + " (${ctl.view.year})", { ctl.exportPdf(c) })
            }
            WorkCards(ctl)
        }
    }
}

/** Cartão da escala ativa (ou da simulação): padrão, bloco atual, próximos 14 dias e números do mês. */
@Composable
private fun ActiveCard(ctl: AppController) {
    val c = Look.colors
    val real = ctl.current
    val sim = ctl.simulated
    val sch = sim ?: real
    val today = ctl.today
    val b = Agenda.blockInfo(today, sch)
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().cardSurface(shape).then(if (sim != null) Modifier.border(1.5.dp, hexColor("#f5b544"), shape) else Modifier).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(KIcons.Calendar, Look.current.sc, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (sim != null) L("Pré-visualização", "Preview", "Vista previa") else L("Escala atual", "Current schedule", "Horario actual"), fontSize = 12.sp, color = c.onSurfaceVar)
                Text(sch.name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = Display, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(L("Bloco ", "Block ", "Bloque ") + Engine.cycleText(sch.blocks) + Agenda.shiftText(sch).let { if (it.isEmpty()) "" else " · $it" }, fontSize = 13.sp, color = c.onSurfaceVar)
            }
            if (sim != null) Badge(L("Simulação", "Preview", "Simulación"), hexColor("#f5b544"), Color(0xFF10141F))
            else Badge(L("Ativa", "Active", "Activo"), c.primary.copy(alpha = .16f), if (c.dark) c.primary else c.onPrimaryContainer)
        }
        if (Engine.periodOn(Dates.iso(today), sch) == null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (b.workLeft > 0) "${b.workLeft} " + P(b.workLeft, "dia de trabalho restante", "dias de trabalho restantes", "work day left", "work days left", "día de trabajo restante", "días de trabajo restantes")
                    else L("Folga hoje", "Day off today", "Libre hoy"),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.onSurface,
                )
                if (b.total in 2..28) BlockBar(b.total, b.block.w, b.index, c.workStrong, c.offStrong)
                Text(L("Bloco atual: ", "Current block: ", "Bloque actual: ") + "${Dates.fmt(b.start)} – ${Dates.fmt(b.end)}", fontSize = 12.sp, color = c.onSurfaceVar)
            }
        }
        // próximos 14 dias em duas ondas de 7 (visual Ondas: os dias são pontos ligados por uma linha ondulada)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in 0 until 2) {
                Box(Modifier.fillMaxWidth()) {
                    WaveLine(c.outlineVar, Modifier.matchParentSize())
                    Row(Modifier.fillMaxWidth()) {
                        for (k in 0 until 7) {
                            val d = today.plusDays((row * 7 + k).toLong())
                            StripDay(dayState(c, true, sch, d), d, d == today, Modifier.weight(1f))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 2.dp)) {
                LegendDot(c.workStrong, L("Trabalho", "Work", "Trabajo"))
                LegendDot(c.outline, L("Folga", "Off", "Libre"), ring = true)
                LegendDot(c.onSurface, L("Hoje", "Today", "Hoy"), ring = true)
            }
        }
        val stats = Agenda.monthStats(today, sch)
        val month = Dates.monthName(today).take(3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("${stats.work}", L("turnos em ", "shifts in ", "turnos en ") + month, Modifier.weight(1f))
            StatBox("${stats.off}", L("folgas em ", "days off in ", "libres en ") + month, Modifier.weight(1f))
            StatBox(stats.minutes?.let { "${it / 60} h" } ?: "—", if (stats.minutes != null) L("horas no mês", "hours this month", "horas en el mes") else L("defina o horário do turno", "set the shift hours", "defina el horario"), Modifier.weight(1f))
        }
    }
}

/** Linha ondulada que passa pelo centro dos pontos de [StripDay] (uma meia onda por dia). */
@Composable
private fun WaveLine(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val step = size.width / 7f
        val y = 8.dp.toPx() + 15.dp.toPx()
        val amp = 9.dp.toPx()
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(step / 2, y)
            for (i in 1 until 7) {
                val x0 = step / 2 + (i - 1) * step
                val dir = if (i % 2 == 1) -1 else 1
                cubicTo(x0 + step * .35f, y + dir * amp, x0 + step * .65f, y + dir * amp, x0 + step, y)
            }
        }
        drawPath(path, color, style = Stroke(1.5.dp.toPx()))
    }
}

/** Um dia da faixa: ponto cheio (trabalho), vazado (folga) ou na cor do período; hoje ganha um anel. */
@Composable
private fun StripDay(st: DayState, d: LocalDate, today: Boolean, modifier: Modifier) {
    val c = Look.colors
    val work = st.work == true
    // período (férias, licença…) aparece cheio na cor dele; folga comum fica vazada
    val filled = work || (st.work == false && st.color != c.offStrong)
    val dot = if (today) 32.dp else 30.dp
    Column(
        modifier.semantics { contentDescription = Dates.capitalize(Dates.weekdayDayMonth(d)) + ", " + st.label }.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(dot)
                    .then(if (today) Modifier.border(1.5.dp, c.onSurface, CircleShape).padding(3.dp) else Modifier)
                    .clip(CircleShape)
                    .background(if (filled) st.color else Look.current.card)
                    .then(if (!filled) Modifier.border(1.5.dp, c.outline, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${d.dayOfMonth}", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = Display,
                    color = if (filled) fgOn(st.color.toHex()) else c.onSurfaceVar,
                )
            }
        }
        Text(if (today) L("Hoje", "Today", "Hoy") else weekdayShort(d), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (today) c.onSurface else c.onSurfaceVar, maxLines = 1)
    }
}

@Composable
private fun LegendDot(color: Color, text: String, ring: Boolean = false) {
    val c = Look.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).then(if (ring) Modifier.border(1.5.dp, color, CircleShape) else Modifier.background(color)))
        Text(text, fontSize = 12.sp, color = c.onSurfaceVar)
    }
}

/** "Simular outro padrão": escolhe 4×1, 5×2, 12×36…, vê o resultado no cartão acima e aplica a partir de amanhã. */
@Composable
private fun SimulateCard(ctl: AppController) {
    val c = Look.colors
    val sch = ctl.current
    val from = Agenda.simulationStart(ctl.today, sch)
    Column(Modifier.fillMaxWidth().cardSurface(RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(KIcons.Eye, hexColor("#f5b544"), 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(L("Simular outro padrão", "Try another pattern", "Simular otro patrón"), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = c.onSurface)
                Text(L("Veja como fica antes de mudar. Vale a partir de ", "See how it looks before changing. Starts on ", "Vea cómo queda antes de cambiar. Empieza el ") + Dates.fmt(from) + ".", fontSize = 12.sp, color = c.onSurfaceVar)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val current = sch.blocks.singleOrNull()
            Agenda.PATTERNS.forEach { (label, block) ->
                val isCurrent = current != null && current.w == block.w && current.o == block.o
                val on = ctl.simulation?.let { it.w == block.w && it.o == block.o } ?: isCurrent
                PillChip(label + if (isCurrent) L(" · atual", " · current", " · actual") else "", on, { ctl.simulate(if (isCurrent) null else block) })
            }
        }
        if (ctl.simulation != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppButton(L("Descartar", "Discard", "Descartar"), { ctl.simulate(null) }, kind = BtnKind.Text)
                Spacer(Modifier.weight(1f))
                AppButton(L("Aplicar a partir de ", "Apply from ", "Aplicar desde ") + Dates.shortDayMonth(from), { ctl.applySimulation() }, kind = BtnKind.Filled)
            }
        }
    }
}

/** Todas as escalas: tocar ativa; a ativa tem o lápis para editar. */
@Composable
private fun ScheduleList(ctl: AppController) {
    val c = Look.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(L("Minhas escalas", "My schedules", "Mis horarios"), if (ctl.data.schedules.size < com.kalell.domain.AppData.MAX_SCHEDULES) L("Nova", "New", "Nuevo") else null, onAction = ctl::newSchedule)
        ctl.data.schedules.forEach { s ->
            val on = s.id == ctl.current.id
            val col = hexColor(s.color)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).cardSurface(RoundedCornerShape(18.dp))
                    .then(if (on) Modifier.border(1.5.dp, col, RoundedCornerShape(18.dp)) else Modifier)
                    .press(role = Role.RadioButton) { if (on) ctl.openCycle() else ctl.selectSchedule(s) }
                    .semantics { selected = on }
                    .padding(start = 0.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.padding(start = 0.dp).size(6.dp, 58.dp).background(col))
                Spacer(Modifier.width(12.dp))
                IconTile(KIcons.Calendar, col, 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(s.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(scheduleLine(s, ctl.today), fontSize = 12.sp, color = c.onSurfaceVar, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (on) Icon(KIcons.Edit, L("Editar escala", "Edit schedule", "Editar horario"), tint = c.onSurfaceVar, modifier = Modifier.size(20.dp))
                else Text(L("Ativar", "Use", "Activar"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.primary)
            }
        }
    }
}

private fun scheduleLine(s: Schedule, today: LocalDate): String {
    val st = if (Engine.periodOn(Dates.iso(today), s) != null) L("período hoje", "period today", "período hoy")
    else if (Engine.status(today, s).work) L("trabalho hoje", "work today", "trabajo hoy") else L("folga hoje", "off today", "libre hoy")
    return listOf(Engine.cycleText(s.blocks), Agenda.shiftText(s), st).filter { it.isNotEmpty() }.joinToString(" · ")
}

/** Histórico: os padrões preservados da escala ativa, do mais recente ao mais antigo, com os turnos de cada um. */
@Composable
private fun HistoryList(ctl: AppController) {
    val c = Look.colors
    val sch = ctl.current
    val since = Engine.lastTo(sch)
    ActionRow(
        KIcons.Calendar, Look.current.sc, L("Atual: ", "Current: ", "Actual: ") + Engine.cycleText(sch.blocks),
        if (since.isNotEmpty()) L("desde ", "since ", "desde ") + Dates.fmt(since) else L("desde ", "since ", "desde ") + Dates.fmt(sch.anchor),
        ctl::openCycle,
    ) { Badge(L("Ativa", "Active", "Activo"), hexColor("#3dd6a8"), Color(0xFF0C2A20)) }
    if (sch.history.isEmpty()) {
        Text(
            L("Ainda não há padrões anteriores. Ao trocar o padrão mantendo os dias anteriores (em Editar escala ou Simular), o anterior aparece aqui.", "No earlier patterns yet. When you change the pattern keeping earlier days (in Edit schedule or Try another pattern), the previous one shows up here.", "Aún no hay patrones anteriores. Al cambiar el patrón manteniendo los días anteriores (en Editar horario o Simular), el anterior aparece aquí."),
            fontSize = 14.sp, color = c.onSurfaceVar, lineHeight = 20.sp,
        )
        return
    }
    sch.history.indices.reversed().forEach { i ->
        val e = sch.history[i]
        val start = if (i == 0) e.anchor else sch.history[i - 1].to
        val from = Dates.parse(minOf(start, e.to))
        val to = Dates.parse(e.to).minusDays(1)
        val span = Dates.dd(from, to).coerceIn(0, 3 * 366)
        val shifts = (0..span).count { Engine.status(from.plusDays(it), sch).work }
        ActionRow(
            KIcons.Clock, c.onSurfaceVar, L("Bloco ", "Block ", "Bloque ") + Engine.cycleText(e.blocks),
            "${Dates.fmt(from)} – ${Dates.fmt(to)} · $shifts " + P(shifts, "turno", "turnos", "shift", "shifts", "turno", "turnos"),
            {},
        ) {}
    }
    Text(L("Para recalcular o calendário inteiro com um padrão, use Editar escala sem manter os dias anteriores.", "To recalculate the whole calendar with one pattern, use Edit schedule without keeping earlier days.", "Para recalcular todo el calendario con un patrón, use Editar horario sin mantener los días anteriores."), fontSize = 12.sp, color = c.onSurfaceVar.copy(alpha = .85f))
}
