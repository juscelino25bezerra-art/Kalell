// Kalell — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalell.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalell.domain.Agenda
import com.kalell.domain.Category
import com.kalell.domain.Dates
import com.kalell.domain.Engine
import com.kalell.domain.Event
import com.kalell.domain.EventKind
import com.kalell.domain.L
import com.kalell.domain.P
import com.kalell.domain.Personal
import com.kalell.domain.PersonalData
import com.kalell.domain.Repeat
import com.kalell.ui.theme.Look
import com.kalell.ui.theme.hexColor
import com.kalell.ui.theme.mix
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** Singular e plural de cada tipo, para o resumo do dia. */
private fun kindName(k: EventKind, n: Int): String = when (k) {
    EventKind.EVENT -> P(n, "evento", "eventos", "event", "events", "evento", "eventos")
    EventKind.APPOINTMENT -> P(n, "compromisso", "compromissos", "appointment", "appointments", "cita", "citas")
    EventKind.REMINDER -> P(n, "lembrete", "lembretes", "reminder", "reminders", "recordatorio", "recordatorios")
}

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

// ---------------------------------------------------------------- folha de evento (redesign 6.0)

/** "09:05" sempre com algarismos 0–9, qualquer que seja o idioma do aparelho. */
private fun hm(h: Int, m: Int) = String.format(Locale.ROOT, "%02d:%02d", h, m)

/** Soma minutos a "HH:mm" sem passar de 23:59 (o evento termina no mesmo dia). */
private fun plusMinutes(t: String, min: Int): String {
    val x = Personal.time(t) ?: return ""
    val total = (x.hour * 60 + x.minute + min).coerceAtMost(23 * 60 + 59)
    return hm(total / 60, total % 60)
}

