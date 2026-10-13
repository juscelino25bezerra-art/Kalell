// Kalendrium — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalendrium.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalendrium.domain.Agenda
import com.kalendrium.domain.Dates
import com.kalendrium.domain.Engine
import com.kalendrium.domain.L
import com.kalendrium.domain.P
import com.kalendrium.domain.Schedule
import com.kalendrium.ui.theme.AppColors
import com.kalendrium.ui.theme.HolidayRed
import com.kalendrium.ui.theme.Look
import com.kalendrium.ui.theme.hexColor
import com.kalendrium.ui.theme.mix
import java.time.LocalDate

/** Céu do card de hoje: sempre escuro (como no conceito), tingido pela cor primária da paleta. */
private val NIGHT_A = Color(0xFF1A1F3D)
private val NIGHT_B = Color(0xFF12152B)

/** "Hoje", "Amanhã" ou "Terça-feira, 13 de outubro". */
fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> L("Hoje", "Today", "Hoy")
    today.plusDays(1) -> L("Amanhã", "Tomorrow", "Mañana")
    else -> Dates.capitalize(Dates.weekdayDayMonth(d))
}

/** Dia curto da semana ("Seg", "Mon", "Lun"). */
fun weekdayShort(d: LocalDate): String = com.kalendrium.domain.weekHeader()[d.dayOfWeek.value % 7].lowercase().replaceFirstChar { it.uppercase() }

@Composable
fun TodayScreen(ctl: AppController) {
    var sel by rememberSaveable { mutableIntStateOf(0) }
    var editName by remember { mutableStateOf(false) }
    ScreenScroll {
        TodayTopBar(ctl)
        Column(
            Modifier.clip(RoundedCornerShape(12.dp)).press { editName = true }.padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val c = Look.colors
            Text(
                if (ctl.userName.isNotEmpty()) L("Olá, ${ctl.userName}!", "Hi, ${ctl.userName}!", "¡Hola, ${ctl.userName}!") else L("Olá!", "Hi!", "¡Hola!"),
                fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.5).sp, color = c.onSurface,
            )
            Text(Dates.capitalize(Dates.longDate(ctl.today)), fontSize = 14.sp, color = c.onSurfaceVar)
        }
        TodayHero(ctl)
        NextDays(ctl, sel) { sel = it }
        val day = ctl.today.plusDays(sel.toLong())
        DayAgenda(ctl, day, if (sel == 0) L("Agenda de hoje", "Today's agenda", "Agenda de hoy") else L("Agenda de ", "Agenda for ", "Agenda del ") + Dates.weekdayDayMonth(day))
        if (ctl.ready) ActiveScheduleCard(ctl)
        Shortcuts(ctl)
    }
    if (editName) NameDialog(ctl.userName, { ctl.setName(it); editName = false }, { editName = false })
}

@Composable
private fun TodayTopBar(ctl: AppController) {
    val c = Look.colors
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconAction(KIcons.Menu, L("Ajustes", "Settings", "Ajustes"), { ctl.openSettings() })
        Spacer(Modifier.width(4.dp))
        Text("Kalendrium", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.4).sp, color = c.onSurface, maxLines = 1)
        IconAction(KIcons.Search, L("Buscar", "Search", "Buscar"), ctl::openSearch)
        val soon = ctl.upcomingReminders(1).isNotEmpty()
        IconAction(
            KIcons.Bell, if (soon) L("Lembretes, há avisos nas próximas 24 horas", "Reminders, some in the next 24 hours", "Recordatorios, hay avisos en las próximas 24 horas") else L("Lembretes", "Reminders", "Recordatorios"),
            ctl::openUpcoming, badge = soon,
        )
    }
}

