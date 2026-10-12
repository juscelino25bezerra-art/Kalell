package com.kalendrium.ui

import com.kalendrium.domain.L
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalendrium.domain.Dates
import com.kalendrium.domain.I18n
import com.kalendrium.domain.P
import com.kalendrium.domain.Event
import com.kalendrium.domain.EventKind
import com.kalendrium.domain.Personal
import com.kalendrium.domain.PersonalData
import com.kalendrium.ui.theme.Look
import com.kalendrium.ui.theme.hexColor
import com.kalendrium.ui.theme.mix
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** Singular e plural de cada tipo, para o resumo do dia. */
private fun kindName(k: EventKind, n: Int): String = when (k) {
    EventKind.EVENT -> P(n, "evento", "eventos", "event", "events", "evento", "eventos")
    EventKind.APPOINTMENT -> P(n, "compromisso", "compromissos", "appointment", "appointments", "cita", "citas")
    EventKind.REMINDER -> P(n, "lembrete", "lembretes", "reminder", "reminders", "recordatorio", "recordatorios")
}

/** Cor sugerida para cada tipo de item (pode ser trocada na folha). */
private val KIND_COLORS = mapOf(EventKind.EVENT to "#1a73e8", EventKind.APPOINTMENT to "#9334e6", EventKind.REMINDER to "#e8710a")

// ---------------------------------------------------------------- resumo do dia (card superior)

/** Texto do card superior no modo Pessoal. */
fun personalSummary(p: PersonalData, today: LocalDate, now: LocalTime): String {
    val list = p.eventsOn(Dates.iso(today))
    if (list.isEmpty()) return L("Nenhum compromisso para hoje", "Nothing scheduled for today", "Nada programado para hoy")
    // conta por tipo: "1 compromisso e 2 lembretes hoje"
    val parts = EventKind.entries.mapNotNull { k ->
        val n = list.count { it.kind == k }
        if (n == 0) null else "$n ${kindName(k, n)}"
    }
    val count = (if (parts.size > 1) parts.dropLast(1).joinToString(", ") + L(" e ", " and ", " y ") + parts.last() else parts[0]) + L(" hoje", " today", " hoy")
    val hm = hm(now.hour, now.minute)
    val next = list.firstOrNull { !it.allDay && it.start >= hm }
    return when {
        next != null -> "$count  •  " + L("Próximo às", "Next at", "Próximo a las") + " ${next.start}: ${next.title}"
        list.size == 1 -> "$count  •  ${list[0].title}"
        else -> count
    }
}

// ---------------------------------------------------------------- calendário

@Composable
fun RowScope.PersonalLegend() {
    val c = Look.colors
    LegendItem(mix(c.primary, .35f, c.surface), L("Com eventos", "With events", "Con eventos"))
    LegendItem(c.primary, L("Hoje", "Today", "Hoy"), round = true)
    LegendItem(hexColor("#f9ab00"), L("Nota", "Note", "Nota"), round = true)
}

/** Dia do calendário pessoal: número, até dois eventos e a nota do dia. */
@Composable
fun PersonalDayCell(
    d: LocalDate,
    p: PersonalData,
    isToday: Boolean,
    isSelected: Boolean,
    narrow: Boolean,
    minH: Dp,
    modifier: Modifier,
    onDay: (String) -> Unit,
) {
    val look = Look.current
    val c = look.colors
    val s = Dates.iso(d)
    val events = p.eventsOn(s)
    val note = p.notes[s]
    val shape = RoundedCornerShape(if (narrow) 14.dp else 16.dp)
    val busy = events.isNotEmpty()
    val bg = when {
        look.glass && busy -> mix(c.primary, .16f, look.glassStrong)
        look.glass -> look.glassStrong
        busy -> mix(c.primary, if (c.dark) .24f else .14f, c.surface)
        else -> look.page
    }
    val desc = Dates.dayMonth(d) +
        (if (events.isEmpty()) L(", sem eventos", ", no events", ", sin eventos") else ", ${events.size} " + L("evento(s)", "event(s)", "evento(s)") + ": ${events.joinToString { it.title }}") +
        (if (note != null) L(", com nota", ", with note", ", con nota") else "")
    Box(
        modifier.heightIn(min = minH)
            .clip(shape).background(bg)
            .then(if (look.glass) Modifier.border(1.dp, look.glassBorder, shape) else Modifier)
            .then(if (isSelected) Modifier.border(2.5.dp, c.primary, shape) else Modifier)
            .press { onDay(s) }
            .semantics { contentDescription = desc },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = if (narrow) 4.dp else 5.dp, vertical = if (narrow) 5.dp else 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(if (isToday) c.primary else androidx.compose.ui.graphics.Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${d.dayOfMonth}", fontSize = if (narrow) 13.sp else 14.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isToday) c.onPrimary else c.onSurface,
                )
            }
            Spacer(Modifier.size(2.dp))
            events.take(2).forEach { DayChip(it.title, it.color) }
            if (events.size > 2) Text("+${events.size - 2}", Modifier.padding(top = 1.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVar)
        }
        if (note != null) Dot(hexColor(note.color), 7.dp, Modifier.align(Alignment.TopStart).offset(7.dp, 7.dp))
    }
}

