package com.kalendrium.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalendrium.domain.Category
import com.kalendrium.domain.Dates
import com.kalendrium.domain.Engine
import com.kalendrium.domain.Event
import com.kalendrium.domain.L
import com.kalendrium.domain.P
import com.kalendrium.domain.ViewMode
import com.kalendrium.domain.Views
import com.kalendrium.domain.weekHeader
import com.kalendrium.ui.theme.HolidayRed
import com.kalendrium.ui.theme.Look
import com.kalendrium.ui.theme.hexColor
import java.time.LocalDate

/** As três visualizações do redesign. Valores antigos gravados (diária, anual…) abrem no Mês. */
private enum class CalView { MONTH, WEEK, AGENDA }

private val ViewMode.calView: CalView get() = when (this) {
    ViewMode.WEEK -> CalView.WEEK
    ViewMode.LIST -> CalView.AGENDA
    else -> CalView.MONTH
}

private val CalView.mode: ViewMode get() = when (this) {
    CalView.MONTH -> ViewMode.MONTH
    CalView.WEEK -> ViewMode.WEEK
    CalView.AGENDA -> ViewMode.LIST
}

@Composable
fun CalendarScreen(ctl: AppController) {
    val view = ctl.viewMode.calView
    ScreenScroll {
        CalendarHeader(ctl, view)
        Segmented(CalView.entries, view, {
            when (it) {
                CalView.MONTH -> L("Mês", "Month", "Mes")
                CalView.WEEK -> L("Semana", "Week", "Semana")
                CalView.AGENDA -> L("Agenda", "Agenda", "Agenda")
            }
        }, { ctl.selectView(it.mode) })
        Filters(ctl)
        when (view) {
            CalView.MONTH -> {
                MonthGridCard(ctl)
                SelectedDayPanel(ctl)
            }
            CalView.WEEK -> WeekList(ctl)
            CalView.AGENDA -> MonthAgenda(ctl)
        }
    }
}

@Composable
private fun CalendarHeader(ctl: AppController, view: CalView) {
    val c = Look.colors
    val title = if (view == CalView.WEEK) {
        val w = Views.weekOf(ctl.selected)
        "${Dates.shortDayMonth(w.first())} – ${Dates.shortDayMonth(w.last())}"
    } else Dates.capitalize(Dates.monthName(ctl.view))
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).press { ctl.monthPicker = true }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.5).sp, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            Text("${(if (view == CalView.WEEK) ctl.selected else ctl.view).year}", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = c.onSurfaceVar, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(
            L("Hoje", "Today", "Hoy"),
            Modifier.clip(RoundedCornerShape(50)).border(1.dp, c.outlineVar, RoundedCornerShape(50)).press(onClick = ctl::goToday).padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.onSurface,
        )
        IconAction(KIcons.ChevronLeft, if (view == CalView.WEEK) L("Semana anterior", "Previous week", "Semana anterior") else L("Mês anterior", "Previous month", "Mes anterior"), ctl::prev)
        IconAction(KIcons.ChevronRight, if (view == CalView.WEEK) L("Próxima semana", "Next week", "Semana siguiente") else L("Próximo mês", "Next month", "Mes siguiente"), ctl::next)
    }
}

/** Legenda que também filtra: Escala (turno/folga) e as categorias de evento. */
@Composable
private fun Filters(ctl: AppController) {
    val c = Look.colors
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (ctl.ready) PillChip(L("Escala", "Schedule", "Horario"), FILTER_SHIFT !in ctl.hidden, { ctl.toggleFilter(FILTER_SHIFT) }, dot = c.workStrong, role = Role.Checkbox)
        Category.entries.forEach { k ->
            PillChip(k.label, k.key !in ctl.hidden, { ctl.toggleFilter(k.key) }, dot = k.tint, role = Role.Checkbox)
        }
    }
}