/** Card de destaque: situação de hoje na escala, o próximo turno e os compromissos do dia. Toque leva às Escalas. */
@Composable
private fun TodayHero(ctl: AppController) {
    val c = Look.colors
    val shape = RoundedCornerShape(26.dp)
    val white = Color.White
    val soft = Color(0xFFCBC6EE)
    val sky = Brush.linearGradient(listOf(mix(c.primary, .42f, NIGHT_A), mix(c.offStrong, .22f, NIGHT_B)), start = Offset.Zero, end = Offset.Infinite)
    val ready = ctl.ready
    val sch = ctl.current
    val today = ctl.today
    val period = if (ready) Engine.periodOn(Dates.iso(today), sch) else null
    val working = ready && period == null && Engine.status(today, sch).work
    Box(
        Modifier.fillMaxWidth().clip(shape).background(sky).border(1.dp, white.copy(alpha = .14f), shape)
            .press { if (ready) ctl.selectTab(Tab.SCHEDULES) else ctl.firstSchedule() },
    ) {
        HeroSky(working, Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!ready) {
                Text(L("Bem-vindo ao Kalendrium", "Welcome to Kalendrium", "Bienvenido a Kalendrium"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = white)
                Text(L("Crie sua escala de trabalho para ver turnos e folgas junto da sua agenda.", "Create your work schedule to see shifts and days off next to your agenda.", "Cree su horario de trabajo para ver turnos y días libres junto a su agenda."), fontSize = 14.sp, color = soft, modifier = Modifier.padding(end = 70.dp))
                AppButton(L("Criar minha escala", "Create my schedule", "Crear mi horario"), ctl::firstSchedule, kind = BtnKind.Inverse, icon = KIcons.Plus)
                return@Column
            }
            val (info, _) = workSummary(today, sch)
            val parts = info.split("  •  ")
            Badge(sch.name, white.copy(alpha = .16f), white)
            Text(parts.first(), fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.5).sp, color = white, modifier = Modifier.padding(end = 64.dp))
            val next = Agenda.nextShift(ctl.clock, sch)
            if (next != null) {
                val now = ctl.clock
                val (label, value) = if (!now.isBefore(next.start)) {
                    L("Em turno", "On shift", "En turno") to L("até ", "until ", "hasta las ") + next.end.toLocalTime().toString()
                } else {
                    L("Próximo turno · em ", "Next shift · in ", "Próximo turno · en ") + Agenda.durationText(now, next.start) to
                        dayLabel(next.start.toLocalDate(), today) + ", " + Agenda.shiftText(sch)
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(white.copy(alpha = .08f)).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconTile(KIcons.Briefcase, c.workStrong, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, fontSize = 12.sp, color = soft)
                        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = white)
                    }
                }
            } else if (parts.size > 1) {
                Text(parts.drop(1).joinToString("\n"), fontSize = 14.sp, lineHeight = 20.sp, color = soft, modifier = Modifier.padding(end = 56.dp))
            }
            val n = ctl.eventsOn(today).size
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (n == 0) L("Nenhum compromisso pessoal hoje", "No personal plans today", "Sin planes personales hoy") else "$n " + P(n, "compromisso pessoal hoje", "compromissos pessoais hoje", "personal plan today", "personal plans today", "plan personal hoy", "planes personales hoy"),
                    Modifier.weight(1f), fontSize = 13.sp, color = soft,
                )
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
                    Icon(KIcons.ChevronRight, L("Abrir escalas", "Open schedules", "Abrir horarios"), tint = c.onPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/** Sol (folga) ou lua (trabalho) sobre montanhas, desenhados à mão: sem imagem, igual em qualquer tela. */
@Composable
private fun HeroSky(working: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = 22.dp.toPx()
        val center = Offset(w - 52.dp.toPx(), 52.dp.toPx())
        val glow = if (working) Color(0xFFDCE5FF) else Color(0xFFF7C873)
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = .35f), Color.Transparent), center, r * 2.6f), r * 2.6f, center)
        drawCircle(glow, r, center)
        // lua: um círculo da cor do céu "morde" o disco
        if (working) drawCircle(Color(0xFF2A2F55), r * .86f, center + Offset(r * .45f, -r * .3f))
        val far = Path().apply {
            moveTo(w * .35f, h); lineTo(w * .62f, h * .58f); lineTo(w * .74f, h * .70f); lineTo(w * .86f, h * .52f); lineTo(w, h * .66f); lineTo(w, h); close()
        }
        drawPath(far, Color.White.copy(alpha = .06f))
        val near = Path().apply {
            moveTo(w * .50f, h); lineTo(w * .72f, h * .74f); lineTo(w * .82f, h * .82f); lineTo(w * .94f, h * .70f); lineTo(w, h * .76f); lineTo(w, h); close()
        }
        drawPath(near, Color.White.copy(alpha = .09f))
    }
}