// ---------------------------------------------------------------- eventos do dia selecionado

@Composable
fun DayEventsCard(ctl: AppController) {
    val c = Look.colors
    val day = ctl.selected
    val s = Dates.iso(day)
    val events = ctl.personal.eventsOn(s)
    val note = ctl.personal.notes[s]
    val title = when (day) {
        ctl.today -> L("Hoje", "Today", "Hoy")
        ctl.today.plusDays(1) -> L("Amanhã", "Tomorrow", "Mañana")
        else -> Dates.capitalize(Dates.weekdayDayMonth(day))
    }
    val text = when (events.size) {
        0 -> (if (day == ctl.today) Dates.capitalize(Dates.weekdayDayMonth(day)) + L(" · nada marcado.", " · nothing scheduled.", " · nada programado.") else L("Nada marcado neste dia.", "Nothing scheduled on this day.", "Nada programado este día.")) +
            L(" Toque de novo no dia selecionado para criar um evento.", " Tap the selected day again to create an event.", " Toque de nuevo el día seleccionado para crear un evento.")
        1 -> L("1 item", "1 item", "1 elemento")
        else -> "${events.size} " + L("itens", "items", "elementos")
    }
    SectionCard(title, text, L("Evento", "Event", "Evento"), { ctl.openEvent(s) }) {
        events.forEachIndexed { i, e ->
            Row(Modifier.fillMaxWidth().press { ctl.openEvent(s, e) }.padding(vertical = 12.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp, 36.dp).clip(RoundedCornerShape(50)).background(hexColor(e.color)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(e.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${e.timeText} · ${e.kind.label}", fontSize = 12.sp, color = c.onSurfaceVar)
                    if (e.description.isNotEmpty()) Text(e.description, fontSize = 12.sp, color = c.onSurfaceVar, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (e.remind >= 0) Icon(Icons.Outlined.Notifications, L("Com lembrete", "With reminder", "Con recordatorio"), tint = c.onSurfaceVar, modifier = Modifier.padding(start = 8.dp).size(18.dp))
            }
            if (i < events.lastIndex) HorizontalDivider(color = c.outlineVar)
        }
        if (note != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).controlSurface(RoundedCornerShape(18.dp), Look.current.page)
                    .press { ctl.openDayNote(s) }.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Box(Modifier.size(4.dp, 20.dp).clip(RoundedCornerShape(50)).background(hexColor(note.color)))
                Spacer(Modifier.width(10.dp))
                Text(note.text, fontSize = 14.sp, color = c.onSurface, lineHeight = 20.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
        }
        AppButton(
            if (note != null) L("Editar nota", "Edit note", "Editar nota") else L("Adicionar nota", "Add note", "Añadir nota"), { ctl.openDayNote(s) },
            Modifier.padding(top = 12.dp), kind = BtnKind.Text, icon = Icons.Outlined.Edit,
            padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        )
    }
}

// ---------------------------------------------------------------- folha de evento

/** "09:05" sempre com algarismos 0–9, qualquer que seja o idioma do aparelho. */
private fun hm(h: Int, m: Int) = String.format(Locale.ROOT, "%02d:%02d", h, m)

@Composable
fun EventSheet(ctl: AppController, date0: String, e: Event?, start0: String? = null) {
    val now = ctl.now()
    // toque numa hora da visualização diária já sugere aquele horário
    val defStart = rememberSaveable { start0?.takeIf { Personal.isTime(it) } ?: hm(minOf(now.hour + 1, 23), 0) }
    val initial = remember {
        EventForm(
            title = e?.title ?: "", kind = e?.kind ?: EventKind.EVENT, date = e?.date ?: date0, allDay = e?.allDay ?: false,
            start = e?.start?.ifEmpty { null } ?: defStart,
            end = if (e != null) e.end else if (defStart < "23:00") hm(defStart.take(2).toInt() + 1, 0) else "23:59",
            description = e?.description ?: "", color = e?.color ?: KIND_COLORS.getValue(EventKind.EVENT), remind = e?.remind ?: -1,
        )
    }
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var kindKey by rememberSaveable { mutableStateOf(initial.kind.key) }
    val kind = EventKind.of(kindKey)
    var date by rememberSaveable { mutableStateOf(initial.date) }
    var allDay by rememberSaveable { mutableStateOf(initial.allDay) }
    var start by rememberSaveable { mutableStateOf(initial.start) }
    var end by rememberSaveable { mutableStateOf(initial.end) }
    var desc by rememberSaveable { mutableStateOf(initial.description) }
    var color by rememberSaveable { mutableStateOf(initial.color) }
    var remind by rememberSaveable { mutableIntStateOf(initial.remind) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val form = EventForm(title, kind, date, allDay, start, end, desc, color, remind)
    AppSheet(ctl::closeSheet, if (e != null) L("Editar", "Edit", "Editar") + " ${e.kind.label.lowercase(I18n.locale)}" else L("Novo evento", "New event", "Nuevo evento"), dirty = form != initial) {
        Field(L("Título", "Title", "Título")) { AppTextField(title, { title = it }, placeholder = L("Ex.: Dentista, aniversário, reunião...", "E.g.: Dentist, birthday, meeting...", "Ej.: Dentista, cumpleaños, reunión..."), maxLength = 80) }
        Field(L("Tipo", "Type", "Tipo")) {
            SegTabs(EventKind.entries, kind, { it.label }, { k ->
                if (color == KIND_COLORS[kind]) color = KIND_COLORS.getValue(k)
                if (k == EventKind.REMINDER && remind < 0) remind = 0
                kindKey = k.key
            })
        }
        Field(L("Data", "Date", "Fecha")) { DateField(date, { date = it }) }
        CheckRow(allDay, { allDay = it }, L("Dia inteiro", "All day", "Todo el día"), L("Sem horário marcado. O lembrete, se houver, usa 9:00 como referência.", "No set time. The reminder, if any, uses 9:00 as reference.", "Sin hora fija. El recordatorio, si lo hay, usa las 9:00 como referencia."))
        if (!allDay) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(L("Início", "Start", "Inicio"), Modifier.weight(1f)) { TimeField(start, { start = it }) }
                Field(L("Fim (opcional)", "End (optional)", "Fin (opcional)"), Modifier.weight(1f)) { TimeField(end, { end = it }, clearable = true) }
            }
        }
        Field(L("Lembrete", "Reminder", "Recordatorio")) {
            SelectBox(Personal.REMINDERS.firstOrNull { it.first == remind }?.second ?: L("Sem lembrete", "No reminder", "Sin recordatorio")) { picking = true }
        }
        if (remind >= 0 && ctl.notifBlocked) NotificationsOff(ctl::enableNotifications)
        Field(L("Cor", "Color", "Color")) { ColorPicker(color, { color = it }, L("Cor do evento", "Event color", "Color del evento")) }
        Field(L("Descrição (opcional)", "Description (optional)", "Descripción (opcional)"), help = "${desc.length}/500 " + L("caracteres", "characters", "caracteres")) {
            AppTextField(desc, { desc = it }, placeholder = L("Local, observações...", "Place, notes...", "Lugar, observaciones..."), maxLength = 500, minLines = 3)
        }
        SheetActions {
            if (e != null) AppButton(L("Excluir", "Delete", "Eliminar"), { ctl.deleteEvent(e) }, kind = BtnKind.Danger)
            Spacer(Modifier.weight(1f))
            AppButton(L("Cancelar", "Cancel", "Cancelar"), ctl::closeSheet, kind = BtnKind.Text)
            AppButton(L("Salvar", "Save", "Guardar"), {
                ctl.submitEvent(e, form)
            }, kind = BtnKind.Filled)
        }
    }
    if (picking) {
        val idx = Personal.REMINDERS.indexOfFirst { it.first == remind }.coerceAtLeast(0)
        OptionListDialog(L("Lembrete", "Reminder", "Recordatorio"), Personal.REMINDERS.map { it.second }, idx, onPick = { remind = Personal.REMINDERS[it].first; picking = false }, onDismiss = { picking = false })
    }
}

/** Aviso na folha do evento: o lembrete não vai tocar enquanto as notificações estiverem bloqueadas. */
@Composable
private fun NotificationsOff(onEnable: () -> Unit) {
    val c = Look.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).controlSurface(RoundedCornerShape(18.dp), mix(com.kalendrium.ui.theme.Danger, .12f, c.surface))
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Notifications, null, tint = com.kalendrium.ui.theme.Danger, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(L("Notificações desativadas", "Notifications are off", "Notificaciones desactivadas"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.onSurface)
            Text(L("O lembrete não vai tocar até você permitir as notificações do Kalendrium.", "The reminder won't ring until you allow notifications for Kalendrium.", "El recordatorio no sonará hasta que permita las notificaciones de Kalendrium."), fontSize = 12.sp, color = c.onSurfaceVar, lineHeight = 16.sp)
        }
        AppButton(L("Ativar", "Turn on", "Activar"), onEnable, kind = BtnKind.Text)
    }
}

