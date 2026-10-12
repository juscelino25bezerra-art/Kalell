package com.kalendrium.ui

import com.kalendrium.domain.L
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalendrium.domain.CalendarMode
import com.kalendrium.domain.Dates
import com.kalendrium.domain.Engine
import com.kalendrium.domain.Event
import com.kalendrium.domain.Schedule
import com.kalendrium.domain.ViewMode
import com.kalendrium.domain.Views
import com.kalendrium.domain.WorkItem
import com.kalendrium.domain.weekHeader
import com.kalendrium.domain.weekLetters
import com.kalendrium.ui.theme.HolidayRed
import com.kalendrium.ui.theme.Look
import com.kalendrium.ui.theme.hexColor
import com.kalendrium.ui.theme.mix
import java.time.LocalDate
import java.util.Locale

// ---------------------------------------------------------------- botão de grade (menu de visualizações)

/** Botão de grade ao lado do seletor Pessoal | Work: abre o menu com as visualizações. */
@Composable
fun ViewMenuButton(ctl: AppController, modifier: Modifier = Modifier) {
    val c = Look.colors
    Box(modifier) {
        RoundIconButton(AppIcons.GridView, L("Visualização", "View", "Vista") + ": " + ctl.viewMode.label, { ctl.viewMenu = true }, solid = Look.current.card)
        DropdownMenu(
            expanded = ctl.viewMenu,
            onDismissRequest = { ctl.viewMenu = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = c.surfaceContainer,
        ) {
            Text(
                L("Visualização", "View", "Vista"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVar,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
            )
            ViewMode.entries.forEach { v ->
                val on = v == ctl.viewMode
                DropdownMenuItem(
                    text = {
                        Text(
                            v.label + if (v == ViewMode.DEFAULT) L(" (padrão)", " (default)", " (predeterminada)") else "",
                            fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = c.onSurface,
                        )
                    },
                    leadingIcon = {
                        if (on) Icon(Icons.Filled.Check, null, tint = c.primary, modifier = Modifier.size(20.dp))
                        else Spacer(Modifier.size(20.dp))
                    },
                    onClick = { ctl.selectView(v) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- conteúdo de cada visualização

/** Corpo da tela abaixo do topo, conforme a visualização e o contexto. */
@Composable
fun CalendarBody(ctl: AppController) {
    val data = ctl.data
    when (ctl.mode) {
        CalendarMode.PERSONAL -> when (ctl.viewMode) {
            ViewMode.DAY -> PersonalDayView(ctl)
            ViewMode.WEEK -> WeekView(ctl)
            ViewMode.YEAR -> YearView(ctl)
            ViewMode.LIST -> PersonalListView(ctl)
            ViewMode.MONTH, ViewMode.MONTH_DAY -> {
                val personal = ctl.personal
                val withDay = ctl.viewMode == ViewMode.MONTH_DAY
                CalendarCard(ctl.view, ctl::prevMonth, ctl::nextMonth, { ctl.monthPicker = true }, { PersonalLegend() }) { d, narrow, minH, mod ->
                    PersonalDayCell(d, personal, d == ctl.today, withDay && d == ctl.selected, narrow, minH, mod, ctl::tapDay)
                }
                if (withDay) DayEventsCard(ctl)
            }
        }
        CalendarMode.WORK -> if (!ctl.ready) EmptySetupCard(ctl::firstSchedule) else {
            WorkChips(ctl)
            val sch = ctl.current
            when (ctl.viewMode) {
                ViewMode.DAY -> WorkDayView(ctl)
                ViewMode.WEEK -> WeekView(ctl)
                ViewMode.YEAR -> YearView(ctl)
                ViewMode.LIST -> WorkListView(ctl)
                ViewMode.MONTH, ViewMode.MONTH_DAY -> {
                    val withDay = ctl.viewMode == ViewMode.MONTH_DAY
                    val others = data.schedules.filter { it.id != sch.id }
                    CalendarCard(ctl.view, ctl::prevMonth, ctl::nextMonth, { ctl.monthPicker = true }, { WorkLegend() }) { d, narrow, minH, mod ->
                        WorkDayCell(d, sch, others, d == ctl.today, narrow, minH, mod, ctl::tapDay, isSelected = withDay && d == ctl.selected)
                    }
                    if (withDay) WorkDayCard(ctl, ctl.selected, title = true)
                    WorkCards(ctl)
                }
            }
        }
    }
}

/** Cabeçalho com setas (dia, semana, ano) igual ao do mês. */
@Composable
private fun NavHeader(title: String, subtitle: String, prevDesc: String, nextDesc: String, onPrev: () -> Unit, onNext: () -> Unit, onTitle: (() -> Unit)? = null) {
    val c = Look.colors
    Row(Modifier.fillMaxWidth().padding(bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, prevDesc, onPrev)
        Column(
            Modifier.weight(1f).then(if (onTitle != null) Modifier.press(onClick = onTitle) else Modifier).padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(subtitle, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.onSurfaceVar, maxLines = 1, textAlign = TextAlign.Center)
        }
        RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, nextDesc, onNext)
    }
}

@Composable
private fun ViewCard(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < 520.dp
        Column(Modifier.fillMaxWidth().cardSurface().padding(if (narrow) 10.dp else 14.dp)) { content() }
    }
}

/** "Hoje", "Amanhã", "Ontem" ou "Terça-feira, 6 de outubro". */
private fun dayTitle(d: LocalDate, today: LocalDate): String = when (d) {
    today -> L("Hoje", "Today", "Hoy")
    today.plusDays(1) -> L("Amanhã", "Tomorrow", "Mañana")
    today.minusDays(1) -> L("Ontem", "Yesterday", "Ayer")
    else -> Dates.capitalize(Dates.weekdayDayMonth(d))
}

@Composable
private fun DayNav(ctl: AppController) {
    val d = ctl.selected
    val title = dayTitle(d, ctl.today)
    val sub = if (title == Dates.capitalize(Dates.weekdayDayMonth(d))) d.year.toString() else Dates.capitalize(Dates.weekdayDayMonth(d)) + " · " + d.year
    NavHeader(title, sub, L("Dia anterior", "Previous day", "Día anterior"), L("Próximo dia", "Next day", "Día siguiente"), ctl::prev, ctl::next)
}

// ---------------------------------------------------------------- diária (Pessoal)

private fun hm(h: Int, m: Int) = String.format(Locale.ROOT, "%02d:%02d", h, m)

@Composable
private fun PersonalDayView(ctl: AppController) {
    val c = Look.colors
    val d = ctl.selected
    val s = Dates.iso(d)
    val events = ctl.personal.eventsOn(s)
    val note = ctl.personal.notes[s]
    val allDay = events.filter { it.allDay }
    val byHour = events.filter { !it.allDay }.groupBy { Views.startHour(it) ?: 0 }
    val nowHour = if (d == ctl.today) ctl.now().hour else -1
    ViewCard {
        DayNav(ctl)
        if (note != null) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).controlSurface(RoundedCornerShape(16.dp), Look.current.page)
                    .press { ctl.openDayNote(s) }.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Box(Modifier.size(4.dp, 20.dp).clip(RoundedCornerShape(50)).background(hexColor(note.color)))
                Spacer(Modifier.width(10.dp))
                Text(note.text, fontSize = 14.sp, color = c.onSurface, lineHeight = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        if (allDay.isNotEmpty()) {
            Text(L("Dia inteiro", "All day", "Todo el día"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVar, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp))
            allDay.forEach { EventBlock(it) { ctl.openEvent(s, it) } }
        }
        Spacer(Modifier.size(6.dp))
        // linha do tempo: uma faixa por hora; toque numa faixa vazia cria um evento naquele horário
        (0..23).forEach { h ->
            val list = byHour[h].orEmpty()
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                Text(
                    hm(h, 0), Modifier.width(48.dp).padding(top = 2.dp), fontSize = 11.sp,
                    fontWeight = if (h == nowHour) FontWeight.Bold else FontWeight.Normal,
                    color = if (h == nowHour) c.primary else c.onSurfaceVar,
                )
                Column(
                    Modifier.weight(1f).heightIn(min = 44.dp)
                        .then(if (list.isEmpty()) Modifier.press { ctl.openEvent(s, start = hm(h, 0)) } else Modifier),
                ) {
                    HorizontalDivider(color = if (h == nowHour) c.primary else c.outlineVar, thickness = if (h == nowHour) 2.dp else 1.dp)
                    list.forEach { EventBlock(it) { ctl.openEvent(s, it) } }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(L("Evento", "Event", "Evento"), { ctl.openEvent(s) }, kind = BtnKind.Filled, icon = Icons.Filled.Add)
            AppButton(if (note != null) L("Editar nota", "Edit note", "Editar nota") else L("Adicionar nota", "Add note", "Añadir nota"), { ctl.openDayNote(s) }, kind = BtnKind.Text, icon = Icons.Outlined.Edit)
        }
    }
}

/** Evento como bloco colorido (visualização diária). */
@Composable
private fun EventBlock(e: Event, onClick: () -> Unit) {
    val c = Look.colors
    val col = hexColor(e.color)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(mix(col, if (c.dark) .32f else .18f, c.surface))
            .press(onClick = onClick).padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp, 40.dp).background(col))
        Column(Modifier.weight(1f).padding(start = 10.dp, top = 6.dp, bottom = 6.dp)) {
            Text(e.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${e.timeText} · ${e.kind.label}", fontSize = 12.sp, color = c.onSurfaceVar, maxLines = 1)
        }
        if (e.remind >= 0) Icon(Icons.Outlined.Notifications, L("Com lembrete", "With reminder", "Con recordatorio"), tint = c.onSurfaceVar, modifier = Modifier.size(16.dp))
    }
}

// ---------------------------------------------------------------- diária (Work)

@Composable
private fun WorkDayView(ctl: AppController) {
    ViewCard {
        DayNav(ctl)
        WorkDayDetails(ctl, ctl.selected)
    }
}

/** Cartão do dia selecionado na visualização mensal + diária do Work. */
@Composable
private fun WorkDayCard(ctl: AppController, d: LocalDate, title: Boolean) {
    val c = Look.colors
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        val narrow = maxWidth < 520.dp
        Column(Modifier.fillMaxWidth().cardSurface().padding(if (narrow) 17.dp else 20.dp)) {
            if (title) Text(dayTitle(d, ctl.today), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.onSurface, modifier = Modifier.padding(bottom = 4.dp))
            WorkDayDetails(ctl, d)
        }
    }
}

/** Situação do dia na escala: trabalho/folga, bloco, período, nota, mudança e as outras escalas. */
@Composable
private fun WorkDayDetails(ctl: AppController, d: LocalDate) {
    val look = Look.current
    val c = look.colors
    val sch = ctl.current
    val s = Dates.iso(d)
    val st = Engine.status(d, sch)
    val note = sch.notes[s]
    val period = Engine.periodOn(s, sch)
    val change = Engine.cfgFor(s, sch).changes.firstOrNull { it.date == s }
    val bg = if (st.work) mix(look.sc, .26f, c.surface) else c.off
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(bg).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (st.work) L("Trabalho", "Work day", "Trabajo") else L("Folga", "Day off", "Libre"),
                fontSize = 20.sp, fontWeight = FontWeight.Black, color = if (st.work) mix(look.sc, .65f, c.onSurface) else c.offStrong,
            )
            Text(st.label, fontSize = 13.sp, color = c.onSurfaceVar)
        }
        if (change != null) LegendItem(c.primary, L("Mudança", "Change", "Cambio"), round = true)
    }
    if (period != null) {
        DetailRow(hexColor(period.color), period.title.ifEmpty { L("Período", "Period", "Período") }, "${Dates.fmt(period.start)} – ${Dates.fmt(period.end)}") { ctl.openPeriod(period) }
    }
    if (note != null) {
        val col = note.color ?: if (note.holiday) "#d93025" else "#f9ab00"
        DetailRow(
            if (note.holiday) HolidayRed else hexColor(col),
            note.text.ifEmpty { if (note.holiday) L("Feriado", "Holiday", "Festivo") else L("Nota", "Note", "Nota") },
            if (note.holiday) L("Feriado", "Holiday", "Festivo") else L("Nota", "Note", "Nota"),
        ) { ctl.openNote(s) }
    }
    val others = ctl.data.schedules.filter { it.id != sch.id }
    if (others.isNotEmpty()) {
        Text(L("Outras escalas", "Other schedules", "Otros horarios"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVar, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
        others.forEach { o ->
            val w = Engine.status(d, o).work
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(hexColor(o.color), 10.dp)
                Spacer(Modifier.width(8.dp))
                Text(o.name, Modifier.weight(1f), fontSize = 14.sp, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (w) L("Trabalho", "Work", "Trabajo") else L("Folga", "Off", "Libre"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (w) c.onSurface else c.offStrong)
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppButton(if (note != null) L("Editar nota", "Edit note", "Editar nota") else L("Nota", "Note", "Nota"), { ctl.openNote(s) }, icon = Icons.Outlined.Edit)
        AppButton(L("Mudança", "Change", "Cambio"), { ctl.openChange(s, change) }, kind = BtnKind.Text, icon = if (change == null) Icons.Filled.Add else Icons.Outlined.Edit)
    }
}

@Composable
private fun DetailRow(color: Color, title: String, sub: String, onClick: () -> Unit) {
    val c = Look.colors
    Row(Modifier.fillMaxWidth().press(onClick = onClick).padding(vertical = 8.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp, 30.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 12.sp, color = c.onSurfaceVar)
        }
    }
}

// ---------------------------------------------------------------- semanal

@Composable
private fun WeekView(ctl: AppController) {
    val c = Look.colors
    val days = Views.weekOf(ctl.selected)
    val first = days.first()
    val last = days.last()
    val title = "${Dates.shortDayMonth(first)} – ${Dates.shortDayMonth(last)}"
    val sub = if (first.year == last.year) "${first.year}" else "${first.year} – ${last.year}"
    val names = weekHeader()
    ViewCard {
        NavHeader(title, sub, L("Semana anterior", "Previous week", "Semana anterior"), L("Próxima semana", "Next week", "Semana siguiente"), ctl::prev, ctl::next)
        days.forEachIndexed { i, d ->
            val isToday = d == ctl.today
            Row(
                Modifier.fillMaxWidth().heightIn(min = 58.dp).clip(RoundedCornerShape(14.dp))
                    .press { ctl.openDay(d) }
                    .semantics { contentDescription = Dates.weekdayDayMonth(d) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(names[i], fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isToday) c.primary else c.onSurfaceVar)
                    Box(
                        Modifier.padding(top = 2.dp).size(30.dp).clip(CircleShape).background(if (isToday) c.primary else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${d.dayOfMonth}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (isToday) c.onPrimary else c.onSurface)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).padding(top = 2.dp)) {
                    if (ctl.mode == CalendarMode.PERSONAL) WeekPersonalDay(ctl, d) else WeekWorkDay(ctl.current, d)
                }
            }
            if (i < days.lastIndex) HorizontalDivider(color = c.outlineVar)
        }
    }
}

@Composable
private fun WeekPersonalDay(ctl: AppController, d: LocalDate) {
    val c = Look.colors
    val s = Dates.iso(d)
    val events = ctl.personal.eventsOn(s)
    val note = ctl.personal.notes[s]
    if (events.isEmpty() && note == null) Text(L("Nada marcado", "Nothing scheduled", "Nada programado"), fontSize = 13.sp, color = c.onSurfaceVar, modifier = Modifier.padding(top = 8.dp))
    events.forEach { e ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(4.dp, 18.dp).clip(RoundedCornerShape(50)).background(hexColor(e.color)))
            Spacer(Modifier.width(8.dp))
            Text(if (e.allDay) e.title else "${e.start}  ${e.title}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (note != null) {
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(hexColor(note.color), 7.dp)
            Spacer(Modifier.width(6.dp))
            Text(note.text, fontSize = 12.sp, color = c.onSurfaceVar, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WeekWorkDay(sch: Schedule, d: LocalDate) {
    val look = Look.current
    val c = look.colors
    val s = Dates.iso(d)
    val st = Engine.status(d, sch)
    val note = sch.notes[s]
    val period = Engine.periodOn(s, sch)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (st.work) L("TRABALHO", "WORK", "TRABAJO") else L("FOLGA", "OFF", "LIBRE"),
            Modifier.clip(RoundedCornerShape(8.dp)).background(if (st.work) mix(look.sc, .26f, c.surface) else c.off).padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (st.work) mix(look.sc, .65f, c.onSurface) else c.offStrong,
        )
        Spacer(Modifier.width(8.dp))
        Text("${st.block.w}×${st.block.o}", fontSize = 12.sp, color = c.onSurfaceVar)
    }
    if (period != null) {
        Text(period.title.ifEmpty { L("Período", "Period", "Período") }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = hexColor(period.color), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
    }
    if (note != null) {
        val t = note.text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: if (note.holiday) L("Feriado", "Holiday", "Festivo") else L("Nota", "Note", "Nota")
        Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(if (note.holiday) HolidayRed else hexColor(note.color ?: "#f9ab00"), 7.dp)
            Spacer(Modifier.width(6.dp))
            Text(t, fontSize = 12.sp, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- anual

@Composable
private fun YearView(ctl: AppController) {
    val year = ctl.view.year
    ViewCard {
        NavHeader("$year", ctl.mode.label, L("Ano anterior", "Previous year", "Año anterior"), L("Próximo ano", "Next year", "Año siguiente"), ctl::prev, ctl::next)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = when {
                maxWidth >= 640.dp -> 4
                maxWidth >= 300.dp -> 3
                else -> 2
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                (1..12).chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { m -> MiniMonth(ctl, LocalDate.of(year, m, 1), Modifier.weight(1f)) }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Mês pequeno da visualização anual; toque abre o mês. */
@Composable
private fun MiniMonth(ctl: AppController, first: LocalDate, modifier: Modifier) {
    val look = Look.current
    val c = look.colors
    val personal = ctl.mode == CalendarMode.PERSONAL
    val sch = ctl.current
    val isCurrent = first.year == ctl.today.year && first.monthValue == ctl.today.monthValue
    val offset = first.dayOfWeek.value % 7
    val cells = List(offset) { null } + (1..first.lengthOfMonth()).map { first.withDayOfMonth(it) }
    val letters = weekLetters()
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).press { ctl.openMonth(first.year, first.monthValue) }
            .semantics { contentDescription = Dates.monthYear(first) }
            .padding(4.dp),
    ) {
        Text(
            Dates.capitalize(Dates.monthName(first)), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (isCurrent) c.primary else c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            letters.forEach { Text("$it", Modifier.weight(1f), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVar, textAlign = TextAlign.Center) }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    if (d == null) Spacer(Modifier.weight(1f))
                    else {
                        val isToday = d == ctl.today
                        val bg = when {
                            isToday -> c.primary
                            personal -> if (ctl.personal.eventsOn(Dates.iso(d)).isNotEmpty()) mix(c.primary, .35f, c.surface) else Color.Transparent
                            Engine.status(d, sch).work -> mix(look.sc, .40f, c.surface)
                            else -> Color.Transparent
                        }
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(1.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                            Text("${d.dayOfMonth}", fontSize = 8.sp, lineHeight = 9.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal, color = if (isToday) c.onPrimary else c.onSurface, maxLines = 1)
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------------------------------------------------------------- lista de eventos

/** Abas Próximos | Anteriores das listas. */
@Composable
private fun ListTabs(past: Boolean, onChange: (Boolean) -> Unit) {
    SegTabs(listOf(false, true), past, { if (it) L("Anteriores", "Past", "Anteriores") else L("Próximos", "Upcoming", "Próximos") }, onChange, Modifier.padding(bottom = 6.dp))
}

/** Título do grupo de um dia na lista. */
@Composable
private fun ListDayHeader(d: LocalDate, today: LocalDate) {
    val c = Look.colors
    val t = dayTitle(d, today) + if (d.year != today.year) " · ${d.year}" else ""
    Text(t, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (d == today) c.primary else c.onSurfaceVar, modifier = Modifier.padding(start = 2.dp, top = 14.dp, bottom = 2.dp))
}

@Composable
private fun MoreButton(shown: Int, total: Int, onMore: () -> Unit) {
    if (total > shown) AppButton(L("Mostrar mais", "Show more", "Mostrar más") + " (${total - shown})", onMore, Modifier.padding(top = 12.dp), kind = BtnKind.Text)
}

private const val PAGE = 60

@Composable
private fun PersonalListView(ctl: AppController) {
    val c = Look.colors
    var past by rememberSaveable { mutableStateOf(false) }
    var limit by rememberSaveable(past) { mutableIntStateOf(PAGE) }
    val all = Views.eventList(ctl.personal, Dates.iso(ctl.today), past)
    SectionCard(
        L("Lista de eventos", "Event list", "Lista de eventos"),
        if (past) L("Eventos anteriores a hoje, do mais recente ao mais antigo.", "Events before today, newest first.", "Eventos anteriores a hoy, del más reciente al más antiguo.")
        else L("Todos os eventos a partir de hoje, em ordem de data.", "All events from today on, by date.", "Todos los eventos desde hoy, por fecha."),
        L("Evento", "Event", "Evento"), { ctl.openEvent(Dates.iso(ctl.today)) },
    ) {
        Spacer(Modifier.size(12.dp))
        ListTabs(past) { past = it }
        if (all.isEmpty()) Para(if (past) L("Nenhum evento anterior.", "No past events.", "No hay eventos anteriores.") else L("Nenhum evento a partir de hoje.", "No events from today on.", "No hay eventos desde hoy."), Modifier.padding(top = 12.dp))
        var lastDate = ""
        all.take(limit).forEach { e ->
            if (e.date != lastDate) { lastDate = e.date; ListDayHeader(Dates.parse(e.date), ctl.today) }
            Row(Modifier.fillMaxWidth().press { ctl.openEvent(e.date, e) }.padding(vertical = 8.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp, 34.dp).clip(RoundedCornerShape(50)).background(hexColor(e.color)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(e.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${e.timeText} · ${e.kind.label}", fontSize = 12.sp, color = c.onSurfaceVar)
                }
                if (e.remind >= 0) Icon(Icons.Outlined.Notifications, L("Com lembrete", "With reminder", "Con recordatorio"), tint = c.onSurfaceVar, modifier = Modifier.padding(start = 8.dp).size(18.dp))
            }
        }
        MoreButton(limit, all.size) { limit += PAGE }
    }
}

@Composable
private fun WorkListView(ctl: AppController) {
    val c = Look.colors
    var past by rememberSaveable { mutableStateOf(false) }
    var limit by rememberSaveable(past) { mutableIntStateOf(PAGE) }
    val sch = ctl.current
    val all = Views.workList(sch, Dates.iso(ctl.today), past)
    val ps = Engine.positions(sch.blocks)
    SectionCard(
        L("Lista de eventos", "Event list", "Lista de eventos"),
        L("Notas, feriados, períodos e mudanças da escala selecionada.", "Notes, holidays, periods and changes of the selected schedule.", "Notas, festivos, períodos y cambios del horario seleccionado."),
        L("Período", "Period", "Período"), { ctl.openPeriod() },
    ) {
        Spacer(Modifier.size(12.dp))
        ListTabs(past) { past = it }
        if (all.isEmpty()) Para(if (past) L("Nada anterior a hoje.", "Nothing before today.", "Nada anterior a hoy.") else L("Nada a partir de hoje.", "Nothing from today on.", "Nada desde hoy."), Modifier.padding(top = 12.dp))
        var lastDate = ""
        all.take(limit).forEach { item ->
            if (item.date != lastDate) { lastDate = item.date; ListDayHeader(Dates.parse(item.date), ctl.today) }
            when (item) {
                is WorkItem.NoteItem -> {
                    val n = item.note
                    val kind = if (n.holiday) L("Feriado", "Holiday", "Festivo") else L("Nota", "Note", "Nota")
                    DetailRow(if (n.holiday) HolidayRed else hexColor(n.color ?: "#f9ab00"), n.text.ifEmpty { kind }, kind) { ctl.openNote(item.date) }
                }
                is WorkItem.PeriodItem -> {
                    val p = item.period
                    DetailRow(hexColor(p.color), p.title.ifEmpty { L("Período sem descrição", "Untitled period", "Período sin descripción") }, L("Período", "Period", "Período") + " · ${Dates.fmt(p.start)} – ${Dates.fmt(p.end)}") { ctl.openPeriod(p) }
                }
                is WorkItem.ChangeItem -> {
                    val ch = item.change
                    val label = (ps.getOrNull(ch.pos) ?: ps[0]).label
                    DetailRow(c.primary, L("Mudança da escala", "Schedule change", "Cambio del horario"), label) { ctl.openChange(ch.date, ch) }
                }
            }
        }
        MoreButton(limit, all.size) { limit += PAGE }
    }
}