/** "Próximos dias": hoje e os 6 seguintes, com trabalho/folga; o toque mostra a agenda do dia abaixo. */
@Composable
private fun NextDays(ctl: AppController, sel: Int, onSelect: (Int) -> Unit) {
    val c = Look.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(L("Próximos dias", "Next days", "Próximos días"), L("Ver todos", "See all", "Ver todos")) {
            ctl.goToday()
            ctl.selectView(com.kalendrium.domain.ViewMode.WEEK)
            ctl.selectTab(Tab.CALENDAR)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until 7) {
                val d = ctl.today.plusDays(i.toLong())
                val on = i == sel
                val st = dayState(ctl, d)
                val shape = RoundedCornerShape(16.dp)
                Column(
                    Modifier.weight(1f).heightIn(min = 82.dp).clip(shape)
                        .then(if (on) Modifier.background(c.primaryContainer).border(1.5.dp, c.primary, shape) else Modifier.cardSurface(shape))
                        .press(role = Role.Tab) { onSelect(i) }
                        .semantics { selected = on; contentDescription = dayLabel(d, ctl.today) + ", " + st.label }
                        .padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    val fg = if (on) c.onPrimaryContainer else c.onSurface
                    Text("${d.dayOfMonth}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = fg)
                    Text(if (i == 0) L("Hoje", "Today", "Hoy") else weekdayShort(d), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (on) fg else c.onSurfaceVar, maxLines = 1)
                    Box(Modifier.padding(top = 2.dp).size(16.dp, 4.dp).clip(RoundedCornerShape(50)).background(st.color))
                    if (st.short.isNotEmpty()) Text(st.short, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (on) fg else c.onSurfaceVar, maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
        }
    }
}

/** Situação de um dia para as barrinhas e chips: cor, rótulo completo e curto. */
data class DayState(val color: Color, val label: String, val short: String, val work: Boolean?)

@Composable
fun dayState(ctl: AppController, d: LocalDate): DayState {
    val c = Look.colors
    return dayState(c, ctl.ready, ctl.current, d)
}

fun dayState(c: AppColors, ready: Boolean, sch: Schedule, d: LocalDate): DayState {
    if (!ready) return DayState(Color.Transparent, "", "", null)
    Engine.periodOn(Dates.iso(d), sch)?.let { p ->
        val t = p.title.ifEmpty { L("Período", "Period", "Período") }
        return DayState(hexColor(p.color), t, t, false)
    }
    return if (Engine.status(d, sch).work) {
        val shift = Agenda.shiftText(sch)
        DayState(c.workStrong, L("Trabalho", "Work", "Trabajo") + (if (shift.isNotEmpty()) " $shift" else ""), L("Trabalho", "Work", "Trabajo"), true)
    } else DayState(c.offStrong, L("Folga", "Day off", "Libre"), L("Folga", "Off", "Libre"), false)
}

/** Agenda de um dia: turno ou folga, feriado e notas, e os eventos (com as repetições). */
@Composable
fun DayAgenda(ctl: AppController, d: LocalDate, title: String) {
    val c = Look.colors
    val s = Dates.iso(d)
    val events = ctl.eventsOn(d)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title)
        Column(Modifier.fillMaxWidth().cardSurface(RoundedCornerShape(22.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DayRows(ctl, d)
            if (events.isEmpty() && !hasDayExtras(ctl, s)) {
                Text(L("Nada marcado neste dia.", "Nothing planned for this day.", "Nada previsto para este día."), fontSize = 14.sp, color = c.onSurfaceVar, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp))
            }
            AddDashedButton(L("Adicionar em ", "Add on ", "Añadir el ") + Dates.dayMonth(d)) { ctl.openEvent(s) }
        }
    }
}

private fun hasDayExtras(ctl: AppController, s: String) = ctl.personal.notes[s] != null || (ctl.ready && ctl.current.notes[s] != null)

/** Linhas de um dia (usadas em Hoje e no painel do Calendário): escala, notas e eventos. */
@Composable
fun DayRows(ctl: AppController, d: LocalDate, showShift: Boolean = true, hiddenCategories: Set<String> = emptySet()) {
    val c = Look.colors
    val s = Dates.iso(d)
    val sch = ctl.current
    if (ctl.ready && showShift) {
        val st = dayState(ctl, d)
        val period = Engine.periodOn(s, sch)
        when {
            period != null -> AgendaRow(st.color, L("Dia todo", "All day", "Todo el día"), st.label, "${Dates.fmt(period.start)} – ${Dates.fmt(period.end)} · ${sch.name}", { ctl.openPeriod(period) }, icon = KIcons.Umbrella)
            st.work == true -> {
                val shift = Agenda.shiftOn(d, sch)
                AgendaRow(
                    st.color, shift?.start?.toLocalTime()?.toString() ?: L("Dia todo", "All day", "Todo el día"),
                    L("Turno de trabalho", "Work shift", "Turno de trabajo"),
                    listOfNotNull(sch.name, shift?.let { L("até ", "until ", "hasta ") + it.end.toLocalTime() }, Engine.status(d, sch).label).joinToString(" · "),
                    { ctl.selectTab(Tab.SCHEDULES) }, icon = KIcons.Briefcase,
                )
            }
            else -> AgendaRow(st.color, L("Dia todo", "All day", "Todo el día"), L("Folga", "Day off", "Día libre"), "${sch.name} · " + Engine.cycleText(Engine.cfgFor(s, sch).blocks), { ctl.selectTab(Tab.SCHEDULES) }, icon = KIcons.Sun)
        }
    }
    if (ctl.ready) sch.notes[s]?.let { n ->
        AgendaRow(
            if (n.holiday) HolidayRed else hexColor(n.color ?: "#f9ab00"), "",
            if (n.holiday) L("Feriado", "Holiday", "Festivo") + (if (n.text.isNotEmpty()) ": ${n.text}" else "") else n.text,
            L("Nota da escala", "Schedule note", "Nota del horario"), { ctl.openNote(s) }, icon = KIcons.Flag,
        )
    }
    ctl.personal.notes[s]?.let { n ->
        AgendaRow(hexColor(n.color), "", n.text, L("Nota pessoal", "Personal note", "Nota personal"), { ctl.openDayNote(s) }, icon = KIcons.Notes)
    }
    ctl.eventsOn(d).filter { it.category.key !in hiddenCategories }.forEach { EventAgendaRow(ctl, it) }
}

/** Botão tracejado "Adicionar em…". */
@Composable
fun AddDashedButton(text: String, onClick: () -> Unit) {
    val c = Look.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(14.dp)).dashedBorder(c.outline, 14.dp).press(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KIcons.Plus, null, tint = c.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.primary)
    }
}