/** Grade do mês: número, pontinhos dos eventos (até 3) e a barrinha de trabalho/folga embaixo. */
@Composable
private fun MonthGridCard(ctl: AppController) {
    val c = Look.colors
    val first = ctl.view
    val start = Views.weekStart(first)
    val weeks = ((Dates.dd(start, first.plusMonths(1).minusDays(1)) / 7) + 1).toInt()
    val showShift = ctl.ready && FILTER_SHIFT !in ctl.hidden
    // eventos do mês calculados uma vez por versão dos dados e do filtro
    val byDay = remember(ctl.personal, ctl.data, first, ctl.hidden) {
        (0 until weeks * 7).associate { i -> val d = start.plusDays(i.toLong()); d to ctl.eventsOn(d).filter { it.category.key !in ctl.hidden } }
    }
    Column(Modifier.fillMaxWidth().cardSurface(RoundedCornerShape(22.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            weekHeader().forEach { Text(it, Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVar, textAlign = TextAlign.Center) }
        }
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (k in 0 until 7) {
                    val d = start.plusDays((w * 7 + k).toLong())
                    DayCell(ctl, d, d.monthValue == first.monthValue, showShift, byDay[d].orEmpty(), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCell(ctl: AppController, d: LocalDate, inMonth: Boolean, showShift: Boolean, events: List<Event>, modifier: Modifier) {
    val c = Look.colors
    val s = Dates.iso(d)
    val on = inMonth && d == ctl.selected
    val isToday = d == ctl.today
    val note = if (ctl.ready) ctl.current.notes[s] else null
    val holiday = note?.holiday == true
    val st = if (showShift && inMonth) dayState(ctl, d) else null
    val shape = RoundedCornerShape(14.dp)
    val bg = when {
        on -> c.primary
        inMonth -> c.onSurface.copy(alpha = .05f)
        else -> Color.Transparent
    }
    val fg = when {
        on -> c.onPrimary
        !inMonth -> c.onSurfaceVar.copy(alpha = .45f)
        holiday -> HolidayRed
        else -> c.onSurface
    }
    val desc = buildString {
        append(Dates.capitalize(Dates.weekdayDayMonth(d)))
        if (isToday) append(", " + L("hoje", "today", "hoy"))
        st?.let { if (it.label.isNotEmpty()) append(", " + it.label) }
        if (holiday) append(", " + L("feriado", "holiday", "festivo"))
        if (events.isNotEmpty()) append(", ${events.size} " + P(events.size, "evento", "eventos", "event", "events", "evento", "eventos"))
    }
    Column(
        modifier.height(58.dp).clip(shape).background(bg)
            .then(if (isToday && !on) Modifier.border(2.dp, c.primary, shape) else Modifier)
            .press(enabled = inMonth, role = Role.Button) { if (d == ctl.selected) ctl.openEvent(s) else ctl.showDay(d) }
            .semantics { selected = on; contentDescription = desc },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("${d.dayOfMonth}", fontSize = 15.sp, fontWeight = if (isToday || on) FontWeight.ExtraBold else FontWeight.Bold, color = fg)
            // nota pessoal ou da escala: cantinho colorido
            val corner = ctl.personal.notes[s]?.color ?: note?.takeIf { !it.holiday }?.color ?: note?.takeIf { !it.holiday }?.let { "#f9ab00" }
            if (inMonth && corner != null) Box(Modifier.align(Alignment.TopEnd).padding(end = 5.dp).size(5.dp).clip(CircleShape).background(hexColor(corner)))
        }
        Row(Modifier.height(9.dp).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (inMonth) events.distinctBy { it.color }.take(3).forEach { e ->
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (on) c.onPrimary else hexColor(e.color)))
            }
        }
        Box(Modifier.padding(top = 4.dp).size(18.dp, 3.dp).clip(RoundedCornerShape(50)).background(if (on && st != null) c.onPrimary.copy(alpha = .85f) else st?.color ?: Color.Transparent))
    }
}

/** Painel do dia selecionado: situação na escala, notas, eventos e "Adicionar em…". */
@Composable
private fun SelectedDayPanel(ctl: AppController) {
    val c = Look.colors
    val d = ctl.selected
    val s = Dates.iso(d)
    val showShift = ctl.ready && FILTER_SHIFT !in ctl.hidden
    Column(Modifier.fillMaxWidth().cardSurface(RoundedCornerShape(22.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(KIcons.Calendar, c.primary, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                val dm = Dates.dayMonth(d)
                Text(L("$dm de ${d.year}", "$dm, ${d.year}", "$dm de ${d.year}"), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = c.onSurface)
                Text((if (d == ctl.today) L("Hoje · ", "Today · ", "Hoy · ") else "") + Dates.capitalize(d.format(java.time.format.DateTimeFormatter.ofPattern("EEEE", com.kalendrium.domain.I18n.locale))), fontSize = 13.sp, color = c.onSurfaceVar)
            }
            if (showShift) {
                val st = dayState(ctl, d)
                if (st.label.isNotEmpty()) Badge(st.short, st.color.copy(alpha = .20f), if (c.dark) st.color else c.onSurface)
            }
        }
        DayRows(ctl, d, showShift = showShift, hiddenCategories = ctl.hidden)
        if (ctl.eventsOn(d).none { it.category.key !in ctl.hidden } && ctl.personal.notes[s] == null && (!ctl.ready || ctl.current.notes[s] == null)) {
            Text(L("Nenhum compromisso neste dia.", "Nothing planned for this day.", "Nada previsto para este día."), fontSize = 14.sp, color = c.onSurfaceVar, modifier = Modifier.padding(horizontal = 4.dp))
        }
        AddDashedButton(L("Adicionar em ", "Add on ", "Añadir el ") + Dates.shortDayMonth(d)) { ctl.openEvent(s) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(
                if (ctl.personal.notes[s] != null) L("Nota pessoal", "Personal note", "Nota personal") else L("+ Nota pessoal", "+ Personal note", "+ Nota personal"),
                { ctl.openDayNote(s) }, kind = BtnKind.Text, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            )
            if (ctl.ready) AppButton(
                L("Nota ou feriado na escala", "Schedule note or holiday", "Nota o festivo del horario"),
                { ctl.openNote(s) }, kind = BtnKind.Text, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

/** Semana: um cartão por dia com a escala e os eventos; tocar no dia abre o Mês nele. */
@Composable
private fun WeekList(ctl: AppController) {
    val c = Look.colors
    val showShift = ctl.ready && FILTER_SHIFT !in ctl.hidden
    Views.weekOf(ctl.selected).forEach { d ->
        val events = ctl.eventsOn(d).filter { it.category.key !in ctl.hidden }
        val isToday = d == ctl.today
        Row(
            Modifier.fillMaxWidth().cardSurface(RoundedCornerShape(20.dp)).then(if (isToday) Modifier.border(1.5.dp, c.primary, RoundedCornerShape(20.dp)) else Modifier)
                .padding(12.dp),
        ) {
            Column(
                Modifier.width(52.dp).clip(RoundedCornerShape(14.dp)).press { ctl.showDay(d); ctl.selectView(ViewMode.MONTH) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(weekdayShort(d), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isToday) c.primary else c.onSurfaceVar)
                Text("${d.dayOfMonth}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (isToday) c.primary else c.onSurface)
                if (showShift) {
                    val st = dayState(ctl, d)
                    Box(Modifier.padding(top = 4.dp).size(20.dp, 4.dp).clip(RoundedCornerShape(50)).background(st.color))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showShift) {
                    val st = dayState(ctl, d)
                    Text(st.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVar)
                }
                if (events.isEmpty()) Text(L("Sem compromissos", "Nothing planned", "Sin planes"), fontSize = 13.sp, color = c.onSurfaceVar.copy(alpha = .8f))
                events.forEach { EventAgendaRow(ctl, it) }
                Text(
                    L("+ Adicionar", "+ Add", "+ Añadir"), Modifier.clip(RoundedCornerShape(10.dp)).press { ctl.openEvent(Dates.iso(d)) }.padding(vertical = 6.dp, horizontal = 4.dp),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.primary,
                )
            }
        }
    }
}

/** Agenda do mês exibido: só os dias com eventos, notas ou feriados, com a situação na escala. */
@Composable
private fun MonthAgenda(ctl: AppController) {
    val c = Look.colors
    val first = ctl.view
    val showShift = ctl.ready && FILTER_SHIFT !in ctl.hidden
    val days = (0 until first.lengthOfMonth()).map { first.plusDays(it.toLong()) }.filter { d ->
        val s = Dates.iso(d)
        ctl.eventsOn(d).any { it.category.key !in ctl.hidden } || ctl.personal.notes[s] != null || (ctl.ready && ctl.current.notes[s] != null)
    }
    if (days.isEmpty()) {
        Column(Modifier.fillMaxWidth().cardSurface(RoundedCornerShape(22.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(L("Nenhum compromisso em ", "Nothing planned in ", "Nada previsto en ") + Dates.monthName(first) + ".", fontSize = 15.sp, color = c.onSurfaceVar)
            AddDashedButton(L("Novo evento", "New event", "Nuevo evento")) { ctl.openEvent(Dates.iso(if (first.monthValue == ctl.today.monthValue && first.year == ctl.today.year) ctl.today else first)) }
        }
        return
    }
    days.forEach { d ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val prefix = when (d) {
                    ctl.today -> L("Hoje · ", "Today · ", "Hoy · ")
                    ctl.today.plusDays(1) -> L("Amanhã · ", "Tomorrow · ", "Mañana · ")
                    else -> ""
                }
                Text(prefix + weekdayShort(d) + ", " + Dates.shortDayMonth(d), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (d == ctl.today) c.primary else c.onSurfaceVar)
                if (showShift) {
                    val st = dayState(ctl, d)
                    if (st.short.isNotEmpty()) Badge(st.short, st.color.copy(alpha = .18f), if (c.dark) st.color else c.onSurface)
                }
            }
            Column(Modifier.fillMaxWidth().cardSurface(RoundedCornerShape(18.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DayRows(ctl, d, showShift = false, hiddenCategories = ctl.hidden)
            }
        }
    }
}