/** Campo de horário (HH:mm) que abre o seletor de hora do Material 3, em 24 horas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(value: String, onChange: (String) -> Unit, clearable: Boolean = false) {
    var open by rememberSaveable { mutableStateOf(false) }
    val c = Look.colors
    Row(
        Modifier.fillMaxWidth().then(fieldSurface(RoundedCornerShape(12.dp))).press(role = Role.Button) { open = true }.padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value.ifEmpty { "--:--" }, color = if (value.isEmpty()) c.onSurfaceVar else c.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Icon(AppIcons.Schedule, L("Escolher horário", "Choose time", "Elegir hora"), tint = c.onSurface, modifier = Modifier.size(22.dp))
    }
    if (open) {
        val t = Personal.time(value)
        val state = rememberTimePickerState(initialHour = t?.hour ?: 9, initialMinute = t?.minute ?: 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = c.surfaceContainer,
            text = {
                TimePicker(
                    state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = c.surface,
                        selectorColor = c.primary,
                        timeSelectorSelectedContainerColor = c.primaryContainer,
                        timeSelectorSelectedContentColor = c.onPrimaryContainer,
                        timeSelectorUnselectedContainerColor = c.surface,
                        timeSelectorUnselectedContentColor = c.onSurface,
                    ),
                )
            },
            confirmButton = {
                AppButton("OK", { onChange(hm(state.hour, state.minute)); open = false }, kind = BtnKind.Text)
            },
            dismissButton = {
                Row {
                    if (clearable && value.isNotEmpty()) AppButton(L("Limpar", "Clear", "Borrar"), { onChange(""); open = false }, kind = BtnKind.Text)
                    AppButton(L("Cancelar", "Cancel", "Cancelar"), { open = false }, kind = BtnKind.Text)
                }
            },
        )
    }
}

// ---------------------------------------------------------------- nota do dia (pessoal)

@Composable
fun DayNoteSheet(ctl: AppController, date: String) {
    val n = ctl.personal.notes[date]
    var text by rememberSaveable { mutableStateOf(n?.text ?: "") }
    var color by rememberSaveable { mutableStateOf(n?.color ?: "#f9ab00") }
    val dirty = text != (n?.text ?: "") || color != (n?.color ?: "#f9ab00")
    AppSheet(ctl::closeSheet, Dates.longDate(Dates.parse(date)), L("Nota pessoal deste dia. Não aparece na escala de trabalho.", "Personal note for this day. It doesn't show on the work schedule.", "Nota personal de este día. No aparece en el horario de trabajo."), dirty = dirty) {
        Field(L("Nota", "Note", "Nota"), help = "${text.length}/500 " + L("caracteres", "characters", "caracteres")) {
            AppTextField(text, { text = it }, placeholder = L("Ex.: Levar documentos, ligar para a escola...", "E.g.: Bring documents, call the school...", "Ej.: Llevar documentos, llamar al colegio..."), maxLength = 500, minLines = 5)
        }
        Field(L("Cor da nota", "Note color", "Color de la nota")) { ColorPicker(color, { color = it }, L("Cor da nota", "Note color", "Color de la nota")) }
        SheetActions {
            if (n != null) AppButton(L("Excluir nota", "Delete note", "Eliminar nota"), { ctl.deleteDayNote(date) }, kind = BtnKind.Danger)
            Spacer(Modifier.weight(1f))
            AppButton(L("Cancelar", "Cancel", "Cancelar"), ctl::closeSheet, kind = BtnKind.Text)
            AppButton(L("Salvar", "Save", "Guardar"), { ctl.submitDayNote(date, text, color) }, kind = BtnKind.Filled)
        }
    }
}