/** "Escala ativa": nome, padrão, barra do bloco e quantos dias de trabalho faltam. */
@Composable
private fun ActiveScheduleCard(ctl: AppController) {
    val c = Look.colors
    val sch = ctl.current
    val inPeriod = Engine.periodOn(Dates.iso(ctl.today), sch) != null
    val b = Agenda.blockInfo(ctl.today, sch)
    Column(
        Modifier.fillMaxWidth().cardSurface(RoundedCornerShape(22.dp)).press { ctl.selectTab(Tab.SCHEDULES) }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(KIcons.Calendar, Look.current.sc)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(L("Escala ativa", "Active schedule", "Horario activo"), fontSize = 12.sp, color = c.onSurfaceVar)
                Text("${sch.name} · ${Engine.cycleText(sch.blocks)}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(L("Ver escala", "View", "Ver"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.primary)
            Icon(KIcons.ChevronRight, null, tint = c.primary, modifier = Modifier.size(18.dp))
        }
        if (!inPeriod && b.total in 2..28) BlockBar(b.total, b.block.w, b.index, c.workStrong, c.offStrong)
        Text(
            when {
                inPeriod -> workSummary(ctl.today, sch).first.split("  •  ").first()
                b.workLeft > 0 -> "${b.workLeft} " + P(b.workLeft, "dia de trabalho restante no bloco", "dias de trabalho restantes no bloco", "work day left in the block", "work days left in the block", "día de trabajo restante en el bloque", "días de trabajo restantes en el bloque")
                else -> L("Folga · o próximo bloco começa em ", "Day off · next block starts on ", "Libre · el próximo bloque empieza el ") + Dates.fmt(b.end.plusDays(1))
            },
            fontSize = 13.sp, color = c.onSurfaceVar,
        )
    }
}

/** Atalhos: novo evento, nota rápida e editar escala. */
@Composable
private fun Shortcuts(ctl: AppController) {
    val c = Look.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Shortcut(KIcons.Plus, hexColor("#7ea2ff"), L("Novo evento", "New event", "Nuevo evento"), Modifier.weight(1f)) { ctl.openEvent(Dates.iso(ctl.today)) }
        Shortcut(KIcons.Notes, hexColor("#f5b544"), L("Nota rápida", "Quick note", "Nota rápida"), Modifier.weight(1f)) { ctl.openQuickNote(Dates.iso(ctl.today)) }
        if (ctl.ready) Shortcut(KIcons.Swap, c.workStrong, L("Editar escala", "Edit schedule", "Editar horario"), Modifier.weight(1f), ctl::openCycle)
        else Shortcut(KIcons.Briefcase, c.workStrong, L("Criar escala", "Create schedule", "Crear horario"), Modifier.weight(1f), ctl::firstSchedule)
    }
}

@Composable
private fun Shortcut(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Look.colors
    Column(
        modifier.heightIn(min = 92.dp).cardSurface(RoundedCornerShape(18.dp)).press(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconTile(icon, tint, 36.dp)
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.onSurface, maxLines = 2)
    }
}

/** Nome da saudação. Fica só neste aparelho. */
@Composable
private fun NameDialog(name: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Look.colors
    var text by rememberSaveable { mutableStateOf(name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceContainer,
        title = { Text(L("Como quer ser chamado?", "What should we call you?", "¿Cómo quiere que le llamemos?"), color = c.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(text, { text = it }, placeholder = L("Seu nome", "Your name", "Su nombre"), maxLength = 30)
                Para(L("Aparece na saudação da tela Hoje. Fica só neste aparelho.", "Shown in the greeting on Today. It stays on this device.", "Aparece en el saludo de Hoy. Solo se guarda en este dispositivo."))
            }
        },
        confirmButton = { AppButton(L("Salvar", "Save", "Guardar"), { onSave(text) }, kind = BtnKind.Filled) },
        dismissButton = { AppButton(L("Cancelar", "Cancel", "Cancelar"), onDismiss, kind = BtnKind.Text) },
    )
}