/**
 * Novo evento / editar evento: categoria (a cor segue), título, quando (Hoje, Amanhã, outra data), aviso de
 * folga ou conflito com o turno, horário, repetição, lembrete e, recolhidos, local, descrição e cor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventSheet(ctl: AppController, date0: String, e: Event?, start0: String? = null) {
    val c = Look.colors
    val now = ctl.now()
    val defStart = rememberSaveable { start0?.takeIf { Personal.isTime(it) } ?: hm(minOf(now.hour + 1, 23), 0) }
    val initial = remember {
        EventForm(
            title = e?.title ?: "", kind = e?.kind ?: EventKind.EVENT, date = e?.date ?: date0, allDay = e?.allDay ?: false,
            start = e?.start?.ifEmpty { null } ?: defStart,
            end = if (e != null) e.end else if (defStart < "23:00") hm(defStart.take(2).toInt() + 1, 0) else "23:59",
            description = e?.description ?: "", color = e?.color ?: Category.PERSONAL.color, remind = e?.remind ?: -1,
            category = e?.category ?: Category.PERSONAL, repeat = e?.repeat ?: Repeat.NONE, until = e?.until ?: "", location = e?.location ?: "",
        )
    }
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var catKey by rememberSaveable { mutableStateOf(initial.category.key) }
    val category = Category.of(catKey)
    var date by rememberSaveable { mutableStateOf(initial.date) }
    var allDay by rememberSaveable { mutableStateOf(initial.allDay) }
    var start by rememberSaveable { mutableStateOf(initial.start) }
    var end by rememberSaveable { mutableStateOf(initial.end) }
    var desc by rememberSaveable { mutableStateOf(initial.description) }
    var location by rememberSaveable { mutableStateOf(initial.location) }
    var color by rememberSaveable { mutableStateOf(initial.color) }
    var remind by rememberSaveable { mutableIntStateOf(initial.remind) }
    var repeatKey by rememberSaveable { mutableStateOf(initial.repeat.key) }
    val repeat = Repeat.of(repeatKey)
    var until by rememberSaveable { mutableStateOf(initial.until) }
    var more by rememberSaveable { mutableStateOf(e != null && (e.location.isNotEmpty() || e.description.isNotEmpty() || e.color != e.category.color)) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    val form = EventForm(title, initial.kind, date, allDay, start, end, desc, color, remind, category, repeat, until, location)
    val canSave = title.isNotBlank()
    AppSheet(ctl::closeSheet, dirty = form != initial) {
        // cabeçalho do conceito: fechar, título e Salvar (ativo só com título)
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(KIcons.Close, L("Cancelar", "Cancel", "Cancelar"), ctl::closeSheet)
            Text(
                if (e != null) L("Editar evento", "Edit event", "Editar evento") else L("Novo evento", "New event", "Nuevo evento"),
                Modifier.weight(1f).padding(start = 6.dp), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = c.onSurface,
            )
            AppButton(L("Salvar", "Save", "Guardar"), { ctl.submitEvent(e, form) }, kind = BtnKind.Filled, enabled = canSave)
        }
        // categoria
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Category.entries.forEach { k ->
                val on = k == category
                val shape = RoundedCornerShape(16.dp)
                Column(
                    Modifier.weight(1f).heightIn(min = 70.dp).clip(shape)
                        .background(if (on) mix(k.tint, .18f, Look.current.card) else Look.current.card)
                        .border(1.5.dp, if (on) k.tint else c.outlineVar, shape)
                        .press(role = Role.RadioButton) {
                            // a cor acompanha a categoria, a menos que tenha sido escolhida à mão
                            if (color.equals(category.color, ignoreCase = true)) color = k.color
                            catKey = k.key
                        }
                        .semantics { selected = on }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                ) {
                    Icon(k.icon, null, tint = if (on) k.tint else c.onSurfaceVar, modifier = Modifier.size(22.dp))
                    Text(k.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (on) c.onSurface else c.onSurfaceVar, maxLines = 1)
                }
            }
        }
        Field(L("Título", "Title", "Título")) {
            AppTextField(title, { title = it }, placeholder = L("Ex.: Academia, reunião, médico...", "E.g.: Gym, meeting, doctor...", "Ej.: Gimnasio, reunión, médico..."), maxLength = 80)
        }
        // quando
        Text(L("Quando", "When", "Cuándo"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVar)
        Spacer(Modifier.height(6.dp))
        val today = ctl.today
        val tomorrow = today.plusDays(1)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateChip(L("Hoje", "Today", "Hoy"), weekdayShort(today) + ", " + Dates.shortDayMonth(today), date == Dates.iso(today), Modifier.weight(1f)) { date = Dates.iso(today) }
            DateChip(L("Amanhã", "Tomorrow", "Mañana"), weekdayShort(tomorrow) + ", " + Dates.shortDayMonth(tomorrow), date == Dates.iso(tomorrow), Modifier.weight(1f)) { date = Dates.iso(tomorrow) }
            val other = date != Dates.iso(today) && date != Dates.iso(tomorrow) && Dates.isDate(date)
            DateChip(
                if (other) weekdayShort(Dates.parse(date)) + ", " + Dates.shortDayMonth(Dates.parse(date)) else L("Outra data", "Other date", "Otra fecha"),
                if (other) "${Dates.parse(date).year}" else L("escolher", "choose", "elegir"), other, Modifier.weight(1f),
            ) { pickDate = true }
        }
        if (pickDate) DatePickDialog(date, { date = it; pickDate = false }, { pickDate = false })
        ShiftContext(ctl, date, allDay, start, end) { s, en -> start = s; end = en }
        // dia todo
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(L("Dia todo", "All day", "Todo el día"), Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
            Switch(allDay, { allDay = it }, colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary))
        }
        if (!allDay) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(L("Início", "Start", "Inicio"), Modifier.weight(1f)) {
                    TimeField(start, { v ->
                        // mantém a duração ao mudar o início
                        val dur = Personal.time(end)?.let { en -> Personal.time(start)?.let { st -> (en.toSecondOfDay() - st.toSecondOfDay()) / 60 } }
                        start = v
                        if (dur != null && dur > 0) end = plusMinutes(v, dur)
                    })
                }
                Field(L("Término", "End", "Fin"), Modifier.weight(1f)) { TimeField(end, { end = it }, clearable = true) }
            }
        }
        // repetir
        Field(L("Repetir", "Repeat", "Repetir")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Repeat.entries.forEach { r -> PillChip(r.label, r == repeat, { repeatKey = r.key }) }
            }
            if (repeat == Repeat.OFF_DAYS) Para(
                if (ctl.ready) L("Repete nas folgas e nos períodos (férias, licenças) da escala ", "Repeats on days off and periods (vacations, leaves) of ", "Se repite en los días libres y períodos (vacaciones, permisos) del horario ") + "\"${ctl.current.name}\"."
                else L("Crie uma escala para o app saber quais são as suas folgas.", "Create a schedule so the app knows your days off.", "Cree un horario para que la app sepa cuáles son sus días libres."),
                Modifier.padding(top = 6.dp),
            )
            if (repeat != Repeat.NONE) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(L("Até", "Until", "Hasta"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVar, modifier = Modifier.width(48.dp))
                    if (until.isEmpty()) AppButton(L("Sem data final · definir", "No end date · set", "Sin fecha final · definir"), { until = date }, kind = BtnKind.Text)
                    else {
                        DateField(until, { until = it }, min = date, modifier = Modifier.weight(1f))
                        IconAction(KIcons.Close, L("Sem data final", "No end date", "Sin fecha final"), { until = "" })
                    }
                }
            }
        }
        // lembrete
        Field(L("Lembrete", "Reminder", "Recordatorio")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Personal.REMINDERS.forEach { (m, label) -> PillChip(label, m == remind, { remind = m }) }
            }
        }
        if (remind >= 0 && ctl.notifBlocked) NotificationsOff(ctl::enableNotifications)
        // mais opções
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).cardSurface(RoundedCornerShape(14.dp)).press { more = !more }.heightIn(min = 48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(L("Local, descrição e cor", "Place, description and color", "Lugar, descripción y color"), Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.primary)
            Text(if (more) L("Ocultar", "Hide", "Ocultar") else L("Opcional", "Optional", "Opcional"), fontSize = 13.sp, color = c.onSurfaceVar)
        }
        if (more) {
            Field(L("Local", "Place", "Lugar")) { AppTextField(location, { location = it }, placeholder = L("Adicionar local", "Add place", "Añadir lugar"), maxLength = 80) }
            Field(L("Descrição", "Description", "Descripción"), help = "${desc.length}/500 " + L("caracteres", "characters", "caracteres")) {
                AppTextField(desc, { desc = it }, placeholder = L("Detalhes, links, o que levar…", "Details, links, what to bring…", "Detalles, enlaces, qué llevar…"), maxLength = 500, minLines = 3)
            }
            Field(L("Cor", "Color", "Color"), help = L("A cor segue a categoria; escolha outra se quiser.", "The color follows the category; pick another if you like.", "El color sigue la categoría; elija otro si quiere.")) {
                ColorPicker(color, { color = it }, L("Cor do evento", "Event color", "Color del evento"))
            }
        }
        if (e != null) {
            if (e.recurring) Para(L("As alterações valem para todas as repetições.", "Changes apply to all repeats.", "Los cambios se aplican a todas las repeticiones."), Modifier.padding(top = 12.dp))
            SheetActions {
                AppButton(L("Excluir", "Delete", "Eliminar"), { ctl.deleteEvent(e) }, kind = BtnKind.Danger, icon = KIcons.Trash)
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DateChip(label: String, sub: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Look.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.heightIn(min = 54.dp).clip(shape).background(if (on) mix(c.primary, .16f, Look.current.card) else Look.current.card)
            .border(1.5.dp, if (on) c.primary else c.outlineVar, shape)
            .press(role = Role.RadioButton, onClick = onClick).semantics { selected = on }.padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.onSurface, maxLines = 1)
        Text(sub, fontSize = 11.sp, color = c.onSurfaceVar, maxLines = 1)
    }
}

/** Aviso do dia escolhido: folga, período, turno sem conflito ou conflito (com sugestão de horário depois do turno). */
@Composable
private fun ShiftContext(ctl: AppController, date: String, allDay: Boolean, start: String, end: String, onSuggest: (String, String) -> Unit) {
    if (!ctl.ready || !Dates.isDate(date)) return
    val c = Look.colors
    val sch = ctl.current
    val d = Dates.parse(date)
    val period = Engine.periodOn(date, sch)
    val conflict = if (!allDay) Agenda.conflict(date, start, end, sch) else null
    val shift = Agenda.shiftText(sch)
    val (tint, text) = when {
        period != null -> c.offStrong to L("Você está em ", "You're on ", "Está en ") + "\"${period.title.ifEmpty { L("período", "period", "período") }}\"" + L(" neste dia.", " this day.", " este día.")
        conflict != null -> c.workStrong to L("Conflita com seu turno ", "Overlaps your shift ", "Coincide con su turno ") + "(${conflict.start.toLocalTime()} – ${conflict.end.toLocalTime()})."
        !Agenda.isWork(d, sch) -> c.offStrong to L("Você está de folga neste dia.", "You're off this day.", "Está libre este día.")
        shift.isNotEmpty() -> hexColor("#3dd6a8") to L("Turno ", "Shift ", "Turno ") + shift + L(" neste dia. Sem conflito.", " this day. No overlap.", " este día. Sin conflicto.")
        else -> c.workStrong to L("Dia de trabalho na escala ", "Work day on ", "Día de trabajo en el horario ") + "\"${sch.name}\"."
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = .14f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (period != null || conflict == null && !Agenda.isWork(d, sch)) KIcons.Sun else KIcons.Briefcase, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
        if (conflict != null && conflict.end.toLocalDate() == d) {
            // sugere começar 30 min depois do fim do turno, mantendo a duração
            val dur = Personal.time(end)?.let { en -> Personal.time(start)?.let { st -> ((en.toSecondOfDay() - st.toSecondOfDay()) / 60).takeIf { it > 0 } } } ?: 60
            val t = conflict.end.toLocalTime().plusMinutes(30)
            if (t.isAfter(conflict.end.toLocalTime())) {
                val s = hm(t.hour, t.minute)
                AppButton(L("Usar ", "Use ", "Usar ") + s, { onSuggest(s, plusMinutes(s, dur)) }, kind = BtnKind.Text, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp))
            }
        }
    }
}

/** Aviso na folha do evento: o lembrete não vai tocar enquanto as notificações estiverem bloqueadas. */
@Composable
private fun NotificationsOff(onEnable: () -> Unit) {
    val c = Look.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).controlSurface(RoundedCornerShape(18.dp), mix(com.kalell.ui.theme.Danger, .12f, c.surface))
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Notifications, null, tint = com.kalell.ui.theme.Danger, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(L("Notificações desativadas", "Notifications are off", "Notificaciones desactivadas"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.onSurface)
            Text(L("O lembrete não vai tocar até você permitir as notificações do Kalell.", "The reminder won't ring until you allow notifications for Kalell.", "El recordatorio no sonará hasta que permita las notificaciones de Kalell."), fontSize = 12.sp, color = c.onSurfaceVar, lineHeight = 16.sp)
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
