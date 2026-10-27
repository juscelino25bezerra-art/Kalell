// Kalell — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalell.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalell.domain.Agenda
import com.kalell.domain.Dates
import com.kalell.domain.L
import com.kalell.domain.SearchHit
import com.kalell.ui.theme.HolidayRed
import com.kalell.ui.theme.Look
import com.kalell.ui.theme.hexColor

/** Busca em eventos (título, local, descrição) e notas, sem diferenciar acentos. */
@Composable
fun SearchSheet(ctl: AppController) {
    val c = Look.colors
    var q by rememberSaveable { mutableStateOf("") }
    AppSheet(ctl::closeSheet, L("Buscar", "Search", "Buscar")) {
        AppTextField(q, { q = it }, placeholder = L("Eventos, locais e notas", "Events, places and notes", "Eventos, lugares y notas"), maxLength = 60)
        val hits = Agenda.search(q, ctl.personal, if (ctl.ready) ctl.current else null, ctl.today, ctl.isOff)
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (q.isNotBlank() && hits.isEmpty()) Text(L("Nada encontrado.", "Nothing found.", "No se encontró nada."), color = c.onSurfaceVar, fontSize = 14.sp)
            hits.forEach { h ->
                val d = Dates.parse(h.date)
                val whenText = dayLabel(d, ctl.today).let { if (it.length > 12) Dates.fmt(d) else it }
                when (h) {
                    is SearchHit.EventHit -> {
                        val e = h.event
                        AgendaRow(hexColor(e.color), whenText, e.title, listOfNotNull(e.timeText, e.location.ifEmpty { null }, e.repeat.takeIf { e.recurring }?.label).joinToString(" · "), {
                            ctl.openEvent(e.date, ctl.baseOf(e))
                        }, icon = e.category.icon)
                    }
                    is SearchHit.PersonalNoteHit -> AgendaRow(hexColor(h.note.color), whenText, h.note.text, L("Nota pessoal", "Personal note", "Nota personal"), { ctl.openDayNote(h.date) }, icon = KIcons.Notes)
                    is SearchHit.WorkNoteHit -> AgendaRow(if (h.note.holiday) HolidayRed else hexColor(h.note.color ?: "#f9ab00"), whenText, h.note.text, L("Nota da escala", "Schedule note", "Nota del horario"), { ctl.openNote(h.date) }, icon = KIcons.Flag)
                }
            }
        }
    }
}

/** Próximos lembretes (7 dias), pelo horário em que vão tocar. */
@Composable
fun UpcomingSheet(ctl: AppController) {
    val c = Look.colors
    val list = ctl.upcomingReminders(7)
    AppSheet(ctl::closeSheet, L("Lembretes", "Reminders", "Recordatorios"), L("Avisos dos próximos 7 dias.", "Alerts for the next 7 days.", "Avisos de los próximos 7 días.")) {
        if (ctl.notifBlocked && list.isNotEmpty()) {
            AgendaRow(com.kalell.ui.theme.Danger, "", L("Notificações desativadas", "Notifications are off", "Notificaciones desactivadas"), L("Toque para permitir; sem isso os lembretes não tocam.", "Tap to allow; otherwise reminders won't ring.", "Toque para permitir; si no, los recordatorios no suenan."), ctl::enableNotifications, icon = KIcons.Bell)
        }
        if (list.isEmpty()) Text(L("Nenhum lembrete nos próximos 7 dias. Escolha um lembrete ao criar um evento.", "No reminders in the next 7 days. Pick a reminder when creating an event.", "No hay recordatorios en los próximos 7 días. Elija un recordatorio al crear un evento."), color = c.onSurfaceVar, fontSize = 14.sp, lineHeight = 20.sp)
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { (e, at) ->
                AgendaRow(
                    hexColor(e.color), at.toLocalTime().toString(), e.title,
                    dayLabel(at.toLocalDate(), ctl.today) + " · " + e.timeText,
                    { ctl.openEvent(e.date, ctl.baseOf(e.copy(id = e.id.substringBefore('@')))) }, icon = KIcons.Bell,
                )
            }
        }
        Text(L("Os lembretes funcionam sem internet.", "Reminders work offline.", "Los recordatorios funcionan sin internet."), fontSize = 12.sp, color = c.onSurfaceVar, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 14.dp))
    }
}
